package com.pulse.app.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission
import com.pulse.app.util.Emotions.EMOTION_ANGRY
import com.pulse.app.util.Emotions.EMOTION_GOOD_NIGHT
import com.pulse.app.util.Emotions.EMOTION_HUG
import com.pulse.app.util.Emotions.EMOTION_LOVE
import com.pulse.app.util.Emotions.EMOTION_MISS

object Emotions {
    const val EMOTION_LOVE = "love"
    const val EMOTION_MISS = "miss_you"
    const val EMOTION_HUG = "hug"
    const val EMOTION_ANGRY = "angry"
    const val EMOTION_GOOD_NIGHT = "good_night"
}

class VibrationManager(private val context: Context) {

    private val patterns: Map<String, LongArray> = mapOf(
        EMOTION_LOVE to longArrayOf(0, 120, 70, 200, 70, 120),
        EMOTION_MISS to longArrayOf(0, 80, 60, 80, 120, 150),
        EMOTION_HUG to longArrayOf(0, 300, 100, 300),
        EMOTION_ANGRY to longArrayOf(0, 50, 40, 50, 40, 50, 40, 120),
        EMOTION_GOOD_NIGHT to longArrayOf(0, 150, 100, 150, 200, 80)
    )

    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    @RequiresPermission(android.Manifest.permission.VIBRATE)
    fun play(emotionId: String) {
        val pattern = patterns[emotionId] ?: longArrayOf(0, 100, 50, 100)
        val vib = vibrator()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(pattern, -1)
        }
    }
}
