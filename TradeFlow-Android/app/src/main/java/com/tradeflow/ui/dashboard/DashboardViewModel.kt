package com.tradeflow.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.map
import com.tradeflow.data.AppDatabase
import com.tradeflow.data.Trade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application, CoroutineScope(SupervisorJob()))
    private val tradeDao = database.tradeDao()
    
    val recentTrades: LiveData<List<Trade>> = tradeDao.getRecentTrades(10)
    val allTrades: LiveData<List<Trade>> = tradeDao.getAllTrades()
    
    // Statistics
    val totalPnl: LiveData<Double> = allTrades.map { trades ->
        trades.sumOf { it.pnl }
    }
    
    val winRate: LiveData<Double> = allTrades.map { trades ->
        if (trades.isEmpty()) 0.0
        else (trades.count { it.isWin }.toDouble() / trades.size) * 100
    }
    
    val profitFactor: LiveData<Double> = allTrades.map { trades ->
        val wins = trades.filter { it.isWin }.sumOf { it.pnl }
        val losses = trades.filter { !it.isWin }.sumOf { kotlin.math.abs(it.pnl) }
        if (losses == 0.0) wins else wins / losses
    }
    
    val avgReturn: LiveData<Double> = allTrades.map { trades ->
        if (trades.isEmpty()) 0.0
        else trades.map { it.pnlPercentage }.average()
    }
}
