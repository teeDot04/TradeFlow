package com.tradeflow.data

import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object SampleData {
    private val symbols = listOf(
        "AAPL", "GOOGL", "MSFT", "AMZN", "TSLA", "META", "NVDA", "AMD",
        "NFLX", "DIS", "BA", "JPM", "GS", "V", "WMT", "PG"
    )
    
    private val strategies = listOf(
        "Breakout", "Pullback", "Trend Following", "Mean Reversion",
        "Support/Resistance", "Moving Average Crossover", "RSI Divergence"
    )
    
    fun generateSampleTrades(): List<Trade> {
        val trades = mutableListOf<Trade>()
        val calendar = Calendar.getInstance()
        
        for (i in 1..50) {
            val symbol = symbols.random()
            val type = if (Random.nextBoolean()) TradeType.BUY else TradeType.SELL
            val basePrice = Random.nextDouble(50.0, 500.0)
            val quantity = Random.nextDouble(1.0, 100.0)
            
            // 60% win rate
            val isWin = Random.nextDouble() < 0.6
            val priceChange = if (isWin) {
                Random.nextDouble(0.5, 5.0) // 0.5% to 5% gain
            } else {
                Random.nextDouble(-3.0, -0.3) // 0.3% to 3% loss
            }
            
            val entryPrice = basePrice
            val exitPrice = basePrice * (1 + priceChange / 100)
            
            // Generate dates (going back in time)
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            val exitDate = calendar.timeInMillis
            calendar.add(Calendar.HOUR, -Random.nextInt(1, 48)) // Hold for 1-48 hours
            val entryDate = calendar.timeInMillis
            
            // Reset calendar
            calendar.add(Calendar.HOUR, Random.nextInt(1, 48))
            calendar.add(Calendar.DAY_OF_YEAR, i)
            
            trades.add(
                Trade(
                    symbol = symbol,
                    type = type,
                    quantity = quantity,
                    entryPrice = entryPrice,
                    exitPrice = exitPrice,
                    entryDate = entryDate,
                    exitDate = exitDate,
                    strategy = strategies.random(),
                    notes = if (Random.nextBoolean()) "Good entry at support level" else ""
                )
            )
        }
        
        return trades.sortedByDescending { it.exitDate }
    }
}
