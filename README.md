# TradeFlow v3.0: Sovereign Agent 🤖🛡️

**The Ultimate Autonomous AI Trading Sovereign.**

TradeFlow v3.0 is a groundbreaking evolution in decentralized trading. It transforms your mobile device into a **Sovereign Trading Node** that monitors markets, digests global news, and executes high-frequency trades autonomously using advanced LLM reasoning (DeepSeek) and real-time sentiment analysis (CryptoPanic).

---

## 🚀 Key Features (Sovereign Edition)

### ⚡ Local-First Autonomous Agent
- **Edge Intelligence**: The `AgentCore` runs directly on your device, performing sub-second market scanning without relying on external cloud triggers.
*   **Master Kill-Switch**: One-tap control to start/stop the autonomous loop instantly during market volatility.

### 🧠 DeepSeek AI Rationale
Every trade is a result of complex reasoning:
- **News Integration**: CryptoPanic API fetches the latest "Important" headlines and sentiment votes for the target coin.
- **LLM Reasoning**: DeepSeek analyzes the price action + news context to differentiate between a "Standard Dip" and a "Fundamental Crash".
- **Strategy Selection**: AI dynamically chooses between `MEAN_REVERSION`, `TREND_FOLLOWING`, and `MOMENTUM_BURST`.

### 🛡️ Institutional-Grade Risk Management
- **Atomic Execution**: Simultaneous Market Buy + Stop Loss (-2.5%) + Take Profit (+5.0%) placement on the exchange.
- **24-Hour Circuit Breaker**: Automatically hibernates the agent if 3 consecutive losses are detected within a rolling 24-hour window.
- **Sizing Logic**: Dynamic balance fetching and safe equity allocation (50% of available trading capital).

### 📊 Advanced Transparency
- **Avoided Trades Log**: View the setups the AI analyzed but chose *not* to take, complete with its rationale and confidence score.
- **AI Rationale & Notes**: Every trade in your journal includes a deep-dive "Thought" section explaining the AI's logic.

---

## 🛠️ Tech Stack

- **Core Engine**: Kotlin (Coroutines/Flows), Android Foreground Services
- **AI Brain**: DeepSeek-V4 Flash LLM
- **News Engine**: CryptoPanic Pro API
- **Exchange Interface**: OKX V5 API (Spot)
- **Database**: Room (SQLite) with encrypted preferences

---

## ⚡ Setup Guide

### 1. API Configuration
Open the app and navigate to **Settings**. Enter your credentials for:
- **OKX**: API Key, Secret, and Passphrase (ensure 'Trade' permission is enabled).
- **DeepSeek**: API Key for market reasoning.
- **CryptoPanic**: API Key for news sentiment injection.

### 2. Modes of Operation
- **Simulated Mode (Dry Run)**: Toggled ON by default. The agent will perform all logic and log "Dummy" trades to your journal without using real funds.
- **Live Mode**: Toggle OFF 'Simulated Mode' to execute real trades on OKX.

---

## 🧪 The Logic & Math (Documentation)

### 1. Market Monitoring (The Scanner)
The agent maintains a rolling buffer of the last 600 ticks (approx. 20 mins) for each coin.
- **Peak Calculation**: `P = max(Buffer)`
- **Drop Trigger**: If `(P - CurrentPrice) / P >= 0.03` (3% Drop from peak), the AI Analysis is triggered.

### 2. Sentiment Injection (CryptoPanic)
The agent fetches the top 3 headlines.
- **Sentiment Score**: Aggregated positive votes from the community.
- **Context Construction**: `Prompt = [System Role] + [Current Price Data] + [Headlines]`.

### 3. AI Decision Matrix (DeepSeek)
The AI must return a JSON response with a **Confidence Score (C)**.
- **Action**: `BUY` is only considered if `C >= 70`.
- **Logic**: If news is negative (e.g., a hack), the AI will output `NO_ACTION` regardless of the price drop.

### 4. Trade Execution (OKX)
- **Trade Amount (A)**: `A = AccountBalance * 0.90 * 0.50` (Safety buffer of 10% reserved, 50% of remainder used).
- **Size (S)**: `S = A / CurrentPrice`.
- **Atomic Order**:
    1.  `POST /api/v5/trade/order` (Market Buy)
    2.  `POST /api/v5/trade/order-algo` (Conditional Stop Loss @ -2.5%)
    3.  `POST /api/v5/trade/order-algo` (Conditional Take Profit @ +5.0%)

---

**Developed with ❤️ by the TradeFlow Team**
