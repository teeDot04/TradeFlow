package com.tradeflow.journal.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "trades")
@TypeConverters(Converters::class)
data class Trade(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val symbol: String,
    val side: TradeSide,
    val entryPrice: Double,
    val exitPrice: Double,
    val quantity: Double,
    val entryTime: Long,
    val exitTime: Long,
    val strategy: String,
    val notes: String,
    val emotion: Emotion,
    val marketCondition: MarketCondition,
    val setupQuality: Int, // 1-10
    val grossPnL: Double,
    val totalFees: Double,
    val netPnL: Double,
    val returnPct: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val ohlcvData: OHLCVData? = null,
    
    // --- Advanced AI Data (Claude Model) ---
    val microstructure: String? = null,    // JSON: Spread, Depth, Order Imbalance
    val marketContext: String? = null,     // JSON: Liquidity, VWAP, Global Volume
    val riskMetrics: String? = null,       // JSON: Realized Vol, Gaps, Drawdown
    val sentimentData: String? = null,     // JSON: Funding Rates, Open Interest
    val fundamentalData: String? = null    // JSON: Market Cap, Events
)

enum class TradeSide {
    LONG, SHORT
}

enum class Emotion {
    CONFIDENT, NEUTRAL, ANXIOUS, FOMO, REVENGE_TRADING
}

enum class MarketCondition {
    TRENDING, RANGE_BOUND, VOLATILE, LOW_VOLUME
}

data class OHLCVData(
    val avgPrice: Double,
    val avgVolume: Double,
    val maxHigh: Double,
    val minLow: Double,
    val priceRange: Double,
    val volatility: Double,
    val numCandles: Int
)

// Type converters for Room
class Converters {
    @androidx.room.TypeConverter
    fun fromTradeSide(side: TradeSide): String = side.name
    
    @androidx.room.TypeConverter
    fun toTradeSide(value: String): TradeSide = TradeSide.valueOf(value)
    
    @androidx.room.TypeConverter
    fun fromEmotion(emotion: Emotion): String = emotion.name
    
    @androidx.room.TypeConverter
    fun toEmotion(value: String): Emotion = Emotion.valueOf(value)
    
    @androidx.room.TypeConverter
    fun fromMarketCondition(condition: MarketCondition): String = condition.name
    
    @androidx.room.TypeConverter
    fun toMarketCondition(value: String): MarketCondition = MarketCondition.valueOf(value)
    
    @androidx.room.TypeConverter
    fun fromOHLCVData(data: OHLCVData?): String? {
        return data?.let { com.google.gson.Gson().toJson(it) }
    }
    
    @androidx.room.TypeConverter
    fun toOHLCVData(value: String?): OHLCVData? {
        return value?.let { com.google.gson.Gson().fromJson(it, OHLCVData::class.java) }
    }
}
