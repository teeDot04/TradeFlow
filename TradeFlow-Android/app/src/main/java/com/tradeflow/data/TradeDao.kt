package com.tradeflow.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface TradeDao {
    @Query("SELECT * FROM trades ORDER BY exitDate DESC")
    fun getAllTrades(): LiveData<List<Trade>>
    
    @Query("SELECT * FROM trades ORDER BY exitDate DESC LIMIT :limit")
    fun getRecentTrades(limit: Int): LiveData<List<Trade>>
    
    @Query("SELECT * FROM trades WHERE id = :tradeId")
    suspend fun getTradeById(tradeId: Long): Trade?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: Trade): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trades: List<Trade>)
    
    @Update
    suspend fun updateTrade(trade: Trade)
    
    @Delete
    suspend fun deleteTrade(trade: Trade)
    
    @Query("DELETE FROM trades")
    suspend fun deleteAllTrades()
    
    @Query("SELECT COUNT(*) FROM trades")
    fun getTradeCount(): LiveData<Int>
    
    // Statistics queries
    @Query("SELECT SUM(CASE WHEN type = 'BUY' THEN (exitPrice - entryPrice) * quantity ELSE (entryPrice - exitPrice) * quantity END) FROM trades")
    fun getTotalPnL(): LiveData<Double?>
    
    @Query("""
        SELECT CAST(SUM(CASE 
            WHEN type = 'BUY' AND exitPrice > entryPrice THEN 1 
            WHEN type = 'SELL' AND entryPrice > exitPrice THEN 1 
            ELSE 0 END) AS REAL) / COUNT(*) * 100 
        FROM trades
    """)
    fun getWinRate(): LiveData<Double?>
}
