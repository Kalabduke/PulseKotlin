package com.pulse.statusapp.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.statusapp.data.NotificationsRepository
import com.pulse.statusapp.data.ProfileRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

data class UsernameUiState(
    val username: String = "",
    val checking: Boolean = false,
    val available: Boolean? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

@OptIn(FlowPreview::class)
class UsernameViewModel : ViewModel() {

    private val notifRepo = NotificationsRepository()
    private val profileRepo = ProfileRepository()

    private val _state = MutableStateFlow(UsernameUiState())
    val state: StateFlow<UsernameUiState> = _state

    private val usernameInput = MutableStateFlow("")

    init {
        // Debounced availability check — fast because username_taken is an indexed RPC
        viewModelScope.launch {
            usernameInput
                .debounce(400)
                .collect { value ->
                    val normalized = normalize(value)
                    if (normalized.length < 3) {
                        _state.value = _state.value.copy(checking = false, available = null)
                        return@collect
                    }
                    _state.value = _state.value.copy(checking = true, available = null)
                    val taken = notifRepo.isUsernameTaken(normalized)
                    _state.value = _state.value.copy(checking = false, available = !taken)
                }
        }
    }

    fun onUsernameChange(value: String) {
        val cleaned = value.lowercase().filter { it.isLetterOrDigit() || it == '_' }.take(30)
        _state.value = _state.value.copy(username = cleaned, error = null)
        usernameInput.value = cleaned
    }

    fun save(userId: String, onDone: () -> Unit) {
        val username = normalize(_state.value.username)
        val s = _state.value
        if (s.available != true || s.checking) {
            _state.value = s.copy(error = "That username is already taken — try another")
            return
        }
        _state.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            runCatching {
                notifRepo.setUsername(username)
                profileRepo.fetchProfile(userId)
            }.onSuccess {
                _state.value = _state.value.copy(saving = false, saved = true)
                onDone()
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    saving = false,
                    error = e.message ?: "Could not save username",
                )
            }
        }
    }

    private fun normalize(value: String) = value.trim().lowercase()
}
