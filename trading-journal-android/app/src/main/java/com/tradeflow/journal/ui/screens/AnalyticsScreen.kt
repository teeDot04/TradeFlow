package com.tradeflow.journal.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.FloatEntry
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.compose.component.shape.shader.fromBrush
import com.patrykandpatrick.vico.core.chart.values.AxisValuesOverrider
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollSpec
import com.tradeflow.journal.ui.components.AvoidedTradesDialog
import com.tradeflow.journal.viewmodel.TradeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: TradeViewModel) {
    val trades by viewModel.allTrades.collectAsState()
    var showAvoidedDialog by remember { mutableStateOf(false) }
    val avoidedTrades = remember(trades) { trades.filter { it.setupQuality == 0 } }
    
    // Calculate analytics data
    val cumulativePnL = remember(trades) {
        trades.sortedBy { it.timestamp }
            .runningFold(0.0) { acc, trade -> acc + trade.netPnL }
            .drop(1)
    }
    
    val symbolBreakdown = remember(trades) {
        trades.groupBy { it.symbol }
            .mapValues { (_, trades) ->
                SymbolStats(
                    totalPnL = trades.sumOf { it.netPnL },
                    winRate = (trades.count { it.netPnL > 0 }.toDouble() / trades.size) * 100,
                    tradeCount = trades.size
                )
            }
    }
    
    val strategyBreakdown = remember(trades) {
        trades.groupBy { it.strategy }
            .mapValues { (_, trades) ->
                StrategyStats(
                    count = trades.size,
                    totalPnL = trades.sumOf { it.netPnL }
                )
            }
    }

    // Timeframe Selector State (Lifted Up)
    var selectedTimeframe by remember { mutableStateOf(TimeWindow.ALL) }

    // Filtering Logic (Lifted Up)
    val filteredTrades = remember(trades, selectedTimeframe) {
        val now = System.currentTimeMillis()
        val cutoff = when (selectedTimeframe) {
            TimeWindow.DAY -> now - 24 * 3600 * 1000L
            TimeWindow.WEEK -> now - 7 * 24 * 3600 * 1000L
            TimeWindow.MONTH -> now - 30 * 24 * 3600 * 1000L
            TimeWindow.SIX_MONTHS -> now - 180 * 24 * 3600 * 1000L
            TimeWindow.ALL -> 0L
        }
        trades.filter { it.timestamp >= cutoff }.sortedBy { it.timestamp }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {


            // Cumulative P&L Section (High Fidelity / OKX Style)
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Timeframe Selector (State is lifted)

                    val cumulativeData = remember(filteredTrades) {
                        if (filteredTrades.isEmpty()) emptyList() else
                        filteredTrades.runningFold(0.0) { acc, trade -> acc + trade.netPnL }.drop(1)
                    }

                    val currentPnL = cumulativeData.lastOrNull() ?: 0.0
                    val isProfit = currentPnL >= 0
                    
                    // 1. Header: Total PnL (Big & Bold)
                    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(
                            "Est total P&L",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$${String.format("%,.2f", currentPnL)}",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val sign = if (isProfit) "+" else ""
                            val label = when (selectedTimeframe) {
                                TimeWindow.DAY -> "Today"
                                TimeWindow.WEEK -> "This Week"
                                TimeWindow.MONTH -> "This Month"
                                TimeWindow.SIX_MONTHS -> "6 Months"
                                TimeWindow.ALL -> "All Time"
                            }
                            Text(
                                text = "$sign$${String.format("%.2f", currentPnL)} $label",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isProfit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 2. Chart: Gradient Area (Material Theme Colors)
                    if (cumulativeData.isNotEmpty()) {
                        val chartEntryModel = entryModelOf(
                            cumulativeData.mapIndexed { index, value -> 
                                FloatEntry(index.toFloat(), value.toFloat()) 
                            }
                        )
                        
                        // Use Material Theme Colors
                        val chartColor = if (isProfit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        val chartColorArgb = chartColor.toArgb()
                        
                        Chart(
                            chart = lineChart(
                                lines = listOf(
                                    lineSpec(
                                        lineColor = chartColor,
                                        lineBackgroundShader = DynamicShaders.fromBrush(
                                            Brush.verticalGradient(
                                                listOf(
                                                    chartColor.copy(alpha = 0.4f),
                                                    chartColor.copy(alpha = 0.0f)
                                                )
                                            )
                                        )
                                    )
                                )
                            ),
                            model = chartEntryModel,
                            chartScrollSpec = rememberChartScrollSpec(isScrollEnabled = false),
                            isZoomEnabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier.height(280.dp).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    
                    // 3. Timeframe Buttons (Pills at the bottom)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TimeWindow.values().forEach { window ->
                            FilterChip(
                                selected = selectedTimeframe == window,
                                onClick = { selectedTimeframe = window },
                                label = { Text(window.label) },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF333333), // Dark grey pill for selected
                                    selectedLabelColor = Color.White,
                                    containerColor = Color.Transparent,
                                    labelColor = Color.Gray
                                ),
                                border = null
                            )
                        }
                    }
                }
            }
            // Symbol Performance
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Performance by Symbol",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        symbolBreakdown.forEach { (symbol, stats) ->
                            SymbolPerformanceRow(
                                symbol = symbol,
                                stats = stats
                            )
                        }
                    }
                }
            }

            // Strategy Distribution
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Strategy Distribution",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        strategyBreakdown.forEach { (strategy, stats) ->
                            StrategyRow(
                                strategy = strategy,
                                stats = stats
                            )
                        }
                    }
                }
            }
            
            // Risk & Consistency (New Feature)
            item {
                val stats = viewModel.calculateStats(filteredTrades)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Risk & Consistency",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            if (avoidedTrades.isNotEmpty()) {
                                TextButton(
                                    onClick = { showAvoidedDialog = true },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("View Avoided Log", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DetailItem("Max Drawdown", String.format("-%.2f%%", stats.maxDrawdown), MaterialTheme.colorScheme.error)
                            DetailItem("Profit Factor", String.format("%.2f", stats.profitFactor))
                        }
                        Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.1f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                             Column {
                                Text("Streaks", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Text("Win: ", style=MaterialTheme.typography.bodySmall)
                                    Text("${stats.maxWinStreak} (Best)", style=MaterialTheme.typography.bodySmall, fontWeight=FontWeight.Bold, color=MaterialTheme.colorScheme.primary)
                                }
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Text("Loss: ", style=MaterialTheme.typography.bodySmall)
                                    Text("${stats.maxLossStreak} (Worst)", style=MaterialTheme.typography.bodySmall, fontWeight=FontWeight.Bold, color=MaterialTheme.colorScheme.error)
                                }
                             }
                             
                             Column {
                                Text("Current", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                                val currentLabel = if(stats.currentWinStreak > 0) "W${stats.currentWinStreak}" else "L${stats.currentLossStreak}"
                                val currentColor = if(stats.currentWinStreak > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                Text(
                                    currentLabel, 
                                    style = MaterialTheme.typography.titleMedium, 
                                    fontWeight = FontWeight.Bold,
                                    color = currentColor
                                )
                             }
                        }
                    }
                }
            }
        }
    }

    if (showAvoidedDialog) {
        AvoidedTradesDialog(
            trades = avoidedTrades,
            onDismiss = { showAvoidedDialog = false },
            onTradeClick = { trade ->
                showAvoidedDialog = false
                viewModel.selectTrade(trade)
            }
        )
    }
}

@Composable
fun DetailItem(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun SymbolPerformanceRow(symbol: String, stats: SymbolStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    symbol,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${stats.tradeCount} trades • ${String.format("%.1f", stats.winRate)}% win rate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "$${String.format("%.2f", stats.totalPnL)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (stats.totalPnL >= 0) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun StrategyRow(strategy: String, stats: StrategyStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    strategy,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${stats.count} trades",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "$${String.format("%.2f", stats.totalPnL)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (stats.totalPnL >= 0) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.error
            )
        }
    }
}

data class SymbolStats(
    val totalPnL: Double,
    val winRate: Double,
    val tradeCount: Int
)

data class StrategyStats(
    val count: Int,
    val totalPnL: Double
)

enum class TimeWindow(val label: String) {
    DAY("1D"),
    WEEK("1W"),
    MONTH("1M"),
    SIX_MONTHS("6M"),
    ALL("ALL")
}
