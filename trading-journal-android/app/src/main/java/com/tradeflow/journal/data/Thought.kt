package com.tradeflow.journal.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Thought(
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val confidence: Int?
) {
    fun getFormattedTime(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }
}

