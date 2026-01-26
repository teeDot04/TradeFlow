import logging
import pandas as pd
from live_data_collector import LiveDataCollector, CONFIG

# Configure logging to show everything clearly
logging.basicConfig(level=logging.INFO, format='%(message)s')

def test_run():
    print("\n" + "="*50)
    print("🚀 STARTING IMMEDIATE MARKET SCAN TEST")
    print("="*50)
    
    try:
        bot = LiveDataCollector()
        print(f"✅ Bot initialized. Exchange: {CONFIG['EXCHANGE_ID']}")
        
        print("\n📊 SCANNING SYMBOLS:")
        print(f"   Symbols: {CONFIG['SYMBOLS']}")
        print(f"   Timeframe: {CONFIG['TIMEFRAME']}")
        print("-" * 50)
        
        for symbol in CONFIG['SYMBOLS']:
            print(f"\n🔍 Analyzing {symbol}...")
            df = bot.fetch_latest_data(symbol)
            
            if df is None:
                print(f"   ❌ Failed to fetch data for {symbol}")
                continue
                
            print(f"   ✅ Fetched {len(df)} candles. Last close: {df.iloc[-1]['close']}")
            
            row = bot.calculate_features(df)
            if row is None:
                print("   ❌ Not enough data for features")
                continue
                
            entry, reason, score = bot.get_trade_signal(row)
            
            print(f"   📈 INDICATORS:")
            print(f"      • RSI: {row['rsi']:.2f}")
            print(f"      • ADX: {row['adx']:.2f}")
            print(f"      • Vol Percentile: {row['vol_percentile']:.2f}")
            print(f"      • ATR %: {row['atr_pct']:.2f}")
            
            print(f"   🎯 SCORE: {score}/100")
            
            if entry:
                print(f"   🚀 SIGNAL TRIGGERED: {reason}")
            else:
                print(f"   Thinking... (No Signal)")
                
        print("\n" + "="*50)
        print("✅ TEST COMPLETE")
        print("="*50)

    except Exception as e:
        print(f"\n❌ FATAL ERROR: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    test_run()
