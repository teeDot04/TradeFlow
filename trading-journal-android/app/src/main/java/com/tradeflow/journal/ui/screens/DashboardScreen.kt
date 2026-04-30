package com.tradeflow.journal.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradeflow.journal.data.*

import com.tradeflow.journal.ui.components.StatCard
import com.tradeflow.journal.ui.components.TradeListItem
import com.tradeflow.journal.viewmodel.TradeViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: TradeViewModel) {
    val trades by viewModel.allTrades.collectAsState()
    val filteredTrades = remember(trades) { trades.filter { it.setupQuality > 0 } }
    val avoidedTrades = remember(trades) { trades.filter { it.setupQuality == 0 } }
    val stats = remember(filteredTrades) { viewModel.calculateStats(filteredTrades) }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "TradeFlow",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "ML Trading Journal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },

    ) { padding ->
        val thoughts by viewModel.thoughts.collectAsState()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stats Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Total P&L",
                        value = "$${String.format("%.2f", stats.totalPnL)}",
                        icon = Icons.Filled.AccountBalance,
                        isProfit = stats.totalPnL >= 0,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Win Rate",
                        value = "${String.format("%.1f", stats.winRate)}%",
                        subtitle = "${trades.count { it.netPnL > 0 }}W / ${trades.count { it.netPnL < 0 }}L",
                        icon = Icons.Filled.TrendingUp,
                        isProfit = stats.winRate >= 50,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Profit Factor",
                        value = String.format("%.2f", stats.profitFactor),
                        icon = Icons.Filled.BarChart,
                        isProfit = stats.profitFactor >= 1.5,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Avg Return",
                        value = "${String.format("%.2f", stats.avgReturn)}%",
                        icon = Icons.Filled.PieChart,
                        isProfit = stats.avgReturn >= 0,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Performance Breakdown
            item {
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
                        Text(
                            "Performance Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DetailStat("Total Trades", "${stats.totalTrades}")
                            DetailStat("Avg Win", "$${String.format("%.2f", stats.avgWin)}")
                            DetailStat("Avg Loss", "$${String.format("%.2f", stats.avgLoss)}")
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DetailStat("Largest Win", "$${String.format("%.2f", stats.largestWin)}")
                            DetailStat("Largest Loss", "$${String.format("%.2f", stats.largestLoss)}")
                            
                            // Visual indicator only
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.VisibilityOff, 
                                    null, 
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Avoided (${avoidedTrades.size})", 
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            // Agent Analysis
            if (thoughts.isNotEmpty()) {
                item {
                    Text(
                        "Agent Analysis",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            thoughts.take(3).forEach { thought ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        thought.getFormattedTime(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                    Text(
                                        thought.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Recent Trades
            item {
                Text(
                    "Recent Trades",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(filteredTrades.take(1)) { trade ->
                TradeListItem(
                    trade = trade,
                    onClick = { viewModel.selectTrade(trade) }
                )
            }
        }
    }
}

@Composable
fun DetailStat(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
