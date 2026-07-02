package com.pulse.app.data

import com.google.firebase.auth.AuthCredential
import com.pulse.app.util.EncryptionHelper
import com.pulse.app.util.KeyStoreManager
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class SignalRepository @Inject constructor(
    private val firebaseManager: FirebaseManager,
    private val encryptionHelper: EncryptionHelper,
    private val keyStoreManager: KeyStoreManager,
    private val apiService: ApiService
) {

    suspend fun signIn(email: String, password: String) =
        firebaseManager.signIn(email, password)

    suspend fun signInWithGoogle(credential: AuthCredential) =
        firebaseManager.signInWithCredential(credential)

    suspend fun refreshToken() = firebaseManager.refreshMessagingToken()

    suspend fun pairWithPartner(coupleId: String, partnerId: String?) {
        val user = firebaseManager.currentUser() ?: throw IllegalStateException("Not signed in")
        firebaseManager.createCouple(coupleId, user.uid, partnerId)
        firebaseManager.ensureUserDoc(user.uid, user.email, partnerId, coupleId)
    }

    suspend fun sendSignal(emotionId: String, coupleId: String) {
        val senderId = firebaseManager.currentUser()?.uid ?: throw IllegalStateException("Not signed in")
        val key = keyStoreManager.getOrCreateKey(coupleId)
        val encrypted = encryptionHelper.encrypt(emotionId, key)
        val signal = Signal(
            coupleId = coupleId,
            senderId = senderId,
            encryptedEmotionId = encrypted,
            delivered = false
        )
        val signalId = firebaseManager.saveSignal(signal)
        firebaseManager.triggerPush(signalId, coupleId, encrypted)
    }

    suspend fun decryptEmotion(coupleId: String, encryptedEmotionId: String): String {
        val key = keyStoreManager.getOrCreateKey(coupleId)
        return encryptionHelper.decrypt(encryptedEmotionId, key)
    }

    suspend fun markDeliveredAndDelete(signalId: String) {
        firebaseManager.markDelivered(signalId)
        firebaseManager.deleteSignal(signalId)
    }

    suspend fun acknowledgeRemote(signalId: String, deliveredAtIso: String, deviceId: String) {
        try {
            apiService.acknowledge(signalId, AckRequest(deliveredAtIso, deviceId))
        } catch (_: Exception) {
            // Avoid user-facing failure; will rely on server timeout/retry strategy
        }
    }

    suspend fun fetchPendingSignalsForCouple(coupleId: String, currentUserId: String): List<Pair<String, Signal>> {
        return firebaseManager.fetchPendingSignals(coupleId)
            .filter { (_, signal) -> signal.senderId != currentUserId }
    }

    suspend fun currentUserId(): String? = firebaseManager.currentUser()?.uid

    suspend fun createInvite(): com.pulse.app.data.InviteResponse = apiService.createInvite()

    suspend fun joinInvite(token: String): com.pulse.app.data.JoinResponse = apiService.joinInvite(token)

    // ===== COUPLE CODE METHODS =====
    suspend fun createCoupleCode(): com.pulse.app.data.CoupleCodeResponse = 
        apiService.createCoupleCode()

    suspend fun joinCoupleCode(code: String): com.pulse.app.data.JoinResponse = 
        apiService.joinCoupleCode(code)

    suspend fun validateCoupleCode(code: String): com.pulse.app.data.ValidateCodeResponse = 
        apiService.validateCoupleCode(code)

    // ===== SIGNAL/VIBRATION METHODS =====
    suspend fun sendVibrationSignal(coupleId: String, signalType: String = "vibrate"): SendSignalResponse {
        return apiService.sendSignal(SendSignalRequest(signalType, coupleId))
    }

    suspend fun getPendingSignals(coupleId: String): GetSignalsResponse = 
        apiService.getPendingSignals(coupleId)

    suspend fun acknowledgeSignalReceived(signalId: String): AcknowledgeSignalResponse = 
        apiService.acknowledgeSignal(signalId)
}
