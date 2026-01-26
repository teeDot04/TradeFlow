import urllib.request
import json
import time
import math
import os
import signal
import sys
from datetime import datetime

# ============================================================
# ⚙️ CONFIGURATION (HYBRID: CHOP SURVIVOR + BULL RUN CATCHER)
# ============================================================
SYMBOLS = ['SOL-USDT', 'LINK-USDT', 'XRP-USDT', 'DOGE-USDT', 'ETH-USDT']
TIMEFRAME = '4H'

# COMMON PARAMS
ALLOCATION_PER_SYMBOL = 200 # USDT Base
MAX_ATR_PCT = 0.05 # Safety High Vol Filter

# ENGINE 1: MEAN REVERSION (CHOP / RANGE)
# Condition: ADX < 25
MR_RSI_MIN = 25
MR_RSI_MAX = 45
MR_TP_PCT = 0.025 # +2.5%
MR_SL_PCT = 0.015 # -1.5%

# ENGINE 2: TURTLE TRADING (BULL RUN)
# Condition: ADX >= 25
# 4H Candles. 20 Days = 120 candles. 10 Days = 60 candles.
DONCHIAN_ENTRY = 120 
DONCHIAN_EXIT = 60
PYRAMID_MAX_ADDS = 3
PYRAMID_ADD_TRIGGER_ATR = 0.5

# STATE
Is_Paper_Trading = True
Paper_Balance = 10000.0
# symbol -> {entry, avg_price, size, tp, sl, type: 'MR'|'TF', adds_count, last_add_price}
Positions = {} 

# ============================================================
# 🛠️ INDICATORS
# ============================================================
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

# ============================================================
# 📡 DATA FEED
# ============================================================
def fetch_candles(instId):
    try:
        # Need ~200 candles for Donchian(120) + indicators
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
# 🤖 BOT ENGINE
# ============================================================
def run_bot():
    print("="*50)
    print("🚀 LIVE BOT: HYBRID (MEAN REV + TURTLE PYRAMID)")
    print("   1. Chop (ADX < 25): RSI Mean Reversion")
    print("   2. Bull (ADX >= 25): Donchian Breakout + Pyramiding")
    print("="*50)
    print(f"Paper Balance: ${Paper_Balance:.2f}")
    
    while True:
        print(f"\nScanning Markets: {datetime.now().strftime('%H:%M:%S')}...")
        
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
            
            # Common Filters
            atr_pct = atr / curr_price
            if atr_pct > MAX_ATR_PCT:
                print(f"   Skipping {symbol}: Volatility {atr_pct*100:.1f}% > 5%")
                continue
            
            regime = "BULL" if adx >= 25 else "CHOP"
            status_msg = f"{regime} (ADX {adx:.1f}) | Donchian H:{donchian_high:.2f} L:{donchian_low:.2f}"
            
            # 1. Manage Position
            if symbol in Positions:
                pos = Positions[symbol]
                avg_price = pos.get('avg_price', pos['entry'])
                pnl_pct = (curr_price - avg_price) / avg_price
                
                # Update Trailing Stop if Turtle Trade
                if pos['type'] == 'TF':
                    # Dynamic Exit: Donchian Low
                    # But respect Pyramiding "Break Even" Guard
                    implied_sl = donchian_low
                    if pos['adds_count'] > 0:
                         # Secure the bag after adding
                         # Start Logic: MAX(Donchian Low, Avg Price)
                         implied_sl = max(donchian_low, avg_price)
                    
                    pos['sl'] = implied_sl
                    
                    # PYRAMIDING CHECK
                    if (pos['adds_count'] < PYRAMID_MAX_ADDS) and (Paper_Balance >= ALLOCATION_PER_SYMBOL):
                        trigger_price = pos['last_add_price'] + (atr * PYRAMID_ADD_TRIGGER_ATR)
                        if curr_price >= trigger_price:
                            # EXECUTE ADD
                            add_size = ALLOCATION_PER_SYMBOL
                            Paper_Balance -= add_size
                            
                            # Update Avg
                            current_total = pos['size']
                            new_total = current_total + add_size
                            new_avg = ((avg_price * current_total) + (curr_price * add_size)) / new_total
                            
                            pos['size'] = new_total
                            pos['avg_price'] = new_avg
                            pos['adds_count'] += 1
                            pos['last_add_price'] = curr_price
                            
                            print(f"🚀 PYRAMID ADD {symbol} @ {curr_price} | New Avg: {new_avg:.2f} | Size: {new_total}")

                exit = False
                pnl_amt = 0
                
                # Check Exits
                if curr_price >= pos['tp'] and pos['tp'] > 0: # TP only applies to MR
                    print(f"💰 TAKE PROFIT {symbol} ({pos['type']}) | Price: {curr_price} | PnL: +{pnl_pct*100:.2f}%")
                    pnl_amt = pos['size'] * pnl_pct
                    exit = True
                elif curr_price <= pos['sl']:
                    print(f"🛑 STOP LOSS {symbol} ({pos['type']}) | Price: {curr_price} | PnL: {pnl_pct*100:.2f}%")
                    pnl_amt = pos['size'] * pnl_pct
                    exit = True
                else:
                    print(f"   Holding {symbol} ({pos['type']} | {status_msg}): {pnl_pct*100:.2f}% (SL: {pos['sl']:.4f})")
                    
                if exit:
                    global Paper_Balance
                    Paper_Balance += (pos['size'] + pnl_amt)
                    del Positions[symbol]
            
            # 2. Check Entry
            else:
                trade_size = ALLOCATION_PER_SYMBOL
                if Paper_Balance < trade_size:
                    print(f"   ⚠️ Insufficient Funds for {symbol}")
                    continue

                if regime == "CHOP":
                    # Mean Reversion Logic
                    # Entry: RSI Dip (25-45)
                    if rsi >= MR_RSI_MIN and rsi <= MR_RSI_MAX:
                        print(f"🚀 BUY {symbol} (Mean Rev) | {status_msg}")
                        Paper_Balance -= trade_size
                        Positions[symbol] = {
                            'entry': curr_price, 'avg_price': curr_price, 'size': trade_size,
                            'tp': curr_price * (1 + MR_TP_PCT),
                            'sl': curr_price * (1 - MR_SL_PCT),
                            'type': 'MR',
                            'adds_count': 0, 'last_add_price': 0
                        }
                    else:
                        print(f"   Watching {symbol}: {status_msg} | RSI {rsi:.1f}")
                        
                elif regime == "BULL":
                    # Turtle Logic + Pyramid Prep
                    # Entry: Breakout 20-Day High
                    if curr_price > donchian_high:
                        print(f"🚀 BUY {symbol} (Turtle) | {status_msg} | Breakout > {donchian_high:.2f}")
                        Paper_Balance -= trade_size
                        Positions[symbol] = {
                            'entry': curr_price, 'avg_price': curr_price, 'size': trade_size,
                            'tp': 999999, # Let it run
                            'sl': donchian_low, # 10-Day Low
                            'type': 'TF',
                            'adds_count': 0, 
                            'last_add_price': curr_price
                        }
                    else:
                        print(f"   Watching {symbol}: {status_msg} | Price vs Breakout")

        print("💤 Waiting 60s...")
        time.sleep(60)

if __name__ == "__main__":
    try:
        run_bot()
    except KeyboardInterrupt:
        print("\n👋 Bot Stopped.")
