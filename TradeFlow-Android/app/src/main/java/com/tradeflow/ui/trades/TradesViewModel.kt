package com.tradeflow.ui.trades

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.tradeflow.data.AppDatabase
import com.tradeflow.data.Trade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TradesViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application, CoroutineScope(SupervisorJob()))
    private val tradeDao = database.tradeDao()
    
    private val _searchQuery = MutableLiveData("")
    
    val allTrades: LiveData<List<Trade>> = tradeDao.getAllTrades()
    
    val filteredTrades: LiveData<List<Trade>> = _searchQuery.switchMap { query ->
        if (query.isNullOrBlank()) {
            allTrades
        } else {
            allTrades.switchMap { trades ->
                val filtered = trades.filter {
                    it.symbol.contains(query, ignoreCase = true) ||
                    it.strategy.contains(query, ignoreCase = true) ||
                    it.notes.contains(query, ignoreCase = true)
                }
                MutableLiveData(filtered)
            }
        }
    }
    
    fun search(query: String) {
        _searchQuery.value = query
    }
}
