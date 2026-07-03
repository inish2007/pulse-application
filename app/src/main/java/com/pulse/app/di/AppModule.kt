package com.pulse.app.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.pulse.app.auth.AuthRepository
import com.pulse.app.data.FirebaseManager
import com.pulse.app.data.SignalRepository
import com.pulse.app.util.EncryptionHelper
import com.pulse.app.util.KeyStoreManager
import com.pulse.app.util.SessionManager
import com.pulse.app.util.VibrationManager
import com.pulse.app.auth.DeviceRegistrationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

    @Provides
    @Singleton
    fun provideFunctions(): FirebaseFunctions = FirebaseFunctions.getInstance()

    @Provides
    @Singleton
    fun provideEncryptionHelper(): EncryptionHelper = EncryptionHelper()

    @Provides
    @Singleton
    fun provideKeyStoreManager(@ApplicationContext context: Context): KeyStoreManager =
        KeyStoreManager(context)

    @Provides
    @Singleton
    fun provideVibrationManager(@ApplicationContext context: Context): VibrationManager =
        VibrationManager(context)

    @Provides
    @Singleton
    fun provideSessionManager(@ApplicationContext context: Context): SessionManager =
        SessionManager(context)

    @Provides
    @Singleton
    fun provideFirebaseManager(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore,
        messaging: FirebaseMessaging,
        functions: FirebaseFunctions
    ): FirebaseManager = FirebaseManager(auth, firestore, messaging, functions)

    @Provides
    @Singleton
    fun provideSignalRepository(
        authRepository: AuthRepository,
        apiService: com.pulse.app.data.ApiService,
        firebaseManager: FirebaseManager,
        encryptionHelper: EncryptionHelper,
        keyStoreManager: KeyStoreManager
    ): SignalRepository = SignalRepository(
        authRepository,
        apiService,
        encryptionHelper,
        keyStoreManager,
        firebaseManager
    )

    @Provides
    @Singleton
    fun provideDeviceRegistrationManager(
        messaging: FirebaseMessaging,
        apiService: com.pulse.app.data.ApiService
    ): DeviceRegistrationManager = DeviceRegistrationManager(messaging, apiService)

    @Provides
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
