package com.tradeflow.utils

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
private val dateTimeFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

fun Long.toFormattedDate(): String {
    return dateFormat.format(Date(this))
}

fun Long.toFormattedDateTime(): String {
    return dateTimeFormat.format(Date(this))
}

fun Double.toCurrency(): String {
    return currencyFormat.format(this)
}

fun Double.toPercentage(): String {
    return String.format("%.2f%%", this)
}

fun Double.toPnlColor(): Int {
    return if (this >= 0) {
        android.graphics.Color.parseColor("#4CAF50") // Green
    } else {
        android.graphics.Color.parseColor("#F44336") // Red
    }
}
