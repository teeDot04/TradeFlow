"""
TradeFlow Sovereign Agent — Core
================================

This module is the single entry point that the Kotlin layer calls into via
Chaquopy. The lifecycle:

    boot(callback,
         okx_api_key, okx_api_secret, okx_api_passphrase,
         deepseek_api_key, db_path)
        Initialises the local SQLite journal (WAL), wires the AgentCallback
        proxy, and runs the asyncio event loop forever.

    shutdown()
        Signals the loop to drain and exit.

The loop itself is the ReAct pipeline:

    Sentry  : OKX websocket ticker stream -> 10-minute price ring buffer
    Trigger : >= 3 % drop within the buffer -> wake the Brain
    Brain   : DeepSeek `deepseek-v4-flash` JSON-only response
    Act     : if BUY and confidence > 80, market BUY of
              (USDT_balance * 0.9 * 0.5) at signed-V5 endpoint, paired with
              a -2.5 % stop loss, then 60 s asyncio.sleep cooldown.

Concurrency control:
    A single asyncio.Lock() wraps Trigger -> Think -> Act. Any redundant
    trigger that fires while the lock is held is silently dropped.

Crash safety:
    Every state mutation lands in `position_journal` so a process restart
    can read what was in flight without double-submitting an order.

This file is designed to also be importable from regular CPython for
local smoke testing — see `tools/agent_dry_run.py` in the project root.
"""

from __future__ import annotations

import asyncio
import base64
import datetime as _dt
import hashlib
import hmac
import json
import sqlite3
import time
import uuid
from collections import deque
from dataclasses import dataclass
from typing import Any, Deque, Mapping, Optional, Tuple

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

OKX_WS_PUBLIC = "wss://ws.okx.com:8443/ws/v5/public"
OKX_REST_BASE = "https://www.okx.com"

DEEPSEEK_ENDPOINT = "https://api.deepseek.com/chat/completions"
DEEPSEEK_MODEL = "deepseek-v4-flash"

INSTRUMENT = "BTC-USDT"
QUOTE_CCY = "USDT"
BUFFER_SECONDS = 600           # 10 minute rolling price buffer
DROP_TRIGGER_PCT = 3.0         # >= 3 % drop wakes the brain
CONFIDENCE_THRESHOLD = 80      # DeepSeek must be >= 80 to act

# Capital sizing rule:
#   exposure = quote_balance * RESERVE_RATIO * EXPOSURE_RATIO
# i.e. 50 % of 90 % of available balance.
RESERVE_RATIO = 0.90           # leave 10 % anchor for fees / dust
EXPOSURE_RATIO = 0.50          # of the reserved slice, deploy half

STOP_LOSS_PCT = 2.5            # -2.5 % protective stop
COOLDOWN_SECONDS = 60          # Mandatory cool-off after any execution


# ---------------------------------------------------------------------------
# Callback shim
# ---------------------------------------------------------------------------

class _CallbackProxy:
    """
    Wraps the Java `AgentCallback` PyObject so calls from inside asyncio
    coroutines never crash even if the JNI handle has been GC'd. Also
    provides a ``log`` convenience that maps to ``onStateChanged`` for
    plain text events.
    """

    def __init__(self, java_callback: Any) -> None:
        self._cb = java_callback

    def state(self, new_state: str) -> None:
        try:
            self._cb.onStateChanged(new_state)
        except Exception as exc:                   # pragma: no cover
            print(f"[callback.state] {exc}: {new_state}")

    def thought(self, text: str, confidence: int) -> None:
        try:
            self._cb.onThoughtGenerated(text, int(confidence))
        except Exception as exc:                   # pragma: no cover
            print(f"[callback.thought] {exc}: {text}")

    def trade(self, details: str) -> None:
        try:
            self._cb.onTradeExecuted(details)
        except Exception as exc:                   # pragma: no cover
            print(f"[callback.trade] {exc}: {details}")

    def error(self, message: str) -> None:
        try:
            self._cb.onError(message)
        except Exception as exc:                   # pragma: no cover
            print(f"[callback.error] {exc}: {message}")


# ---------------------------------------------------------------------------
# Position journal — SQLite + WAL
# ---------------------------------------------------------------------------

class PositionJournal:
    """Crash-safe journal of every order the agent has tried to place."""

    SCHEMA = """
        CREATE TABLE IF NOT EXISTS position_journal (
            client_order_id TEXT PRIMARY KEY,
            status          TEXT NOT NULL,
            entry_price     REAL,
            created_at      REAL NOT NULL DEFAULT (strftime('%s','now'))
        );
    """

    def __init__(self, db_path: str) -> None:
        self.db_path = db_path
        self._conn = sqlite3.connect(
            db_path,
            check_same_thread=False,
            isolation_level=None,  # autocommit; we control transactions
        )
        self._conn.execute("PRAGMA journal_mode=WAL;")
        self._conn.execute("PRAGMA synchronous=NORMAL;")
        self._conn.executescript(self.SCHEMA)

    def upsert(self, client_order_id: str, status: str,
               entry_price: Optional[float] = None) -> None:
        self._conn.execute(
            """
            INSERT INTO position_journal(client_order_id, status, entry_price)
            VALUES (?, ?, ?)
            ON CONFLICT(client_order_id) DO UPDATE SET
                status      = excluded.status,
                entry_price = COALESCE(excluded.entry_price, position_journal.entry_price)
            """,
            (client_order_id, status, entry_price),
        )

    def has_pending(self) -> bool:
        cur = self._conn.execute(
            "SELECT 1 FROM position_journal WHERE status IN ('PENDING','SUBMITTED') LIMIT 1"
        )
        return cur.fetchone() is not None

    def close(self) -> None:
        try:
            self._conn.close()
        except Exception:
            pass


# ---------------------------------------------------------------------------
# Sentry — rolling price buffer + drop detector
# ---------------------------------------------------------------------------

@dataclass
class TickSample:
    ts: float
    price: float


class PriceBuffer:
    """Time-bounded ring of (ts, price) — 10 minutes wide."""

    def __init__(self, window_seconds: int = BUFFER_SECONDS) -> None:
        self.window = window_seconds
        self._buf: Deque[TickSample] = deque()

    def push(self, price: float, ts: Optional[float] = None) -> None:
        ts = ts if ts is not None else time.time()
        self._buf.append(TickSample(ts=ts, price=float(price)))
        cutoff = ts - self.window
        while self._buf and self._buf[0].ts < cutoff:
            self._buf.popleft()

    def latest(self) -> Optional[float]:
        return self._buf[-1].price if self._buf else None

    def max_drop_pct(self) -> Tuple[float, Optional[float], Optional[float]]:
        """Return (drop %, peak, current) over the buffer; 0 if empty."""
        if len(self._buf) < 2:
            return 0.0, None, None
        peak = max(s.price for s in self._buf)
        cur = self._buf[-1].price
        if peak <= 0:
            return 0.0, peak, cur
        return ((peak - cur) / peak) * 100.0, peak, cur


# ---------------------------------------------------------------------------
# OKX V5 signed REST client
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class OkxCredentials:
    api_key: str
    api_secret: str
    passphrase: str

    def is_complete(self) -> bool:
        return bool(self.api_key and self.api_secret and self.passphrase)


def _okx_timestamp() -> str:
    """Millisecond ISO-8601 with trailing Z, exactly as OKX expects."""
    now = _dt.datetime.now(_dt.timezone.utc)
    return now.strftime("%Y-%m-%dT%H:%M:%S.") + f"{now.microsecond // 1000:03d}Z"


def _okx_sign(creds: OkxCredentials, ts: str, method: str,
              request_path: str, body: str) -> str:
    """
    OKX V5 signing:
        sign = base64( HMAC-SHA256(secret,
                                    timestamp + method + requestPath + body) )
    `request_path` MUST start with '/' and include the query string.
    `body` MUST be the empty string for GETs.
    """
    msg = f"{ts}{method.upper()}{request_path}{body}".encode("utf-8")
    digest = hmac.new(creds.api_secret.encode("utf-8"), msg,
                      hashlib.sha256).digest()
    return base64.b64encode(digest).decode("utf-8")


def _okx_headers(creds: OkxCredentials, method: str,
                 request_path: str, body: str) -> Mapping[str, str]:
    ts = _okx_timestamp()
    return {
        "Content-Type": "application/json",
        "OK-ACCESS-KEY": creds.api_key,
        "OK-ACCESS-SIGN": _okx_sign(creds, ts, method, request_path, body),
        "OK-ACCESS-TIMESTAMP": ts,
        "OK-ACCESS-PASSPHRASE": creds.passphrase,
    }


async def _okx_request(creds: OkxCredentials, method: str,
                       request_path: str,
                       body_obj: Optional[dict] = None) -> dict:
    """
    Run a signed OKX REST call without blocking the asyncio loop.
    Returns the parsed JSON body or `{"code":"-1","msg":"<exc>"}` on failure.
    """
    import requests  # Chaquopy provides this at build time

    body = json.dumps(body_obj, separators=(",", ":")) if body_obj else ""
    headers = _okx_headers(creds, method, request_path, body)
    url = f"{OKX_REST_BASE}{request_path}"

    def _do() -> dict:
        if method.upper() == "GET":
            resp = requests.get(url, headers=headers, timeout=10)
        else:
            resp = requests.request(
                method.upper(),
                url,
                headers=headers,
                data=body,
                timeout=10,
            )
        # OKX returns 200 even for business errors; let JSON drive truth.
        try:
            return resp.json()
        except ValueError:
            return {"code": str(resp.status_code), "msg": resp.text[:200]}

    loop = asyncio.get_event_loop()
    try:
        return await loop.run_in_executor(None, _do)
    except Exception as exc:
        return {"code": "-1", "msg": f"{exc!r}"}


async def okx_quote_balance(creds: OkxCredentials,
                             ccy: str = QUOTE_CCY) -> float:
    """
    Return available `ccy` balance from the OKX trading account.

    OKX V5 `GET /api/v5/account/balance?ccy=USDT` returns:
      { "code":"0", "data":[{ "details":[{ "ccy":"USDT", "availBal":"123.45", ... }]}]}
    """
    path = f"/api/v5/account/balance?ccy={ccy}"
    body = await _okx_request(creds, "GET", path)
    if str(body.get("code")) != "0":
        return 0.0
    try:
        details = body["data"][0]["details"]
        for row in details:
            if row.get("ccy") == ccy:
                return float(row.get("availBal") or 0.0)
    except (KeyError, IndexError, ValueError, TypeError):
        return 0.0
    return 0.0


def compute_trade_exposure(quote_balance: float) -> float:
    """50 % of 90 % of available balance, rounded down to 2 dp."""
    raw = quote_balance * RESERVE_RATIO * EXPOSURE_RATIO
    # Floor to 2 decimal places to keep the order size deterministic.
    return float(int(raw * 100)) / 100.0


# ---------------------------------------------------------------------------
# OKX websocket sentry
# ---------------------------------------------------------------------------

async def _okx_subscribe(ws) -> None:
    sub = {
        "op": "subscribe",
        "args": [{"channel": "tickers", "instId": INSTRUMENT}],
    }
    await ws.send(json.dumps(sub))


async def okx_ticker_stream(buffer: PriceBuffer, cb: _CallbackProxy) -> None:
    """
    Long-lived OKX websocket consumer. Reconnects with exponential backoff
    so a transient socket reset never kills the sentry. Each tick updates
    the rolling buffer in place.
    """
    import websockets

    backoff = 1.0
    while True:
        try:
            cb.state("SENTRY:CONNECTING")
            async with websockets.connect(
                OKX_WS_PUBLIC, ping_interval=20, ping_timeout=20
            ) as ws:
                await _okx_subscribe(ws)
                cb.state("SENTRY:LISTENING")
                backoff = 1.0
                async for raw in ws:
                    try:
                        msg = json.loads(raw)
                    except json.JSONDecodeError:
                        continue
                    data = msg.get("data") or []
                    if not data:
                        continue
                    last = data[0].get("last")
                    if last is None:
                        continue
                    buffer.push(float(last))
        except Exception as exc:
            cb.error(f"WS dropped: {exc!r} — reconnecting in {backoff:.1f}s")
            await asyncio.sleep(backoff)
            backoff = min(backoff * 2.0, 30.0)


# ---------------------------------------------------------------------------
# OKX signed order placement
# ---------------------------------------------------------------------------

async def okx_market_buy(creds: OkxCredentials,
                          usd_size: float,
                          last_price: float) -> Tuple[str, float, dict]:
    """
    Submit a fully-signed market BUY worth `usd_size` USDT against
    `INSTRUMENT`. Returns (client_order_id, fill_price_estimate, raw_response).
    """
    client_order_id = uuid.uuid4().hex[:24]
    payload = {
        "instId": INSTRUMENT,
        "tdMode": "cash",
        "clOrdId": client_order_id,
        "side": "buy",
        "ordType": "market",
        "sz": f"{usd_size:.2f}",
        "tgtCcy": "quote_ccy",  # `sz` is denominated in quote currency (USDT)
    }
    response = await _okx_request(creds, "POST", "/api/v5/trade/order",
                                   body_obj=payload)
    return client_order_id, last_price, response


async def okx_place_stop_loss(creds: OkxCredentials,
                               entry_price: float,
                               base_size: float) -> Tuple[str, dict]:
    """
    Place a paired protective stop using OKX's algo-order endpoint.
    `base_size` is the asset quantity (e.g. BTC), not USDT.
    """
    trigger_px = round(entry_price * (1 - STOP_LOSS_PCT / 100.0), 2)
    client_order_id = "sl_" + uuid.uuid4().hex[:20]
    payload = {
        "instId": INSTRUMENT,
        "tdMode": "cash",
        "algoClOrdId": client_order_id,
        "side": "sell",
        "ordType": "conditional",
        "sz": f"{base_size:.6f}",
        "slTriggerPx": f"{trigger_px}",
        "slOrdPx": "-1",   # market price on trigger
    }
    response = await _okx_request(creds, "POST", "/api/v5/trade/order-algo",
                                   body_obj=payload)
    return client_order_id, response


# ---------------------------------------------------------------------------
# Brain — DeepSeek v4-flash
# ---------------------------------------------------------------------------

SYSTEM_PROMPT = (
    "You are a highly analytical quantitative execution agent. "
    "No pleasantries. Evaluate the anomaly. "
    "Respond ONLY as a JSON object with keys "
    '"action" ("BUY" or "HOLD"), "rationale" (string), '
    '"confidence" (integer 0-100).'
)


async def deepseek_evaluate(api_key: str, drop_pct: float,
                             peak: float, current: float) -> dict:
    """
    Synchronous network call wrapped via run_in_executor so it never
    blocks the event loop. Returns the parsed JSON dict, or a HOLD
    fallback on any failure.
    """
    import requests

    user_msg = (
        f"Instrument: {INSTRUMENT}. "
        f"Drop: {drop_pct:.2f}% over the last 10 minutes "
        f"(peak={peak:.2f}, current={current:.2f}). "
        "Evaluate. Respond strictly as JSON."
    )
    payload = {
        "model": DEEPSEEK_MODEL,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_msg},
        ],
        "response_format": {"type": "json_object"},
        "temperature": 0.0,
    }
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }

    def _do_request() -> dict:
        resp = requests.post(
            DEEPSEEK_ENDPOINT,
            headers=headers,
            data=json.dumps(payload),
            timeout=15,
        )
        resp.raise_for_status()
        body = resp.json()
        content = body["choices"][0]["message"]["content"]
        return json.loads(content)

    loop = asyncio.get_event_loop()
    try:
        return await loop.run_in_executor(None, _do_request)
    except Exception as exc:
        return {
            "action": "HOLD",
            "rationale": f"DeepSeek failure: {exc!r}",
            "confidence": 0,
        }


# ---------------------------------------------------------------------------
# Pipeline — Trigger -> Think -> Act, lock-protected
# ---------------------------------------------------------------------------

class AgentLoop:

    def __init__(self, callback: _CallbackProxy,
                 okx_creds: OkxCredentials,
                 deepseek_key: str,
                 journal: PositionJournal) -> None:
        self.cb = callback
        self.okx = okx_creds
        self.deepseek_key = deepseek_key
        self.journal = journal
        self.buffer = PriceBuffer()
        self.lock = asyncio.Lock()
        self._stopping = False

    async def sentry_task(self) -> None:
        await okx_ticker_stream(self.buffer, self.cb)

    async def trigger_task(self) -> None:
        """Polls the rolling buffer at 1 Hz and fires the pipeline on drops."""
        self.cb.state("SENTRY:READY")
        while not self._stopping:
            await asyncio.sleep(1.0)
            drop, peak, cur = self.buffer.max_drop_pct()
            if drop >= DROP_TRIGGER_PCT and peak is not None and cur is not None:
                if self.lock.locked():
                    # Another evaluation is already in flight. Drop silently.
                    continue
                asyncio.create_task(self._run_pipeline(drop, peak, cur))

    async def _run_pipeline(self, drop: float, peak: float, cur: float) -> None:
        async with self.lock:
            self.cb.state("TRIGGERED")
            self.cb.thought(
                f"Volatility spike: -{drop:.2f}% (peak={peak:.2f}, now={cur:.2f})",
                100,
            )

            # ---- THINK -------------------------------------------------
            self.cb.state("THINKING")
            decision = await deepseek_evaluate(
                self.deepseek_key, drop, peak, cur
            )
            action = str(decision.get("action", "HOLD")).upper()
            rationale = str(decision.get("rationale", "")).strip()
            confidence = int(decision.get("confidence", 0))

            self.cb.thought(rationale or "(no rationale)", confidence)

            # ---- ACT ---------------------------------------------------
            if action == "BUY" and confidence > CONFIDENCE_THRESHOLD:
                await self._execute_buy(cur)

            else:
                self.cb.thought(
                    f"HOLD — action={action} confidence={confidence}",
                    confidence,
                )

            # ---- COOLDOWN ----------------------------------------------
            self.cb.state("COOLDOWN")
            await asyncio.sleep(COOLDOWN_SECONDS)
            self.cb.state("SENTRY:READY")

    async def _execute_buy(self, last_price: float) -> None:
        self.cb.state("SIZING")
        balance = await okx_quote_balance(self.okx)
        if balance <= 0:
            self.cb.error("Balance check returned 0 — skipping execution.")
            return

        usd_size = compute_trade_exposure(balance)
        if usd_size <= 0:
            self.cb.error(
                f"Computed exposure ${usd_size:.2f} from balance "
                f"${balance:.2f} — skipping execution."
            )
            return

        self.cb.thought(
            f"Sizing: balance=${balance:.2f}  "
            f"reserve={RESERVE_RATIO*100:.0f}%  "
            f"exposure={EXPOSURE_RATIO*100:.0f}%  "
            f"-> ${usd_size:.2f}",
            100,
        )

        self.cb.state("EXECUTING")
        client_order_id, fill_price, buy_resp = await okx_market_buy(
            self.okx, usd_size, last_price
        )
        if str(buy_resp.get("code")) != "0":
            self.journal.upsert(client_order_id, "FAILED", fill_price)
            self.cb.error(
                f"BUY rejected: code={buy_resp.get('code')} "
                f"msg={buy_resp.get('msg')}"
            )
            return

        self.journal.upsert(client_order_id, "SUBMITTED", fill_price)

        base_qty = usd_size / fill_price if fill_price > 0 else 0.0
        sl_id, sl_resp = await okx_place_stop_loss(
            self.okx, fill_price, base_qty
        )
        sl_status = "STOP_PLACED" if str(sl_resp.get("code")) == "0" else "STOP_FAILED"
        self.journal.upsert(sl_id, sl_status, fill_price)

        self.cb.trade(
            f"BUY ${usd_size:.2f} {INSTRUMENT} @ ~{fill_price:.2f} "
            f"| stop @ -{STOP_LOSS_PCT}% ({sl_status}) | id={client_order_id}"
        )

    async def run(self) -> None:
        await asyncio.gather(self.sentry_task(), self.trigger_task())

    def stop(self) -> None:
        self._stopping = True


# ---------------------------------------------------------------------------
# Public entry points (called from Kotlin via Chaquopy)
# ---------------------------------------------------------------------------

_LOOP_HANDLE: Optional[AgentLoop] = None
_RUNNING_LOOP: Optional[asyncio.AbstractEventLoop] = None


def boot(callback: Any,
         okx_api_key: str,
         okx_api_secret: str,
         okx_api_passphrase: str,
         deepseek_api_key: str,
         db_path: str) -> None:
    """
    Block forever running the agent's asyncio loop. Invoked from the
    dedicated Python single-thread executor on the Kotlin side.
    """
    global _LOOP_HANDLE, _RUNNING_LOOP

    cb = _CallbackProxy(callback)
    cb.state("BOOTING")

    creds = OkxCredentials(
        api_key=okx_api_key,
        api_secret=okx_api_secret,
        passphrase=okx_api_passphrase,
    )
    if not creds.is_complete():
        cb.error("Incomplete OKX credentials — aborting boot.")
        cb.state("STOPPED")
        return

    journal = PositionJournal(db_path)
    cb.state("JOURNAL_READY")

    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    _RUNNING_LOOP = loop

    agent = AgentLoop(cb, creds, deepseek_api_key, journal)
    _LOOP_HANDLE = agent

    try:
        loop.run_until_complete(agent.run())
    except Exception as exc:
        cb.error(f"Agent loop crashed: {exc!r}")
    finally:
        journal.close()
        loop.close()
        _LOOP_HANDLE = None
        _RUNNING_LOOP = None
        cb.state("STOPPED")


def shutdown() -> None:
    """Request the loop to drain. Safe to call from any thread."""
    if _LOOP_HANDLE is not None:
        _LOOP_HANDLE.stop()
    if _RUNNING_LOOP is not None and _RUNNING_LOOP.is_running():
        _RUNNING_LOOP.call_soon_threadsafe(_RUNNING_LOOP.stop)


# ---------------------------------------------------------------------------
# Dry-run helpers (used by the local Replit status server)
# ---------------------------------------------------------------------------

def describe() -> dict:
    """Return a static description of the agent — used for diagnostics."""
    return {
        "instrument": INSTRUMENT,
        "quote_ccy": QUOTE_CCY,
        "buffer_seconds": BUFFER_SECONDS,
        "drop_trigger_pct": DROP_TRIGGER_PCT,
        "confidence_threshold": CONFIDENCE_THRESHOLD,
        "sizing_rule": (
            f"quote_balance * {RESERVE_RATIO} (reserve) * "
            f"{EXPOSURE_RATIO} (exposure) "
            f"= {RESERVE_RATIO * EXPOSURE_RATIO * 100:.0f}% of balance"
        ),
        "reserve_ratio": RESERVE_RATIO,
        "exposure_ratio": EXPOSURE_RATIO,
        "stop_loss_pct": STOP_LOSS_PCT,
        "cooldown_seconds": COOLDOWN_SECONDS,
        "deepseek_model": DEEPSEEK_MODEL,
        "okx_ws": OKX_WS_PUBLIC,
        "okx_rest": OKX_REST_BASE,
    }


def smoke_test(db_path: str) -> dict:
    """
    Initialise the journal and the price buffer, push a synthetic
    -3.5 % drop into the buffer, exercise the V5 signing routine on a
    deterministic timestamp, and report what the trigger condition would
    have produced. No network. No order submission.
    """
    journal = PositionJournal(db_path)
    buf = PriceBuffer()

    base = 100_000.0
    now = time.time()
    for i in range(0, 600, 5):
        buf.push(base + (i % 7) - 3.5, ts=now - (600 - i))
    buf.push(base * (1 - 0.035), ts=now)

    drop, peak, cur = buf.max_drop_pct()
    journal.upsert("smoke-1", "PENDING", entry_price=cur)
    journal.upsert("smoke-1", "SUBMITTED", entry_price=cur)
    journal.close()

    # Verify the V5 signature against the official OKX worked example.
    # (https://www.okx.com/docs-v5/en/#rest-api-authentication-signature)
    test_creds = OkxCredentials(
        api_key="dummy",
        api_secret="22582BD0CFF14C41EDBF1AB98506286D",
        passphrase="dummy-pass",
    )
    sig = _okx_sign(
        test_creds,
        ts="2020-12-08T09:08:57.715Z",
        method="GET",
        request_path="/api/v5/account/balance?ccy=BTC",
        body="",
    )

    sample_balances = [50.0, 100.0, 155.0, 1000.0]
    sizing_examples = {
        f"${b:.2f}": f"${compute_trade_exposure(b):.2f}"
        for b in sample_balances
    }

    return {
        "ok": True,
        "drop_pct": round(drop, 4),
        "peak": peak,
        "current": cur,
        "would_trigger": drop >= DROP_TRIGGER_PCT,
        "v5_signature_sample": sig,
        "sizing_examples": sizing_examples,
        "journal_path": db_path,
    }
