package com.pulse.app.data

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

sealed class WsEvent {
    data class PartnerConnected(val partnerId: String, val partnerName: String) : WsEvent()
    object Connected : WsEvent()
    object Disconnected : WsEvent()
}

@Singleton
class WebSocketClient @Inject constructor(
    private val client: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    
    private val _events = MutableSharedFlow<WsEvent>(replay = 1)
    val events = _events.asSharedFlow()

    fun connect(jwtToken: String) {
        if (webSocket != null) return
        
        // Use wss for production
        val url = "wss://pluse-app-backend.onrender.com/ws?token=$jwtToken"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocketClient", "Connected")
                _events.tryEmit(WsEvent.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocketClient", "Message: $text")
                try {
                    val json = JSONObject(text)
                    if (json.optString("type") == "PARTNER_CONNECTED") {
                        val partner = json.getJSONObject("partner")
                        _events.tryEmit(
                            WsEvent.PartnerConnected(
                                partner.getString("id"),
                                partner.getString("name")
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e("WebSocketClient", "Parse error", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocketClient", "Closed")
                _events.tryEmit(WsEvent.Disconnected)
                this@WebSocketClient.webSocket = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocketClient", "Failure", t)
                _events.tryEmit(WsEvent.Disconnected)
                this@WebSocketClient.webSocket = null
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Normal closure")
        webSocket = null
    }
}
