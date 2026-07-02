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
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: SignalRepository,
    private val sessionManager: SessionManager,
    private val ioDispatcher: CoroutineDispatcher,
    private val webSocketClient: WebSocketClient,
    private val tokenStore: TokenStore
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    init {
        viewModelScope.launch(ioDispatcher) {
            webSocketClient.events.collectLatest { event ->
                Timber.tag(TAG).d("WS event received: $event")
                if (event is WsEvent.PartnerConnected) {
                    Timber.tag(TAG).d("Partner connected via WS")
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

    private val _toast = MutableLiveData<Event<String>>()
    val toast: LiveData<Event<String>> = _toast

    fun showToast(message: String) {
        _toast.postValue(Event(message))
    }

    private val _paired = MutableLiveData<Boolean>(false)
    val paired: LiveData<Boolean> = _paired

    private val _coupleId = MutableLiveData<String>()
    val coupleId: LiveData<String> = _coupleId

    private val _navEvents = MutableLiveData<Event<Destination>>()
    val navEvents: LiveData<Event<Destination>> = _navEvents

    private val _inviteLink = MutableLiveData<String>()
    val inviteLink: LiveData<String> = _inviteLink

    private val _inviteCode = MutableLiveData<String>()
    val inviteCode: LiveData<String> = _inviteCode

    private val _generatedCode = MutableLiveData<String>()
    val generatedCode: LiveData<String> = _generatedCode

    fun loadSavedCoupleId() {
        sessionManager.coupleId()?.let { saved ->
            Timber.tag(TAG).d("Loaded saved coupleId=$saved")
            _coupleId.value = saved
        }
    }

    fun resumeIfPaired() {
        sessionManager.coupleId()?.let { saved ->
            Timber.tag(TAG).d("Resuming paired session for coupleId=$saved")
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
                Timber.tag(TAG).d("signIn: attempting for email=$email")
                repository.signIn(email, password)
                onAuthSuccess("Signed in")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "signIn failed")
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
                Timber.tag(TAG).d("signInWithGoogle: attempting")
                repository.signInWithGoogle(credential)
                onAuthSuccess("Signed in with Google")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "signInWithGoogle failed")
                _toast.postValue(Event("Google Auth failed: ${e.localizedMessage}"))
            } finally {
                _busy.postValue(false)
            }
        }
    }

    fun signUp(email: String, password: String) {
        // For this MVP signUp == signIn fallback to create
        Timber.tag(TAG).d("signUp: delegating to signIn for email=$email")
        signIn(email, password)
    }

    /** Shared post-authentication flow used by both email and Google sign-in. */
    private suspend fun onAuthSuccess(statusMessage: String) {
        repository.refreshToken()
        Timber.tag(TAG).d("Auth success, token refreshed")
        _status.postValue(statusMessage)
        connectWebSocket()
        _navEvents.postValue(Event(Destination.PAIR))
    }

    fun pair(coupleId: String, partnerId: String?) {
        viewModelScope.launch(ioDispatcher) {
            try {
                _busy.postValue(true)
                Timber.tag(TAG).d("pair: coupleId=$coupleId partnerId=$partnerId")
                repository.pairWithPartner(coupleId, partnerId)
                sessionManager.saveCoupleId(coupleId)
                _coupleId.postValue(coupleId)
                _paired.postValue(true)
                _status.postValue("Connected ❤️")
                _navEvents.postValue(Event(Destination.SIGNAL))
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "pair failed")
                _paired.postValue(false)
                _toast.postValue(Event("Pairing failed: ${e.localizedMessage}"))
                _status.postValue("Invalid ID")
            } finally {
                _busy.postValue(false)
            }
        }
    }

    fun sendEmotion(emotionId: String) {
        val currentCouple = _coupleId.value
        if (currentCouple == null) {
            Timber.tag(TAG).w("sendEmotion: no coupleId set, aborting")
            return
        }
        viewModelScope.launch(ioDispatcher) {
            try {
                _busy.postValue(true)
                Timber.tag(TAG).d("sendEmotion: emotionId=$emotionId couple=$currentCouple")
                repository.sendSignal(emotionId, currentCouple)
                _toast.postValue(Event("Sent"))
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "sendEmotion failed")
                _toast.postValue(Event("Send failed: ${e.localizedMessage}"))
            } finally {
                _busy.postValue(false)
            }
        }
    }

    fun markPairedState(isPaired: Boolean) {
        Timber.tag(TAG).d("markPairedState: $isPaired")
        _paired.postValue(isPaired)
    }

    fun connectWebSocket() {
        val token = tokenStore.access()
        if (token == null) {
            Timber.tag(TAG).w("connectWebSocket: no access token available")
            return
        }
        Timber.tag(TAG).d("connectWebSocket: connecting")
        webSocketClient.connect(token)
    }

    fun createInviteLink() {
        if (_inviteCode.value != null) {
            Timber.tag(TAG).d("createInviteLink: already generated for this session")
            return
        }
        viewModelScope.launch(ioDispatcher) {
            try {
                _busy.postValue(true)
                val response = repository.createInvite()
                Timber.tag(TAG).d("createInviteLink: success=${response.success}")
                if (response.success) {
                    _inviteLink.postValue(response.link)
                    _inviteCode.postValue(response.code)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "createInviteLink failed")
                _toast.postValue(Event("Failed to create invite: ${e.localizedMessage}"))
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
                Timber.tag(TAG).d("consumeInviteLink: validating token")
                val response = repository.joinInvite(token)
                if (response.success) {
                    Timber.tag(TAG).d("consumeInviteLink: joined coupleId=${response.couple_id}")
                    sessionManager.saveCoupleId(response.couple_id)
                    _coupleId.postValue(response.couple_id)
                    _paired.postValue(true)
                    _status.postValue("Connected ❤️")
                    _navEvents.postValue(Event(Destination.SIGNAL))
                    _toast.postValue(Event("Connected with partner! 💕"))
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "consumeInviteLink failed")
                _toast.postValue(Event("Invalid or expired invite"))
                _status.postValue("Invalid Link")
            } finally {
                _busy.postValue(false)
            }
        }
    }

    // ===== COUPLE CODE METHODS =====

    fun generateCoupleCode() {
        viewModelScope.launch(ioDispatcher) {
            try {
                _busy.postValue(true)
                val response = repository.createCoupleCode()
                Timber.tag(TAG).d("generateCoupleCode: success=${response.success}")
                if (response.success) {
                    _generatedCode.postValue(response.code)
                    _toast.postValue(Event("Code generated: ${response.code}"))
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "generateCoupleCode failed")
                _toast.postValue(Event("Failed to generate code: ${e.localizedMessage}"))
            } finally {
                _busy.postValue(false)
            }
        }
    }

    fun joinCoupleCodeUsingManualEntry(code: String) {
        if (code.isBlank()) {
            Timber.tag(TAG).w("joinCoupleCodeUsingManualEntry: blank code entered")
            _toast.postValue(Event("Please enter a code"))
            return
        }
        joinCoupleCode(code)
    }

    fun joinCoupleCode(code: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                _busy.postValue(true)
                _status.postValue("Joining with code...")
                Timber.tag(TAG).d("joinCoupleCode: validating code=$code")

                val validation = repository.validateCoupleCode(code)
                if (!validation.valid) {
                    Timber.tag(TAG).w("joinCoupleCode: code not found or expired")
                    _status.postValue("Code not found or expired")
                    _toast.postValue(Event("Invalid or expired code"))
                    return@launch
                }

                if (validation.is_full == true) {
                    Timber.tag(TAG).w("joinCoupleCode: code already claimed")
                    _status.postValue("Code already used")
                    _toast.postValue(Event("Code has already been claimed"))
                    return@launch
                }

                val response = repository.joinCoupleCode(code)
                if (response.success) {
                    Timber.tag(TAG).d("joinCoupleCode: joined coupleId=${response.couple_id}")
                    sessionManager.saveCoupleId(response.couple_id)
                    _coupleId.postValue(response.couple_id)
                    _paired.postValue(true)
                    _status.postValue("Connected ❤️")
                    _navEvents.postValue(Event(Destination.SIGNAL))
                    _toast.postValue(Event("Connected with partner! 💕"))
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "joinCoupleCode failed")
                _status.postValue("Failed to join")
                _toast.postValue(Event("Error: ${e.localizedMessage}"))
            } finally {
                _busy.postValue(false)
            }
        }
    }
}