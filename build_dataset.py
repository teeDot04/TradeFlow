import pandas as pd
import numpy as np
import random
from datetime import datetime, timedelta

# 1. RAW DATA (Scraped from your Screenshots)
# I have normalized the dates to the format found in your logs.
raw_trades = [
    # --- BTC TRADES ---
    {"symbol": "BTCUSDT", "side": "BUY", "entry": 115100.4, "exit": 115772.4, "pnl_pct": 50.35, "leverage": 100, "date": "2025-07-25"},
    {"symbol": "BTCUSDT", "side": "BUY", "entry": 107228.7, "exit": 107748.7, "pnl_pct": 44.12, "leverage": 100, "date": "2025-05-28"},
    {"symbol": "BTCUSDT", "side": "BUY", "entry": 95464.0, "exit": 95981.0, "pnl_pct": 43.97, "leverage": 100, "date": "2024-12-26"},
    {"symbol": "BTCUSDT", "side": "SELL", "entry": 109000.0, "exit": 109660.7, "pnl_pct": -106.92, "leverage": 100, "date": "2025-05-21"}, # Liquidation
    {"symbol": "BTCUSDT", "side": "BUY", "entry": 115546.1, "exit": 114740.4, "pnl_pct": -119.41, "leverage": 100, "date": "2025-09-20"}, # Liquidation
    {"symbol": "BTCUSDT", "side": "BUY", "entry": 81950.0, "exit": 81300.3, "pnl_pct": -122.32, "leverage": 100, "date": "2025-03-10"}, # Liquidation
    {"symbol": "BTCUSDT", "side": "SELL", "entry": 63232.2, "exit": 62920.0, "pnl_pct": 6.10, "leverage": 15, "date": "2024-09-20"},
    {"symbol": "BTCUSDT", "side": "SELL", "entry": 118183.5, "exit": 118163.7, "pnl_pct": -0.46, "leverage": 20, "date": "2025-07-26"},
    {"symbol": "BTCUSDT", "side": "SELL", "entry": 116799.2, "exit": 116776.0, "pnl_pct": -2.50, "leverage": 50, "date": "2025-09-16"},
    {"symbol": "BTCUSDT", "side": "BUY", "entry": 115464.3, "exit": 116022.3, "pnl_pct": 40.30, "leverage": 100, "date": "2025-07-25"},
    {"symbol": "BTCUSDT", "side": "BUY", "entry": 114424.3, "exit": 115309.9, "pnl_pct": 28.22, "leverage": 40, "date": "2025-09-15"},
    {"symbol": "BTCUSDT", "side": "SELL", "entry": 89912.9, "exit": 87652.0, "pnl_pct": 24.55, "leverage": 10, "date": "2025-12-29"},

    # --- ETH TRADES ---
    {"symbol": "ETHUSDT", "side": "BUY", "entry": 2348.08, "exit": 2472.66, "pnl_pct": 522.16, "leverage": 100, "date": "2025-05-19"},
    {"symbol": "ETHUSDT", "side": "BUY", "entry": 1764.87, "exit": 1782.47, "pnl_pct": 92.95, "leverage": 100, "date": "2025-04-30"},
    {"symbol": "ETHUSDT", "side": "BUY", "entry": 3820.01, "exit": 3843.71, "pnl_pct": 55.01, "leverage": 100, "date": "2025-10-22"},
    {"symbol": "ETHUSDT", "side": "BUY", "entry": 2541.54, "exit": 2486.66, "pnl_pct": -44.56, "leverage": 20, "date": "2025-05-15"},
    {"symbol": "ETHUSDT", "side": "BUY", "entry": 3624.61, "exit": 3609.12, "pnl_pct": -46.72, "leverage": 100, "date": "2025-07-23"},
    {"symbol": "ETHUSDT", "side": "BUY", "entry": 3820.67, "exit": 3798.00, "pnl_pct": -101.97, "leverage": 100, "date": "2025-10-22"}, # Liquidation
    {"symbol": "ETHUSDT", "side": "SELL", "entry": 1845.82, "exit": 1843.33, "pnl_pct": 6.49, "leverage": 100, "date": "2025-05-02"},
    {"symbol": "ETHUSDT", "side": "BUY", "entry": 3650.40, "exit": 3652.80, "pnl_pct": -0.42, "leverage": 100, "date": "2025-07-23"},
    {"symbol": "ETHUSDT", "side": "SELL", "entry": 3820.00, "exit": 3822.46, "pnl_pct": -9.44, "leverage": 100, "date": "2025-07-27"},
    {"symbol": "ETHUSDT", "side": "BUY", "entry": 1795.17, "exit": 1803.49, "pnl_pct": 39.32, "leverage": 100, "date": "2025-04-30"},
    {"symbol": "ETHUSDT", "side": "SELL", "entry": 2517.62, "exit": 2477.41, "pnl_pct": 30.68, "leverage": 20, "date": "2025-05-20"},
    {"symbol": "ETHUSDT", "side": "SELL", "entry": 1845.71, "exit": 1839.34, "pnl_pct": 27.52, "leverage": 100, "date": "2025-05-02"},

    # --- SOL TRADES ---
    {"symbol": "SOLUSDT", "side": "BUY", "entry": 206.30, "exit": 217.14, "pnl_pct": 50.24, "leverage": 10, "date": "2024-11-12"},
    {"symbol": "SOLUSDT", "side": "BUY", "entry": 196.71, "exit": 203.56, "pnl_pct": 34.28, "leverage": 10, "date": "2024-11-09"},
    {"symbol": "SOLUSDT", "side": "BUY", "entry": 214.24, "exit": 210.00, "pnl_pct": -20.18, "leverage": 10, "date": "2024-11-12"},

    # --- LINK TRADES ---
    {"symbol": "LINKUSDT", "side": "BUY", "entry": 13.40, "exit": 13.936, "pnl_pct": 39.30, "leverage": 10, "date": "2024-11-09"},
    {"symbol": "LINKUSDT", "side": "BUY", "entry": 13.875, "exit": 14.40, "pnl_pct": 37.30, "leverage": 10, "date": "2024-11-17"},
    {"symbol": "LINKUSDT", "side": "BUY", "entry": 11.95, "exit": 11.85, "pnl_pct": -18.12, "leverage": 20, "date": "2025-11-22"},
]

def convert_to_spot(trades):
    """
    Converts mixed Futures/Spot trade data into pure Spot Trade logic.
    Refined Logic for 'SELL' (Short) trades:
    1. A profitable Short (SELL) means price went DOWN. In Spot, this is a BAD time to Buy. Target = 0.
    2. A losing Short (SELL) means price went UP. In Spot, this MIGHT be a good time to Buy. Target = 1.
    3. Normal BUY trades: Profitable = Target 1, Losing = Target 0.
    """
    spot_trades = []
    for t in trades:
        spot_trade = {}
        spot_trade['symbol'] = t['symbol']
        spot_trade['date'] = t['date']
        spot_trade['entry'] = t['entry']
        spot_trade['exit'] = t['exit']
        spot_trade['leverage'] = 1 # Spot is 1x
        spot_trade['original_side'] = t['side']
        
        # Spot PnL: Price Change %
        # (Exit - Entry) / Entry
        # Note: accurate PnL for the *direction* of prices, regardless of Short/Long intent
        price_change_pct = ((t['exit'] - t['entry']) / t['entry']) * 100
        spot_trade['pnl_pct'] = round(price_change_pct, 4)
        
        # LOGIC:
        # If original was BUY:
        #   - Price went UP (PnL > 0) -> Good Spot Buy (Target 1)
        #   - Price went DOWN (PnL < 0) -> Bad Spot Buy (Target 0)
        #
        # If original was SELL (Short):
        #   - Profitable Short means Price went DOWN.
        #   - So if original PnL (Futures) was Positive, it means Exit < Entry.
        #   - This means Spot Price dropped. -> Bad Spot Buy (Target 0).
        
        # Let's simplify by looking at Entry vs Exit Price directly:
        if t['exit'] > t['entry']:
            # Price went UP. Good for Spot.
            spot_trade['target'] = 1
        else:
            # Price went DOWN. Bad for Spot.
            spot_trade['target'] = 0
            
        spot_trades.append(spot_trade)
    return spot_trades

def augment_data(trades, multiplier=10):
    """
    Takes a list of trades and creates 'synthetic' variations.
    """
    synthetic_data = []
    
    for trade in trades:
        # Add the original trade first
        original = trade.copy()
        original['type'] = 'real'
        # Target is already calculated in conversion step
        synthetic_data.append(original)
        
        # Create variations
        for _ in range(multiplier - 1):
            variation = trade.copy()
            
            # Jitter: Randomly shift price by +/- 0.3%
            noise = np.random.uniform(-0.003, 0.003) 
            variation['entry'] = variation['entry'] * (1 + noise)
            
            # Adjust exit to maintain roughly same PnL direction
            variation['exit'] = variation['exit'] * (1 + noise)
            
            # Recalculate PnL
            variation['pnl_pct'] = ((variation['exit'] - variation['entry']) / variation['entry']) * 100
            
            # Recalculate Target based on Price Direction
            variation['target'] = 1 if variation['exit'] > variation['entry'] else 0
            
            variation['type'] = 'synthetic'
            synthetic_data.append(variation)
            
    return pd.DataFrame(synthetic_data)

# 2. RUN THE PIPELINE
print(f"Loading {len(raw_trades)} raw trades...")

# Convert to Spot Logic FIRST
print("Converting to Spot Logic (Target based on Price Direction)...")
spot_trades = convert_to_spot(raw_trades)

# Then Augment
print(f"Augmenting {len(spot_trades)} spot trades...")
df = augment_data(spot_trades, multiplier=10) # 10x multiplier = 300 rows

# 3. SAVE TO CSV
filename = "training_data_v1.csv"
df.to_csv(filename, index=False)
print(f"Success! Generated {len(df)} training rows (High Quality Spot Logic).")
print(f"Saved to {filename}")
print("\nSample Data (SELL Trades -> Short Logic Check):")
# Show some SELL trades to verify logic
print(df[df['original_side'] == 'SELL'][['symbol', 'original_side', 'entry', 'exit', 'pnl_pct', 'target']].head().to_string())
