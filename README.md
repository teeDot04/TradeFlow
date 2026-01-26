
# TradeFlow v2.0 🤖📈

**The World's First AI-Powered, Fully Automated Trading Journal.**

TradeFlow v2.0 is a complete reimagining of the trading journal. It is no longer just a manual logbook; it is a **hands-off, autonomous intelligence system** that executes trades in the cloud, analyzes them with advanced AI models, and syncs everything to your mobile device in real-time.

---

## 🚀 Key Features

### 🤖 Autonomous Cloud Agent
- **Always On**: Runs 24/7 on GitHub Actions (scheduled every 4 hours).
- **Smart Execution**: The `TradeFlowAgent` (`main.py`) scans the market, executes setups based on a hybrid Pivot/SuperTrend strategy, and manages risk automatically.
- **Zero Infrastructure**: No VPS needed. The bot runs entirely serverless.

### 🧠 AI Market Intelligence
Every trade is enriched with deep market data "injected" by the AI:
- **Microstructure**: Bid/Ask spread, order book depth, and imbalance.
- **Market Context**: 24h volume, VWAP, and funding rates.
- **Risk Metrics**: Real-time volatility and ATR analysis.
- **Sentiment**: Funding analysis.

### 📱 Premium Android App
- **Auto-Sync**: Connects directly to your GitHub repository to pull new trades instantly. No manual entry required.
- **Micro-Animations & Glassmorphism**: A stunning, modern UI built with Jetpack Compose.
- **Advanced Data Visualization**: View the AI's "brain" for every trade in the detailed breakdown.
- **PDF & CSV Exports**: Generate professional reports including all AI-derived metrics.

---

## 🛠️ Tech Stack

- **Bot**: Python 3.12, CCXT, Pandas, NumPy, GitHub Actions
- **App**: Kotlin, Jetpack Compose, Room Database, Retrofit
- **Data**: OKX API (Spot Market)

---

## ⚡ Setup Guide

### 1. Fork the Repository
Clone this repo to your own GitHub account.

### 2. Configure Secrets
Go to **Settings > Secrets and variables > Actions** and add:
- `OKX_API_KEY`
- `OKX_SECRET`
- `OKX_PASSWORD`

### 3. Install the App
Download the latest `app-debug.apk` from the releases section (or build from source) and install it on your Android device.

### 4. Sync & Forget
Open the app. It is pre-configured to sync from your repo. Just watch the trades roll in!

---

**Developed with ❤️ by [Telo OtienoTradeFlow Team]**
