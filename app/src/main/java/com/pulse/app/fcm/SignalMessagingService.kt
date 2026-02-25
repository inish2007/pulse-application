package com.pulse.app.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pulse.app.data.SignalRepository
import com.pulse.app.util.VibrationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import android.provider.Settings
import javax.inject.Inject

@AndroidEntryPoint
class SignalMessagingService : FirebaseMessagingService() {

    @Inject lateinit var repository: SignalRepository
    @Inject lateinit var vibrationManager: VibrationManager

    override fun onMessageReceived(message: RemoteMessage) {
        val signalId = message.data["signalId"] ?: return
        val coupleId = message.data["coupleId"] ?: return
        val encryptedEmotionId = message.data["encryptedEmotionId"] ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val emotionId = repository.decryptEmotion(coupleId, encryptedEmotionId)
                vibrationManager.play(emotionId)
                repository.markDeliveredAndDelete(signalId)
                val deliveredAtIso = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                repository.acknowledgeRemote(signalId, deliveredAtIso, deviceId ?: "unknown")
            } catch (_: Exception) {
                // Ignore failures to keep silent
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            repository.refreshToken()
        }
    }
}
