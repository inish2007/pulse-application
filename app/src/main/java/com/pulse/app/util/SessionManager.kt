package com.pulse.app.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SessionManager @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("pulse_session", Context.MODE_PRIVATE)

    fun saveCoupleId(id: String) {
        prefs.edit().putString(KEY_COUPLE_ID, id).apply()
    }

    fun coupleId(): String? = prefs.getString(KEY_COUPLE_ID, null)

    companion object {
        private const val KEY_COUPLE_ID = "couple_id"
    }
}
