import urllib.request
import json
from datetime import datetime

instId = "BTC-USDT"
bar = "4H"
jump_start = datetime(2024, 1, 5)
after_cursor = str(int(jump_start.timestamp() * 1000))

url = f"https://www.okx.com/api/v5/market/candles?instId={instId}&bar={bar}&limit=100&after={after_cursor}"
print(f"Requesting: {url}")

req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req) as response:
    raw = response.read().decode()
    data = json.loads(raw)
    print(f"Code: {data['code']}")
    candles = data['data']
    print(f"Candles Returned: {len(candles)}")
    if candles:
        print(f"First Candle: {candles[0]}")
        print(f"Last Candle:  {candles[-1]}")
        ts = int(candles[0][0])
        print(f"Date: {datetime.fromtimestamp(ts/1000)}")
