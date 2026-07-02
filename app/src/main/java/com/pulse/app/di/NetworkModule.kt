package com.pulse.app.di

import com.pulse.app.BuildConfig
import com.pulse.app.data.ApiService
import com.pulse.app.util.TokenStore
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthInterceptor

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackoffInterceptor

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideCertificatePinner(): CertificatePinner =
        CertificatePinner.Builder()
            .add("pluse-app-backend.onrender.com", "sha256/IX2/a47sFHkF9jewioc5OzEDzS0dNQjNMCX8PCQ26Pg=")
            .add("pluse-app-backend.onrender.com", "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=")
            .build()

    @Provides
    @Singleton
    @AuthInterceptor
    fun provideAuthInterceptor(tokenStore: TokenStore): Interceptor = Interceptor { chain ->
        val reqId = UUID.randomUUID().toString()
        val builder = chain.request().newBuilder()
            .addHeader("X-Request-Id", reqId)
        val token = tokenStore.access()
        if (token != null) {
            builder.addHeader("Authorization", "Bearer $token")
        }
        chain.proceed(builder.build())
    }

    @Provides
    @Singleton
    @BackoffInterceptor
    fun provideBackoffInterceptor(): Interceptor = Interceptor { chain ->
        var attempt = 0
        var waitMs = 300L
        var lastResponse: okhttp3.Response? = null
        while (true) {
            val response = try {
                chain.proceed(chain.request())
            } catch (e: Exception) {
                if (attempt >= 3) {
                    Log.w("BackoffInterceptor", "Giving up after $attempt attempts: ${e.message}")
                    throw e
                } else null
            }
            lastResponse = response
            if (response != null && response.code !in listOf(502, 503)) return@Interceptor response
            response?.close()
            if (attempt >= 3) {
                Log.w("BackoffInterceptor", "Max retries reached for ${chain.request().url}")
                throw java.io.IOException("Max retries reached for ${chain.request().url}")
            }
            Log.d("BackoffInterceptor", "Retry $attempt for ${chain.request().url} after ${waitMs}ms")
            Thread.sleep(waitMs)
            waitMs = (waitMs * 2).coerceAtMost(2400)
            attempt++
        }
        // This line is never reached but satisfies the compiler
        lastResponse!!
    }

    @Provides
    @Singleton
    fun provideOkHttp(
        certificatePinner: CertificatePinner,
        @AuthInterceptor authInterceptor: Interceptor,
        @BackoffInterceptor backoffInterceptor: Interceptor
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .addInterceptor(backoffInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): ApiService =
        retrofit.create(ApiService::class.java)

    @Provides
    @Singleton
    fun provideWebSocketClient(client: OkHttpClient): com.pulse.app.data.WebSocketClient =
        com.pulse.app.data.WebSocketClient(client)
}
