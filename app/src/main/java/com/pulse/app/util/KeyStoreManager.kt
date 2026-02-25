package com.pulse.app.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

class KeyStoreManager(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "pulse_keys",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val secureRandom = SecureRandom()

    fun getOrCreateKey(coupleId: String): SecretKeySpec {
        val existing = prefs.getString(coupleId, null)
        val keyBytes = if (existing != null) {
            Base64.decode(existing, Base64.DEFAULT)
        } else {
            ByteArray(32).also {
                secureRandom.nextBytes(it)
                prefs.edit().putString(coupleId, Base64.encodeToString(it, Base64.NO_WRAP)).apply()
            }
        }
        return SecretKeySpec(keyBytes, "AES")
    }
}
