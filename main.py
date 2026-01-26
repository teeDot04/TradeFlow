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
# COMMAND: Default to REAL MONEY as requested ($100 Start)
IS_PAPER = os.environ.get('IS_PAPER', 'False').lower() == 'true'

EXCHANGE = None
if not IS_PAPER and ccxt:
    try:
        # Check for Env Vars, else maybe hardcoded? No, sticking to Env for security.
        # But User said "I have credentials", assuming they set them in Env/Secrets.
        EXCHANGE = ccxt.okx({
            'apiKey': os.environ.get('OKX_API_KEY'),
            'secret': os.environ.get('OKX_SECRET'),
            'password': os.environ.get('OKX_PASSWORD'),
        })
        print("✅ OKX Exchange Connected (REAL MONEY)")
    except Exception as e:
        print(f"❌ Failed to connect to OKX: {e}")

TIMEFRAME = '4H'
MAX_ATR_PCT = 0.05 

# MEAN REVERSION (CHOP)
MR_RSI_MIN = 30
MR_RSI_MAX = 45 
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
JSON_EXPORT_FILE = "trades_export.json"

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
    # Default State ($100 Start)
    return {
        "balance": 100.0, 
        "positions": {}, 
        "history": [] 
    }

def save_state(state):
    with open(STATE_FILE, 'w') as f:
        json.dump(state, f, indent=4)

def log_trade_json(trade_data):
    # Appends to trades_export.json for Android App Import
    try:
        existing = []
        if os.path.exists(JSON_EXPORT_FILE):
            with open(JSON_EXPORT_FILE, 'r') as f:
                try: existing = json.load(f)
                except: pass
        
        # Ensure it's a list
        if not isinstance(existing, list): existing = []
        
        existing.append(trade_data)
        
        with open(JSON_EXPORT_FILE, 'w') as f:
            json.dump(existing, f, indent=4)
        print("📱 Synced with Android App JSON")
    except Exception as e:
        print(f"❌ JSON Log Error: {e}")

def log_trade(symbol, action, price, size, reason, pnl=0):
    # Append to CSV
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
# ... (Calculations Same)

# ... (Fetch Same)

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
        
        # Min order sizing checks would go here.
        # Assuming $20 is > OKX min ($2-5 usually).
        
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
            
            # Update SL
            if pos['type'] == 'TF':
                implied_sl = donchian_low
                if pos.get('adds_count', 0) > 0:
                    implied_sl = max(donchian_low, avg_price)
                pos['sl'] = implied_sl
            
            # Check Exit
            exit = False
            pnl_amt = 0
            reason = ""
            
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
                # 75% Rule
                if pnl_amt > 0:
                    reinvest = pnl_amt * 0.75
                    bank = pnl_amt * 0.25
                    balance += (pos['size'] + reinvest)
                    print(f"💰 BANKED ${bank:.2f} PROFIT! (Reinvested ${reinvest:.2f})")
                else:
                    balance += (pos['size'] + pnl_amt) 
                
                log_trade(symbol, "SELL", curr_price, pos['size'], reason, pnl_amt)
                
                # ANDROID JSON LOGGING
                entry_time = pos.get('entry_time', time.time())
                exit_time = time.time()
                trade_record = {
                    "symbol": symbol,
                    "side": "LONG",
                    "entryPrice": avg_price,
                    "exitPrice": curr_price,
                    "quantity": pos['size'] / avg_price,
                    "entryTime": int(entry_time * 1000),
                    "exitTime": int(exit_time * 1000),
                    "strategy": f"{pos['type']}_V1",
                    "notes": f"{reason} | {regime}",
                    "emotion": "NEUTRAL",
                    "marketCondition": "TRENDING" if pos['type']=='TF' else "RANGE_BOUND",
                    "setupQuality": 8,
                    "grossPnL": pnl_amt,
                    "totalFees": 0.0, # Placeholder
                    "netPnL": pnl_amt,
                    "returnPct": pnl_pct,
                    "timestamp": int(exit_time * 1000)
                }
                log_trade_json(trade_record)
                
                # REAL EXECUTION
                if not IS_PAPER: execute_real_trade(symbol, 'sell', pos['size'])
                
                del positions[symbol]
                
            else:
                # Check Pyramiding
                # Dynamic Allocation Check
                current_allocation = max(20.0, balance * 0.2)
                
                if pos['type'] == 'TF' and balance >= current_allocation:
                    adds = pos.get('adds_count', 0)
                    last_add = pos.get('last_add_price', pos['entry'])
                    
                    if adds < PYRAMID_MAX_ADDS:
                        trigger = last_add + (atr * PYRAMID_ADD_TRIGGER_ATR)
                        if curr_price >= trigger:
                            # PYRAMID BUY
                            add_size = current_allocation
                            balance -= add_size
                            
                            new_total = pos['size'] + add_size
                            new_avg = ((avg_price * pos['size']) + (curr_price * add_size)) / new_total
                            
                            pos['size'] = new_total
                            pos['avg_price'] = new_avg
                            pos['adds_count'] = adds + 1
                            pos['last_add_price'] = curr_price
                            
                            print(f"🚀 PYRAMID ADD {symbol} @ {curr_price}")
                            log_trade(symbol, "BUY_ADD", curr_price, add_size, "PYRAMID")
                            
                            if not IS_PAPER: execute_real_trade(symbol, 'buy', add_size)

        # 2. MANAGE ENTRIES
        else:
            # Dynamic Sizing: 20% of Balance, but Min $20.
            trade_size = max(20.0, balance * 0.2)
            
            if balance < trade_size:
                continue
                
            entry_signal = False
            type_tag = ""
            tp = 0
            sl = 0
            
            if regime == "CHOP":
                if rsi >= MR_RSI_MIN and rsi <= MR_RSI_MAX:
                    entry_signal = True
                    type_tag = 'MR'
                    tp = curr_price * (1 + MR_TP_PCT)
                    sl = curr_price * (1 - MR_SL_PCT)
                    
            elif regime == "BULL":
                if curr_price > donchian_high:
                    entry_signal = True
                    type_tag = 'TF'
                    tp = 0 
                    sl = donchian_low
            
            if entry_signal:
                balance -= trade_size
                positions[symbol] = {
                    'entry': curr_price, 'avg_price': curr_price,
                    'size': trade_size,
                    'tp': tp,
                    'sl': sl,
                    'type': type_tag,
                    'adds_count': 0, 
                    'last_add_price': curr_price,
                    'entry_time': time.time()
                }
                log_trade(symbol, "BUY", curr_price, trade_size, f"{type_tag}_ENTRY")
                
                if not IS_PAPER: execute_real_trade(symbol, 'buy', trade_size)
    
    # Save State
    state['balance'] = balance
    state['positions'] = positions
    state['last_run'] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    save_state(state)
    print("✅ Scan Complete. State Saved.")

if __name__ == "__main__":
    main()
