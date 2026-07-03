package com.pulse.app.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pulse.app.auth.DeviceRegistrationManager
import com.pulse.app.auth.SecureTokenStore
import com.pulse.app.data.SignalRepository
import com.pulse.app.util.VibrationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SignalMessagingService : FirebaseMessagingService() {

    @Inject lateinit var repository: SignalRepository
    @Inject lateinit var vibrationManager: VibrationManager
    @Inject lateinit var deviceRegistrationManager: DeviceRegistrationManager
    @Inject lateinit var tokenStore: SecureTokenStore

    override fun onMessageReceived(message: RemoteMessage) {
        val signalId = message.data["signalId"] ?: return
        // Try to get signal type, fallback to emotionId if that's what backend sends
        val signalType = message.data["signal_type"] ?: message.data["signalType"] ?: message.data["emotionId"]

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (signalType != null) {
                    vibrationManager.play(signalType)
                }
                
                // Task 9: Acknowledge signal
                repository.acknowledgeSignal(signalId)
            } catch (e: Exception) {
                android.util.Log.e("SignalMessagingService", "Error handling FCM message", e)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        // Task 8 (Partial): SignalMessagingService re-registers device on FCM token refresh
        if (tokenStore.access().isNullOrBlank()) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                deviceRegistrationManager.registerCurrentDevice()
                android.util.Log.i("SignalMessagingService", "Device re-registration succeeded")
            } catch (e: Exception) {
                android.util.Log.w("SignalMessagingService", "Device re-registration failed", e)
            }
        }
    }
}
