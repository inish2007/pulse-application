package com.pulse.app.data

data class Signal(
    val coupleId: String = "",
    val senderId: String = "",
    val encryptedEmotionId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val delivered: Boolean = false
)
