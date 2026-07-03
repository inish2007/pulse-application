package com.pulse.app.data

import com.pulse.app.auth.AuthRepository
import com.pulse.app.util.EncryptionHelper
import com.pulse.app.util.KeyStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SignalRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val apiService: ApiService,
    private val encryptionHelper: EncryptionHelper,
    private val keyStoreManager: KeyStoreManager,
    private val firebaseManager: FirebaseManager
) {

    suspend fun refreshToken() = firebaseManager.refreshMessagingToken()

    suspend fun sendSignal(emotionId: String): SendSignalResponseDto = withContext(Dispatchers.IO) {
        apiService.sendSignal(SendSignalRequestDto(signalType = emotionId))
    }

    suspend fun getPendingSignals(): List<SignalDto> = withContext(Dispatchers.IO) {
        val response = apiService.getPendingSignals()
        if (response.success) response.signals else emptyList()
    }

    suspend fun acknowledgeSignal(signalId: String) = withContext(Dispatchers.IO) {
        apiService.acknowledgeSignal(signalId)
    }

    suspend fun fetchPendingSignalsForCouple(
        coupleId: String,
        currentUserId: String
    ): List<Pair<String, Signal>> = withContext(Dispatchers.IO) {
        val response = apiService.getPendingSignals()
        if (!response.success) return@withContext emptyList()

        response.signals
            .filter { it.senderId != currentUserId }
            .map {
                it.id to Signal(
                    coupleId = coupleId,
                    senderId = it.senderId,
                    encryptedEmotionId = it.signalType,
                    createdAt = System.currentTimeMillis(),
                    delivered = it.acknowledgedAt != null
                )
            }
    }

    suspend fun markDeliveredAndDelete(signalId: String) = withContext(Dispatchers.IO) {
        apiService.acknowledgeSignal(signalId)
    }

    suspend fun acknowledgeRemote(
        signalId: String,
        deliveredAtIso: String,
        deviceId: String
    ) = withContext(Dispatchers.IO) {
        apiService.acknowledgeSignal(signalId)
    }

    fun currentUserId(): String? = authRepository.currentUser()?.id

    // Legacy/Encryption related - keep if still needed for local decryption of FCM payloads
    suspend fun decryptEmotion(coupleId: String, encryptedEmotionId: String): String {
        val key = keyStoreManager.getOrCreateKey(coupleId)
        return encryptionHelper.decrypt(encryptedEmotionId, key)
    }
}
