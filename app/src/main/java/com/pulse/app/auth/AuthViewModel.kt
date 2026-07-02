package com.pulse.app.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthCredential
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: AuthRepository
) : ViewModel() {

    private val _state = MutableLiveData<AuthState>(AuthState.Loading)
    val state: LiveData<AuthState> = _state

    fun start() {
        viewModelScope.launch {
            try {
                _state.postValue(AuthState.Loading)
                if (repo.storedAccessValid()) {
                    val cached = repo.currentUser()
                    if (cached != null) {
                        _state.postValue(AuthState.Authenticated(cached))
                        return@launch
                    }
                }

                // Attempt refresh with timeout and a single retry
                var success = false
                repeat(2) { attempt ->
                    try {
                        val refreshed = withTimeout(8_000L) { repo.refreshAccessToken() }
                        if (refreshed) {
                            val user = repo.currentUser()
                            if (user != null) {
                                _state.postValue(AuthState.Authenticated(user))
                                success = true
                                return@launch
                            }
                        }
                    } catch (e: Exception) {
                        // transient error, wait briefly and retry
                        if (attempt == 0) delay(500L)
                    }
                }

                if (!success) {
                    _state.postValue(AuthState.Unauthenticated)
                }
            } catch (e: Exception) {
                _state.postValue(AuthState.Error(e.localizedMessage ?: "Unknown"))
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _state.postValue(AuthState.Loading)
            try {
                val user = withTimeout(10_000L) { repo.signIn(email, password) }
                _state.postValue(AuthState.Authenticated(user))
            } catch (e: Exception) {
                _state.postValue(AuthState.Error(e.localizedMessage ?: "Login failed"))
            }
        }
    }

    fun loginWithGoogle(credential: AuthCredential) {
        viewModelScope.launch {
            _state.postValue(AuthState.Loading)
            try {
                val user = withTimeout(10_000L) { repo.signInWithGoogle(credential) }
                _state.postValue(AuthState.Authenticated(user))
            } catch (e: Exception) {
                _state.postValue(AuthState.Error(e.localizedMessage ?: "Google login failed"))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                repo.clear()
            } catch (_: Exception) {
            }
            _state.postValue(AuthState.Unauthenticated)
        }
    }
}
