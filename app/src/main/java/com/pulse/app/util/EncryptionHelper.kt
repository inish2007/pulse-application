package com.pulse.app.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptionHelper {

    private val secureRandom = SecureRandom()

    fun encrypt(plain: String, secretKey: SecretKeySpec): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val iv = ByteArray(16).also { secureRandom.nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val payload = iv + encrypted
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String, secretKey: SecretKeySpec): String {
        val payload = Base64.decode(encoded, Base64.DEFAULT)
        val iv = payload.copyOfRange(0, 16)
        val data = payload.copyOfRange(16, payload.size)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
        val decrypted = cipher.doFinal(data)
        return String(decrypted, Charsets.UTF_8)
    }
}
