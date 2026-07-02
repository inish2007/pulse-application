package com.pulse.app.data

import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class FirebaseManager(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
    private val functions: FirebaseFunctions
)
{
    companion object {
        private const val USERS = "users"
        private const val COUPLES = "couples"
        private const val SIGNALS = "signals"
    }

    fun currentUser(): FirebaseUser? = auth.currentUser

    suspend fun signIn(email: String, password: String): FirebaseUser {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            auth.currentUser ?: throw IllegalStateException("User missing after sign-in")
        } catch (e: Exception) {
            auth.createUserWithEmailAndPassword(email, password).await()
            auth.currentUser ?: throw IllegalStateException("User missing after sign-up")
        }
    }

    suspend fun signInWithCredential(credential: AuthCredential): FirebaseUser {
        auth.signInWithCredential(credential).await()
        return auth.currentUser ?: throw IllegalStateException("User missing after credential sign-in")
    }

    suspend fun ensureUserDoc(
        userId: String,
        name: String?,
        partnerId: String?,
        coupleId: String?
    ) {
        val data = hashMapOf<String, Any?>(
            "name" to name,
            "partnerId" to partnerId,
            "coupleId" to coupleId,
            "createdAt" to com.google.firebase.Timestamp.now()
        ).filterValues { it != null }
        firestore.collection(USERS).document(userId).set(data, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    suspend fun createCouple(coupleId: String, userA: String, userB: String?) {
        val data = hashMapOf(
            "userA" to userA,
            "userB" to (userB ?: ""),
            "createdAt" to com.google.firebase.Timestamp.now()
        )
        firestore.collection(COUPLES).document(coupleId).set(data, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    suspend fun saveSignal(signal: Signal): String {
        val doc = firestore.collection(SIGNALS).document()
        doc.set(signal).await()
        return doc.id
    }

    suspend fun markDelivered(signalId: String) {
        firestore.collection(SIGNALS).document(signalId)
            .update("delivered", true)
            .await()
    }

    suspend fun deleteSignal(signalId: String) {
        firestore.collection(SIGNALS).document(signalId).delete().await()
    }

    suspend fun triggerPush(signalId: String, coupleId: String, encryptedEmotionId: String) {
        // Expects a callable Cloud Function named "dispatchSignal" to fan-out FCM data message
        try {
            functions.getHttpsCallable("dispatchSignal")
                .call(
                    mapOf(
                        "signalId" to signalId,
                        "coupleId" to coupleId,
                        "encryptedEmotionId" to encryptedEmotionId
                    )
                )
                .await()
        } catch (_: Exception) {
            // Ignore if function not yet deployed; Firestore listeners can still fetch signal.
        }
    }

    suspend fun refreshMessagingToken() {
        val userId = currentUser()?.uid ?: return
        val token = messaging.token.await()
        firestore.collection(USERS).document(userId)
            .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    suspend fun fetchPendingSignals(coupleId: String): List<Pair<String, Signal>> {
        val snap = firestore.collection(SIGNALS)
            .whereEqualTo("coupleId", coupleId)
            .whereEqualTo("delivered", false)
            .get()
            .await()
        return snap.documents.mapNotNull { doc ->
            doc.toObject(Signal::class.java)?.let { signal -> doc.id to signal }
        }
    }

    fun signOut() {
        auth.signOut()
    }
}
