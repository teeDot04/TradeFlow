package com.tradeflow.journal

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.tradeflow.journal.data.TradeDatabase
import com.tradeflow.journal.data.PositionJournal
import com.tradeflow.journal.data.PositionJournalDao
import com.tradeflow.journal.data.ThoughtManager
import com.tradeflow.journal.data.UserPreferencesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

object AgentCore {
    private const val TAG = "AgentCore"
    private val mutex = Mutex()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
        
    private var coroutineScope: CoroutineScope? = null
    
    var heartbeatListener: (() -> Unit)? = null
    var isSystemEnabled = true

    private val priceBuffers = mapOf(
        "BTC-USDT" to java.util.ArrayDeque<Double>(600),
        "ETH-USDT" to java.util.ArrayDeque<Double>(600),
        "SOL-USDT" to java.util.ArrayDeque<Double>(600),
        "LINK-USDT" to java.util.ArrayDeque<Double>(600)
    )
    
    private const val MAX_BUFFER_SIZE = 600
    private val gson = Gson()

    fun toggleAgent(active: Boolean) {
        isSystemEnabled = active
        ThoughtManager.addThought("Sovereign Control: $active", null)
    }

    fun startAgent(context: Context) {
        if (coroutineScope != null) return
        coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        
        ThoughtManager.addThought("Sovereign AgentCore starting...", null)
        startScanner(context)
    }

    fun stopAgent() {
        coroutineScope?.cancel()
        coroutineScope = null
        ThoughtManager.addThought("AgentCore stopped.", null)
    }

    fun testDeepSeek(context: Context) {
        coroutineScope?.launch {
            ThoughtManager.addThought("AI Test: Manual diagnostic request sent...", 30)
            thinkAndAct(context, "BTC-USDT-TEST", 50000.0)
        }
    }

    
    private fun startScanner(context: Context) {
        coroutineScope?.launch {
            while (isActive) {
                if (isSystemEnabled) {
                    heartbeatListener?.invoke()
                    
                    val activePairs = priceBuffers.keys.joinToString(", ")
                    if (System.currentTimeMillis() % 60000 < 3000) { // Every ~1 min
                         ThoughtManager.addThought("Scanner Pulse: Monitoring $activePairs...", 20)
                    }

                    priceBuffers.keys.forEach { instId ->
                        launch {
                            val price = fetchTicker(instId)
                            if (price > 0.0) {
                                updateBufferAndCheck(context, instId, price)
                            } else {
                                // Log if fetching fails
                                Log.e(TAG, "Failed to fetch price for $instId")
                            }
                        }
                    }
                }
                delay(2000)
            }
        }
    }

    private fun fetchTicker(instId: String): Double {
        val url = "https://www.okx.com/api/v5/market/ticker?instId=$instId"
        val request = Request.Builder().url(url).build()
        
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = gson.fromJson(response.body?.string(), JsonObject::class.java)
                    val data = json.getAsJsonArray("data")
                    if (data != null && data.size() > 0) {
                        val price = data.get(0).asJsonObject.get("last").asDouble
                        if (instId == "BTC-USDT" && System.currentTimeMillis() % 60000 < 3000) {
                             ThoughtManager.addThought("BTC-USDT Pulse: $price", 10)
                        }
                        price
                    } else 0.0
                } else 0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }

    private fun updateBufferAndCheck(context: Context, instId: String, lastPrice: Double) {
        val buffer = priceBuffers[instId] ?: return
        
        synchronized(priceBuffers) {
            if (buffer.size >= MAX_BUFFER_SIZE) {
                buffer.pollFirst()
            }
            buffer.addLast(lastPrice)
        }

        val prices = synchronized(priceBuffers) { buffer.toList() }
        if (prices.size > 1) {
            val peak = prices.maxOrNull() ?: 0.0
            if (peak > 0.0) {
                val drop = (peak - lastPrice) / peak
                if (drop >= 0.03) {
                    ThoughtManager.addThought("Drop alert: $instId dropped ${String.format("%.2f", drop * 100)}% from peak.", 70)
                    if (mutex.tryLock()) {
                        coroutineScope?.launch {
                            try {
                                thinkAndAct(context, instId, lastPrice)
                            } finally {
                                delay(60000)
                                mutex.unlock()
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun thinkAndAct(context: Context, instId: String, currentPrice: Double) {
        val db = TradeDatabase.getDatabase(context)
        val positionDao = db.positionJournalDao()
        val prefs = UserPreferencesRepository(context)
        
        val since24Hours = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val liquidations = positionDao.getRecentLiquidationsCount(since24Hours)
        if (liquidations >= 3) {
            ThoughtManager.addThought("Circuit Breaker: $liquidations liquidations in 24hrs. Hibernating.", 99)
            return
        }

        val okxKey = prefs.okxApiKey.first() ?: ""
        val okxSecret = prefs.okxApiSecret.first() ?: ""
        val okxPass = prefs.okxApiPassphrase.first() ?: ""
        val deepseekKey = prefs.deepSeekApiKey.first() ?: ""
        val cryptopanicKey = prefs.cryptoPanicApiKey.first() ?: ""
        
        val isSimulated = prefs.simulatedMode.first()
        
        if (!isSimulated && (okxKey.isEmpty() || okxSecret.isEmpty())) {
            ThoughtManager.addThought("Missing credentials in settings.", null)
            return
        }

        if (deepseekKey.isEmpty()) {
            ThoughtManager.addThought("Missing DeepSeek key.", null)
            return
        }

        // Fetch News if key is available
        var newsContext = "No recent news available."
        if (cryptopanicKey.isNotEmpty()) {
            ThoughtManager.addThought("Fetching latest news for $instId...", 40)
            val coin = instId.split("-")[0]
            newsContext = fetchNews(cryptopanicKey, coin)
        }

        ThoughtManager.addThought("Consulting DeepSeek for $instId...", 50)
        
        val deepSeekRequest = """
        {
            "model": "deepseek-v4-flash",
            "messages": [
                {"role": "system", "content": "You are a sovereign trading assistant. Respond ONLY with a JSON object containing keys 'action' (string: 'BUY' or 'NO_ACTION'), 'rationale' (string), 'confidence' (integer 0-100)."},
                {"role": "user", "content": "Analyze drop for $instId. Current price: $currentPrice.\n\nNEWS CONTEXT:\n$newsContext\n\n${if(instId.contains("TEST")) "PLEASE RESPOND WITH ACTION: BUY FOR SYSTEM TEST." else ""}"}
            ],
            "response_format": {"type": "json_object"}
        }
        """.trimIndent()
        
        val mediaType = "application/json".toMediaTypeOrNull()
        val reqBody = deepSeekRequest.toRequestBody(mediaType)
        val dsRequest = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .addHeader("Authorization", "Bearer $deepseekKey")
            .post(reqBody)
            .build()

        var action = "NO_ACTION"
        var rationale = "No response"
        var confidence = 0

        try {
            withContext(Dispatchers.IO) {
                client.newCall(dsRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val dsRes = gson.fromJson(response.body?.string(), JsonObject::class.java)
                        val choices = dsRes.getAsJsonArray("choices")
                        val content = choices?.get(0)?.asJsonObject?.getAsJsonObject("message")?.get("content")?.asString
                        if (content != null) {
                            val result = gson.fromJson(content, JsonObject::class.java)
                            action = result.get("action").asString
                            rationale = result.get("rationale").asString
                            confidence = result.get("confidence").asInt
                            ThoughtManager.addThought("DeepSeek: $action ($confidence%) - $rationale", confidence)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            ThoughtManager.addThought("DeepSeek Error: ${e.message}", null)
        }

        if (action == "BUY") {
            if (isSimulated) {
                ThoughtManager.addThought("[SIMULATION] Executing dummy BUY for $instId @ $currentPrice", 100)
                
                // Insert a dummy trade into the database for visual verification
                val dummyTrade = com.tradeflow.journal.data.Trade(
                    symbol = instId.replace("-", "/"),
                    side = com.tradeflow.journal.data.TradeSide.LONG,
                    entryPrice = currentPrice,
                    exitPrice = currentPrice * 1.01, // Simulated 1% win
                    quantity = 1000.0 / currentPrice,
                    entryTime = System.currentTimeMillis(),
                    exitTime = System.currentTimeMillis() + 60000,
                    timestamp = System.currentTimeMillis(),
                    strategy = "DeepSeek AI (Simulated)",
                    notes = rationale,
                    emotion = com.tradeflow.journal.data.Emotion.NEUTRAL,
                    marketCondition = com.tradeflow.journal.data.MarketCondition.VOLATILE,
                    setupQuality = confidence / 10,
                    grossPnL = 10.0,
                    totalFees = 1.0,
                    netPnL = 9.0,
                    returnPct = 0.9,
                    ohlcvData = null
                )
                coroutineScope?.launch {
                    db.tradeDao().insertTrade(dummyTrade)
                    ThoughtManager.addThought("[SIMULATION] Dummy Trade logged to Journal.", 100)
                }
            } else {
                executeTrade(context, instId, currentPrice, okxKey, okxSecret, okxPass, positionDao)
            }
        }
    }

    private suspend fun executeTrade(context: Context, instId: String, currentPrice: Double, okxKey: String, okxSecret: String, okxPass: String, positionDao: PositionJournalDao) {
        val uuid = UUID.randomUUID().toString()
        val position = PositionJournal(uuid = uuid, instId = instId, status = "PENDING", action = "BUY")
        positionDao.insert(position)
        ThoughtManager.addThought("Prepared Order: UUID=$uuid", 90)
        
        val balance = withContext(Dispatchers.IO) { fetchAccountBalance(okxKey, okxSecret, okxPass) }
        val tradeAmount = balance * 0.90 * 0.50
        val size = tradeAmount / currentPrice
        
        if (size <= 0.0) {
            ThoughtManager.addThought("Insufficient sizing calculated.", null)
            return
        }
        
        val orderSuccess = withContext(Dispatchers.IO) { placeOrder(okxKey, okxSecret, okxPass, instId, "buy", size, uuid) }
        if (orderSuccess) {
            ThoughtManager.addThought("Atomic Buy Complete for $instId. Executing exchange Stop Loss...", 100)
            withContext(Dispatchers.IO) { placeStopLoss(okxKey, okxSecret, okxPass, instId, "sell", size, currentPrice * 0.975) }
        }
    }

    private fun fetchAccountBalance(key: String, secret: String, passphrase: String): Double {
        val timestamp = System.currentTimeMillis().toString()
        val method = "GET"
        val path = "/api/v5/account/balance"
        val signature = OkxSigner.signRequest(timestamp, method, path, "", secret)
        
        val request = Request.Builder()
            .url("https://www.okx.com$path")
            .addHeader("OK-ACCESS-KEY", key)
            .addHeader("OK-ACCESS-SIGN", signature)
            .addHeader("OK-ACCESS-TIMESTAMP", timestamp)
            .addHeader("OK-ACCESS-PASSPHRASE", passphrase)
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = gson.fromJson(response.body?.string(), JsonObject::class.java)
                    val data = json.getAsJsonArray("data").get(0).asJsonObject
                    data.get("availEq").asDouble
                } else 0.0
            }
        } catch (e: Exception) { 0.0 }
    }

    private fun placeOrder(key: String, secret: String, passphrase: String, instId: String, side: String, sz: Double, clOrdId: String): Boolean {
        val timestamp = System.currentTimeMillis().toString()
        val method = "POST"
        val path = "/api/v5/trade/order"
        
        val bodyObj = JsonObject().apply {
            addProperty("instId", instId)
            addProperty("tdMode", "cash")
            addProperty("side", side)
            addProperty("ordType", "market")
            addProperty("sz", sz.toString())
            addProperty("clOrdId", clOrdId)
        }
        val bodyStr = bodyObj.toString()
        val signature = OkxSigner.signRequest(timestamp, method, path, bodyStr, secret)
        
        val mediaType = "application/json".toMediaTypeOrNull()
        val reqBody = bodyStr.toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://www.okx.com$path")
            .addHeader("OK-ACCESS-KEY", key)
            .addHeader("OK-ACCESS-SIGN", signature)
            .addHeader("OK-ACCESS-TIMESTAMP", timestamp)
            .addHeader("OK-ACCESS-PASSPHRASE", passphrase)
            .post(reqBody)
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) { false }
    }

    private fun placeStopLoss(key: String, secret: String, passphrase: String, instId: String, side: String, sz: Double, slTriggerPrice: Double) {
        val timestamp = System.currentTimeMillis().toString()
        val method = "POST"
        val path = "/api/v5/trade/order-algo"
        
        val bodyObj = JsonObject().apply {
            addProperty("instId", instId)
            addProperty("tdMode", "cash")
            addProperty("side", side)
            addProperty("ordType", "conditional")
            addProperty("sz", sz.toString())
            addProperty("slTriggerPx", slTriggerPrice.toString())
            addProperty("slOrdPx", "-1")
        }
        val bodyStr = bodyObj.toString()
        val signature = OkxSigner.signRequest(timestamp, method, path, bodyStr, secret)
        
        val mediaType = "application/json".toMediaTypeOrNull()
        val reqBody = bodyStr.toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://www.okx.com$path")
            .addHeader("OK-ACCESS-KEY", key)
            .addHeader("OK-ACCESS-SIGN", signature)
            .addHeader("OK-ACCESS-TIMESTAMP", timestamp)
            .addHeader("OK-ACCESS-PASSPHRASE", passphrase)
            .post(reqBody)
            .build()
            
        try {
            client.newCall(request).execute().close()
        } catch (e: Exception) { }
    }

    private fun fetchNews(apiKey: String, coin: String): String {
        val url = "https://cryptopanic.com/api/v1/posts/?auth_token=$apiKey&currencies=$coin&filter=important&kind=news"
        val request = Request.Builder().url(url).build()
        
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = gson.fromJson(response.body?.string(), JsonObject::class.java)
                    val results = json.getAsJsonArray("results")
                    if (results != null && results.size() > 0) {
                        val headlines = mutableListOf<String>()
                        for (i in 0 until minOf(results.size(), 3)) {
                            val post = results.get(i).asJsonObject
                            val title = post.get("title").asString
                            val sentiment = post.get("votes")?.asJsonObject?.get("positive")?.asInt ?: 0
                            headlines.add("- $title (Positive votes: $sentiment)")
                        }
                        headlines.joinToString("\n")
                    } else "No major news found for $coin."
                } else "Failed to fetch news."
            }
        } catch (e: Exception) { "Error fetching news: ${e.message}" }
    }
}
