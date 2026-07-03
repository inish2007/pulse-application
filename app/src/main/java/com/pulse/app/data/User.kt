package com.pulse.app.data

data class User(
    val id: String,
    val email: String?,
    val personalCode: String? = null,
    val coupleId: String? = null
)
