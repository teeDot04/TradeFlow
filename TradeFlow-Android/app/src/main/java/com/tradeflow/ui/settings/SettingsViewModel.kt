package com.tradeflow.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.tradeflow.data.AppDatabase
import com.tradeflow.data.Trade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application, CoroutineScope(SupervisorJob()))
    private val tradeDao = database.tradeDao()
    
    val allTrades: LiveData<List<Trade>> = tradeDao.getAllTrades()
    
    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            tradeDao.deleteAllTrades()
        }
    }
}
