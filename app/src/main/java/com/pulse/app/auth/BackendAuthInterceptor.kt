package com.pulse.app.auth

import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendAuthInterceptor @Inject constructor(
    private val tokenStore: SecureTokenStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()
            .addHeader("X-Request-Id", UUID.randomUUID().toString())

        if (request.header("Authorization") == null) {
            tokenStore.access()?.takeIf { it.isNotBlank() }?.let { token ->
                builder.header("Authorization", "Bearer $token")
            }
        }

        return chain.proceed(builder.build())
    }
}
