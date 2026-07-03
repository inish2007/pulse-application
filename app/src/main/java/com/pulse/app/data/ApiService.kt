package com.pulse.app.data

import com.squareup.moshi.Json
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body body: FirebaseLoginRequestDto): AuthLoginResponseDto

    @GET("me/code")
    suspend fun getMyPersonalCode(): PersonalCodeResponseDto

    @POST("devices/register")
    suspend fun registerDevice(@Body body: RegisterDeviceRequestDto): SuccessResponseDto

    @POST("devices/unregister")
    suspend fun unregisterDevice(@Body body: UnregisterDeviceRequestDto): SuccessResponseDto

    @POST("couple/connect")
    suspend fun connectCouple(@Body body: ConnectCoupleRequestDto): ConnectCoupleResponseDto

    @POST("couple/disconnect")
    suspend fun disconnectCouple(): SuccessResponseDto

    @POST("signals/send")
    suspend fun sendSignal(@Body body: SendSignalRequestDto): SendSignalResponseDto

    @GET("signals/pending")
    suspend fun getPendingSignals(): GetSignalsResponseDto

    @POST("signals/{id}/ack")
    suspend fun acknowledgeSignal(@Path("id") signalId: String): AcknowledgeSignalResponseDto
}

data class FirebaseLoginRequestDto(
    val firebaseToken: String
)

data class AuthLoginResponseDto(
    val success: Boolean,
    val token: String,
    val expiresIn: String,
    val user: AuthUserDto
)

data class AuthUserDto(
    val id: String,
    @Json(name = "firebaseUid") val firebaseUid: String,
    val name: String,
    val email: String,
    @Json(name = "personalCode") val personalCode: String,
    @Json(name = "coupleId") val coupleId: String?
)

data class PersonalCodeResponseDto(
    val success: Boolean,
    val personalCode: String
)

data class RegisterDeviceRequestDto(
    val fcmToken: String,
    val platform: String
)

data class UnregisterDeviceRequestDto(
    val fcmToken: String
)

data class ConnectCoupleRequestDto(
    val personalCode: String
)

data class PartnerDto(
    val id: String,
    val name: String,
    val personalCode: String
)

data class ConnectCoupleResponseDto(
    val success: Boolean,
    val coupleId: String,
    val partner: PartnerDto
)

data class SuccessResponseDto(
    val success: Boolean
)

data class SendSignalRequestDto(
    @Json(name = "signal_type") val signalType: String
)

data class SendSignalResponseDto(
    val success: Boolean,
    @Json(name = "signal_id") val signalId: String
)

data class SignalDto(
    val id: String,
    @Json(name = "sender_id") val senderId: String,
    @Json(name = "signal_type") val signalType: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "acknowledged_at") val acknowledgedAt: String? = null
)

data class GetSignalsResponseDto(
    val success: Boolean,
    val signals: List<SignalDto>
)

data class AcknowledgeSignalResponseDto(
    val success: Boolean
)
