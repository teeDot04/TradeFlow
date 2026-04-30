package com.tradeflow.journal.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThoughtManager {
    private val _thoughts = MutableStateFlow<List<Thought>>(emptyList())
    val thoughts: StateFlow<List<Thought>> = _thoughts.asStateFlow()

    fun addThought(message: String, confidence: Int? = null) {
        val newThought = Thought(message = message, confidence = confidence)
        val currentList = _thoughts.value.toMutableList()
        currentList.add(0, newThought) // Newest first
        if (currentList.size > 50) {
            currentList.removeAt(currentList.size - 1)
        }
        _thoughts.value = currentList
    }

    fun clear() {
        _thoughts.value = emptyList()
    }
}
