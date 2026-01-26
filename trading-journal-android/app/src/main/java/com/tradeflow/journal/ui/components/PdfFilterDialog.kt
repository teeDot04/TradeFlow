package com.tradeflow.journal.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfFilterDialog(
    onDismiss: () -> Unit,
    onGenerate: (PdfFilters) -> Unit
) {
    var includeSummary by remember { mutableStateOf(true) }
    var includeCharts by remember { mutableStateOf(true) }
    var includeTradeList by remember { mutableStateOf(true) }
    var includeStrategyBreakdown by remember { mutableStateOf(true) }
    var includeSymbolPerformance by remember { mutableStateOf(true) }
    
    var dateRange by remember { mutableStateOf("all") }
    var minPnL by remember { mutableStateOf("") }
    var maxPnL by remember { mutableStateOf("") }
    
    var showDateRangeMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.85f)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    "PDF Report Generator",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                // Include Sections
                Text(
                    "Include Sections",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CheckboxRow(
                        label = "Performance Summary",
                        checked = includeSummary,
                        onCheckedChange = { includeSummary = it }
                    )
                    CheckboxRow(
                        label = "Charts & Graphs",
                        checked = includeCharts,
                        onCheckedChange = { includeCharts = it }
                    )
                    CheckboxRow(
                        label = "Trade List",
                        checked = includeTradeList,
                        onCheckedChange = { includeTradeList = it }
                    )
                    CheckboxRow(
                        label = "Strategy Breakdown",
                        checked = includeStrategyBreakdown,
                        onCheckedChange = { includeStrategyBreakdown = it }
                    )
                    CheckboxRow(
                        label = "Symbol Performance",
                        checked = includeSymbolPerformance,
                        onCheckedChange = { includeSymbolPerformance = it }
                    )
                }

                Divider()

                // Date Range
                Text(
                    "Date Range",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ExposedDropdownMenuBox(
                    expanded = showDateRangeMenu,
                    onExpandedChange = { showDateRangeMenu = it }
                ) {
                    OutlinedTextField(
                        value = when (dateRange) {
                            "all" -> "All Time"
                            "7d" -> "Last 7 Days"
                            "30d" -> "Last 30 Days"
                            "90d" -> "Last 90 Days"
                            "1y" -> "Last Year"
                            else -> "All Time"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Range") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDateRangeMenu) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = showDateRangeMenu,
                        onDismissRequest = { showDateRangeMenu = false }
                    ) {
                        listOf(
                            "all" to "All Time",
                            "7d" to "Last 7 Days",
                            "30d" to "Last 30 Days",
                            "90d" to "Last 90 Days",
                            "1y" to "Last Year"
                        ).forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    dateRange = value
                                    showDateRangeMenu = false
                                }
                            )
                        }
                    }
                }

                Divider()

                // P&L Range
                Text(
                    "P&L Range (Optional)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = minPnL,
                        onValueChange = { minPnL = it },
                        label = { Text("Min P&L") },
                        placeholder = { Text("e.g., -500") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = maxPnL,
                        onValueChange = { maxPnL = it },
                        label = { Text("Max P&L") },
                        placeholder = { Text("e.g., 1000") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onGenerate(
                                PdfFilters(
                                    includeSummary = includeSummary,
                                    includeCharts = includeCharts,
                                    includeTradeList = includeTradeList,
                                    includeStrategyBreakdown = includeStrategyBreakdown,
                                    includeSymbolPerformance = includeSymbolPerformance,
                                    dateRange = dateRange,
                                    minPnL = minPnL.toDoubleOrNull(),
                                    maxPnL = maxPnL.toDoubleOrNull()
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Generate PDF")
                    }
                }
            }
        }
    }
}

@Composable
fun CheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

data class PdfFilters(
    val includeSummary: Boolean,
    val includeCharts: Boolean,
    val includeTradeList: Boolean,
    val includeStrategyBreakdown: Boolean,
    val includeSymbolPerformance: Boolean,
    val dateRange: String,
    val minPnL: Double?,
    val maxPnL: Double?
)
