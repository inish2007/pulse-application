package com.pulse.app.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthCredential
import com.pulse.app.data.SignalRepository
import com.pulse.app.util.Event
import com.pulse.app.util.SessionManager
import com.pulse.app.data.WebSocketClient
import com.pulse.app.data.WsEvent
import com.pulse.app.util.TokenStore
import kotlinx.coroutines.flow.collectLatest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: SignalRepository,
    private val sessionManager: SessionManager,
    private val ioDispatcher: CoroutineDispatcher,
    private val webSocketClient: WebSocketClient,
    private val tokenStore: TokenStore
) : ViewModel() {

    init {
        viewModelScope.launch(ioDispatcher) {
            webSocketClient.events.collectLatest { event ->
                if (event is WsEvent.PartnerConnected) {
                    _paired.postValue(true)
                    _status.postValue("Connected ❤️")
                    _navEvents.postValue(Event(Destination.SIGNAL))
                }
            }
        }
    }

    enum class Destination { PAIR, SIGNAL, LOGIN, SIGNUP }

    private val _status = MutableLiveData<String>("Waiting for partner…")
    val status: LiveData<String> = _status

    private val _busy = MutableLiveData<Boolean>(false)
    val busy: LiveData<Boolean> = _busy

    private val _toast = MutableLiveData<String>()
    val toast: LiveData<String> = _toast

    fun showToast(message: String) {
        _toast.postValue(message)
    }

    private val _paired = MutableLiveData<Boolean>(false)
    val paired: LiveData<Boolean> = _paired

    private val _coupleId = MutableLiveData<String>()
    val coupleId: LiveData<String> = _coupleId

    private val _navEvents = MutableLiveData<Event<Destination>>()
    val navEvents: LiveData<Event<Destination>> = _navEvents

    fun loadSavedCoupleId() {
        sessionManager.coupleId()?.let { saved ->
            _coupleId.value = saved
        }
    }

    fun resumeIfPaired() {
        sessionManager.coupleId()?.let { saved ->
            _coupleId.postValue(saved)
            _paired.postValue(true)
            _status.postValue("Connected ❤️")
            _navEvents.postValue(Event(Destination.SIGNAL))
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                _busy.postValue(true)
                repository.signIn(email, password)
                repository.refreshToken()
                _status.postValue("Signed in")
                connectWebSocket()
                _navEvents.postValue(Event(Destination.PAIR))
            } catch (e: Exception) {
                _toast.postValue("Auth failed: ${e.localizedMessage}")
            } finally {
                _busy.postValue(false)
            }
        }
    }

    fun signInWithGoogle(credential: AuthCredential) {
        viewModelScope.launch(ioDispatcher) {
            try {
                _busy.postValue(true)
                repository.signInWithGoogle(credential)
                repository.refreshToken()
                _status.postValue("Signed in with Google")
                connectWebSocket()
                _navEvents.postValue(Event(Destination.PAIR))
            } catch (e: Exception) {
                _toast.postValue("Google Auth failed: ${e.localizedMessage}")
            } finally {
                _busy.postValue(false)
            }
        }
    }

    fun signUp(email: String, password: String) {
        // For this MVP signUp == signIn fallback to create
        signIn(email, password)
    }

    fun pair(coupleId: String, partnerId: String?) {
        viewModelScope.launch(ioDispatcher) {
            try {
                _busy.postValue(true)
                repository.pairWithPartner(coupleId, partnerId)
                sessionManager.saveCoupleId(coupleId)
                _coupleId.postValue(coupleId)
                _paired.postValue(true)
                _status.postValue("Connected ❤️")
                _navEvents.postValue(Event(Destination.SIGNAL))
            } catch (e: Exception) {
                _paired.postValue(false)
                _toast.postValue("Pairing failed: ${e.localizedMessage}")
                _status.postValue("Invalid ID")
            } finally {
                _busy.postValue(false)
            }
        }
    }

    fun sendEmotion(emotionId: String) {
        val currentCouple = _coupleId.value ?: return
        viewModelScope.launch(ioDispatcher) {
            try {
                _busy.postValue(true)
                repository.sendSignal(emotionId, currentCouple)
                _toast.postValue("Sent")
            } catch (e: Exception) {
                _toast.postValue("Send failed: ${e.localizedMessage}")
            } finally {
                _busy.postValue(false)
            }
        }
    }

    fun markPairedState(isPaired: Boolean) {
        _paired.postValue(isPaired)
    }

    fun connectWebSocket() {
        tokenStore.access()?.let { token ->
            webSocketClient.connect(token)
        }
    }

    private val _inviteLink = MutableLiveData<String>()
    val inviteLink: LiveData<String> = _inviteLink
    
    private val _inviteCode = MutableLiveData<String>()
    val inviteCode: LiveData<String> = _inviteCode

    fun createInviteLink() {
        if (_inviteCode.value != null) return // Already generated for this session
        viewModelScope.launch(ioDispatcher) {
            try {
                _busy.postValue(true)
                val response = repository.createInvite()
                if (response.success) {
                    _inviteLink.postValue(response.link)
                    _inviteCode.postValue(response.code)
                }
            } catch (e: Exception) {
                _toast.postValue("Failed to create invite: ${e.localizedMessage}")
            } finally {
                _busy.postValue(false)
            }
        }
    }

    fun consumeInviteLink(token: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                _busy.postValue(true)
                _status.postValue("Validating invite...")
                val response = repository.joinInvite(token)
                if (response.success) {
                    sessionManager.saveCoupleId(response.couple_id)
                    _coupleId.postValue(response.couple_id)
                    _paired.postValue(true)
                    _status.postValue("Connected ❤️")
                    _navEvents.postValue(Event(Destination.SIGNAL))
                    _toast.postValue("Connected with partner! 💕")
                }
            } catch (e: Exception) {
                _toast.postValue("Invalid or expired invite")
                _status.postValue("Invalid Link")
            } finally {
                _busy.postValue(false)
            }
        }
    }

    // ===== COUPLE CODE METHODS =====
    private val _generatedCode = MutableLiveData<String>()
    val generatedCode: LiveData<String> = _generatedCode

    fun generateCoupleCode() {
        viewModelScope.launch(ioDispatcher) {
            try {
                _busy.postValue(true)
                val response = repository.createCoupleCode()
                if (response.success) {
                    _generatedCode.postValue(response.code)
                    _toast.postValue("Code generated: ${response.code}")
                }
            } catch (e: Exception) {
                _toast.postValue("Failed to generate code: ${e.localizedMessage}")
            } finally {
                _busy.postValue(false)
            }
        }
    }

    fun joinCoupleCodeUsingManualEntry(code: String) {
        if (code.isBlank()) {
            _toast.postValue("Please enter a code")
            return
        }
        
        joinCoupleCode(code)
    }

    fun joinCoupleCode(code: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                _busy.postValue(true)
                _status.postValue("Joining with code...")
                
                // Validate code first
                val validation = repository.validateCoupleCode(code)
                if (!validation.valid) {
                    _status.postValue("Code not found or expired")
                    _toast.postValue("Invalid or expired code")
                    return@launch
                }
                
                if (validation.is_full == true) {
                    _status.postValue("Code already used")
                    _toast.postValue("Code has already been claimed")
                    return@launch
                }

                // Join with code
                val response = repository.joinCoupleCode(code)
                if (response.success) {
                    sessionManager.saveCoupleId(response.couple_id)
                    _coupleId.postValue(response.couple_id)
                    _paired.postValue(true)
                    _status.postValue("Connected ❤️")
                    _navEvents.postValue(Event(Destination.SIGNAL))
                    _toast.postValue("Connected with partner! 💕")
                }
            } catch (e: Exception) {
                _status.postValue("Failed to join")
                _toast.postValue("Error: ${e.localizedMessage}")
            } finally {
                _busy.postValue(false)
            }
        }
    }git init
