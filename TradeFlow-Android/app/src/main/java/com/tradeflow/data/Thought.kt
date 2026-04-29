package com.tradeflow.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Thought(
    val timestamp: Long,
    val message: String,
    val confidence: Int?
) {
    fun getFormattedTime(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }
}

object ThoughtManager {
    private val thoughtsList = mutableListOf<Thought>()
    private val _thoughts = MutableLiveData<List<Thought>>(emptyList())
    val thoughts: LiveData<List<Thought>> = _thoughts

    fun addThought(message: String, confidence: Int? = null) {
        thoughtsList.add(0, Thought(System.currentTimeMillis(), message, confidence))
        if (thoughtsList.size > 100) {
            thoughtsList.removeLast()
        }
        _thoughts.postValue(thoughtsList.toList())
    }
}
