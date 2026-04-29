package com.tradeflow

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.tradeflow.data.AppDatabase
import com.tradeflow.data.PositionJournal
import com.tradeflow.data.ThoughtManager
import com.tradeflow.utils.EncryptedPrefs
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import okhttp3.*
import okio.ByteString
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.ArrayDeque

object AgentCore {
    private const val TAG = "AgentCore"
    private val mutex = Mutex()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
        
    private var webSocket: WebSocket? = null
    private var coroutineScope: CoroutineScope? = null
    
    var heartbeatListener: (() -> Unit)? = null
    var isSystemEnabled = true

    fun toggleAgent(active: Boolean) {
        isSystemEnabled = active
        ThoughtManager.addThought("Sovereign Control: $active", null)
    }

    fun triggerPanic() {
        ThoughtManager.addThought("CRITICAL: PANIC SIGNAL RECEIVED. Halting operations.", 100)
        isSystemEnabled = false
        // In pure Kotlin we drop triggers and stop agent operations safely
        stopAgent()
    }
    
    // Buffers for the 4 assets
    private val priceBuffers = mapOf(
        "BTC-USDT" to java.util.ArrayDeque<Double>(600),
        "ETH-USDT" to java.util.ArrayDeque<Double>(600),
        "SOL-USDT" to java.util.ArrayDeque<Double>(600),
        "LINK-USDT" to java.util.ArrayDeque<Double>(600)
    )
    
    private const val MAX_BUFFER_SIZE = 600
    private const val OKX_WS_URL = "wss://ws.okx.com:8443/ws/v5/public"
    private val gson = Gson()

    fun startAgent(context: Context) {
        if (coroutineScope != null) return
        coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        
        ThoughtManager.addThought("Native AgentCore starting...", null)
        connectWebSocket()
        startScanner(context)
    }

    fun stopAgent() {
        coroutineScope?.cancel()
        coroutineScope = null
        webSocket?.close(1000, "Agent stopped")
        webSocket = null
        ThoughtManager.addThought("Native AgentCore stopped.", null)
    }
    
    private fun connectWebSocket() {
        val request = Request.Builder().url(OKX_WS_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "OKX WS Connected")
                ThoughtManager.addThought("OKX WebSocket connected.", null)
                
                // Subscribe to assets
                priceBuffers.keys.forEach { instId ->
                    val subJson = """{"op": "subscribe", "args": [{"channel": "tickers", "instId": "$instId"}]}"""
                    webSocket.send(subJson)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = gson.fromJson(text, JsonObject::class.java)
                    if (json.has("arg") && json.has("data")) {
                        val arg = json.getAsJsonObject("arg")
                        val instId = arg.get("instId").asString
                        val dataArray = json.getAsJsonArray("data")
                        if (dataArray.size() > 0) {
                            val lastPrice = dataArray[0].asJsonObject.get("last").asDouble
                            synchronized(priceBuffers) {
                                val buffer = priceBuffers[instId]
                                if (buffer != null) {
                                    if (buffer.size >= MAX_BUFFER_SIZE) {
                                        buffer.pollFirst()
                                    }
                                    buffer.addLast(lastPrice)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "WS parse error: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WS Failure: ${t.message}")
                ThoughtManager.addThought("OKX WS Error: ${t.message}", null)
                // Reconnect after delay
                coroutineScope?.launch {
                    delay(5000)
                    connectWebSocket()
                }
            }
        })
    }

    private fun startScanner(context: Context) {
        coroutineScope?.launch {
            while (isActive) {
                delay(1000)
                if (!isSystemEnabled) continue
                heartbeatListener?.invoke()
                
                priceBuffers.forEach { (instId, buffer) ->
                    val prices = synchronized(priceBuffers) { buffer.toList() }
                    if (prices.size > 1) {
                        val peak = prices.maxOrNull() ?: 0.0
                        val current = prices.last()
                        
                        if (peak > 0.0) {
                            val drop = (peak - current) / peak
                            if (drop >= 0.03) {
                                ThoughtManager.addThought("Drop alert: $instId dropped ${String.format("%.2f", drop * 100)}% from peak.", 70)
                                if (mutex.tryLock()) {
                                    launch {
                                        try {
                                            thinkAndAct(context, instId, current)
                                        } finally {
                                            delay(60000)
                                            mutex.unlock()
                                        }
                                    }
                                } else {
                                    Log.d(TAG, "Mutex locked, dropping trigger for $instId")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun thinkAndAct(context: Context, instId: String, currentPrice: Double) {
        val db = AppDatabase.getDatabase(context, this.coroutineScope!!)
        val positionDao = db.positionJournalDao()
        
        // Circuit Breaker Check
        val since24Hours = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val liquidations = positionDao.getRecentLiquidationsCount(since24Hours)
        if (liquidations >= 3) {
            ThoughtManager.addThought("Circuit Breaker: $liquidations liquidations in 24hrs. Hibernating.", 99)
            return
        }

        val okxKey = EncryptedPrefs.getKey(context, EncryptedPrefs.KEY_OKX_API_KEY) ?: ""
        val okxSecret = EncryptedPrefs.getKey(context, EncryptedPrefs.KEY_OKX_API_SECRET) ?: ""
        val okxPass = EncryptedPrefs.getKey(context, EncryptedPrefs.KEY_OKX_PASSPHRASE) ?: ""
        val deepseekKey = EncryptedPrefs.getKey(context, EncryptedPrefs.KEY_DEEPSEEK_API_KEY) ?: ""
        
        if (okxKey.isEmpty() || okxSecret.isEmpty() || deepseekKey.isEmpty()) {
            ThoughtManager.addThought("Missing credentials. Skipping DeepSeek analysis.", null)
            return
        }

        ThoughtManager.addThought("Consulting DeepSeek for $instId...", 50)
        
        // 1. Brain (DeepSeek API)
        val deepSeekRequest = """
        {
            "model": "deepseek-v4-flash",
            "messages": [
                {"role": "system", "content": "You are a sovereign trading assistant. Respond ONLY with a JSON object containing keys 'action' (string: 'BUY' or 'NO_ACTION'), 'rationale' (string), 'confidence' (integer 0-100)."},
                {"role": "user", "content": "Analyze drop for $instId. Current price: $currentPrice."}
            ],
            "response_format": {"type": "json_object"}
        }
        """.trimIndent()
        
        val reqBody = RequestBody.create(MediaType.parse("application/json"), deepSeekRequest)
        val dsRequest = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .addHeader("Authorization", "Bearer $deepseekKey")
            .post(reqBody)
            .build()

        var action = "NO_ACTION"
        var rationale = "No response"
        var confidence = 0

        try {
            client.newCall(dsRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val dsRes = gson.fromJson(response.body()?.string(), JsonObject::class.java)
                    val choice = dsRes.getAsJsonArray("messages") ?: dsRes.getAsJsonArray("choices")
                    val content = choice?.get(0)?.asJsonObject?.getAsJsonObject("message")?.get("content")?.asString
                    if (content != null) {
                        val result = gson.fromJson(content, JsonObject::class.java)
                        action = result.get("action").asString
                        rationale = result.get("rationale").asString
                        confidence = result.get("confidence").asInt
                        ThoughtManager.addThought("DeepSeek: $action ($confidence%) - $rationale", confidence)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "DeepSeek API Error: ${e.message}")
            ThoughtManager.addThought("DeepSeek Error: ${e.message}", null)
        }

        if (action == "BUY") {
            executeTrade(context, instId, currentPrice, okxKey, okxSecret, okxPass, positionDao)
        }
    }

    private suspend fun executeTrade(context: Context, instId: String, currentPrice: Double, okxKey: String, okxSecret: String, okxPass: String, positionDao: com.tradeflow.data.PositionJournalDao) {
        val uuid = UUID.randomUUID().toString()
        
        // 2. DB Idempotency Record
        val position = PositionJournal(uuid = uuid, instId = instId, status = "PENDING", action = "BUY")
        positionDao.insert(position)
        ThoughtManager.addThought("Prepared Order: UUID=$uuid", 90)
        
        // 3. OKX Sizing Check
        val balance = fetchAccountBalance(okxKey, okxSecret, okxPass)
        val tradeAmount = balance * 0.90 * 0.50
        val size = tradeAmount / currentPrice
        
        if (size <= 0.0) {
            ThoughtManager.addThought("Insufficient sizing calculated.", null)
            return
        }
        
        // 4. Execution (Atomic Buy Order & Algorithm Stop Loss)
        val orderSuccess = placeOrder(okxKey, okxSecret, okxPass, instId, "buy", size, uuid)
        if (orderSuccess) {
            ThoughtManager.addThought("Atomic Buy Complete for $instId. Executing exchange Stop Loss...", 100)
            placeStopLoss(okxKey, okxSecret, okxPass, instId, "sell", size, currentPrice * 0.975)
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
                    val json = gson.fromJson(response.body()?.string(), JsonObject::class.java)
                    val data = json.getAsJsonArray("data").get(0).asJsonObject
                    val availEq = data.get("availEq").asDouble
                    availEq
                } else 0.0
            }
        } catch (e: Exception) { 0.0 }
    }

    private fun placeOrder(key: String, secret: String, passphrase: String, instId: String, tdMode: String, sz: Double, clOrdId: String): Boolean {
        val timestamp = System.currentTimeMillis().toString()
        val method = "POST"
        val path = "/api/v5/trade/order"
        
        val bodyObj = JsonObject().apply {
            addProperty("instId", instId)
            addProperty("tdMode", "cash")
            addProperty("side", tdMode)
            addProperty("ordType", "market")
            addProperty("sz", sz.toString())
            addProperty("clOrdId", clOrdId)
        }
        val bodyStr = bodyObj.toString()
        val signature = OkxSigner.signRequest(timestamp, method, path, bodyStr, secret)
        
        val reqBody = RequestBody.create(MediaType.parse("application/json"), bodyStr)
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
            addProperty("slOrdPx", "-1") // Market SL
        }
        val bodyStr = bodyObj.toString()
        val signature = OkxSigner.signRequest(timestamp, method, path, bodyStr, secret)
        
        val reqBody = RequestBody.create(MediaType.parse("application/json"), bodyStr)
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
}
