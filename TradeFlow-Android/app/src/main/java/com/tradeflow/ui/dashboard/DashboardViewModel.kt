package com.tradeflow.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.tradeflow.agent.ThoughtStream

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    val lines: LiveData<List<String>> = ThoughtStream.lines
    val state: LiveData<String> = ThoughtStream.state
}
