import pandas as pd
import time
from datetime import datetime
import ccxt # Install with: pip install ccxt

def fetch_ohlcv(symbol, date_str, exchange):
    """
    Fetches 1-hour candles for a specific date.
    We fetch a bit more context (e.g., 24 hours before) to calculate indicators if needed.
    """
    try:
        # Convert date string to timestamp (ms)
        dt = datetime.strptime(date_str, "%Y-%m-%d")
        timestamp = int(dt.timestamp() * 1000)
        
        # Symbol format: 'BTC/USDT' (Check if we need to adjust format)
        symbol_formatted = symbol
        if "USDT" in symbol and "/" not in symbol:
             symbol_formatted = symbol.replace("USDT", "/USDT")

        print(f"Fetching {symbol_formatted} for {date_str}...")
        
        # Fetch OHLCV (Timeframe: 1h)
        # We start fetching from the start of that day (UTC)
        ohlcv = exchange.fetch_ohlcv(symbol_formatted, timeframe='1h', since=timestamp, limit=24)
        
        if not ohlcv:
            print(f"Warning: No data found for {symbol_formatted} on {date_str}")
            return None
            
        # Calculate summary stats for the day
        # Structure: [timestamp, open, high, low, close, volume]
        opens = [x[1] for x in ohlcv]
        highs = [x[2] for x in ohlcv]
        lows = [x[3] for x in ohlcv]
        closes = [x[4] for x in ohlcv]
        volumes = [x[5] for x in ohlcv]
        
        # Simple Feature Engineering directly here
        market_data = {
            'open_avg': sum(opens) / len(opens),
            'high_max': max(highs),
            'low_min': min(lows),
            'close_avg': sum(closes) / len(closes),
            'volume_avg': sum(volumes) / len(volumes),
            'volatility_pct': ((max(highs) - min(lows)) / min(lows)) * 100
        }
        
        return market_data

    except Exception as e:
        print(f"Error fetching {symbol}: {e}")
        return None

# 1. SETUP
# We use Binance (free public API for historical data usually works without key for basics)
# If rate limited, we can switch or add delays.
exchange = ccxt.binance() 

# 2. LOAD DATA
input_file = "training_data_v1.csv"
print(f"Loading {input_file}...")
df = pd.read_csv(input_file)

# 3. FETCH & ENRICH
new_columns = ['market_open', 'market_high', 'market_low', 'market_close', 'market_volume', 'market_volatility']

# Initialize blank columns
for col in new_columns:
    df[col] = 0.0

print("Starting Market Data Fetch loop...")

# Optimization: Cache fetches so we don't spam API for duplicate days (synthetic data has same dates)
cache = {}

for index, row in df.iterrows():
    key = (row['symbol'], row['date'])
    
    if key in cache:
        data = cache[key]
    else:
        # Rate limit compliance (Binance is generous but let's be safe)
        time.sleep(0.2) 
        data = fetch_ohlcv(row['symbol'], row['date'], exchange)
        if data:
            cache[key] = data
            
    if data:
        df.at[index, 'market_open'] = data['open_avg']
        df.at[index, 'market_high'] = data['high_max']
        df.at[index, 'market_low'] = data['low_min']
        df.at[index, 'market_close'] = data['close_avg']
        df.at[index, 'market_volume'] = data['volume_avg']
        df.at[index, 'market_volatility'] = data['volatility_pct']

# 4. SAVE
output_file = "training_data_v2_with_market.csv"
df.to_csv(output_file, index=False)
print(f"\nSuccess! Enriched data saved to {output_file}")
print(df[['symbol', 'date', 'market_close', 'market_volatility', 'target']].head())
