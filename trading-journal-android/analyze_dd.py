
import csv

filename = 'journal_import.csv'

trades = []
with open(filename, 'r') as f:
    reader = csv.reader(f)
    header = next(reader)
    for row in reader:
        if len(row) < 5: continue
        date_str = row[1]
        entry = float(row[2])
        exit_price = float(row[3])
        
        # Calculate PnL %
        pnl_pct = ((exit_price - entry) / entry) * 100
        
        trades.append({'date': date_str, 'pnl': pnl_pct})

# Sort by Date
trades.sort(key=lambda x: x['date'])
sorted_pnls = [t['pnl'] for t in trades]

# Calculate Max Drawdown (Percentage Based - Accumulation)
peak = 0.0
max_dd = 0.0
current_cum = 0.0

for pnl in sorted_pnls:
    current_cum += pnl
    current_cum += pnl
    if current_cum > peak:
        peak = current_cum
    
    dd = peak - current_cum
    if dd > max_dd:
        max_dd = dd

print(f"Total Trades: {len(trades)}")
print(f"Max Drawdown (Accumulated %): {max_dd:.2f}%")

# Calculate Max Drawdown (Equity Compounding %)
capital = 100.0
peak_eq = 100.0
max_dd_eq = 0.0

for pnl in sorted_pnls:
    # return is pnl/100
    capital = capital * (1 + (pnl / 100))
    
    if capital > peak_eq:
        peak_eq = capital
        
    dd_eq = (peak_eq - capital) / peak_eq * 100
    if dd_eq > max_dd_eq:
        max_dd_eq = dd_eq

print(f"Max Drawdown (Equity Compounding %): {max_dd_eq:.2f}%")
