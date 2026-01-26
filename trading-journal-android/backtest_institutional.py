import pandas as pd
import numpy as np
import logging
from main import TradeFlowAgent
import ccxt
import time
from datetime import datetime, timedelta

# Setup Logging
logging.basicConfig(level=logging.INFO, format='%(message)s')
logger = logging.getLogger()

def run_multi_period_backtest():
    periods = [30/365, 1, 2, 5] # 30 days, 1y, 2y, 5y
    print(f"🏛️ STARTING BENCHMARK: BOT vs HODL")
    print("="*60)
    
    # Init Agent (Binance for history)
    try:
        agent = TradeFlowAgent(exchange_id='binance')
    except:
        print("Failed to init agent")
        return

    coins = ['BTC/USDT', 'ETH/USDT', 'SOL/USDT', 'LINK/USDT', 'BNB/USDT']
    limit_per_fetch = 1000
    
    # 1. Fetch EVERYTHING once (largest period)
    max_years = max(periods)
    full_history = {}
    
    print(f"📥 Pre-loading {max_years} years of data for all coins...")
    for symbol in coins:
        all_candles = []
        try:
            since = agent.exchange.milliseconds() - (max_years * 365 * 24 * 60 * 60 * 1000)
            current_since = since
            
            while current_since < agent.exchange.milliseconds():
                try:
                    bars = agent.exchange.fetch_ohlcv(symbol, timeframe='4h', limit=limit_per_fetch, since=current_since)
                except:
                    break
                if not bars: break
                last_ts = bars[-1][0]
                if last_ts == current_since: break
                all_candles.extend(bars)
                current_since = last_ts + 1
                if len(all_candles) > 30000: break # Safety cap
            
            if not all_candles: continue
            
            df = pd.DataFrame(all_candles, columns=['ts', 'open', 'high', 'low', 'close', 'vol'])
            df['ts'] = pd.to_datetime(df['ts'], unit='ms')
            
            # Indicators
            df = agent.calculate_indicators(df)
            
            # Calculate Rolling Regime Threshold HERE to prevent lookahead bias
            # Using a 200-period rolling quantile for the 'Regime' (Top 20% volatility)
            df['regime_thresh'] = df['atr'].rolling(window=200).quantile(0.80)
            df['regime_thresh'] = df['regime_thresh'].bfill() # Fill start
            
            full_history[symbol] = df
            print(f"   ✅ {symbol}: {len(df)} candles")
        except Exception as e:
            print(f"   ❌ Failed to load {symbol}: {e}")

    # 2. Run Simulations for each period
    for years in periods:
        print(f"\n📅 TESTING PERIOD: {years:.2f} Years")
        print("-" * 50)
        
        total_pnl = 0.0 # Raw Sum of bot returns per coin
        total_hodl = 0.0 # Raw Sum of HODL returns per coin
        total_trades = 0
        
        cutoff_date = pd.Timestamp.now() - pd.Timedelta(days=years*365)
        
        for symbol, full_df in full_history.items():
            # Slice Data
            df = full_df[full_df['ts'] >= cutoff_date].copy()
            if df.empty: continue
            
            # --- HODL CALCULATION ---
            first_price = df.iloc[0]['close']
            last_price = df.iloc[-1]['close']
            hodl_return = ((last_price - first_price) / first_price) * 100
            
            trades = []
            df = df.reset_index(drop=True)
            
            for i in range(200, len(df)):
                # ... [Keep Loop Logic] ...
                row = df.iloc[i]
                prev = df.iloc[i-1]
                signal = agent.analyze_market_snapshot(row, prev, row['regime_thresh'])
                
                if signal:
                    # Simulation Logic for Rubber Band (Fixed Targets)
                    entry = signal['price']
                    sl = signal['sl']
                    tp = signal['tp1'] # Fixed +2.5%
                    
                    outcome = None
                    pnl = 0
                    
                    for j in range(i+1, min(i+100, len(df))): # Shorter horizon for scalps
                        future_low = df.iloc[j]['low']
                        future_high = df.iloc[j]['high']
                        
                        # Stop Check
                        if future_low <= sl:
                            outcome = 'LOSS'
                            pnl = (sl - entry) / entry
                            break
                        
                        # Target Check
                        if future_high >= tp:
                            outcome = 'WIN'
                            pnl = (tp - entry) / entry
                            break
                    
                    if outcome:
                        trades.append({'pnl': pnl })

            # Bot Stats
            coin_bot_return = sum(t['pnl'] for t in trades) * 10 
            # *10 is a simplified "10% Position Size" multiplier for 'Net Profit' approx check
            # Real Equity Curve is better, but for rapid relative comparison:
            # Let's use Compound Equity for Bot.
            
            # --- COMPOUNDING SIMULATION ---
            # Scenario A: Conservative (10% Reinvest)
            cap_conservative = 1000.0
            
            # Scenario B: Aggressive (50% Reinvest - User Goal)
            cap_aggressive = 1000.0
            
            for t in trades:
                 pnl_ratio = t['pnl']
                 
                 # Scenario A: Fixed risk % of growing capital, but only 10% of portfolio active
                 # Meaning: position_size = cap_conservative * 0.10
                 # profit = position_size * pnl_ratio
                 profit_A = (cap_conservative * 0.10) * pnl_ratio
                 cap_conservative += profit_A
                 
                 # Scenario B: 50% Reinvest?
                 # Interpret: "Compound 50% wins back" -> Aggressive Risk Sizing?
                 # Or simply: Position Size = 50% of Portfolio? (Very high risk)
                 # Or: Reinvest 50% of PROFITS?
                 # Let's assume User means "Aggressive Growth Portfolio" -> Position Size = 50% of Cap?
                 # No, that will blow up.
                 # User likely means: "I leave 50% of my profits in the bot to grow."
                 # Current logic ALREADY reinvests 100% of profits (capital grows).
                 # The constraint is POSITION SIZE.
                 # Valid Interpretation: Increase Position Size from 10% to 50%?
                 # Let's test Position Size = 50% (High Conviction)
                 
                 profit_B = (cap_aggressive * 0.50) * pnl_ratio
                 cap_aggressive += profit_B

            ret_conservative = ((cap_conservative - 1000) / 1000) * 100
            ret_aggressive = ((cap_aggressive - 1000) / 1000) * 100
            
            print(f"   {symbol}: 10%Size={ret_conservative:>6.0f}% | 50%Size={ret_aggressive:>6.0f}% | HODL={hodl_return:>6.0f}%")
            
            total_pnl += ret_aggressive # Track aggressive for final verdict
            total_hodl += hodl_return
            total_trades += len(trades)

        print(f"   👉 AVG RESULT: Bot {total_pnl/len(full_history):.1f}% vs HODL {total_hodl/len(full_history):.1f}%")

if __name__ == "__main__":
    run_multi_period_backtest()
