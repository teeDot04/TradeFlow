package com.tradeflow.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel

/**
 * Settings now persists encrypted API credentials directly via
 * [com.tradeflow.security.SecureCredentialStore]. The legacy Room/journal
 * dependencies have been dropped from this screen — it is a pure config
 * surface for the autonomous agent.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application)
