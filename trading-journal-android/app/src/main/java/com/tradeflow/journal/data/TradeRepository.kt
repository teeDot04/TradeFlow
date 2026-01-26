package com.tradeflow.journal.data

import kotlinx.coroutines.flow.Flow

class TradeRepository(private val tradeDao: TradeDao) {
    val allTrades: Flow<List<Trade>> = tradeDao.getAllTrades()

    suspend fun insertTrade(trade: Trade): Long {
        return tradeDao.insertTrade(trade)
    }

    suspend fun updateTrade(trade: Trade) {
        tradeDao.updateTrade(trade)
    }

    suspend fun deleteTrade(trade: Trade) {
        tradeDao.deleteTrade(trade)
    }



    suspend fun deleteAllTrades() {
        tradeDao.deleteAllTrades()
    }

    suspend fun getTradeById(id: Long): Trade? {
        return tradeDao.getTradeById(id)
    }
}
