package com.tradeflow.journal.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradeflow.journal.ui.components.TradeDetailCard
import com.tradeflow.journal.ui.components.TradeListItem
import com.tradeflow.journal.viewmodel.TradeViewModel
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradesScreen(viewModel: TradeViewModel) {
    val trades by viewModel.allTrades.collectAsState()
    val selectedTrade by viewModel.selectedTrade.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (selectedTrade != null) "Trade Details" else "All Trades (${trades.size})",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    if (selectedTrade != null) {
                        IconButton(onClick = { viewModel.selectTrade(null) }) {
                            Icon(Icons.Filled.ArrowBack, "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (selectedTrade != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    TradeDetailCard(
                        trade = selectedTrade!!,
                        onDelete = {
                            viewModel.deleteTrade(selectedTrade!!)
                            viewModel.selectTrade(null) // Go back to list
                        },
                        onRegenerateNotes = { viewModel.regenerateNotes(it) }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Time Filter Components
                var selectedFilter by remember { mutableStateOf("All Time") }
                val filters = listOf("All Time", "Last 7 Days", "Last 30 Days", "This Year")
                
                ScrollableTabRow(
                    selectedTabIndex = filters.indexOf(selectedFilter),
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[filters.indexOf(selectedFilter)]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    divider = {}
                ) {
                    filters.forEach { filter ->
                        Tab(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            text = { 
                                Text(
                                    filter, 
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if(selectedFilter == filter) FontWeight.Bold else FontWeight.Normal
                                ) 
                            }
                        )
                    }
                }
                
                Divider()

                val filteredTrades = remember(trades, selectedFilter) {
                    val now = System.currentTimeMillis()
                    val oneDay = 24 * 60 * 60 * 1000L
                    
                    when (selectedFilter) {
                        "Last 7 Days" -> trades.filter { now - it.entryTime < 7 * oneDay }
                        "Last 30 Days" -> trades.filter { now - it.entryTime < 30 * oneDay }
                        "This Year" -> {
                            val calendar = java.util.Calendar.getInstance()
                            val currentYear = calendar.get(java.util.Calendar.YEAR)
                            trades.filter { 
                                calendar.timeInMillis = it.entryTime
                                calendar.get(java.util.Calendar.YEAR) == currentYear
                            }
                        }
                        else -> trades
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTrades) { trade ->
                        TradeListItem(
                            trade = trade,
                            onClick = { viewModel.selectTrade(trade) }
                        )
                    }
                }
            }
        }

    }
}
