package com.pulse.app.ui

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthCredential
import com.pulse.app.auth.AuthRepository
import com.pulse.app.auth.SecureTokenStore
import com.pulse.app.data.SignalRepository
import com.pulse.app.data.WebSocketClient
import com.pulse.app.data.WsConnectionState
import com.pulse.app.data.WsEvent
import com.pulse.app.util.Event
import com.pulse.app.util.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val signalRepository: SignalRepository,
    private val sessionManager: SessionManager,
    private val ioDispatcher: CoroutineDispatcher,
    private val webSocketClient: WebSocketClient,
    private val tokenStore: SecureTokenStore
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    enum class Destination { PAIR, SIGNAL, LOGIN, SIGNUP }

    private val _status = MutableLiveData<String>("Waiting for partnerâ€¦")
    val status: LiveData<String> = _status

    private val _busy = MutableLiveData<Boolean>(false)
    val busy: LiveData<Boolean> = _busy

    private val _toast = MutableLiveData<Event<String>>()
    val toast: LiveData<Event<String>> = _toast

    private val _paired = MutableLiveData<Boolean>(false)
    val paired: LiveData<Boolean> = _paired

    private val _partnerOnline = MutableLiveData<Boolean>(false)
    val partnerOnline: LiveData<Boolean> = _partnerOnline

    private val _coupleId = MutableLiveData<String?>()
    val coupleId: LiveData<String?> = _coupleId

    private val _personalCode = MutableLiveData<String?>()
    val personalCode: LiveData<String?> = _personalCode

    private val _navEvents = MutableLiveData<Event<Destination>>()
    val navEvents: LiveData<Event<Destination>> = _navEvents

    init {
        viewModelScope.launch(ioDispatcher) {
            webSocketClient.events.collectLatest { event ->
                Log.d(TAG, "WS event received: $event")
                when (event) {
                    is WsEvent.Connected -> {
                        Log.d(TAG, "WebSocket connected")
                    }
                    is WsEvent.Disconnected -> {
                        Log.d(TAG, "WebSocket disconnected")
                        _partnerOnline.postValue(false)
                    }
                    is WsEvent.PartnerConnected -> {
                        _paired.postValue(true)
                        _status.postValue("Connected â¤ï¸")
                        _navEvents.postValue(Event(Destination.SIGNAL))
                    }
                    is WsEvent.SignalReceived -> {
                        _toast.postValue(Event("New signal received!"))
                    }
                    is WsEvent.PartnerDisconnected -> {
                        _paired.postValue(false)
                        _status.postValue("Partner disconnected")
                        _partnerOnline.postValue(false)
                    }
                    is WsEvent.PartnerOnline -> {
                        _partnerOnline.postValue(true)
                        _status.postValue("Partner online")
                    }
                    is WsEvent.PartnerOffline -> {
                        _partnerOnline.postValue(false)
                        _status.postValue("Partner offline")
                    }
                }
            }
        }

        viewModelScope.launch(ioDispatcher) {
            webSocketClient.partnerPresence.collect { isOnline ->
                if (isOnline == null) return@collect
                _partnerOnline.postValue(isOnline)
                _status.postValue(if (isOnline) "Partner online" else "Partner offline")
            }
        }

        viewModelScope.launch(ioDispatcher) {
            webSocketClient.connectionState.collect { state ->
                when (state) {
                    WsConnectionState.Disconnected -> {
                        Log.d(TAG, "WebSocket state: disconnected")
                        _partnerOnline.postValue(false)
                    }
                    WsConnectionState.Connecting -> Log.d(TAG, "WebSocket state: connecting")
                    WsConnectionState.Connected -> Log.d(TAG, "WebSocket state: connected")
                    WsConnectionState.Reconnecting -> Log.d(TAG, "WebSocket state: reconnecting")
                }
            }
        }
    }

    fun loadInitialData() {
        _coupleId.value = sessionManager.coupleId()
        _personalCode.value = sessionManager.personalCode()
        _paired.value = _coupleId.value != null

        if (_personalCode.value == null && authRepository.storedAccessValid()) {
            fetchPersonalCode()
        }
    }

    fun loadSavedCoupleId() {
        _coupleId.value = sessionManager.coupleId()
        _paired.value = _coupleId.value != null
    }

    fun showToast(message: String) {
        _toast.postValue(Event(message))
    }

    fun fetchPersonalCode() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val code = authRepository.fetchPersonalCode()
                _personalCode.postValue(code)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch personal code", e)
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                _busy.postValue(true)
                authRepository.signIn(email, password)
                onAuthSuccess("Signed in")
            } catch (e: Exception) {
                Log.e(TAG, "signIn failed", e)
                _toast.postValue(Event("Auth failed: ${e.localizedMessage}"))
            } finally {
                _busy.postValue(false)
            }
        }
    }

    fun signInWithGoogle(credential: AuthCredential) {
        viewModelScope.launch(ioDispatcher) {
            try {
                _busy.postValue(true)
                authRepository.signInWithGoogle(credential)
                onAuthSuccess("Signed in with Google")
            } catch (e: Exception) {
                Log.e(TAG, "signInWithGoogle failed", e)
                _toast.postValue(Event("Google Auth failed: ${e.localizedMessage}"))
            } finally {
                _busy.postValue(false)
            }
        }
    }

    private suspend fun onAuthSuccess(statusMessage: String) {
        Log.d(TAG, "Auth success")
        _status.postValue(statusMessage)
        _personalCode.postValue(sessionManager.personalCode())
        _coupleId.postValue(sessionManager.coupleId())

        connectWebSocket()

        if (sessionManager.coupleId() != null) {
            _paired.postValue(true)
            _navEvents.postValue(Event(Destination.SIGNAL))
        } else {
            _navEvents.postValue(Event(Destination.PAIR))
        }
    }

    fun connectCouple(code: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                _busy.postValue(true)
                val newCoupleId = authRepository.connectCouple(code)
                _coupleId.postValue(newCoupleId)
                _paired.postValue(true)
                _status.postValue("Connected â¤ï¸")
                _navEvents.postValue(Event(Destination.SIGNAL))
            } catch (e: Exception) {
                Log.e(TAG, "connectCouple failed", e)
                _toast.postValue(Event("Pairing failed: ${e.localizedMessage}"))
            } finally {
                _busy.postValue(false)
            }
        }
    }

    fun disconnectCouple() {
        viewModelScope.launch(ioDispatcher) {
            try {
                _busy.postValue(true)
                if (authRepository.disconnectCouple()) {
                    _coupleId.postValue(null)
                    _paired.postValue(false)
                    _status.postValue("Disconnected")
                    _navEvents.postValue(Event(Destination.PAIR))
                }
            } catch (e: Exception) {
                Log.e(TAG, "disconnectCouple failed", e)
                _toast.postValue(Event("Disconnect failed"))
            } finally {
                _busy.postValue(false)
            }
        }
    }

    fun sendEmotion(emotionId: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                _busy.postValue(true)
                signalRepository.sendSignal(emotionId)
                _toast.postValue(Event("Sent"))
            } catch (e: Exception) {
                Log.e(TAG, "sendEmotion failed", e)
                _toast.postValue(Event("Send failed: ${e.localizedMessage}"))
            } finally {
                _busy.postValue(false)
            }
        }
    }

    fun connectWebSocket() {
        val token = tokenStore.access()
        if (token != null) {
            webSocketClient.connect(token)
        }
    }

    fun logout() {
        viewModelScope.launch(ioDispatcher) {
            authRepository.logout()
            webSocketClient.disconnect()
            _paired.postValue(false)
            _coupleId.postValue(null)
            _personalCode.postValue(null)
            _navEvents.postValue(Event(Destination.LOGIN))
        }
    }

    fun getCurrentUser() = authRepository.currentUser()

    fun getPendingSignals(onResult: (List<com.pulse.app.data.SignalDto>) -> Unit) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val signals = signalRepository.getPendingSignals()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(signals)
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(emptyList())
                }
            }
        }
    }
}
