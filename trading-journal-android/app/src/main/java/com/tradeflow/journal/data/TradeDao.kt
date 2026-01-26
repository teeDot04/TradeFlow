package com.tradeflow.journal.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {
    @Query("SELECT * FROM trades ORDER BY timestamp DESC")
    fun getAllTrades(): Flow<List<Trade>>

    @Query("SELECT * FROM trades WHERE id = :id")
    suspend fun getTradeById(id: Long): Trade?

    @Insert
    suspend fun insertTrade(trade: Trade): Long

    @Update
    suspend fun updateTrade(trade: Trade)

    @Delete
    suspend fun deleteTrade(trade: Trade)



    @Query("DELETE FROM trades")
    suspend fun deleteAllTrades()
}
