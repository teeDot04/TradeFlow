package com.tradeflow.journal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferencesRepository(private val context: Context) {
    
    private val MAKER_FEE_KEY = doublePreferencesKey("maker_fee")
    private val TAKER_FEE_KEY = doublePreferencesKey("taker_fee")
    private val GIT_SYNC_URL_KEY = androidx.datastore.preferences.core.stringPreferencesKey("git_sync_url")
    private val GIT_TOKEN_KEY = androidx.datastore.preferences.core.stringPreferencesKey("git_token")
    private val OKX_API_KEY = androidx.datastore.preferences.core.stringPreferencesKey("okx_api_key")
    private val OKX_API_SECRET = androidx.datastore.preferences.core.stringPreferencesKey("okx_api_secret")
    private val OKX_API_PASSPHRASE = androidx.datastore.preferences.core.stringPreferencesKey("okx_api_passphrase")
    private val DEEPSEEK_API_KEY = androidx.datastore.preferences.core.stringPreferencesKey("deepseek_api_key")
    private val SIMULATED_MODE_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("simulated_mode")
    private val CRYPTOPANIC_API_KEY = androidx.datastore.preferences.core.stringPreferencesKey("cryptopanic_api_key")
    
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

    val okxApiKey: Flow<String?> = context.dataStore.data.map { preferences -> preferences[OKX_API_KEY] }
    val okxApiSecret: Flow<String?> = context.dataStore.data.map { preferences -> preferences[OKX_API_SECRET] }
    val okxApiPassphrase: Flow<String?> = context.dataStore.data.map { preferences -> preferences[OKX_API_PASSPHRASE] }
    val deepSeekApiKey: Flow<String?> = context.dataStore.data.map { preferences -> preferences[DEEPSEEK_API_KEY] }
    val simulatedMode: Flow<Boolean> = context.dataStore.data.map { preferences -> preferences[SIMULATED_MODE_KEY] ?: true }
    val cryptoPanicApiKey: Flow<String?> = context.dataStore.data.map { preferences -> preferences[CRYPTOPANIC_API_KEY] }
    
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

    suspend fun saveOkxCredentials(key: String, secret: String, passphrase: String) {
        context.dataStore.edit { preferences ->
            preferences[OKX_API_KEY] = key
            preferences[OKX_API_SECRET] = secret
            preferences[OKX_API_PASSPHRASE] = passphrase
        }
    }

    suspend fun saveDeepSeekKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[DEEPSEEK_API_KEY] = key
        }
    }

    suspend fun saveSimulatedMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SIMULATED_MODE_KEY] = enabled
        }
    }

    suspend fun saveCryptoPanicKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[CRYPTOPANIC_API_KEY] = key
        }
    }
    

}
