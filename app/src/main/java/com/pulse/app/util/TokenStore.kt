package com.pulse.app.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class TokenStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "pulse_tokens",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(access: String?, refresh: String?) {
        prefs.edit().apply {
            access?.let { putString(KEY_ACCESS, it) }
            refresh?.let { putString(KEY_REFRESH, it) }
        }.apply()
    }

    fun access(): String? = prefs.getString(KEY_ACCESS, null)
    fun refresh(): String? = prefs.getString(KEY_REFRESH, null)

    companion object {
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
    }
}
