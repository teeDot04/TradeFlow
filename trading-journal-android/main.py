import ccxt
import pandas as pd
import pandas_ta as ta
import numpy as np
import os
import time
import json
import requests
import logging
import yfinance as yf
from datetime import datetime

# ============================================================
# ⚙️ CONFIGURATION (THE CONTROL PANEL)
# ============================================================
LIVE_MODE = True            # Set to True for Real Alerts
TIMEFRAME = '4h'            # The "Sweet Spot"
RISK_PER_TRADE = 0.02

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
logger = logging.getLogger()

def get_macro_regime():
    """
    Checks the 'Tide' of the global market.
    Returns: 'BULLISH' (Safe to Long), 'BEARISH' (Cash Only), or 'NEUTRAL'
    """
    score = 0
    
    # 1. THE DOLLAR CHECK (YFinance)
    # If the Dollar (DXY) is crashing, Assets (BTC/Stocks) fly.
    try:
        dxy = yf.Ticker("DX-Y.NYB")
        hist = dxy.history(period="5d")
        
        if not hist.empty:
            # Is DXY closing lower than 5 days ago? (Dollar Weakness = Good for Crypto)
            dxy_change = hist['Close'].iloc[-1] - hist['Close'].iloc[0]
            
            if dxy_change < 0: 
                score += 1  # Dollar is weak -> BULLISH
            else:
                score -= 1  # Dollar is strong -> BEARISH
    except Exception as e:
        logger.warning(f"⚠️ Macro (DXY) check failed: {e}")

    # 2. THE LIQUIDITY CHECK (DefiLlama)
    # Are stablecoins being printed (printing money) or burned (cashing out)?
    try:
        url = "https://stablecoins.llama.fi/stablecoins?includePrices=true"
        response = requests.get(url, timeout=5).json()
        
        # Sum Market Cap of Top 3: USDT, USDC, DAI
        current_cap = 0
        for coin in response['peggedAssets']:
            if coin['symbol'] in ['USDT', 'USDC', 'DAI']:
                current_cap += coin['circulating']['peggedUSD']
        
        # Dynamic Logic: We care about FLOW, not total amount.
        # If Stablecoin Market Cap is GROWING, money is entering crypto.
        # We sum the 7-day change of the top stablecoins.
        
        growth_score = 0
        for coin in response['peggedAssets']:
             if coin['symbol'] in ['USDT', 'USDC', 'DAI']:
                 # Check 'change7d' (if available) or similar field
                 # The API usually returns 'change_7d' or we calculate it?
                 # Actually, DefiLlama /stablecoins response has "circulatingPrev1Day", "circulatingPrev7Day"
                 
                 current = coin['circulating']['peggedUSD']
                 prev_7d = coin.get('circulatingPrev7Day', {}).get('peggedUSD', current)
                 
                 if current > prev_7d:
                     growth_score += 1
        
        # If at least 2 of the Top 3 are growing, Liquidity is Bullish.
        if growth_score >= 2: 
            score += 1
    except Exception as e:
        logger.warning(f"⚠️ Macro (DefiLlama) check failed: {e}")

    # 3. VERDICT
    if score >= 1: return "RISK_ON"  # Allow All Trades
    if score <= -1: return "RISK_OFF" # Block All Trades
    return "NEUTRAL" # Allow only Perfect Setups

class TradeFlowAgent:
    def __init__(self, exchange_id='okx'):
        self.exchange_id = exchange_id
        self.setup_exchange()
        
    def setup_exchange(self):
        try:
            api_key = os.environ.get('OKX_API_KEY')
            secret = os.environ.get('OKX_SECRET')
            password = os.environ.get('OKX_PASSWORD')
            
            # Safe init for paper mode if keys missing
            if not api_key:
                 if LIVE_MODE: logger.warning("⚠️ No API Keys found! Running in Paper Mode.")
                 self.exchange = getattr(ccxt, self.exchange_id)({'enableRateLimit': True, 'options': {'defaultType': 'spot'}})
            else:
                 self.exchange = getattr(ccxt, self.exchange_id)({
                    'apiKey': api_key, 'secret': secret, 'password': password,
                    'enableRateLimit': True, 'options': {'defaultType': 'spot'}
                 })
            logger.info(f"✅ Agent connected to {self.exchange_id.upper()}")
        except Exception as e:
            logger.error(f"❌ Connection Failed: {e}")

    def fetch_data(self, symbol, limit=100):
        for i in range(3): # Self-Healing Retry Loop
            try:
                bars = self.exchange.fetch_ohlcv(symbol, timeframe=TIMEFRAME, limit=limit)
                df = pd.DataFrame(bars, columns=['ts', 'open', 'high', 'low', 'close', 'vol'])
                df['ts'] = pd.to_datetime(df['ts'], unit='ms')
                return df
            except Exception as e:
                time.sleep(1)
        return None

    def calculate_indicators(self, df):
        if df is None or df.empty: return None
        
        # 1. Regime (ATR)
        df['atr'] = ta.atr(df['high'], df['low'], df['close'], length=14)
        df['atr_pct'] = (df['atr'] / df['close']) * 100
        
        # 5. Trend & Momentum
        df['ma_200'] = df['close'].rolling(200).mean()
        df['ma_50'] = df['close'].rolling(50).mean() # Faster trend
        df['rsi'] = ta.rsi(df['close'], length=14)
        
        # 6. Trend Strength (ADX)
        adx = ta.adx(df['high'], df['low'], df['close'], length=14)
        if adx is not None:
            df['adx'] = adx['ADX_14']
        else:
             df['adx'] = 0
             
        # 7. SuperTrend (The King of Trends)
        supertrend = ta.supertrend(df['high'], df['low'], df['close'], length=10, multiplier=3)
        if supertrend is not None:
             # Dynamically find columns
             trend_col = [c for c in supertrend.columns if c.startswith('SUPERT_')][0]
             dir_col = [c for c in supertrend.columns if c.startswith('SUPERTd_')][0]
             
             df['supertrend'] = supertrend[trend_col]
             df['supertrend_dir'] = supertrend[dir_col]
        else:
             df['supertrend'] = 0
             df['supertrend_dir'] = 1
             
        # 8. Daily Pivot Points (The Floors)
        # Resample to Daily to get Previous Day's High/Low/Close
        try:
            # Set index to ts for resampling
            df_daily = df.set_index('ts').resample('D').agg({
                'high': 'max',
                'low': 'min',
                'close': 'last'
            })
            
            # Calculate Standard Pivots (Classic)
            df_daily['P'] = (df_daily['high'] + df_daily['low'] + df_daily['close']) / 3
            df_daily['S1'] = (2 * df_daily['P']) - df_daily['high']
            df_daily['S2'] = df_daily['P'] - (df_daily['high'] - df_daily['low'])
            df_daily['S3'] = df_daily['S2'] - (df_daily['high'] - df_daily['low']) # S3 = S2 - Range (Deep Flush)
            
            # Shift by 1 so that 'today' sees 'yesterday's values
            df_daily = df_daily.shift(1)
            
            # Reset index to merge back
            df_daily = df_daily.reset_index()
            
            # Merge logic: Each 4H candle gets the pivot of the day it belongs to.
            # Convert both to date for merging
            df['date'] = df['ts'].dt.date
            df_daily['date'] = df_daily['ts'].dt.date
            
            df = df.merge(df_daily[['date', 'S1', 'S2', 'S3']], on='date', how='left')
            
            # Fill NaNs (start of data)
            df['S1'] = df['S1'].bfill()
            df['S2'] = df['S2'].bfill()
            df['S3'] = df['S3'].bfill()
            
        except Exception as e:
            # Fallback if resampling fails
            logger.warning(f"Pivot calc failed: {e}")
            df['S1'] = np.nan
            df['S2'] = np.nan
            df['S3'] = np.nan

        return df

    def analyze_market_snapshot(self, row, prev_row, regime_thresh):
        """THE 'SMART HYBRID' (Pivot + SuperTrend + Wick)"""
        
        # LOGIC:
        # 1. Pivot Bounce (S1/S2 Close) -> Safety
        # 2. Wick Hunter (S3 Limit) -> Flash Crashes
        # 3. SuperTrend (Breakout) -> Bull Runs
        
        # 0. Safety Check
        if pd.isna(row['S1']) or pd.isna(row['S2']): return None

        # --- A. WICK HUNTER (Flash Crash) ---
        # Did price wick down to S3?
        # If Low <= S3, we assume our Limit Order at S3 was filled.
        if row['low'] <= row['S3']:
            # Validation: We only catch knives if RSI is screaming overshoot
            # Or just trust the level? S3 is extreme. Let's trust it.
            
            entry = row['S3']
            sl_price = entry * 0.95 # -5% Emergency Stop
            tp_price = row['S2']    # Snap back to S2 (often huge R:R)
            
            # If S2 is too close? Ensure min 2% profit
            if tp_price < entry * 1.02: tp_price = entry * 1.03
            
            return {
                'ts': row['ts'],
                'type': 'WICK_HUNTER',
                'price': entry,
                'sl': sl_price,
                'tp1': tp_price,
                'tp2': tp_price, 
                'trailing': False, 
                'atr': row['atr']
            }

        # --- B. PIVOT HUNTER (Standard Bounce) ---
        # 1. SETUP: Price is at Support
        # Check if the candle LOW touched the support level
        # We give a small buffer (0.5%) around the level?
        # User said "fuzzy entry", "not too much accuracy".
        # So: Low <= S1 * 1.005 (Within 0.5% above S1 or lower)
        
        # We check S1 and S2
        touched_s1 = row['low'] <= (row['S1'] * 1.005) and row['close'] > (row['S1'] * 0.99) # Touched and held?
        touched_s2 = row['low'] <= (row['S2'] * 1.005)
        
        at_support = touched_s1 or touched_s2
        
        if not at_support:
            # CHECK SECONDARY ENTRY: SuperTrend Flip (Catch the Bull Run)
            # If we miss Support, we buy the Breakout.
            # Trend Flip: Current Green, Prev Red.
            trend_flip = (row['supertrend_dir'] == 1) and (prev_row['supertrend_dir'] == -1)
            
            # Smart Filter: Only take Trend Flip if ADX > 20 (Avoid chop)
            if trend_flip and row['adx'] > 20: 
                # Valid Momentum Entry
                pass
            else:
                return None
        
        # 2. FILTER: Oversold (Only applies to Pivot Bounce)
        # If we are strictly buying Support, we want Oversold.
        # If we are buying SuperTrend Momentum, RSI might be high (50-60), so we skip this check for Momentum.
        if at_support and row['rsi'] > 50: return None # Relaxed from 35 to 50 for Crypto Volatility
        
        # 3. TRIGGER: Momentum (Green Candle)
        # Applies to both.
        green_candle = row['close'] > row['open']
        
        if not green_candle: return None
        
        # --- SMART EXIT LOGIC (Navigate Bull Markets) ---
        # If ADX > 25, we are in a Strong Trend (Bull Market).
        is_bull_market = row['adx'] > 25
        
        entry = row['close']
        atr = row['atr']
        
        # 1. RANGE REGIME (Mean Reversion)
        # Paper Strategy A: Target Mean, Stop 1 ATR
        tp_price = entry + (1.0 * atr) 
        sl_price = entry - (1.0 * atr)
        
        target_2 = tp_price 
        do_trail = False
        
        # 2. TREND REGIME (Momentum)
        # Paper Strategy B: Infinite TP, Stop at SuperTrend
        if is_bull_market or (not at_support): 
             target_2 = None # Let it run to infinity
             do_trail = True
             
             # If SuperTrend is below close (Bullish), use it. 
             # Safety: If SuperTrend is weirdly above, look for fallback.
             if row['supertrend'] < entry:
                 sl_price = row['supertrend']
             else:
                 # Fallback if SuperTrend hasn't flipped yet (rare)
                 sl_price = entry - (2.0 * atr) # Wide 2 ATR stop for volatility
            
        return {
            'ts': row['ts'],
            'type': 'SMART_HYBRID',
            'price': entry,
            'sl': sl_price,
            'tp1': tp_price, # Always bank the first 2.5% (Income)
            'tp2': target_2, # Runner (Moonbag) or Fixed (Scalp)
            'trailing': do_trail, 
            'atr': row['atr']
        }

    def analyze_market(self, symbol):
        """The Decision Engine (Live)"""
        
        # LAYER 0: MACRO GUARD
        # We check the global tide before looking at the chart.
        if LIVE_MODE: # Only run macro check in live mode to avoid spamming APIs in backtest loop
            macro = get_macro_regime()
            logger.info(f"🌍 Macro Regime: {macro}")
            
            if macro == "RISK_OFF":
                logger.info(f"🛡️ MACRO BLOCK: Market Conditions Unsafe (DXY Strong / Liquidity Low). Staying in Cash.")
                return # Stop here. Do not trade. 

        df = self.fetch_data(symbol)
        if df is None: return

        df = self.calculate_indicators(df)
        
        # Dynamic Regime based on recent history
        regime = df['atr'].quantile(0.80)
        
        last = df.iloc[-1]
        prev = df.iloc[-2]
        
        if signal:
            # Fetch Balance for accurate sizing
            balance = self.fetch_balance()
            
            # 1. Execute Real Trade (if LIVE_MODE)
            # We calculate size again inside execute to be safe
            order = self.execute_trade(symbol, signal, balance)
            
            # 2. Log to CSV for Android Import (and Auto-Push)
            if order:
                self.log_to_csv(symbol, signal, order)
            
            logger.info(f"🚀 SIGNAL EXECUTED: {symbol} @ {signal['price']}")
                
        else:
            logger.info(f"Scanning {symbol}... No signal.")

    def fetch_balance(self):
        """Fetch USDT Balance (Live or Paper)"""
        if not LIVE_MODE or not hasattr(self, 'exchange'):
             return 1000.0 # Paper Balance
        
        try:
            bal = self.exchange.fetch_balance()
            return bal['total'].get('USDT', 0.0)
        except:
            return 1000.0 # Fallback
            
    def calculate_position_size(self, balance, price, stop_loss):
        """
        The 'Upgraded Logic Math' (Aggressive Compounding).
        Target: Reinvest 50% of portfolio into high-conviction setups.
        """
        size_aggressive = balance * 0.50
        return size_aggressive

    def execute_trade(self, symbol, sig, balance):
        """Executes the trade on OKX"""
        if not LIVE_MODE:
            logger.info("⚠️ Paper Mode: Trade simulated.")
            return {'id': 'sim_' + str(int(time.time())), 'price': sig['price'], 'amount': 0}
            
        try:
            price = sig['price']
            sl = sig['sl']
            usd_size = self.calculate_position_size(balance, price, sl)
            amount = usd_size / price
            
            # 1. Market Buy
            logger.info(f"💸 EXECUTING BUY: {symbol} Size: ${usd_size:.2f} ({amount:.4f})")
            order = self.exchange.create_market_buy_order(symbol, amount)
            
            logger.info(f"✅ Trade Executed! ID: {order['id']}")
            return order
            
        except Exception as e:
            logger.error(f"❌ Trade Execution Failed: {e}")
            return None

    def git_push_journal(self):
        """Pushes the updated journal to the Git Server"""
        if not LIVE_MODE: return
        
        try:
            import subprocess
            subprocess.run(["git", "add", "journal_import.csv"], check=True)
            subprocess.run(["git", "commit", "-m", "🤖 Bot: Auto-Journal Trade"], check=True)
            subprocess.run(["git", "push"], check=True)
            logger.info("✅ Journal synced to Git Server")
        except Exception as e:
            logger.warning(f"⚠️ Git Push Failed: {e}")

    def log_to_csv(self, symbol, sig, order):
        """Appends trade to 'trades.csv' with ADVANCED AI DATA"""
        filename = "journal_import.csv"
        file_exists = os.path.isfile(filename)
        
        try:
            # 1. Gather Advanced Metrics (Snapshot)
            ticker = self.exchange.fetch_ticker(symbol)
            book = self.exchange.fetch_order_book(symbol, limit=5)
            
            # Microstructure
            spread = ticker['ask'] - ticker['bid']
            microstructure = {
                "spread": spread,
                "bid_depth": sum([b[1] for b in book['bids']]),
                "ask_depth": sum([a[1] for a in book['asks']]),
                "imbalance": (sum([b[1] for b in book['bids']]) / (sum([a[1] for a in book['asks']]) + 1))
            }
            
            # Market Context
            context = {
                "24h_vol": ticker['quoteVolume'],
                "vwap": ticker.get('vwap', 0),
                "funding": 0.0001 # Placeholder for Spot
            }
            
            # Risk & Sentiment (Simulated for Spot)
            row_atr = sig.get('atr', 0)
            risk = {"atr": row_atr, "volatility_pct": (row_atr / sig['price']) * 100}
            
            sentiment = {"funding_rate": "neutral", "open_interest": "N/A"}
            
            # 2. Serialize to JSON (Escape commas for CSV)
            micro_json = json.dumps(microstructure).replace(",", "|") # Pipe separator to avoid CSV break
            ctx_json = json.dumps(context).replace(",", "|")
            risk_json = json.dumps(risk).replace(",", "|")
            sent_json = json.dumps(sentiment).replace(",", "|")
            fund_json = "{}" 
            
            with open(filename, 'a') as f:
                if not file_exists:
                    # Header matches Android parser expectations (Indices 0-9)
                    f.write("Symbol,Date,Entry Price,Exit Price,Side,Microstructure,Context,Risk,Sentiment,Fundamental\n")
                
                date_str = datetime.now().strftime("%Y-%m-%d")
                entry_price = order.get('average', sig['price']) 
                if entry_price is None: entry_price = sig['price']
                
                # Format: BTC/USDT,2026-01-23,65000,0,LONG, {json}, {json}...
                line = (f"{symbol},{date_str},{entry_price},0,LONG,"
                        f"{micro_json},{ctx_json},{risk_json},{sent_json},{fund_json}\n")
                        
                f.write(line)
                logger.info(f"📝 Journaled ADVANCED DATA to {filename}")
                
            # Auto-Push to Git Listener
            self.git_push_journal()
                
        except Exception as e:
             logger.error(f"❌ CSV Log Failed: {e}")

if __name__ == "__main__":
    # CHECK FOR MANUAL SYSTEM TEST TRIGGER from GitHub Actions
    if os.environ.get('MOCK_TRADE') == 'true':
        logger.info("🤖 Agent Active. Hunting alpha... (SYSTEM TEST MODE)")
        agent = TradeFlowAgent()
        
        logger.info("🧪 MANUAL TRIGGER: Forcing Mock Trade on BTC/USDT...")
        
        # Create a dummy signal
        mock_signal = {
            'ts': pd.Timestamp.now(),
            'type': 'SYSTEM_TEST',
            'price': 65000.0,
            'sl': 64000.0,
            'tp1': 66000.0,
            'tp2': None,
            'trailing': False,
            'atr': 1200.0
        }
        
        # Create a dummy order
        mock_order = {
            'id': 'test_order_' + str(int(time.time())),
            'average': 65000.0,
            'amount': 0.015
        }
        
        # Force Log (This triggers the CSV write and Git Push)
        agent.log_to_csv('BTC/USDT', mock_signal, mock_order)
        
        logger.info("✅ SYSTEM TEST COMPLETE. Trade pushed to repo.")
        exit(0)

    # NORMAL MODE
    logger.info("🤖 Agent Active. Hunting alpha...")
    agent = TradeFlowAgent()
    coins = ['BTC/USDT', 'ETH/USDT', 'SOL/USDT', 'LINK/USDT', 'OKB/USDT']
    
    for coin in coins:
        agent.analyze_market(coin)
