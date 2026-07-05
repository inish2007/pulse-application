package com.pulse.app.util

import com.pulse.app.data.ApiService
import com.pulse.app.data.FirebaseLoginRequestDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class BackendAuthTest {
    @Test
    fun testLogin() = runBlocking {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://pluse-app-backend.onrender.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        val apiService = retrofit.create(ApiService::class.java)

        try {
            println("Sending request...")
            val response = apiService.login(FirebaseLoginRequestDto("fake-token-123"))
            println("Success response: $response")
        } catch (e: Exception) {
            println("Request failed with exception: \${e::class.java.name}")
            e.printStackTrace()
        }
    }
}
