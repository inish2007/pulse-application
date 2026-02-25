package com.pulse.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class EncryptionHelperTest {

    private val helper = EncryptionHelper()

    @Test
    fun `encrypt and decrypt round trip`() {
        val key = SecretKeySpec(ByteArray(32) { 1 }, "AES")
        val plain = "love"

        val encrypted = helper.encrypt(plain, key)
        val decrypted = helper.decrypt(encrypted, key)

        assertEquals(plain, decrypted)
    }
}
