package com.pulse.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.app.auth.AuthException
import com.pulse.app.data.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val loading: Boolean = false,
    val personalCode: String? = null,
    val error: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun loadPersonalCode() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val code = userRepository.getPersonalCode()
                _state.value = ProfileUiState(loading = false, personalCode = code, error = null)
            } catch (e: AuthException) {
                _state.value = ProfileUiState(
                    loading = false,
                    personalCode = null,
                    error = e.message ?: "Failed to load personal code"
                )
            } catch (e: Exception) {
                _state.value = ProfileUiState(
                    loading = false,
                    personalCode = null,
                    error = e.localizedMessage ?: "Failed to load personal code"
                )
            }
        }
    }
}
