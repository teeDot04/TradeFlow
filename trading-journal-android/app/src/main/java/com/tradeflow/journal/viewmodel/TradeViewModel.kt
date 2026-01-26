package com.tradeflow.journal.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tradeflow.journal.api.OkxApiService
import com.tradeflow.journal.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

class TradeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TradeRepository
    private val preferencesRepository: UserPreferencesRepository
    private val okxApiService = OkxApiService.create()
    
    val allTrades: StateFlow<List<Trade>>
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _selectedTrade = MutableStateFlow<Trade?>(null)
    val selectedTrade: StateFlow<Trade?> = _selectedTrade.asStateFlow()
    
    val makerFee: StateFlow<Double>
    val takerFee: StateFlow<Double>

    val gitSyncUrl: StateFlow<String?>
    val gitToken: StateFlow<String?>
    
    init {
        val tradeDao = TradeDatabase.getDatabase(application).tradeDao()
        repository = TradeRepository(tradeDao)
        preferencesRepository = UserPreferencesRepository(application)
        
        allTrades = repository.allTrades.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        
        makerFee = preferencesRepository.makerFee.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.1
        )
        
        takerFee = preferencesRepository.takerFee.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.1
        )
        
        gitSyncUrl = preferencesRepository.gitSyncUrl.stateIn(
             scope = viewModelScope,
             started = SharingStarted.WhileSubscribed(5000),
             initialValue = null
        )
        
        gitToken = preferencesRepository.gitToken.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
        
        // Auto-Sync Git Journal on App Start
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(gitSyncUrl, gitToken) { url, token -> Pair(url, token) }
                .collect { (url, token) ->
                    if (!url.isNullOrBlank()) {
                        android.util.Log.d("TradeViewModel", "Auto-Syncing from: $url")
                        importTradesFromUrl(url, token)
                    }
                }
        }
    }

    fun selectTrade(trade: Trade?) {
        _selectedTrade.value = trade
    }


    private suspend fun fetchOHLCVData(symbol: String, entryTime: Long, exitTime: Long): OHLCVData? {
        return try {
            val instId = symbol.replace("/", "-")
            // Request candles older than 'exitTime' (going backwards in time)
            val response = okxApiService.getCandles(
                instId = instId,
                bar = "1H",
                after = exitTime, // Get data OLDER than exitTime
                limit = 100
            )
            
            if (response.code == "0" && response.data.isNotEmpty()) {
                val candles = response.data.map { candle ->
                    Candle(
                        timestamp = candle[0].toLong(),
                        open = candle[1].toDouble(),
                        high = candle[2].toDouble(),
                        low = candle[3].toDouble(),
                        close = candle[4].toDouble(),
                        volume = candle[5].toDouble()
                    )
                }.filter { it.timestamp + 3600000 > entryTime } // Keep candles that overlap with trade start
                
                if (candles.isNotEmpty()) {
                    calculateOHLCVSummary(candles)
                } else {
                    android.util.Log.w("TradeViewModel", "No candles found in range for $symbol")
                    null
                }
            } else {
                android.util.Log.e("TradeViewModel", "API Error: ${response.code} - ${response.msg}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("TradeViewModel", "Error fetching OHLCV data", e)
            null
        }
    }

    private fun calculateOHLCVSummary(candles: List<Candle>): OHLCVData {
        val prices = candles.map { it.close }
        val volumes = candles.map { it.volume }
        val highs = candles.map { it.high }
        val lows = candles.map { it.low }
        
        val avgPrice = prices.average()
        val avgVolume = volumes.average()
        val maxHigh = highs.maxOrNull() ?: 0.0
        val minLow = lows.minOrNull() ?: 0.0
        val priceRange = maxHigh - minLow
        val volatility = calculateVolatility(prices)
        
        return OHLCVData(
            avgPrice = avgPrice,
            avgVolume = avgVolume,
            maxHigh = maxHigh,
            minLow = minLow,
            priceRange = priceRange,
            volatility = volatility,
            numCandles = candles.size
        )
    }

    private fun calculateVolatility(prices: List<Double>): Double {
        if (prices.size < 2) return 0.0
        
        val returns = mutableListOf<Double>()
        for (i in 1 until prices.size) {
            returns.add((prices[i] - prices[i - 1]) / prices[i - 1])
        }
        
        val mean = returns.average()
        val variance = returns.map { (it - mean).pow(2) }.average()
        return sqrt(variance) * 100
    }

    fun deleteTrade(trade: Trade) {
        viewModelScope.launch {
            repository.deleteTrade(trade)
        }
    }

    fun deleteAllTrades() {
        viewModelScope.launch {
            repository.deleteAllTrades()
        }
    }

    // Statistics calculations
    fun calculateStats(trades: List<Trade>): TradeStats {
        if (trades.isEmpty()) {
            return TradeStats()
        }
        
        val sortedTrades = trades.sortedBy { it.timestamp }
        val wins = sortedTrades.filter { it.netPnL > 0 }
        val losses = sortedTrades.filter { it.netPnL < 0 }
        
        val totalWins = wins.sumOf { it.netPnL }
        val totalLosses = losses.sumOf { kotlin.math.abs(it.netPnL) }
        
        // --- Max Drawdown Calculation (Equity Compounding %) ---
        // This is the "True" drawdown that reflects impact on wallet.
        // We start with a hypothetical 100% equity and compound the returns.
        var maxDrawdownCompounded = 0.0
        var currentEquity = 100.0
        var peakEquity = 100.0
        
        // --- Streak Calculation ---
        var maxWinStreak = 0
        var maxLossStreak = 0
        var currentWinStreak = 0
        var currentLossStreak = 0
        var tempWinStreak = 0
        var tempLossStreak = 0
        
        sortedTrades.forEach { trade ->
            // Compound the return: New Equity = Old Equity * (1 + ReturnPct/100)
            // e.g. +5% -> * 1.05, -5% -> * 0.95
            currentEquity = currentEquity * (1 + (trade.returnPct / 100))
            
            if (currentEquity > peakEquity) {
                peakEquity = currentEquity
            }
            
            // Drawdown is percentage drop from Peak
            // (Peak - Current) / Peak
            val drawdown = ((peakEquity - currentEquity) / peakEquity) * 100
            
            if (drawdown > maxDrawdownCompounded) {
                maxDrawdownCompounded = drawdown
            }
            
            // Streaks
            if (trade.netPnL > 0) {
                tempWinStreak++
                tempLossStreak = 0
            } else {
                tempLossStreak++
                tempWinStreak = 0
            }
            
            if (tempWinStreak > maxWinStreak) maxWinStreak = tempWinStreak
            if (tempLossStreak > maxLossStreak) maxLossStreak = tempLossStreak
        }
        
        currentWinStreak = tempWinStreak
        currentLossStreak = tempLossStreak
        
        return TradeStats(
            totalTrades = trades.size,
            winRate = (wins.size.toDouble() / trades.size) * 100,
            totalPnL = trades.sumOf { it.netPnL },
            avgWin = if (wins.isNotEmpty()) totalWins / wins.size else 0.0,
            avgLoss = if (losses.isNotEmpty()) totalLosses / losses.size else 0.0,
            largestWin = wins.maxOfOrNull { it.netPnL } ?: 0.0,
            largestLoss = losses.minOfOrNull { it.netPnL } ?: 0.0,
            profitFactor = if (totalLosses > 0) totalWins / totalLosses else if (totalWins > 0) 999.0 else 0.0,
            avgReturn = trades.map { it.returnPct }.average(),
            maxDrawdown = maxDrawdownCompounded, // Now stores Real Equity DD %
            maxWinStreak = maxWinStreak,
            maxLossStreak = maxLossStreak,
            currentWinStreak = currentWinStreak,
            currentLossStreak = currentLossStreak
        )
    }

    fun importTradesFromCsv(uri: android.net.Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                
                // Read Header
                val header = reader.readLine() // Expecting: Symbol,Date,Entry Price,Exit Price,Side
                
                var line = reader.readLine()
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                var importedCount = 0
                
                while (line != null) {
                    val tokens = line.split(",")
                    if (tokens.size >= 5) {
                        // 1. Parse Basic Data
                        var symbol = tokens[0].trim()
                        if (!symbol.contains("/") && symbol.endsWith("USDT")) {
                             symbol = symbol.replace("USDT", "/USDT")
                        }
                        
                        val dateStr = tokens[1].trim()
                        val entryPrice = tokens[2].toDoubleOrNull() ?: 0.0
                        val exitPrice = tokens[3].toDoubleOrNull() ?: 0.0
                        val sideStr = tokens[4].trim().uppercase()
                        val side = if (sideStr.contains("SELL") || sideStr.contains("SHORT")) TradeSide.SHORT else TradeSide.LONG
                        
                        val timestamp = try {
                            if (dateStr.contains(":")) {
                                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).parse(dateStr)?.time
                            } else {
                                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(dateStr)?.time
                            }
                        } catch (e: Exception) {
                            null
                        } ?: System.currentTimeMillis()
                        
                        // 2. Calculate PnL & Fees (Standard 0.1%)
                        val quantity = 1000.0 / entryPrice // Simulate $1000 size per trade
                        val takerFeeRate = 0.001 
                        
                        val grossPnL = if (side == TradeSide.LONG) {
                            (exitPrice - entryPrice) * quantity
                        } else {
                            (entryPrice - exitPrice) * quantity
                        }
                        
                        val entryVal = entryPrice * quantity
                        val exitVal = exitPrice * quantity
                        val totalFees = (entryVal + exitVal) * takerFeeRate
                        val netPnL = grossPnL - totalFees
                        val returnPct = (netPnL / entryVal) * 100
                        
                        // 3. Fetch OHLCV Data (Auto-Enrichment)
                        // Using a 1h duration assumption for the "exit time" if not provided, just to fetch context
                        val exitTime = timestamp + 3600000L 
                        val ohlcvData = try {
                            fetchOHLCVData(symbol, timestamp, exitTime)
                        } catch (e: Exception) {
                            null
                        }
                        
                        val marketCondition = if (ohlcvData != null) {
                             if (ohlcvData.volatility > 4.0) MarketCondition.VOLATILE else MarketCondition.TRENDING
                        } else {
                            MarketCondition.RANGE_BOUND // Default if fetch fails
                        }

                        // 4. Create & Insert Trade
                        val trade = Trade(
                            symbol = symbol,
                            side = side,
                            entryPrice = entryPrice,
                            exitPrice = exitPrice,
                            quantity = quantity,
                            entryTime = timestamp,
                            exitTime = exitTime,
                            timestamp = timestamp,
                            strategy = "Imported CSV",
                            notes = "Imported from CSV. " + (if (ohlcvData != null) "${ohlcvData.volatility}% Vol" else ""),
                            emotion = Emotion.NEUTRAL,
                            marketCondition = marketCondition,
                            setupQuality = 3, // Neutral default
                            grossPnL = grossPnL,
                            totalFees = totalFees,
                            netPnL = netPnL,
                            returnPct = returnPct,
                            ohlcvData = ohlcvData
                        )
                        
                        repository.insertTrade(trade)
                        importedCount++
                    }
                    line = reader.readLine()
                }
                reader.close()
                android.widget.Toast.makeText(getApplication(), "Imported $importedCount trades with market data", android.widget.Toast.LENGTH_LONG).show()
                
                // --- AUTO DELETE FEATURE ---
                try {
                    val rowsDeleted = getApplication<Application>().contentResolver.delete(uri, null, null)
                    if (rowsDeleted > 0) {
                        android.widget.Toast.makeText(getApplication(), "File deleted to prevent duplicates.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TradeViewModel", "Could not delete file", e)
                }
                // ---------------------------
                
            } catch (e: Exception) {
                android.util.Log.e("TradeViewModel", "Error importing CSV", e)
                android.widget.Toast.makeText(getApplication(), "Import Failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun importTradesFromUrl(csvUrl: String, token: String? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isLoading.value = true
            try {
                val url = java.net.URL(csvUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                
                // ADD AUTHORIZATION HEADER IF TOKEN EXISTS
                if (!token.isNullOrBlank()) {
                    connection.setRequestProperty("Authorization", "token $token")
                    connection.setRequestProperty("Accept", "application/vnd.github.v3.raw")
                }
                
                connection.connect()

                if (connection.responseCode == 200) {
                    val inputStream = connection.inputStream
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                    
                    // Skip Header
                    reader.readLine() 
                    
                    // 1. Fetch Existing Signatures (Idempotency Key: Symbol + Timestamp)
                    val existingTrades = repository.allTrades.first()
                    val existingSignatures = existingTrades.map { "${it.symbol}|${it.timestamp}" }.toHashSet()
                    
                    var line = reader.readLine()
                    var importedCount = 0
                    var skippedCount = 0
                    
                    while (line != null) {
                        try {
                            if (parseAndSaveTrade(line, existingSignatures)) {
                                importedCount++
                            } else {
                                skippedCount++
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("TradeViewModel", "Error parsing line: $line", e)
                        }
                        line = reader.readLine()
                    }
                    reader.close()
                    
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        if (importedCount > 0) {
                            android.widget.Toast.makeText(getApplication(), "Synced $importedCount new trades ($skippedCount skipped)", android.widget.Toast.LENGTH_LONG).show()
                            showTradeNotification(importedCount)
                        } else {
                            android.util.Log.d("TradeViewModel", "Sync Complete: No new trades.")
                        }
                    }
                } else {
                    throw Exception("HTTP ${connection.responseCode}")
                }
            } catch (e: Exception) {
                android.util.Log.e("TradeViewModel", "Sync Error", e)
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                     android.widget.Toast.makeText(getApplication(), "Sync Failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun saveGitToken(token: String) {
        viewModelScope.launch {
            preferencesRepository.saveGitToken(token)
        }
    }

    private fun showTradeNotification(count: Int) {
        val context = getApplication<Application>()
        val builder = androidx.core.app.NotificationCompat.Builder(context, "TRADE_ALERTS")
            .setSmallIcon(android.R.drawable.stat_sys_download_done) // Use generic icon for now
            .setContentTitle("New Trades Synced")
            .setContentText("TradeFlow added $count new trades from your bot.")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            
        val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
        try {
            // Permission check for Android 13+
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, 
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                 notificationManager.notify(1001, builder.build())
            }
        } catch (e: SecurityException) {
            // Handle permission missing
        }
    }

    private fun parseAndSaveTrade(line: String, existingSignatures: HashSet<String>): Boolean {
        val tokens = line.split(",")
        if (tokens.size < 5) return false

        // 1. Basic Data Parsing
        var symbol = tokens[0].trim()
        if (!symbol.contains("/") && symbol.endsWith("USDT")) {
             symbol = symbol.replace("USDT", "/USDT")
        }
        
        val dateStr = tokens[1].trim()
        // ... (Parsing logic similar to before, simplified access for timestamp)
        val timestamp = try {
            if (dateStr.contains(":")) {
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).parse(dateStr)?.time
            } else {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(dateStr)?.time
            }
        } catch (e: Exception) {
            null
        } ?: System.currentTimeMillis()

        // --- DEDUPLICATION CHECK ---
        val signature = "$symbol|$timestamp"
        if (existingSignatures.contains(signature)) {
            return false // Skip duplicate
        }
        // ---------------------------
        
        // Re-parsing the rest (Unavoidable duplication of logic unless refactored, but sticking to minimal edit for safety)
        val entryPrice = tokens[2].toDoubleOrNull() ?: 0.0
        val exitPrice = tokens[3].toDoubleOrNull() ?: 0.0
        val sideStr = tokens[4].trim().uppercase()
        val side = if (sideStr.contains("SELL") || sideStr.contains("SHORT")) com.tradeflow.journal.data.TradeSide.SHORT else com.tradeflow.journal.data.TradeSide.LONG

        // 2. Calculate PnL & Fees
        val quantity = 1000.0 / entryPrice 
        val takerFeeRate = 0.001 
        
        val grossPnL = if (side == com.tradeflow.journal.data.TradeSide.LONG) {
            (exitPrice - entryPrice) * quantity
        } else {
            (entryPrice - exitPrice) * quantity
        }
        
        val entryVal = entryPrice * quantity
        val totalFees = (entryVal + (exitPrice * quantity)) * takerFeeRate
        val netPnL = grossPnL - totalFees
        val returnPct = (netPnL / entryVal) * 100

        // 3. Optional Advanced Data (Indices 5+)
        val microstructure = if (tokens.size > 5) tokens[5] else null
        val marketContext = if (tokens.size > 6) tokens[6] else null
        val riskMetrics = if (tokens.size > 7) tokens[7] else null
        val sentimentData = if (tokens.size > 8) tokens[8] else null
        val fundamentalData = if (tokens.size > 9) tokens[9] else null
        
        // Parse OHLCV from CSV (Index 10)
        val ohlcvData = if (tokens.size > 10 && tokens[10].isNotBlank() && tokens[10] != "{}") {
             try {
                 val map = tokens[10].replace("{", "").replace("}", "").split("|")
                     .associate { 
                         val p = it.split(":")
                         p[0].trim().replace("\"", "") to p.getOrElse(1) { "0" }.toDoubleOrNull()
                     }
                 
                 com.tradeflow.journal.data.OHLCVData(
                     avgPrice = map["avgPrice"] ?: 0.0,
                     avgVolume = map["avgVolume"] ?: 0.0,
                     maxHigh = map["maxHigh"] ?: 0.0,
                     minLow = map["minLow"] ?: 0.0,
                     priceRange = map["priceRange"] ?: 0.0,
                     volatility = map["volatility"] ?: 0.0,
                     numCandles = (map["numCandles"] ?: 1.0).toInt()
                 )
             } catch (e: Exception) {
                 null
             }
        } else {
             null
        }

        // Parse Quality & Condition embedded in Market Context JSON
        var parsedQuality = 5
        var parsedCondition = com.tradeflow.journal.data.MarketCondition.TRENDING
        
        if (marketContext != null && marketContext.contains("{")) {
            try {
                val cleanJson = marketContext.replace("{", "").replace("}", "")
                val parts = cleanJson.split("|")
                
                parts.forEach { part ->
                    val pair = part.split(":")
                    if (pair.size == 2) {
                        val key = pair[0].trim().replace("\"", "")
                        val value = pair[1].trim().replace("\"", "")
                        
                        if (key == "quality") {
                            val rawScore = value.toDoubleOrNull()?.toInt() ?: 50
                            parsedQuality = (rawScore / 10).coerceIn(1, 10)
                        } else if (key == "condition") {
                            parsedCondition = try {
                                com.tradeflow.journal.data.MarketCondition.valueOf(value.uppercase())
                            } catch (e: Exception) {
                                com.tradeflow.journal.data.MarketCondition.TRENDING
                            }
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }

        // 4. Create Trade
        val trade = com.tradeflow.journal.data.Trade(
            symbol = symbol,
            side = side,
            entryPrice = entryPrice,
            exitPrice = exitPrice,
            quantity = quantity,
            entryTime = timestamp,
            exitTime = timestamp + 3600000L, 
            timestamp = timestamp,
            strategy = "Git Sync",
            notes = "Synced from Server",
            emotion = com.tradeflow.journal.data.Emotion.NEUTRAL,
            marketCondition = parsedCondition,
            setupQuality = parsedQuality,
            grossPnL = grossPnL,
            totalFees = totalFees,
            netPnL = netPnL,
            returnPct = returnPct,
            ohlcvData = ohlcvData,
            microstructure = microstructure,
            marketContext = marketContext,
            riskMetrics = riskMetrics,
            sentimentData = sentimentData,
            fundamentalData = fundamentalData
        )
        
        viewModelScope.launch {
            repository.insertTrade(trade)
        }
        return true
    }
    
    fun saveMakerFee(fee: Double) {
        viewModelScope.launch {
            preferencesRepository.saveMakerFee(fee)
        }
    }

    fun saveTakerFee(fee: Double) {
        viewModelScope.launch {
            preferencesRepository.saveTakerFee(fee)
        }
    }
    

}

data class Candle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

data class TradeStats(
    val totalTrades: Int = 0,
    val winRate: Double = 0.0,
    val totalPnL: Double = 0.0,
    val avgWin: Double = 0.0,
    val avgLoss: Double = 0.0,
    val largestWin: Double = 0.0,
    val largestLoss: Double = 0.0,
    val profitFactor: Double = 0.0,
    val avgReturn: Double = 0.0,
    val maxDrawdown: Double = 0.0,
    val maxWinStreak: Int = 0,
    val maxLossStreak: Int = 0,
    val currentWinStreak: Int = 0,
    val currentLossStreak: Int = 0
)
