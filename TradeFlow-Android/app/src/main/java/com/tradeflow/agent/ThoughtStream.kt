package com.tradeflow.agent

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Process-wide singleton that buffers the agent's "thought stream" and exposes
 * it to any observer (the dashboard RecyclerView, the foreground service
 * notification updater, etc.). Backed by a bounded ring of 500 lines so the
 * UI does not grow unboundedly while the Python loop runs for hours.
 */
object ThoughtStream : AgentCallback {

    private const val MAX_LINES = 500
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val buffer: ArrayDeque<String> = ArrayDeque(MAX_LINES)
    private val _lines = MutableLiveData<List<String>>(emptyList())
    val lines: LiveData<List<String>> = _lines

    private val _state = MutableLiveData("IDLE")
    val state: LiveData<String> = _state

    @Synchronized
    private fun append(line: String) {
        val stamp = timeFormat.format(Date())
        val entry = "[$stamp] $line"
        if (buffer.size >= MAX_LINES) buffer.removeFirst()
        buffer.addLast(entry)
        _lines.postValue(buffer.toList())
    }

    override fun onThoughtGenerated(thought: String, confidence: Int) {
        append("BRAIN: $thought (conf=$confidence)")
    }

    override fun onStateChanged(newState: String) {
        _state.postValue(newState)
        append("STATE -> $newState")
    }

    override fun onTradeExecuted(details: String) {
        append("EXECUTE: $details")
    }

    override fun onError(message: String) {
        append("ERROR: $message")
    }
}
