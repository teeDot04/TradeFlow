package com.tradeflow.journal.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tradeflow.journal.data.Trade
import com.tradeflow.journal.data.TradeSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * AgentCommentService
 *
 * Calls an OpenAI-compatible chat completions endpoint to generate a one or
 * two sentence rationale ("why this trade was taken / how it played out") that
 * is written into the Trade.notes field. Designed to work with DeepSeek,
 * OpenAI, OpenRouter, Groq, Together AI, Mistral and any other vendor that
 * exposes a /v1/chat/completions endpoint with the OpenAI schema.
 *
 * The base URL must include the `/v1` segment but no trailing slash, e.g.
 *   https://api.deepseek.com/v1
 *   https://api.openai.com/v1
 *   https://openrouter.ai/api/v1
 */
class AgentCommentService(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String,
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Returns a short natural-language reason for the trade, or null on failure.
     * The caller is responsible for falling back to a default note.
     */
    suspend fun generateReason(trade: Trade): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null

        val sideStr = if (trade.side == TradeSide.LONG) "LONG" else "SHORT"
        val outcome = if (trade.netPnL >= 0) "WIN" else "LOSS"
        val volStr = trade.ohlcvData?.let { String.format("%.2f%%", it.volatility) } ?: "n/a"
        val rangeStr = trade.ohlcvData?.let {
            String.format("%.2f - %.2f", it.minLow, it.maxHigh)
        } ?: "n/a"

        val context = buildString {
            append("Symbol: ").append(trade.symbol).append('\n')
            append("Side: ").append(sideStr).append('\n')
            append("Entry: ").append(String.format("%.4f", trade.entryPrice)).append('\n')
            append("Exit: ").append(String.format("%.4f", trade.exitPrice)).append('\n')
            append("Return: ").append(String.format("%+.2f%%", trade.returnPct)).append('\n')
            append("Net PnL: $").append(String.format("%.2f", trade.netPnL)).append('\n')
            append("Outcome: ").append(outcome).append('\n')
            append("Market Condition: ").append(trade.marketCondition.name).append('\n')
            append("Setup Quality: ").append(trade.setupQuality).append("/10\n")
            append("Strategy: ").append(trade.strategy).append('\n')
            append("1H Volatility: ").append(volStr).append('\n')
            append("1H Price Range: ").append(rangeStr).append('\n')
            trade.microstructure?.let { append("Microstructure: ").append(it).append('\n') }
            trade.marketContext?.let { append("Market Context: ").append(it).append('\n') }
            trade.riskMetrics?.let { append("Risk Metrics: ").append(it).append('\n') }
            trade.sentimentData?.let { append("Sentiment: ").append(it).append('\n') }
            trade.fundamentalData?.let { append("Fundamentals: ").append(it).append('\n') }
        }

        val systemPrompt = "You are a concise trading-journal assistant. " +
            "Given a trade's data, write a one or two sentence note explaining " +
            "the most likely reason this trade was taken and what the data " +
            "suggests about the outcome. No markdown, no preamble, no lists. " +
            "Plain prose, max 240 characters."

        val payload = JsonObject().apply {
            addProperty("model", model)
            addProperty("temperature", 0.4)
            addProperty("max_tokens", 160)
            add("messages", gson.toJsonTree(listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to context),
            )))
        }

        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val body = payload.toString().toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    android.util.Log.w(
                        "AgentCommentService",
                        "AI call failed: HTTP ${resp.code} ${resp.message}",
                    )
                    return@withContext null
                }
                val raw = resp.body?.string() ?: return@withContext null
                val root = JsonParser.parseString(raw).asJsonObject
                val choices = root.getAsJsonArray("choices") ?: return@withContext null
                if (choices.size() == 0) return@withContext null
                val message = choices[0].asJsonObject.getAsJsonObject("message")
                val content = message?.get("content")?.asString ?: return@withContext null
                content.trim().ifBlank { null }
            }
        } catch (e: Exception) {
            android.util.Log.e("AgentCommentService", "AI call error", e)
            null
        }
    }
}
