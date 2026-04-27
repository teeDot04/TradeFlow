package com.tradeflow.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Thin wrapper around EncryptedSharedPreferences for the two API credentials
 * the agent needs at runtime. Backing store is AES-256-GCM with the key
 * material rooted in the AndroidKeyStore, so the secrets are not extractable
 * from a rooted backup of /data/data/com.tradeflow/.
 */
class SecureCredentialStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveOkxKey(value: String) = prefs.edit().putString(KEY_OKX, value.trim()).apply()
    fun saveOkxSecret(value: String) = prefs.edit().putString(KEY_OKX_SECRET, value.trim()).apply()
    fun saveOkxPassphrase(value: String) = prefs.edit().putString(KEY_OKX_PASS, value.trim()).apply()
    fun saveDeepseekKey(value: String) = prefs.edit().putString(KEY_DEEPSEEK, value.trim()).apply()

    fun okxKey(): String = prefs.getString(KEY_OKX, "").orEmpty()
    fun okxSecret(): String = prefs.getString(KEY_OKX_SECRET, "").orEmpty()
    fun okxPassphrase(): String = prefs.getString(KEY_OKX_PASS, "").orEmpty()
    fun deepseekKey(): String = prefs.getString(KEY_DEEPSEEK, "").orEmpty()

    fun hasAllKeys(): Boolean =
        okxKey().isNotEmpty() &&
        okxSecret().isNotEmpty() &&
        okxPassphrase().isNotEmpty() &&
        deepseekKey().isNotEmpty()

    fun clearAll() {
        prefs.edit()
            .remove(KEY_OKX)
            .remove(KEY_OKX_SECRET)
            .remove(KEY_OKX_PASS)
            .remove(KEY_DEEPSEEK)
            .apply()
    }

    companion object {
        private const val FILE_NAME = "tradeflow_secure_prefs"
        const val KEY_OKX = "OKX_API_KEY"
        const val KEY_OKX_SECRET = "OKX_API_SECRET"
        const val KEY_OKX_PASS = "OKX_API_PASSPHRASE"
        const val KEY_DEEPSEEK = "DEEPSEEK_API_KEY"
    }
}
