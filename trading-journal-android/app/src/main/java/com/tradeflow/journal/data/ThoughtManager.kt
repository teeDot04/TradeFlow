package com.tradeflow.journal.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object ThoughtManager {
    private val _thoughts = MutableLiveData<List<Thought>>(emptyList())
    val thoughts: LiveData<List<Thought>> = _thoughts

    fun addThought(message: String, confidence: Int? = null) {
        val newThought = Thought(message = message, confidence = confidence)
        val currentList = _thoughts.value.orEmpty().toMutableList()
        currentList.add(0, newThought) // Newest first
        if (currentList.size > 50) {
            currentList.removeAt(currentList.size - 1)
        }
        _thoughts.postValue(currentList)
    }

    fun clear() {
        _thoughts.postValue(emptyList())
    }
}
