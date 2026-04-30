package com.tradeflow.journal.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "position_journal")
data class PositionJournal(
    @PrimaryKey
    val uuid: String,
    val instId: String,
    val status: String,
    val action: String,
    val created_at: Long = System.currentTimeMillis()
)
