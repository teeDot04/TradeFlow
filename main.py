import urllib.request
import json
import time
import math
import os
import sys
from datetime import datetime

try:
    import ccxt
except ImportError:
    ccxt = None

# ============================================================
# ⚙️ CONFIGURATION (HYBRID: CHOP + PYRAMID TURTLE)
# ============================================================
SYMBOLS = ['SOL-USDT', 'LINK-USDT', 'XRP-USDT', 'DOGE-USDT', 'ETH-USDT']
# Check Environment for Real Trading
IS_PAPER = os.environ.get('IS_PAPER', 'True').lower() == 'true'

EXCHANGE = None
if not IS_PAPER and ccxt:
    try:
        EXCHANGE = ccxt.okx({
            'apiKey': os.environ.get('OKX_API_KEY'),
            'secret': os.environ.get('OKX_SECRET'),
            'password': os.environ.get('OKX_PASSWORD'),
        })
        print("✅ OKX Exchange Connected")
    except Exception as e:
        print(f"❌ Failed to connect to OKX: {e}")

TIMEFRAME = '4H'

# COMMON PARAMS
ALLOCATION_PER_SYMBOL = 200 # Starting Allocation (if fixed)
# But we use State Balance mostly.
MAX_ATR_PCT = 0.05 

# MEAN REVERSION (CHOP)
MR_RSI_MIN = 30
MR_RSI_MAX = 45 # Slightly wider for 4H
MR_TP_PCT = 0.025
MR_SL_PCT = 0.015

# TURTLE PYRAMID (BULL)
DONCHIAN_ENTRY = 120 
DONCHIAN_EXIT = 60
PYRAMID_MAX_ADDS = 3
PYRAMID_ADD_TRIGGER_ATR = 0.5

# FILES
STATE_FILE = "bot_state.json"
JOURNAL_FILE = "trades_journal.csv"

# ============================================================
# 🛠️ UTILS & INDICATORS
# ============================================================
def load_state():
    if os.path.exists(STATE_FILE):
        try:
            with open(STATE_FILE, 'r') as f:
                return json.load(f)
        except:
            pass
    # Default State
    return {
        "balance": 10000.0,
        "positions": {}, # symbol -> {entry, avg_price, size, tp, sl, type, adds_count, last_add_price}
        "history": [] # Recently closed trades
    }

def save_state(state):
    with open(STATE_FILE, 'w') as f:
        json.dump(state, f, indent=4)

def log_trade(symbol, action, price, size, reason, pnl=0):
    # Append to CSV for Android App
    # Format: timestamp, symbol, action, price, size, pnl, reason
    file_exists = os.path.exists(JOURNAL_FILE)
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    with open(JOURNAL_FILE, 'a') as f:
        if not file_exists:
            f.write("timestamp,symbol,action,price,size,pnl,reason\n")
        f.write(f"{timestamp},{symbol},{action},{price},{size},{pnl},{reason}\n")
    print(f"📝 Journaled: {action} {symbol} @ {price}")

def rma(data, period):
    if len(data) < period: return [None] * len(data)
    alpha = 1 / period
    res = [None] * len(data)
    res[period-1] = sum(data[:period]) / period
    for i in range(period, len(data)):
        res[i] = (data[i] * alpha) + (res[i-1] * (1 - alpha))
    return res

def calc_rsi(closes, period=14):
    if len(closes) < period + 1: return None
    deltas = [closes[i] - closes[i-1] for i in range(1, len(closes))]
    gains = [max(0, d) for d in deltas]
    losses = [max(0, -d) for d in deltas]
    avg_gain = rma(gains, period)
    avg_loss = rma(losses, period)
    if avg_loss[-1] == 0: return 100.0
    rs = avg_gain[-1] / avg_loss[-1]
    return 100 - (100 / (1 + rs))

def calc_atr(highs, lows, closes, period=14):
    if len(closes) < period: return None
    tr = []
    for i in range(len(closes)):
        if i == 0:
            tr.append(highs[i] - lows[i])
        else:
            hl = highs[i] - lows[i]
            hc = abs(highs[i] - closes[i-1])
            lc = abs(lows[i] - closes[i-1])
            tr.append(max(hl, hc, lc))
    atr_series = rma(tr, period)
    return atr_series[-1]

def calc_full_adx(highs, lows, closes, period=14):
    if len(closes) < period*2: return 0
    dm_plus = []
    dm_minus = []
    tr = []
    for i in range(len(closes)):
        if i == 0:
            dm_plus.append(0); dm_minus.append(0); tr.append(highs[i]-lows[i])
            continue
        up = highs[i] - highs[i-1]
        down = lows[i-1] - lows[i]
        dm_plus.append(up if up > down and up > 0 else 0)
        dm_minus.append(down if down > up and down > 0 else 0)
        tr.append(max(highs[i]-lows[i], abs(highs[i]-closes[i-1]), abs(lows[i]-closes[i-1])))
        
    atr = rma(tr, period)
    smooth_dp = rma(dm_plus, period)
    smooth_dm = rma(dm_minus, period)
    dx = []
    for i in range(len(closes)):
        if atr[i] and atr[i] > 0:
            p = 100 * smooth_dp[i] / atr[i]
            m = 100 * smooth_dm[i] / atr[i]
            denom = p + m
            dx.append(100 * abs(p - m) / denom if denom > 0 else 0)
        else:
            dx.append(0)
    
    adx_series = rma(dx, period)
    return adx_series[-1]

def fetch_candles(instId):
    try:
        url = f"https://www.okx.com/api/v5/market/candles?instId={instId}&bar={TIMEFRAME}&limit=200"
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req) as response:
            data = json.loads(response.read().decode())
            if data['code'] == '0':
                closes = []; highs = []; lows = []
                for c in data['data']:
                    highs.append(float(c[2]))
                    lows.append(float(c[3]))
                    closes.append(float(c[4]))
                highs.reverse(); lows.reverse(); closes.reverse()
                return highs, lows, closes
    except Exception as e:
        print(f"   ⚠️ Error fetching {instId}: {e}")
    return [], [], []

# ============================================================
# 🤖 MAIN ONE-SHOT LOGIC
# ============================================================
def execute_real_trade(symbol, side, size_usd):
    if not EXCHANGE: return 0
    try:
        # Get Price
        ticker = EXCHANGE.fetch_ticker(symbol)
        price = ticker['last']
        amount = size_usd / price
        
        # OKX requires size in Base Asset usually, or valid precision
        # CCXT handles precision often, but let's be safe.
        # Checking Min Amount? 
        # For 'market' buy/sell.
        
        print(f"💸 EXECUTING REAL {side} on {symbol} for ${size_usd}...")
        order = EXCHANGE.create_market_order(symbol, side.lower(), amount)
        print(f"   ✅ ORDER FILLED: {order['id']}")
        return order
        
    except Exception as e:
        print(f"   ❌ EXECUTION ERROR: {e}")
        return None

def main():
    print(f"🚀 Running Bot Scan: {datetime.now()}")
    print(f"   Mode: {'PAPER' if IS_PAPER else 'REAL MONEY ⚠️'}")
    
    state = load_state()
    balance = state['balance']
    positions = state['positions']
    
    print(f"   Balance: ${balance:.2f} | Positions: {len(positions)}")
    
    for symbol in SYMBOLS:
        highs, lows, closes = fetch_candles(symbol)
        if len(closes) < 150: continue
        
        curr_price = closes[-1]
        rsi = calc_rsi(closes)
        atr = calc_atr(highs, lows, closes)
        adx = calc_full_adx(highs, lows, closes)
        
        donchian_high = max(highs[-DONCHIAN_ENTRY-1:-1]) 
        donchian_low = min(lows[-DONCHIAN_EXIT-1:-1])
        
        if rsi is None or atr is None: continue
        
        # Regime
        atr_pct = atr / curr_price
        if atr_pct > MAX_ATR_PCT:
            print(f"   Skipping {symbol}: Volatility {atr_pct*100:.1f}%")
            continue
            
        regime = "BULL" if adx >= 25 else "CHOP"
        
        # 1. MANAGE EXITS & PYRAMIDS
        if symbol in positions:
            pos = positions[symbol]
            avg_price = pos.get('avg_price', pos['entry'])
            
            # Update SL for Turtle
            if pos['type'] == 'TF':
                implied_sl = donchian_low
                # Add-on Break Even Guard
                if pos.get('adds_count', 0) > 0:
                    implied_sl = max(donchian_low, avg_price)
                pos['sl'] = implied_sl
            
            # Check Exit
            exit = False
            pnl_amt = 0
            reason = ""
            side = "sell"
            
            if pos.get('tp', 0) > 0 and curr_price >= pos['tp']:
                exit = True
                pnl_pct = (curr_price - avg_price) / avg_price
                pnl_amt = pos['size'] * pnl_pct
                reason = "TAKE_PROFIT"
            elif curr_price <= pos['sl']:
                exit = True
                pnl_pct = (curr_price - avg_price) / avg_price
                pnl_amt = pos['size'] * pnl_pct
                reason = "STOP_LOSS"
            
            if exit:
                # 75% Reinvestment Rule
                if pnl_amt > 0:
                    reinvest = pnl_amt * 0.75
                    bank = pnl_amt * 0.25
                    balance += (pos['size'] + reinvest)
                    print(f"💰 BANKED ${bank:.2f} PROFIT! (Reinvested ${reinvest:.2f})")
                else:
                    balance += (pos['size'] + pnl_amt) # Loss reduces balance
                
                log_trade(symbol, "SELL", curr_price, pos['size'], reason, pnl_amt)
                
                # REAL EXECUTION
                if not IS_PAPER: execute_real_trade(symbol, 'sell', pos['size']) # Sell full size
                
                del positions[symbol]
                
            else:
                # Check Pyramiding (Only if TF)
                if pos['type'] == 'TF' and balance >= ALLOCATION_PER_SYMBOL:
                    adds = pos.get('adds_count', 0)
                    last_add = pos.get('last_add_price', pos['entry'])
                    
                    if adds < PYRAMID_MAX_ADDS:
                        trigger = last_add + (atr * PYRAMID_ADD_TRIGGER_ATR)
                        if curr_price >= trigger:
                            # PYRAMID BUY
                            add_size = ALLOCATION_PER_SYMBOL
                            balance -= add_size
                            
                            new_total = pos['size'] + add_size
                            new_avg = ((avg_price * pos['size']) + (curr_price * add_size)) / new_total
                            
                            pos['size'] = new_total
                            pos['avg_price'] = new_avg
                            pos['adds_count'] = adds + 1
                            pos['last_add_price'] = curr_price
                            
                            print(f"🚀 PYRAMID ADD {symbol} @ {curr_price}")
                            log_trade(symbol, "BUY_ADD", curr_price, add_size, "PYRAMID")
                            
                            # REAL EXECUTION
                            if not IS_PAPER: execute_real_trade(symbol, 'buy', add_size)

        # 2. MANAGE ENTRIES
        else:
            if balance < ALLOCATION_PER_SYMBOL:
                continue
                
            trade_size = ALLOCATION_PER_SYMBOL
            entry_signal = False
            type_tag = ""
            tp = 0
            sl = 0
            
            if regime == "CHOP":
                if rsi >= MR_RSI_MIN and rsi <= MR_RSI_MAX:
                    # BUY MEAN REV
                    entry_signal = True
                    type_tag = 'MR'
                    tp = curr_price * (1 + MR_TP_PCT)
                    sl = curr_price * (1 - MR_SL_PCT)
                    
            elif regime == "BULL":
                if curr_price > donchian_high:
                    # BUY TURTLE
                    entry_signal = True
                    type_tag = 'TF'
                    tp = 0 # Infinite
                    sl = donchian_low
            
            if entry_signal:
                balance -= trade_size
                positions[symbol] = {
                    'entry': curr_price, 'avg_price': curr_price,
                    'size': trade_size,
                    'tp': tp,
                    'sl': sl,
                    'type': type_tag,
                    'adds_count': 0, 'last_add_price': curr_price
                }
                log_trade(symbol, "BUY", curr_price, trade_size, f"{type_tag}_ENTRY")
                
                # REAL EXECUTION
                if not IS_PAPER: execute_real_trade(symbol, 'buy', trade_size)
    
    # Save State
    state['balance'] = balance
    state['positions'] = positions
    state['last_run'] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    save_state(state)
    print("✅ Scan Complete. State Saved.")

if __name__ == "__main__":
    main()
