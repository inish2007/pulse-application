package com.pulse.app.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.app.data.SignalRepository
import com.pulse.app.util.Event
import com.pulse.app.util.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: SignalRepository,
    private val sessionManager: SessionManager,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    enum class Destination { PAIR, SIGNAL, LOGIN, SIGNUP }

    private val _status = MutableLiveData<String>("Waiting for partner…")
    val status: LiveData<String> = _status

    private val _busy = MutableLiveData<Boolean>(false)
    val busy: LiveData<Boolean> = _busy

    private val _toast = MutableLiveData<String>()
    val toast: LiveData<String> = _toast

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
                _navEvents.postValue(Event(Destination.PAIR))
            } catch (e: Exception) {
                _toast.postValue("Auth failed: ${e.localizedMessage}")
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
}
