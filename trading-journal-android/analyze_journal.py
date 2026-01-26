import pandas as pd
import json
import numpy as np

def parse_json_col(x):
    try:
        # The CSV uses pipe | instead of comma , inside JSON to clean it
        # We need to revert that for parsing
        clean_x = x.replace('|', ',')
        return json.loads(clean_x)
    except:
        return {}

def analyze():
    print("📂 Loading Journal Data...")
    try:
        df = pd.read_csv("journal_import.csv")
    except Exception as e:
        print(f"❌ Error reading CSV: {e}")
        return

    print(f"📊 Total Rows: {len(df)}")
    
    # 1. Parse Risk Column to get Volatility
    # Risk column format: {"atr": 100| "volatility_pct": 2.5}
    logging_data = []
    
    for index, row in df.iterrows():
        try:
            risk_str = row['Risk'].replace('|', ',')
            risk_json = json.loads(risk_str)
            vol_pct = risk_json.get('volatility_pct', 0)
            
            entry = float(row['Entry Price'])
            exit_price = float(row['Exit Price'])
            
            # If Exit is 0, trade is Open. Skip or estimate? Use Current Price if avail?
            # Data looks like Exit Price is populated.
            if exit_price == 0: continue 
            
            pnl_pct = (exit_price - entry) / entry * 100
            
            logging_data.append({
                'volatility': vol_pct,
                'pnl': pnl_pct,
                'win': 1 if pnl_pct > 0 else 0
            })
        except:
            continue
            
    stats = pd.DataFrame(logging_data)
    
    if stats.empty:
        print("⚠️ No valid trade data found.")
        return

    # 2. Overall Metrics
    total_trades = len(stats)
    win_rate = (stats['win'].sum() / total_trades) * 100
    
    # Profit Factor: Gross Win / Gross Loss
    gross_win = stats[stats['pnl'] > 0]['pnl'].sum()
    gross_loss = abs(stats[stats['pnl'] < 0]['pnl'].sum())
    profit_factor = gross_win / gross_loss if gross_loss != 0 else 0
    
    print("\n🏆 --- OVERALL RESULTS ---")
    print(f"Total Trades: {total_trades}")
    print(f"Win Rate: {win_rate:.1f}%")
    print(f"Profit Factor: {profit_factor:.2f}")
    
    # 3. Regime Segmentation
    # Low Vol (< 2%) = Choppy/Range
    # Med Vol (2-4%) = Normal Trend
    # High Vol (> 4%) = Crisis/Parabolic
    
    chop = stats[stats['volatility'] < 2.0]
    normal = stats[(stats['volatility'] >= 2.0) & (stats['volatility'] <= 4.0)]
    volatile = stats[stats['volatility'] > 4.0]
    
    def get_seg_stats(sub_df, name):
        if sub_df.empty:
            return f"| {name} | 0 | 0.0% | 0.00 |"
        
        count = len(sub_df)
        wr = (sub_df['win'].sum() / count) * 100
        gw = sub_df[sub_df['pnl'] > 0]['pnl'].sum()
        gl = abs(sub_df[sub_df['pnl'] < 0]['pnl'].sum())
        pf = gw / gl if gl != 0 else 99.99
        
        return f"| {name} | {count} | {wr:.1f}% | {pf:.2f} |"

    print("\n🌍 --- REGIME ANALYSIS ---")
    print("| Regime | Trades | Win Rate | Profit Factor |")
    print("|---|---|---|---|")
    print(get_seg_stats(chop, "Low Vol (Choppy)"))
    print(get_seg_stats(normal, "Normal (Trend)"))
    print(get_seg_stats(volatile, "High Vol (Stress)"))

if __name__ == "__main__":
    analyze()
