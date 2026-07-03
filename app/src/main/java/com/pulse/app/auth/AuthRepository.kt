package com.pulse.app.auth

import android.util.Log
import com.google.firebase.auth.AuthCredential
import com.google.firebase.messaging.FirebaseMessaging
import com.pulse.app.data.ApiService
import com.pulse.app.data.AuthLoginResponseDto
import com.pulse.app.data.ConnectCoupleRequestDto
import com.pulse.app.data.FirebaseLoginRequestDto
import com.pulse.app.data.FirebaseManager
import com.pulse.app.data.UnregisterDeviceRequestDto
import com.pulse.app.data.User
import com.pulse.app.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * Custom exceptions for authentication-related errors to allow specific UI feedback.
 */
sealed class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidCredentials(message: String) : AuthException(message)
    class TokenExchangeFailed(message: String, cause: Throwable? = null) : AuthException(message, cause)
    class NetworkError(cause: Throwable) : AuthException("Network connection failed", cause)
    class DeviceRegistrationFailed(message: String, cause: Throwable? = null) : AuthException(message, cause)
    class LogoutFailed(message: String, cause: Throwable? = null) : AuthException(message, cause)
    class Unknown(message: String, cause: Throwable? = null) : AuthException(message, cause)
}

class AuthRepository @Inject constructor(
    private val firebaseManager: FirebaseManager,
    private val tokenStore: SecureTokenStore,
    private val sessionManager: SessionManager,
    private val apiService: ApiService,
    private val deviceRegistrationManager: DeviceRegistrationManager
) {

    suspend fun signIn(email: String, password: String): User = withContext(Dispatchers.IO) {
        try {
            val firebaseUser = firebaseManager.signIn(email, password)
            val tokenResult = firebaseUser.getIdToken(false).await()
            val backendResponse = exchangeFirebaseToken(tokenResult.token)
            storeBackendSession(backendResponse)
            backendResponse.user.toDomainUser()
        } catch (e: Exception) {
            throw mapToAuthException(e)
        }
    }

    suspend fun signInWithGoogle(credential: AuthCredential): User = withContext(Dispatchers.IO) {
        try {
            val firebaseUser = firebaseManager.signInWithCredential(credential)
            val tokenResult = firebaseUser.getIdToken(false).await()
            val backendResponse = exchangeFirebaseToken(tokenResult.token)
            storeBackendSession(backendResponse)
            backendResponse.user.toDomainUser()
        } catch (e: Exception) {
            throw mapToAuthException(e)
        }
    }

    /**
     * Fetches the personal code. Checks local cache first to minimize network usage.
     */
    suspend fun fetchPersonalCode(): String = withContext(Dispatchers.IO) {
        sessionManager.personalCode()?.let { return@withContext it }
        
        try {
            val response = apiService.getMyPersonalCode()
            if (response.success) {
                sessionManager.savePersonalCode(response.personalCode)
                response.personalCode
            } else {
                throw AuthException.Unknown("Failed to fetch personal code")
            }
        } catch (e: Exception) {
            throw mapToAuthException(e)
        }
    }

    suspend fun connectCouple(personalCode: String): String = withContext(Dispatchers.IO) {
        try {
            val response = apiService.connectCouple(ConnectCoupleRequestDto(personalCode))
            if (response.success) {
                sessionManager.saveCoupleId(response.coupleId)
                response.coupleId
            } else {
                throw AuthException.Unknown("Failed to connect couple")
            }
        } catch (e: Exception) {
            throw mapToAuthException(e)
        }
    }

    suspend fun disconnectCouple(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiService.disconnectCouple()
            if (response.success) {
                sessionManager.saveCoupleId(null)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Disconnect failed", e)
            false
        }
    }

    /**
     * Refreshes the backend JWT by obtaining a fresh Firebase ID token.
     * Never stores the Firebase token; always exchanges it for a backend JWT.
     */
    suspend fun refreshAccessToken(): Boolean = withContext(Dispatchers.IO) {
        val user = firebaseManager.currentUser() ?: return@withContext false
        try {
            val refreshed = user.getIdToken(true).await()
            val backendResponse = exchangeFirebaseToken(refreshed.token)
            storeBackendSession(backendResponse)
            true
        } catch (e: AuthException) {
            Log.e("AuthRepository", "Token refresh failed", e)
            false
        }
    }

    fun storedAccessValid(): Boolean = tokenStore.isAccessValid()

    fun storedAccess(): String? = tokenStore.access()

    /**
     * Improved logout sequence: unregister the device FCM token before clearing local state.
     */
    suspend fun logout() = withContext(Dispatchers.IO) {
        try {
            val currentFcmToken = FirebaseMessaging.getInstance().token.await()
            if (!currentFcmToken.isNullOrBlank()) {
                apiService.unregisterDevice(UnregisterDeviceRequestDto(currentFcmToken))
            }
        } catch (e: IOException) {
            Log.e("AuthRepository", "Unregister during logout failed", e)
        } catch (e: AuthException) {
            Log.e("AuthRepository", "Unregister during logout failed", e)
        } finally {
            clear()
        }
    }

    fun clear() {
        tokenStore.clear()
        firebaseManager.signOut()
        try {
            sessionManager.clear()
        } catch (_: Exception) {
        }
    }

    fun currentUser(): User? {
        val fu = firebaseManager.currentUser() ?: return null
        return User(
            id = fu.uid,
            email = fu.email,
            personalCode = sessionManager.personalCode(),
            coupleId = sessionManager.coupleId()
        )
    }

    private suspend fun exchangeFirebaseToken(firebaseToken: String?): AuthLoginResponseDto {
        if (firebaseToken.isNullOrBlank()) throw AuthException.TokenExchangeFailed("Firebase token missing")
        return try {
            apiService.login(FirebaseLoginRequestDto(firebaseToken))
        } catch (e: IOException) {
            throw AuthException.NetworkError(e)
        } catch (e: Exception) {
            throw AuthException.TokenExchangeFailed("Backend login failed", e)
        }
    }

    private suspend fun storeBackendSession(response: AuthLoginResponseDto) {
        // Expiry parsing removed; rely on JWT expiration or 401 handling as per requirements.
        tokenStore.save(response.token, null, null)

        sessionManager.savePersonalCode(response.user.personalCode)
        sessionManager.saveCoupleId(response.user.coupleId)

        try {
            deviceRegistrationManager.registerCurrentDevice()
        } catch (e: IOException) {
            Log.w("AuthRepository", "Post-login device registration failed", e)
            throw AuthException.DeviceRegistrationFailed("Device registration failed", e)
        }
    }

    private fun mapToAuthException(e: Exception): AuthException = when (e) {
        is AuthException -> e
        is IOException -> AuthException.NetworkError(e)
        is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> 
            AuthException.InvalidCredentials("Invalid credentials")
        else -> AuthException.Unknown(e.localizedMessage ?: "Authentication error", e)
    }

    private fun com.pulse.app.data.AuthUserDto.toDomainUser(): User {
        return User(
            id = id, 
            email = email,
            personalCode = personalCode,
            coupleId = coupleId
        )
    }
}
