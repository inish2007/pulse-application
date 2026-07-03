package com.pulse.app.data

import com.pulse.app.auth.AuthException
import com.pulse.app.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val apiService: ApiService,
    private val sessionManager: SessionManager
) {
    suspend fun getPersonalCode(): String = withContext(Dispatchers.IO) {
        sessionManager.personalCode()?.let { return@withContext it }

        try {
            val response = apiService.getMyPersonalCode()
            if (!response.success) {
                throw AuthException.Unknown("Failed to fetch personal code")
            }

            sessionManager.savePersonalCode(response.personalCode)
            response.personalCode
        } catch (e: IOException) {
            throw AuthException.NetworkError(e)
        } catch (e: Exception) {
            if (e is AuthException) throw e
            throw AuthException.Unknown(e.localizedMessage ?: "Failed to fetch personal code", e)
        }
    }
}
