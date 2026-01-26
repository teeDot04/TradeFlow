package com.tradeflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "trades")
data class Trade(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val symbol: String,
    val type: TradeType,
    val quantity: Double,
    val entryPrice: Double,
    val exitPrice: Double,
    val entryDate: Long,
    val exitDate: Long,
    val notes: String = "",
    val strategy: String = ""
) {
    // Calculate P&L based on trade type
    val pnl: Double
        get() = when (type) {
            TradeType.BUY -> (exitPrice - entryPrice) * quantity
            TradeType.SELL -> (entryPrice - exitPrice) * quantity
        }
    
    val pnlPercentage: Double
        get() = ((pnl / (entryPrice * quantity)) * 100)
    
    val isWin: Boolean
        get() = pnl > 0
}

enum class TradeType {
    BUY,
    SELL
}
