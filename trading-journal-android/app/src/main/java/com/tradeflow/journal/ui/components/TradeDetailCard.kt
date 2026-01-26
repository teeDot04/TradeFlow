package com.tradeflow.journal.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradeflow.journal.data.Trade
import com.tradeflow.journal.data.TradeSide
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TradeDetailCard(
    trade: Trade,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete This Trade?") },
            text = { Text("Are you sure you want to delete this trade? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    trade.symbol,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Divider()

            // Price Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DetailItem("Entry Price", "$${String.format("%.2f", trade.entryPrice)}")
                DetailItem("Exit Price", "$${String.format("%.2f", trade.exitPrice)}")
                DetailItem("Quantity", String.format("%.4f", trade.quantity))
            }

            // P&L Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DetailItem(
                    "Net P&L",
                    "$${String.format("%.2f", trade.netPnL)}",
                    color = if (trade.netPnL >= 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
                DetailItem(
                    "Return %",
                    "${if (trade.returnPct >= 0) "+" else ""}${String.format("%.2f", trade.returnPct)}%",
                    color = if (trade.returnPct >= 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
                DetailItem("Fees", "$${String.format("%.2f", trade.totalFees)}")
            }

            // OHLCV Data
            trade.ohlcvData?.let { ohlcv ->
                Divider()
                
                Text(
                    "Market Data (OHLCV)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DetailItem("Avg Price", "$${String.format("%.2f", ohlcv.avgPrice)}")
                            DetailItem("Volatility", "${String.format("%.2f", ohlcv.volatility)}%")
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DetailItem("Max High", "$${String.format("%.2f", ohlcv.maxHigh)}")
                            DetailItem("Min Low", "$${String.format("%.2f", ohlcv.minLow)}")
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DetailItem("Price Range", "$${String.format("%.2f", ohlcv.priceRange)}")
                            DetailItem("Avg Volume", String.format("%.0f", ohlcv.avgVolume))
                        }
                    }
                }
            }

            Divider()

            // Trade Metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DetailItem("Strategy", trade.strategy)
                DetailItem("Setup Quality", "${trade.setupQuality}/10")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DetailItem("Market Condition", trade.marketCondition.name.replace("_", " "))
                DetailItem(
                    "Entry Time",
                    SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                        .format(Date(trade.entryTime))
                )
                DetailItem(
                    "Exit Time",
                    SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                        .format(Date(trade.exitTime))
                )
            }
            
            // --- Advanced AI Data (Claude Model) ---
            if (trade.microstructure != null || trade.marketContext != null || trade.riskMetrics != null) {
                Divider()
                Text(
                    "Market Context",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Card(
                    colors = CardDefaults.cardColors(
                         containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        trade.microstructure?.let { 
                            DetailItem("Microstructure", it.replace("|", ", ").replace("\"", "").replace("{", "").replace("}", "")) 
                        }
                        trade.marketContext?.let { 
                            DetailItem("Market Context", it.replace("|", ", ").replace("\"", "").replace("{", "").replace("}", "")) 
                        }
                        trade.riskMetrics?.let { 
                            DetailItem("Risk Metrics", it.replace("|", ", ").replace("\"", "").replace("{", "").replace("}", "")) 
                        }
                        trade.sentimentData?.let { 
                            DetailItem("Sentiment", it.replace("|", ", ").replace("\"", "").replace("{", "").replace("}", "")) 
                        }
                        trade.fundamentalData?.let { 
                            DetailItem("Fundamental", it.replace("|", ", ").replace("\"", "").replace("{", "").replace("}", "")) 
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Delete Button
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Filled.Delete, "Delete Trade", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Trade")
            }
        }
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
