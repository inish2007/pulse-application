package com.pulse.app.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SessionManager @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("pulse_session", Context.MODE_PRIVATE)

    fun saveCoupleId(id: String?) {
        prefs.edit().putString(KEY_COUPLE_ID, id).apply()
    }

    fun coupleId(): String? = prefs.getString(KEY_COUPLE_ID, null)

    fun savePersonalCode(code: String?) {
        prefs.edit().putString(KEY_PERSONAL_CODE, code).apply()
    }

    fun personalCode(): String? = prefs.getString(KEY_PERSONAL_CODE, null)

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_COUPLE_ID = "couple_id"
        private const val KEY_PERSONAL_CODE = "personal_code"
    }
}
