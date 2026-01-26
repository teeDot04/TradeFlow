import ccxt
import pandas as pd
import numpy as np
import time
import os
import random
from datetime import datetime, timedelta
import logging

# ============================================================
# 1. CONFIGURATION (Jan 2026 - Jan 2027)
# ============================================================
# Setup logging to console and file
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler("bot_activity.log"),
        logging.StreamHandler()
    ]
)

CONFIG = {
    'SYMBOLS': ['BTC/USDT', 'ETH/USDT', 'SOL/USDT', 'LINK/USDT', 'XRP/USDT'],
    'TIMEFRAME': '4h',
    'EXCHANGE_ID': 'okx',
    'CSV_FILE': 'real_world_training_data.csv',
    'END_DATE': datetime.now() + timedelta(days=365), # Stops in 1 year
    'RISK_REWARD': 1.5,  # 1:1.5 Risk Reward Ratio
    'TP_PCT': 0.025,     # 2.5% Take Profit
    'SL_PCT': 0.025,     # 2.5% Stop Loss (1:1 for binary classification simplicity)
    'EXPLORATION_RATE': 0.15 # 15% random trades
}

# ============================================================
# 2. CORE SYSTEM
# ============================================================
class LiveDataCollector:
    def __init__(self):
        self.exchange = getattr(ccxt, CONFIG['EXCHANGE_ID'])({
            'enableRateLimit': True
        })
        self.active_trades = [] # Tracks currently running "paper" trades
        self.initialize_csv()

    def initialize_csv(self):
        """Creates the CSV file with headers if it doesn't exist."""
        if not os.path.exists(CONFIG['CSV_FILE']):
            headers = [
                'entry_timestamp', 'symbol', 'entry_price', 'direction',
                'rsi', 'adx', 'atr_pct', 'tae', 'vol_percentile', 'score',
                'entry_reason', 'exit_timestamp', 'exit_price', 'exit_reason',
                'pnl_pct', 'target_hit' # 1 = TP, 0 = SL
            ]
            pd.DataFrame(columns=headers).to_csv(CONFIG['CSV_FILE'], index=False)
            logging.info(f"Created new data file: {CONFIG['CSV_FILE']}")

    def fetch_latest_data(self, symbol):
        """Fetches the last 100 candles to calculate indicators."""
        try:
            # Fetch limit=100 to have enough for MA200 and Volatility Rank
            ohlcv = self.exchange.fetch_ohlcv(symbol, CONFIG['TIMEFRAME'], limit=250)
            df = pd.DataFrame(ohlcv, columns=['timestamp', 'open', 'high', 'low', 'close', 'volume'])
            df['timestamp'] = pd.to_datetime(df['timestamp'], unit='ms')
            return df
        except Exception as e:
            logging.error(f"Error fetching {symbol}: {e}")
            return None

    # ============================================================
    # 3. FEATURE CALCULATION (Exact same as ML inputs)
    # ============================================================
    def calculate_features(self, df):
        if len(df) < 200: return None
        
        # RSI
        delta = df['close'].diff()
        gain = (delta.where(delta > 0, 0)).rolling(14).mean()
        loss = (-delta.where(delta < 0, 0)).rolling(14).mean()
        rs = gain / loss
        df['rsi'] = 100 - (100 / (1 + rs))

        # ATR & Volatility Rank
        tr = pd.concat([df['high']-df['low'], abs(df['high']-df['close'].shift()), abs(df['low']-df['close'].shift())], axis=1).max(axis=1)
        df['atr'] = tr.rolling(14).mean()
        df['atr_pct'] = (df['atr'] / df['close']) * 100
        # Rank current volatility against last 200 periods (Institutional Metric)
        df['vol_percentile'] = df['atr_pct'].rolling(200).rank(pct=True)

        # ADX (Trend Strength)
        plus_dm = df['high'].diff()
        minus_dm = df['low'].diff()
        plus_dm = np.where((plus_dm > minus_dm) & (plus_dm > 0), plus_dm, 0)
        minus_dm = np.where((minus_dm > plus_dm) & (minus_dm > 0), minus_dm, 0)
        tr_s = tr.rolling(14).sum()
        plus_di = 100 * (pd.Series(plus_dm).rolling(14).sum() / tr_s)
        minus_di = 100 * (pd.Series(minus_dm).rolling(14).sum() / tr_s)
        dx = (abs(plus_di - minus_di) / (plus_di + minus_di)) * 100
        df['adx'] = dx.rolling(14).mean().fillna(0)

        # Time-At-Extremes (TAE)
        bb_lower = (df['close'].rolling(20).mean() - (2 * df['close'].rolling(20).std()))
        # Check last candle for TAE
        last_idx = df.index[-1]
        tae_count = 0
        for i in range(last_idx-1, last_idx-21, -1):
             if df.loc[i, 'low'] <= bb_lower.loc[i] * 1.01: tae_count += 1
             else: break
        df.loc[last_idx, 'tae'] = tae_count

        return df.iloc[-1] # Return only the latest closed candle row

    # ============================================================
    # 4. SCORING ENGINE (The "Brain")
    # ============================================================
    def get_trade_signal(self, row):
        """Calculates Score (0-100) and decides Entry."""
        score = 0
        
        # Scoring Logic (Option A from Guide)
        if row['rsi'] < 30: score += 30
        elif row['rsi'] < 40: score += 20
        elif row['rsi'] < 50: score += 10
            
        if row['adx'] < 20: score += 20
        elif row['adx'] < 25: score += 10
            
        if 3 <= row['tae'] <= 10: score += 20
        
        if row['vol_percentile'] < 0.5: score += 15 # Prefer lower volatility regimes
        
        # Decision
        reason = "NONE"
        entry = False
        
        # Safety Kill Switch
        if row['atr_pct'] > 6.0: return False, "HIGH_VOL_SAFETY", score

        # 1. Exploitation
        if score >= 60:
            entry = True
            reason = "HIGH_SCORE"
        # 2. Exploration (Epsilon Greedy)
        elif random.random() < CONFIG['EXPLORATION_RATE'] and score > 25:
            entry = True
            reason = "EXPLORATION"
            
        return entry, reason, score

    # ============================================================
    # 5. MAIN LOOP
    # ============================================================
    def run(self):
        logging.info(f"Bot started. Collecting data until {CONFIG['END_DATE']}")
        
        while datetime.now() < CONFIG['END_DATE']:
            current_time = datetime.now()
            logging.info(f"--- Scanning Markets: {current_time.strftime('%Y-%m-%d %H:%M:%S')} ---")
            
            # 1. CHECK EXIT CONDITIONS FOR ACTIVE TRADES
            # We iterate a copy to allow modifying the list
            for trade in self.active_trades[:]:
                symbol = trade['symbol']
                df = self.fetch_latest_data(symbol)
                
                if df is None: continue
                
                # Check if price hit TP or SL in the most recent candles
                # (In a real 4h loop, we check the latest candle)
                latest_low = df.iloc[-1]['low']
                latest_high = df.iloc[-1]['high']
                
                exit_triggered = False
                exit_reason = ""
                exit_price = 0
                
                if latest_high >= trade['tp']:
                    exit_triggered = True
                    exit_reason = "TP"
                    exit_price = trade['tp']
                elif latest_low <= trade['sl']:
                    exit_triggered = True
                    exit_reason = "SL"
                    exit_price = trade['sl']
                    
                if exit_triggered:
                    # Log the completed trade data
                    pnl_pct = (exit_price - trade['entry_price']) / trade['entry_price']
                    
                    full_record = trade['features'].copy()
                    full_record.update({
                        'exit_timestamp': df.iloc[-1]['timestamp'],
                        'exit_price': exit_price,
                        'exit_reason': exit_reason,
                        'pnl_pct': pnl_pct,
                        'target_hit': 1 if exit_reason == "TP" else 0
                    })
                    
                    # Save to CSV immediately
                    pd.DataFrame([full_record]).to_csv(CONFIG['CSV_FILE'], mode='a', header=False, index=False)
                    logging.info(f"✅ Trade Closed: {symbol} | {exit_reason} | PnL: {pnl_pct*100:.2f}%")
                    self.active_trades.remove(trade)

            # 2. SCAN FOR NEW ENTRIES
            for symbol in CONFIG['SYMBOLS']:
                # Skip if we already have a trade for this symbol (simplification for data collection)
                if any(t['symbol'] == symbol for t in self.active_trades):
                    continue
                    
                df = self.fetch_latest_data(symbol)
                if df is None: continue
                
                row = self.calculate_features(df)
                if row is None: continue
                
                entry, reason, score = self.get_trade_signal(row)
                
                if entry:
                    entry_price = row['close']
                    tp = entry_price * (1 + CONFIG['TP_PCT'])
                    sl = entry_price * (1 - CONFIG['SL_PCT'])
                    
                    # Store Features separately
                    features = {
                        'entry_timestamp': row['timestamp'],
                        'symbol': symbol,
                        'entry_price': entry_price,
                        'direction': 'LONG',
                        'rsi': row['rsi'],
                        'adx': row['adx'],
                        'atr_pct': row['atr_pct'],
                        'tae': row['tae'],
                        'vol_percentile': row['vol_percentile'],
                        'score': score,
                        'entry_reason': reason
                    }
                    
                    new_trade = {
                        'symbol': symbol,
                        'entry_price': entry_price,
                        'tp': tp,
                        'sl': sl,
                        'features': features
                    }
                    
                    self.active_trades.append(new_trade)
                    logging.info(f"🚀 New Entry: {symbol} | Score: {score:.1f} | Reason: {reason}")
            
            # 3. SLEEP UNTIL NEXT CHECK
            # We want to check roughly every 15 minutes to catch TP/SL hits, 
            # even though timeframe is 4h.
            logging.info("Sleeping for 15 minutes...")
            time.sleep(900) # 900 seconds = 15 minutes

if __name__ == "__main__":
    bot = LiveDataCollector()
    bot.run()
