package com.pulse.app.data

import android.util.Log
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

    companion object {
        private const val TAG = "SignalRepository"
    }

    suspend fun signIn(email: String, password: String) =
        firebaseManager.signIn(email, password)

    suspend fun signInWithGoogle(credential: AuthCredential) =
        firebaseManager.signInWithCredential(credential)

    suspend fun refreshToken() = firebaseManager.refreshMessagingToken()

    suspend fun pairWithPartner(coupleId: String, partnerId: String?) {
        val user = firebaseManager.currentUser() ?: throw IllegalStateException("Not signed in")
        Log.d(TAG, "pairWithPartner: coupleId=$coupleId partnerId=$partnerId uid=${user.uid}")
        firebaseManager.createCouple(coupleId, user.uid, partnerId)
        firebaseManager.ensureUserDoc(user.uid, user.email, partnerId, coupleId)
    }

    suspend fun sendSignal(emotionId: String, coupleId: String) {
        val senderId = firebaseManager.currentUser()?.uid ?: throw IllegalStateException("Not signed in")
        Log.d(TAG, "sendSignal: emotionId=$emotionId coupleId=$coupleId sender=$senderId")
        val key = keyStoreManager.getOrCreateKey(coupleId)
        val encrypted = encryptionHelper.encrypt(emotionId, key)

        // TODO: fix constructor — compiler expects params named `id` and `sender_id`
        // (and possibly other snake_case names). Paste Signal.kt so I can match it exactly.
        val signal = Signal(
            coupleId = coupleId,
            senderId = senderId,
            encryptedEmotionId = encrypted,
            delivered = false
        )
        val signalId = firebaseManager.saveSignal(signal)
        Log.d(TAG, "sendSignal: saved signalId=$signalId, triggering push")
        firebaseManager.triggerPush(signalId, coupleId, encrypted)
    }

    suspend fun decryptEmotion(coupleId: String, encryptedEmotionId: String): String {
        Log.d(TAG, "decryptEmotion: coupleId=$coupleId")
        val key = keyStoreManager.getOrCreateKey(coupleId)
        return try {
            encryptionHelper.decrypt(encryptedEmotionId, key)
        } catch (e: Exception) {
            Log.e(TAG, "decryptEmotion: failed for coupleId=$coupleId", e)
            throw e
        }
    }

    suspend fun markDeliveredAndDelete(signalId: String) {
        Log.d(TAG, "markDeliveredAndDelete: signalId=$signalId")
        firebaseManager.markDelivered(signalId)
        firebaseManager.deleteSignal(signalId)
    }

    suspend fun acknowledgeRemote(signalId: String, deliveredAtIso: String, deviceId: String) {
        try {
            apiService.acknowledge(signalId, AckRequest(deliveredAtIso, deviceId))
            Log.d(TAG, "acknowledgeRemote: acked signalId=$signalId")
        } catch (e: Exception) {
            Log.w(TAG, "acknowledgeRemote: failed for signalId=$signalId, relying on server retry", e)
        }
    }

    suspend fun fetchPendingSignalsForCouple(coupleId: String, currentUserId: String): List<Pair<String, Signal>> {
        val result = firebaseManager.fetchPendingSignals(coupleId)
            .filter { (_, signal) -> signal.senderId != currentUserId }
        Log.d(TAG, "fetchPendingSignalsForCouple: coupleId=$coupleId found=${result.size}")
        return result
    }

    suspend fun currentUserId(): String? = firebaseManager.currentUser()?.uid

    suspend fun createInvite(): InviteResponse {
        Log.d(TAG, "createInvite: requesting new invite")
        return apiService.createInvite()
    }

    suspend fun joinInvite(token: String): JoinResponse {
        Log.d(TAG, "joinInvite: token=${token.take(4)}***")
        return apiService.joinInvite(token)
    }

    // ===== COUPLE CODE METHODS =====
    suspend fun createCoupleCode(): CoupleCodeResponse {
        Log.d(TAG, "createCoupleCode: requesting new code")
        return apiService.createCoupleCode()
    }

    suspend fun joinCoupleCode(code: String): JoinResponse {
        Log.d(TAG, "joinCoupleCode: code=${code.take(2)}***")
        return apiService.joinCoupleCode(code)
    }

    suspend fun validateCoupleCode(code: String): ValidateCodeResponse {
        Log.d(TAG, "validateCoupleCode: code=${code.take(2)}***")
        return apiService.validateCoupleCode(code)
    }

    // ===== SIGNAL/VIBRATION METHODS =====
    suspend fun sendVibrationSignal(coupleId: String, signalType: String = "vibrate"): SendSignalResponse {
        Log.d(TAG, "sendVibrationSignal: coupleId=$coupleId type=$signalType")
        return apiService.sendSignal(SendSignalRequest(signalType, coupleId))
    }

    suspend fun getPendingSignals(coupleId: String): GetSignalsResponse {
        Log.d(TAG, "getPendingSignals: coupleId=$coupleId")
        return apiService.getPendingSignals(coupleId)
    }

    suspend fun acknowledgeSignalReceived(signalId: String): AcknowledgeSignalResponse {
        Log.d(TAG, "acknowledgeSignalReceived: signalId=$signalId")
        return apiService.acknowledgeSignal(signalId)
    }
}