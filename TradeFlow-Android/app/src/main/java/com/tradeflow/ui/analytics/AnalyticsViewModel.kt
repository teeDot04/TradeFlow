package com.tradeflow.ui.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.tradeflow.data.AppDatabase
import com.tradeflow.data.Trade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application, CoroutineScope(SupervisorJob()))
    private val tradeDao = database.tradeDao()
    
    val allTrades: LiveData<List<Trade>> = tradeDao.getAllTrades()
}
