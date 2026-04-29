import asyncio
import collections
import json
import sqlite3
import time
import hmac
import base64
import hashlib
import requests
import websockets
import threading

# Configuration
PAIRS = ["BTC-USDT", "SOL-USDT", "ETH-USDT", "LINK-USDT"]
DEEPSEEK_URL = "https://api.deepseek.com/chat/completions"
OKX_REST_URL = "https://www.okx.com"
OKX_WS_URL = "wss://ws.okx.com:8443/ws/v5/public"

# State
buffers = {pair: collections.deque(maxlen=600) for pair in PAIRS} # 10 min at 1Hz max
current_book = {pair: {'ask': 0.0, 'bid': 0.0} for pair in PAIRS}
global_lock = asyncio.Lock()
agent_loop = None
agent_thread = None
system_enabled = False
hibernate = False

def set_system_enabled(active):
    global system_enabled
    system_enabled = active
    if ctx.callback:
        ctx.callback.onStateChanged(f"Sovereign Control: {'ACTIVE' if active else 'DISABLED'}")

class AgentContext:
    okx_key = ""
    okx_secret = ""
    okx_pass = ""
    deepseek_key = ""
    db_path = ""
    callback = None
    running = False

ctx = AgentContext()

def init_db():
    conn = sqlite3.connect(ctx.db_path)
    conn.execute("PRAGMA journal_mode=WAL;")
    conn.execute("""
        CREATE TABLE IF NOT EXISTS position_journal (
            client_order_id TEXT PRIMARY KEY,
            status TEXT,
            entry_price REAL,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            rationale TEXT
        )
    """)
    try:
        conn.execute("ALTER TABLE position_journal ADD COLUMN created_at DATETIME DEFAULT CURRENT_TIMESTAMP")
    except sqlite3.OperationalError:
        pass
    try:
        conn.execute("ALTER TABLE position_journal ADD COLUMN rationale TEXT")
    except sqlite3.OperationalError:
        pass
    conn.commit()
    return conn

def reconcile_flight():
    global hibernate
    conn = init_db()
    
    # Check circuit breaker
    cursor = conn.execute("SELECT COUNT(*) FROM position_journal WHERE status='LIQUIDATED' AND created_at > datetime('now', '-24 hours')")
    count = cursor.fetchone()[0]
    
    if count >= 3:
        hibernate = True
        if ctx.callback:
            ctx.callback.onStateChanged("HIBERNATE: Daily Risk Ceiling Reached.")
    else:
        hibernate = False

    # Ghost Check
    try:
        headers = sign_request("GET", "/api/v5/trade/orders-pending")
        resp = requests.get(OKX_REST_URL + "/api/v5/trade/orders-pending", headers=headers, timeout=10)
        data = resp.json()
        if data.get("code") == "0" and data.get("data"):
            cursor = conn.cursor()
            for order in data["data"]:
                cl_ord_id = order.get("clOrdId")
                ord_id = order.get("ordId")
                if not cl_ord_id:
                    continue
                cursor.execute("SELECT status FROM position_journal WHERE client_order_id=?", (cl_ord_id,))
                row = cursor.fetchone()
                if not row or row[0] not in ['OPEN', 'PENDING']:
                    # Ghost position detected
                    cursor.execute("INSERT OR REPLACE INTO position_journal (client_order_id, status, entry_price, rationale) VALUES (?, ?, ?, ?)", 
                                 (cl_ord_id, "OPEN", float(order.get("px", 0)), "GHOST_RECOVERY"))
                    if ctx.callback:
                        ctx.callback.onThoughtGenerated(f"[RECOVERY] Ghost position detected and re-synchronized: {cl_ord_id}", 100)
            conn.commit()
    except Exception as e:
        if ctx.callback:
            ctx.callback.onStateChanged(f"Ghost check failed: {str(e)}")

def sign_request(method, request_path, body=""):
    timestamp = str(time.time()).split('.')[0] + '.' + str(time.time()).split('.')[1][:3]
    if '.' not in timestamp:
        timestamp += '.000'
    else:
        timestamp = timestamp.ljust(14, '0')[:14] # Format: 2020-12-08T09:08:57.715Z... Wait, OKX uses ISO format
        
    import datetime
    timestamp = datetime.datetime.utcnow().isoformat()[:-3] + 'Z'
        
    message = timestamp + method + request_path + body
    mac = hmac.new(bytes(ctx.okx_secret, encoding='utf8'), bytes(message, encoding='utf-8'), digestmod='sha256')
    d = mac.digest()
    sign = base64.b64encode(d).decode('utf-8')
    return {
        "OK-ACCESS-KEY": ctx.okx_key,
        "OK-ACCESS-SIGN": sign,
        "OK-ACCESS-TIMESTAMP": timestamp,
        "OK-ACCESS-PASSPHRASE": ctx.okx_pass,
        "Content-Type": "application/json"
    }

def execute_trade(pair, current_price, ask, bid, rationale):
    ctx.callback.onStateChanged(f"Executing TRADE logic for {pair}")
    try:
        # Slippage Guard
        if bid > 0:
            spread = (ask - bid) / bid
            if spread > 0.005:
                ctx.callback.onStateChanged(f"Slippage Guard: Spread {spread*100:.2f}% > 0.5%. Aborting.")
                return

        # 1. Get Balance
        headers = sign_request("GET", "/api/v5/account/balance?ccy=USDT")
        resp = requests.get(OKX_REST_URL + "/api/v5/account/balance?ccy=USDT", headers=headers)
        data = resp.json()
        if data.get("code") != "0":
            ctx.callback.onStateChanged(f"Balance error: {data}")
            return
            
        avail_bal = float(data["data"][0]["details"][0]["availBal"])
        trade_usdt = avail_bal * 0.90 * 0.50
        
        ctx.callback.onStateChanged(f"Avail: {avail_bal}, Size: {trade_usdt} USDT")
        
        if trade_usdt < 10.0:
            ctx.callback.onStateChanged("[SYSTEM] Balance insufficient for minimum order.")
            return

        # 2. Market BUY with Attached TP/SL
        import uuid
        client_oid = uuid.uuid4().hex
        
        # Log to Journal
        conn = init_db()
        conn.execute("INSERT INTO position_journal (client_order_id, status, entry_price, rationale) VALUES (?, ?, ?, ?)", 
                     (client_oid, "PENDING", current_price, rationale))
        conn.commit()
        
        sl_price = current_price * 0.975
        buy_payload = json.dumps({
            "instId": pair,
            "tdMode": "cash",
            "side": "buy",
            "ordType": "market",
            "sz": str(trade_usdt),
            "tgtCcy": "quote_ccy",
            "clOrdId": client_oid,
            "attachAlgoOrds": [{
                "attachAlgoOrd": "1",
                "slTriggerPx": str(sl_price),
                "slOrdPx": "-1"
            }]
        })
        
        buy_headers = sign_request("POST", "/api/v5/trade/order", buy_payload)
        buy_resp = requests.post(OKX_REST_URL + "/api/v5/trade/order", headers=buy_headers, data=buy_payload)
        buy_res_data = buy_resp.json()
        
        if buy_res_data.get("code") != "0":
            conn.execute("UPDATE position_journal SET status='FAILED' WHERE client_order_id=?", (client_oid,))
            conn.commit()
            ctx.callback.onStateChanged(f"Buy failed: {buy_res_data}")
            return
            
        conn.execute("UPDATE position_journal SET status='OPEN' WHERE client_order_id=?", (client_oid,))
        conn.commit()
        ctx.callback.onTradeExecuted(f"Atomic Market BUY {pair} at {current_price} with SL {sl_price}")
        
    except Exception as e:
        ctx.callback.onStateChanged(f"Execution Error: {str(e)}")

def panic_close_all():
    ctx.callback.onThoughtGenerated("[PANIC] Initiating Emergency Close All...", 100)
    set_system_enabled(False)
    
    try:
        # 1. Cancel Algos
        headers = sign_request("GET", "/api/v5/trade/orders-algo-pending?ordType=conditional")
        resp = requests.get(OKX_REST_URL + "/api/v5/trade/orders-algo-pending?ordType=conditional", headers=headers)
        data = resp.json()
        if data.get("code") == "0" and data.get("data"):
            cancels = [{"instId": ord["instId"], "algoId": ord["algoId"]} for ord in data["data"]]
            if cancels:
                cancel_payload = json.dumps(cancels)
                cancel_headers = sign_request("POST", "/api/v5/trade/cancel-algos", cancel_payload)
                requests.post(OKX_REST_URL + "/api/v5/trade/cancel-algos", headers=cancel_headers, data=cancel_payload)
                ctx.callback.onStateChanged(f"[PANIC] Canceled {len(cancels)} algo orders.")

        # 2. Market Sells
        headers = sign_request("GET", "/api/v5/account/balance")
        resp = requests.get(OKX_REST_URL + "/api/v5/account/balance", headers=headers)
        data = resp.json()
        if data.get("code") == "0":
            for detail in data["data"][0]["details"]:
                ccy = detail["ccy"]
                avail = float(detail["availBal"])
                if ccy != "USDT" and avail > 0:
                    inst_id = f"{ccy}-USDT"
                    if inst_id in PAIRS:
                        sell_payload = json.dumps({
                            "instId": inst_id,
                            "tdMode": "cash",
                            "side": "sell",
                            "ordType": "market",
                            "sz": str(avail)
                        })
                        sell_headers = sign_request("POST", "/api/v5/trade/order", sell_payload)
                        requests.post(OKX_REST_URL + "/api/v5/trade/order", headers=sell_headers, data=sell_payload)
                        ctx.callback.onStateChanged(f"[PANIC] Market Sold {avail} {ccy}.")
    except Exception as e:
        ctx.callback.onStateChanged(f"[PANIC] Error during close all: {str(e)}")

async def call_deepseek(pair, peak, current, delta):
    ctx.callback.onStateChanged(f"Querying DeepSeek for {pair}...")
    prompt = f"The crypto pair {pair} has dropped from a peak of {peak} to {current} (a drop of {delta*100:.2f}%). Analyze the market context and decide if this is a buy opportunity. Respond strictly in JSON format: {{\"action\": \"BUY\" or \"HOLD\", \"rationale\": \"...\", \"confidence\": 0-100}}"
    
    headers = {
        "Authorization": f"Bearer {ctx.deepseek_key}",
        "Content-Type": "application/json"
    }
    payload = {
        "model": "deepseek-v4-flash",
        "messages": [
            {"role": "system", "content": "You are a crypto trading agent. Return only valid JSON."},
            {"role": "user", "content": prompt}
        ],
        "response_format": {"type": "json_object"}
    }
    
    try:
        resp = requests.post(DEEPSEEK_URL, headers=headers, json=payload, timeout=10)
        data = resp.json()
        content = data['choices'][0]['message']['content']
        parsed = json.loads(content)
        ctx.callback.onThoughtGenerated(parsed.get('rationale', 'No rationale'), parsed.get('confidence', 0))
        return parsed
    except Exception as e:
        ctx.callback.onThoughtGenerated(f"DeepSeek Error: {str(e)}", 0)
        return {"action": "HOLD", "confidence": 0}

async def trigger_scan():
    while ctx.running:
        if not system_enabled or hibernate:
            if hibernate and ctx.callback and time.time() % 60 < 1: # broadcast hibernate rarely
                ctx.callback.onStateChanged("HIBERNATE: Daily Risk Ceiling Reached .")
            await asyncio.sleep(1)
            continue
            
        if not global_lock.locked():
            for pair in PAIRS:
                buf = buffers[pair]
                if len(buf) > 10:
                    current_price = buf[-1]
                    peak_price = max(buf)
                    delta = (peak_price - current_price) / peak_price
                    
                    if delta >= 0.03:
                        # Attempt to acquire lock
                        acquired = await global_lock.acquire()
                        if acquired:
                            try:
                                ctx.callback.onStateChanged(f"Trigger! {pair} dropped > 3%")
                                decision = await call_deepseek(pair, peak_price, current_price, delta)
                                
                                if decision.get("action") == "BUY" and int(decision.get("confidence", 0)) > 80:
                                    ask = current_book[pair]['ask']
                                    bid = current_book[pair]['bid']
                                    rationale = decision.get("rationale", "")
                                    execute_trade(pair, current_price, ask, bid, rationale)
                                
                                ctx.callback.onStateChanged("Entering 60s cooldown.")
                                await asyncio.sleep(60)
                            finally:
                                # Reset buffer so we don't immediately trigger again
                                buffers[pair].clear()
                                global_lock.release()
                                break
        await asyncio.sleep(1)

async def ws_listener():
    subscribe_msg = {
        "op": "subscribe",
        "args": [{"channel": "tickers", "instId": pair} for pair in PAIRS]
    }
    
    while ctx.running:
        try:
            ctx.callback.onStateChanged("Connecting to OKX WS...")
            async with websockets.connect(OKX_WS_URL) as ws:
                await ws.send(json.dumps(subscribe_msg))
                ctx.callback.onStateChanged("Subscribed to OKX Tickers")
                
                while ctx.running:
                    msg = await ws.recv()
                    data = json.loads(msg)
                    if "data" in data:
                        for item in data["data"]:
                            instId = item["instId"]
                            last_price = float(item["last"])
                            ask_px = float(item.get("askPx", 0))
                            bid_px = float(item.get("bidPx", 0))
                            buffers[instId].append(last_price)
                            current_book[instId]['ask'] = ask_px
                            current_book[instId]['bid'] = bid_px
                        if ctx.callback:
                            ctx.callback.onHeartbeat()
        except Exception as e:
            ctx.callback.onStateChanged(f"WS Error: {str(e)}")
            await asyncio.sleep(5)

def run_async_loop():
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    global agent_loop
    agent_loop = loop
    
    loop.create_task(ws_listener())
    loop.create_task(trigger_scan())
    
    loop.run_forever()

def start_agent(okx_key, okx_secret, okx_pass, deepseek_key, db_path, callback):
    ctx.okx_key = okx_key
    ctx.okx_secret = okx_secret
    ctx.okx_pass = okx_pass
    ctx.deepseek_key = deepseek_key
    ctx.db_path = db_path
    ctx.callback = callback
    ctx.running = True
    
    init_db()
    reconcile_flight()
    
    callback.onStateChanged("Agent initializing...")
    
    global agent_thread
    agent_thread = threading.Thread(target=run_async_loop)
    agent_thread.start()

def stop_agent():
    ctx.running = False
    ctx.callback.onStateChanged("Stopping agent...")
    if agent_loop:
        agent_loop.call_soon_threadsafe(agent_loop.stop)
    if agent_thread:
        agent_thread.join(timeout=5)
