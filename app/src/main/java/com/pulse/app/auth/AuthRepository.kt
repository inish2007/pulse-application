package com.pulse.app.auth

import com.google.firebase.auth.AuthCredential
import com.pulse.app.data.FirebaseManager
import com.pulse.app.util.SessionManager
import com.pulse.app.data.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val firebaseManager: FirebaseManager,
    private val tokenStore: SecureTokenStore,
    private val sessionManager: SessionManager
) {

    suspend fun signIn(email: String, password: String): User = withContext(Dispatchers.IO) {
        val firebaseUser = firebaseManager.signIn(email, password)
        val tokenResult = firebaseUser.getIdToken(false).await()
        val token = tokenResult.token
        val expiry = tokenResult.expirationTimestamp?.toLong()
        token?.let { tokenStore.save(it, null, expiry) }
        User(id = firebaseUser.uid, email = firebaseUser.email)
    }

    suspend fun signInWithGoogle(credential: AuthCredential): User = withContext(Dispatchers.IO) {
        val firebaseUser = firebaseManager.signInWithCredential(credential)
        val tokenResult = firebaseUser.getIdToken(false).await()
        val token = tokenResult.token
        val expiry = tokenResult.expirationTimestamp?.toLong()
        token?.let { tokenStore.save(it, null, expiry) }
        User(id = firebaseUser.uid, email = firebaseUser.email)
    }

    suspend fun refreshAccessToken(): Boolean = withContext(Dispatchers.IO) {
        val user = firebaseManager.currentUser() ?: return@withContext false
        return@withContext try {
            val refreshed = user.getIdToken(true).await()
            val token = refreshed.token
            val expiry = refreshed.expirationTimestamp?.toLong()
            token?.let { tokenStore.save(it, tokenStore.refresh(), expiry) }
            token != null
        } catch (e: Exception) {
            false
        }
    }

    fun storedAccessValid(): Boolean = tokenStore.isAccessValid()

    fun storedAccess(): String? = tokenStore.access()

    fun clear() {
        tokenStore.clear()
        firebaseManager.signOut()
        try {
            sessionManager.clear()
        } catch (_: Exception) {
        }
    }

    fun currentUser(): com.pulse.app.data.User? {
        val fu = firebaseManager.currentUser() ?: return null
        return com.pulse.app.data.User(fu.uid, fu.email)
    }
}
