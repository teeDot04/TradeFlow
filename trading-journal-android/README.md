# TradeFlow v2.0 - Automated AI Trading Journal

TradeFlow is a fully automated trading system that combines a **Cloud-Based AI Bot** for execution and a **Native Android App** for monitoring and analysis.

## 🔄 How It Works

The system operates in a seamless loop:

1.  **The Bot Hunts (GitHub Actions)**
    *   **What it does:** Runs automatically every 4 hours in the cloud.
    *   **Logic:** Connects to OKX, scans for setups (Pivot/SuperTrend), and executes trades.
    *   **AI Injection:** Before saving, it calculates advanced metrics like "Order Book Imbalance", "VWAP", and "Sentiment".
    *   **Output:** It pushes the trade details (including AI data) to `journal_import.csv` in this repository.

2.  **The App Syncs (Android)**
    *   **What it does:** Serves as your personal "Command Center".
    *   **Sync:** Automatically pulls the latest `journal_import.csv` from GitHub.
    *   **Visuals:** Displays the trade with rich visualizations of the AI data (e.g., "Microstructure: Bid Heavy").
    *   **Alerts:** Notifies you immediately when a new trade is detected.

---

## 🚀 Getting Started

### 1. The Bot (Setup Once)
You don't need to run anything on your computer. The bot lives in the `.github/workflows` folder.
Just add your keys to **Settings > Secrets > Actions**:
*   `OKX_API_KEY`
*   `OKX_SECRET`
*   `OKX_PASSWORD`

### 2. The App (Install & Forget)
1.  Install `app-debug.apk` on your phone.
2.  Open the app. It is pre-linked to this repo.
3.  That's it. Watch the trades roll in.

---

## 🛠️ Technology

*   **Bot:** Python 3.12, CCXT (Execution), Pandas-TA (Analysis), GitHub Actions (Hosting).
*   **App:** Kotlin, Jetpack Compose, Room Database, Retrofit.
*   **Data:** OKX Spot API.

---

> **Note:** This is a zero-maintenance system. As long as GitHub is up, your bot is trading.
