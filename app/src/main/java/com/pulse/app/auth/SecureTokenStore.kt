package com.pulse.app.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.System
import javax.inject.Inject

class SecureTokenStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "pulse_secure_tokens",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_EXPIRY = "access_expiry"
    }

    fun save(access: String, refresh: String?, expiryEpochSeconds: Long?) {
        prefs.edit().apply {
            putString(KEY_ACCESS, access)
            putString(KEY_REFRESH, refresh)
            expiryEpochSeconds?.let { putLong(KEY_EXPIRY, it) }
        }.apply()
    }

    fun access(): String? = prefs.getString(KEY_ACCESS, null)
    fun refresh(): String? = prefs.getString(KEY_REFRESH, null)
    fun expiryEpoch(): Long = prefs.getLong(KEY_EXPIRY, 0L)

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun isAccessValid(): Boolean {
        val access = access() ?: return false
        val expiry = expiryEpoch()
        if (expiry == 0L) return true // unknown expiry: assume valid
        val now = System.currentTimeMillis() / 1000L
        return now < expiry - 10 // 10s safety
    }
}
