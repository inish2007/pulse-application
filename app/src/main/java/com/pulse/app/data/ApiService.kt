package com.pulse.app.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    @POST("signals/{id}/ack")
    suspend fun acknowledge(
        @Path("id") signalId: String,
        @Body body: AckRequest
    )

    @GET("signals/pending")
    suspend fun pendingSignals(): List<PendingSignalDto>
}

data class AckRequest(
    val deliveredAt: String,
    val deviceId: String
)

data class PendingSignalDto(
    val signalId: String,
    val coupleId: String,
    val encEmotion: String,
    val iv: String,
    val alg: String,
    val sentAt: String
)
