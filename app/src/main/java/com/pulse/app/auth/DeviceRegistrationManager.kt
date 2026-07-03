package com.pulse.app.auth

import com.google.firebase.messaging.FirebaseMessaging
import com.pulse.app.data.ApiService
import com.pulse.app.data.RegisterDeviceRequestDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRegistrationManager @Inject constructor(
    private val messaging: FirebaseMessaging,
    private val apiService: ApiService
) {
    suspend fun registerCurrentDevice() = withContext(Dispatchers.IO) {
        try {
            val fcmToken = messaging.token.await()
            if (fcmToken.isBlank()) return@withContext
            apiService.registerDevice(
                RegisterDeviceRequestDto(
                    fcmToken = fcmToken,
                    platform = "android"
                )
            )
        } catch (e: Exception) {
            throw e
        }
    }
}
