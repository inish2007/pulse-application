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

    @POST("couple/invite")
    suspend fun createInvite(): InviteResponse

    @POST("couple/join/{token}")
    suspend fun joinInvite(@Path("token") token: String): JoinResponse

    @POST("couple/code/create")
    suspend fun createCoupleCode(): CoupleCodeResponse

    @POST("couple/code/join/{code}")
    suspend fun joinCoupleCode(@Path("code") code: String): JoinResponse

    @GET("couple/code/validate/{code}")
    suspend fun validateCoupleCode(@Path("code") code: String): ValidateCodeResponse

    @POST("signal/send")
    suspend fun sendSignal(@Body body: SendSignalRequest): SendSignalResponse

    @GET("signal/pending/{coupleId}")
    suspend fun getPendingSignals(@Path("coupleId") coupleId: String): GetSignalsResponse

    @POST("signal/{signalId}/acknowledge")
    suspend fun acknowledgeSignal(@Path("signalId") signalId: String): AcknowledgeSignalResponse
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

data class InviteResponse(
    val success: Boolean,
    val link: String,
    val code: String,
    val expires_at: String
)

data class CoupleCodeResponse(
    val success: Boolean,
    val code: String,
    val couple_id: String,
    val expires_at: String
)

data class JoinResponse(
    val success: Boolean,
    val couple_id: String
)

data class ValidateCodeResponse(
    val valid: Boolean,
    val couple_id: String? = null,
    val creator_id: String? = null,
    val is_full: Boolean? = null
)

data class SendSignalRequest(
    val signal_type: String,
    val couple_id: String
)

data class SendSignalResponse(
    val success: Boolean,
    val signal_id: String
)

data class Signal(
    val id: String,
    val sender_id: String,
    val signal_type: String,
    val created_at: String,
    val acknowledged_at: String? = null
)

data class GetSignalsResponse(
    val success: Boolean,
    val signals: List<Signal>
)

data class AcknowledgeSignalResponse(
    val success: Boolean
)
