package com.pulse.app.data

import android.util.Log
import com.pulse.app.auth.SecureTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONException
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed class WsEvent {
    object Connected : WsEvent()
    object Disconnected : WsEvent()
    data class PartnerConnected(val partnerId: String, val partnerName: String) : WsEvent()
    object PartnerDisconnected : WsEvent()
    data class PartnerOnline(val partnerId: String) : WsEvent()
    data class PartnerOffline(val partnerId: String) : WsEvent()
    data class SignalReceived(val signalId: String, val type: String, val senderId: String) : WsEvent()
}

sealed class WsConnectionState {
    object Disconnected : WsConnectionState()
    object Connecting : WsConnectionState()
    object Connected : WsConnectionState()
    object Reconnecting : WsConnectionState()
}

@Singleton
class WebSocketClient @Inject constructor(
    private val client: OkHttpClient,
    private val apiService: ApiService,
    private val tokenStore: SecureTokenStore
) {
    companion object {
        private const val TAG = "WebSocketClient"
        private const val WEB_SOCKET_URL = "wss://pluse-app-backend.onrender.com/ws"
        private val RECONNECT_DELAYS_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 16_000, 30_000)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateLock = Any()
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var recoveryJob: Job? = null
    private var connectionGeneration = 0L
    private var reconnectAttempt = 0
    private var intentionallyClosed = true

    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 10)
    val events = _events.asSharedFlow()

    private val _connectionState = MutableStateFlow<WsConnectionState>(WsConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _partnerPresence = MutableStateFlow<Boolean?>(null)
    val partnerPresence = _partnerPresence.asStateFlow()

    fun connect(jwtToken: String) {
        if (jwtToken.isBlank()) return

        synchronized(stateLock) {
            if (webSocket != null || _connectionState.value != WsConnectionState.Disconnected) return

            intentionallyClosed = false
            reconnectAttempt = 0
            reconnectJob?.cancel()
            reconnectJob = null
            connectionGeneration++
            transitionTo(WsConnectionState.Connecting)
            openSocket(jwtToken, connectionGeneration, isReconnect = false)
        }
    }

    private fun openSocket(jwtToken: String, generation: Long, isReconnect: Boolean) {
        val request = Request.Builder()
            .url(WEB_SOCKET_URL)
            .header("Authorization", "Bearer $jwtToken")
            .build()

        val socket = client.newWebSocket(request, createListener(generation, isReconnect))
        synchronized(stateLock) {
            if (connectionGeneration == generation && !intentionallyClosed && webSocket == null) {
                webSocket = socket
            } else if (webSocket !== socket) {
                socket.cancel()
            }
        }
    }

    private fun createListener(generation: Long, isReconnect: Boolean) =
        object : WebSocketListener() {
            override fun onOpen(socket: WebSocket, response: Response) {
                synchronized(stateLock) {
                    if (connectionGeneration != generation || intentionallyClosed) {
                        socket.close(1000, "Superseded connection")
                        return
                    }
                    webSocket = socket
                    reconnectJob?.cancel()
                    reconnectJob = null
                    reconnectAttempt = 0
                    transitionTo(WsConnectionState.Connected)
                }

                Log.d(TAG, if (isReconnect) "Reconnect success" else "Socket connected")
                _events.tryEmit(WsEvent.Connected)
                recoverPendingSignals(generation)
            }

            override fun onMessage(socket: WebSocket, text: String) {
                if (!isCurrent(socket, generation)) return
                handleMessage(text)
            }

            override fun onClosed(socket: WebSocket, code: Int, reason: String) {
                handleUnexpectedDisconnect(socket, generation, "closed", null, isReconnect)
            }

            override fun onFailure(socket: WebSocket, error: Throwable, response: Response?) {
                handleUnexpectedDisconnect(socket, generation, "failure", error, isReconnect)
            }
        }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "PARTNER_CONNECTED" -> {
                    val partner = json.getJSONObject("partner")
                    _events.tryEmit(
                        WsEvent.PartnerConnected(
                            partner.getString("id"),
                            partner.getString("name")
                        )
                    )
                }
                "PARTNER_DISCONNECTED" -> _events.tryEmit(WsEvent.PartnerDisconnected)
                "PARTNER_ONLINE" -> {
                    val partnerId = json.optString("partnerId")
                    if (partnerId.isNotBlank() && _partnerPresence.value != true) {
                        _partnerPresence.value = true
                        _events.tryEmit(WsEvent.PartnerOnline(partnerId))
                    }
                }
                "PARTNER_OFFLINE" -> {
                    val partnerId = json.optString("partnerId")
                    if (partnerId.isNotBlank() && _partnerPresence.value != false) {
                        _partnerPresence.value = false
                        _events.tryEmit(WsEvent.PartnerOffline(partnerId))
                    }
                }
                "SIGNAL" -> {
                    val signal = json.getJSONObject("signal")
                    _events.tryEmit(
                        WsEvent.SignalReceived(
                            signal.getString("id"),
                            signal.getString("type"),
                            signal.getString("senderId")
                        )
                    )
                }
            }
        } catch (error: JSONException) {
            Log.w(TAG, "Ignored malformed WebSocket message", error)
        }
    }

    private fun handleUnexpectedDisconnect(
        socket: WebSocket,
        generation: Long,
        reason: String,
        error: Throwable?,
        wasReconnect: Boolean
    ) {
        synchronized(stateLock) {
            if (connectionGeneration != generation || (webSocket != null && webSocket !== socket)) return

            socket.cancel()
            webSocket = null
            recoveryJob?.cancel()
            recoveryJob = null
            connectionGeneration++
            _events.tryEmit(WsEvent.Disconnected)
            _partnerPresence.value = false

            val tokenAvailable = !tokenStore.access().isNullOrBlank()
            if (intentionallyClosed || !tokenAvailable) {
                transitionTo(WsConnectionState.Disconnected)
                Log.d(TAG, "Socket disconnected", error)
                return
            }

            transitionTo(WsConnectionState.Reconnecting)
            if (wasReconnect) {
                Log.w(TAG, "Reconnect failure", error)
            } else {
                Log.d(TAG, "Reconnect started", error)
            }
            scheduleReconnectLocked(reason)
        }
    }

    private fun scheduleReconnectLocked(reason: String) {
        if (reconnectJob?.isActive == true) return

        val delayIndex = reconnectAttempt.coerceAtMost(RECONNECT_DELAYS_MS.lastIndex)
        val reconnectDelay = RECONNECT_DELAYS_MS[delayIndex]
        reconnectAttempt++

        reconnectJob = scope.launch {
            delay(reconnectDelay)

            val token = tokenStore.access()
            synchronized(stateLock) {
                reconnectJob = null
                if (intentionallyClosed || token.isNullOrBlank()) {
                    transitionTo(WsConnectionState.Disconnected)
                    return@synchronized
                }

                connectionGeneration++
                Log.d(TAG, "Reconnect attempt")
                openSocket(token, connectionGeneration, isReconnect = true)
            }
        }

        Log.d(TAG, "Reconnect scheduled after ${reconnectDelay}ms ($reason)")
    }

    private fun recoverPendingSignals(generation: Long) {
        recoveryJob?.cancel()
        recoveryJob = scope.launch {
            try {
                val response = apiService.getPendingSignals()
                if (!response.success || connectionGeneration != generation) return@launch

                response.signals.forEach { signal ->
                    if (connectionGeneration == generation) {
                        _events.emit(
                            WsEvent.SignalReceived(
                                signalId = signal.id,
                                type = signal.signalType,
                                senderId = signal.senderId
                            )
                        )
                    }
                }
                Log.d(TAG, "Connection recovery completed; pending=${response.signals.size}")
            } catch (error: IOException) {
                Log.w(TAG, "Connection recovery failed", error)
            } catch (error: HttpException) {
                Log.w(TAG, "Connection recovery failed with HTTP ${error.code()}")
            }
        }
    }

    private fun isCurrent(socket: WebSocket, generation: Long): Boolean =
        synchronized(stateLock) {
            connectionGeneration == generation && webSocket === socket && !intentionallyClosed
        }

    private fun transitionTo(state: WsConnectionState) {
        if (_connectionState.value != state) {
            _connectionState.value = state
        }
    }

    fun disconnect() {
        val socket = synchronized(stateLock) {
            val wasActive = webSocket != null || reconnectJob?.isActive == true ||
                _connectionState.value != WsConnectionState.Disconnected
            intentionallyClosed = true
            connectionGeneration++
            reconnectJob?.cancel()
            reconnectJob = null
            recoveryJob?.cancel()
            recoveryJob = null
            reconnectAttempt = 0
            val current = webSocket
            webSocket = null
            transitionTo(WsConnectionState.Disconnected)
            if (wasActive) {
                _events.tryEmit(WsEvent.Disconnected)
            }
            _partnerPresence.value = false
            current
        }

        if (socket?.close(1000, "Normal closure") == false) {
            socket.cancel()
        }
        Log.d(TAG, "Socket intentionally closed")
    }
}
