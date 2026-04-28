package com.tradeflow.journal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferencesRepository(private val context: Context) {

    private val MAKER_FEE_KEY = doublePreferencesKey("maker_fee")
    private val TAKER_FEE_KEY = doublePreferencesKey("taker_fee")
    private val GIT_SYNC_URL_KEY = stringPreferencesKey("git_sync_url")
    private val GIT_TOKEN_KEY = stringPreferencesKey("git_token")

    // --- AI Comment Agent ---
    private val AI_PROVIDER_KEY = stringPreferencesKey("ai_provider")
    private val AI_API_KEY_KEY = stringPreferencesKey("ai_api_key")
    private val AI_BASE_URL_KEY = stringPreferencesKey("ai_base_url")
    private val AI_MODEL_KEY = stringPreferencesKey("ai_model")
    private val AI_AUTO_GENERATE_KEY = booleanPreferencesKey("ai_auto_generate")

    val makerFee: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[MAKER_FEE_KEY] ?: 0.1
    }

    val takerFee: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[TAKER_FEE_KEY] ?: 0.1
    }

    // Hardcoded URL (Ignores DataStore to prevent user errors)
    val gitSyncUrl: Flow<String?> = kotlinx.coroutines.flow.flowOf("https://raw.githubusercontent.com/teeDot04/TradeFlow/main/journal_import.csv")

    val gitToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[GIT_TOKEN_KEY]
    }

    // --- AI prefs flows ---
    val aiProvider: Flow<String> = context.dataStore.data.map { p ->
        p[AI_PROVIDER_KEY] ?: "DeepSeek"
    }
    val aiApiKey: Flow<String> = context.dataStore.data.map { p ->
        p[AI_API_KEY_KEY] ?: ""
    }
    val aiBaseUrl: Flow<String> = context.dataStore.data.map { p ->
        p[AI_BASE_URL_KEY] ?: "https://api.deepseek.com/v1"
    }
    val aiModel: Flow<String> = context.dataStore.data.map { p ->
        p[AI_MODEL_KEY] ?: "deepseek-chat"
    }
    val aiAutoGenerate: Flow<Boolean> = context.dataStore.data.map { p ->
        p[AI_AUTO_GENERATE_KEY] ?: false
    }

    suspend fun saveMakerFee(fee: Double) {
        context.dataStore.edit { preferences ->
            preferences[MAKER_FEE_KEY] = fee
        }
    }

    suspend fun saveTakerFee(fee: Double) {
        context.dataStore.edit { preferences ->
            preferences[TAKER_FEE_KEY] = fee
        }
    }

    suspend fun saveGitToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[GIT_TOKEN_KEY] = token
        }
    }

    // --- AI savers ---
    suspend fun saveAiProvider(value: String) {
        context.dataStore.edit { it[AI_PROVIDER_KEY] = value }
    }
    suspend fun saveAiApiKey(value: String) {
        context.dataStore.edit { it[AI_API_KEY_KEY] = value }
    }
    suspend fun saveAiBaseUrl(value: String) {
        context.dataStore.edit { it[AI_BASE_URL_KEY] = value }
    }
    suspend fun saveAiModel(value: String) {
        context.dataStore.edit { it[AI_MODEL_KEY] = value }
    }
    suspend fun saveAiAutoGenerate(value: Boolean) {
        context.dataStore.edit { it[AI_AUTO_GENERATE_KEY] = value }
    }
}
