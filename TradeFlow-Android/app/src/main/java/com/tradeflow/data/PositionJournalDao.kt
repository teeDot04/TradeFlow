package com.tradeflow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PositionJournalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(position: PositionJournal)

    @Query("SELECT * FROM position_journal WHERE status = 'PENDING'")
    suspend fun getPendingPositions(): List<PositionJournal>

    @Query("SELECT COUNT(*) FROM position_journal WHERE status = 'LIQUIDATED' AND created_at > :sinceTimestamp")
    suspend fun getRecentLiquidationsCount(sinceTimestamp: Long): Int
}
