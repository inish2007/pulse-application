package com.pulse.app.work

import android.content.Context
import android.provider.Settings
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pulse.app.data.SignalRepository
import com.pulse.app.util.SessionManager
import com.pulse.app.util.VibrationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter

@HiltWorker
class PendingSignalsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val signalRepository: SignalRepository,
    private val sessionManager: SessionManager,
    private val vibrationManager: VibrationManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val coupleId = sessionManager.coupleId() ?: return@withContext Result.success()
        val currentUserId = signalRepository.currentUserId()
            ?: return@withContext Result.retry()

        val pending = signalRepository.fetchPendingSignalsForCouple(coupleId, currentUserId)
        if (pending.isEmpty()) return@withContext Result.success()

        val deviceId = Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
        pending.forEach { (signalId, signal) ->
            try {
                val emotion = signalRepository.decryptEmotion(coupleId, signal.encryptedEmotionId)
                vibrationManager.play(emotion)
                signalRepository.markDeliveredAndDelete(signalId)
                val deliveredAtIso = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                signalRepository.acknowledgeRemote(signalId, deliveredAtIso, deviceId ?: "unknown")
            } catch (_: Exception) {
                // Skip failed items; they can be retried in next run
            }
        }
        Result.success()
    }
}
