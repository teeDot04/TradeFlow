package com.tradeflow.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object EncryptedPrefs {
    private const val PREFS_NAME = "tradeflow_secure_prefs"

    const val KEY_OKX_API_KEY = "okx_api_key"
    const val KEY_OKX_API_SECRET = "okx_api_secret"
    const val KEY_OKX_PASSPHRASE = "okx_passphrase"
    const val KEY_DEEPSEEK_API_KEY = "deepseek_api_key"
    const val KEY_TAVILY_API_KEY = "tavily_api_key"
    const val KEY_SOVEREIGN_CONTROL = "sovereign_control"

    private fun getPrefs(context: Context): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveKey(context: Context, key: String, value: String) {
        getPrefs(context).edit().putString(key, value).apply()
    }

    fun getKey(context: Context, key: String): String? {
        return getPrefs(context).getString(key, null)
    }

    fun clearAll(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
