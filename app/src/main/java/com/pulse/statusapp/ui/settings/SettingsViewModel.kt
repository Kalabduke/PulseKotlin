package com.pulse.statusapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.statusapp.data.AuthRepository
import com.pulse.statusapp.data.NotificationsRepository
import com.pulse.statusapp.data.Profile
import com.pulse.statusapp.data.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val profile: Profile? = null,
    val loading: Boolean = true,
    val name: String = "",
    val username: String = "",
    val newUsername: String = "",
    val usernameChecking: Boolean = false,
    val usernameAvailable: Boolean? = null,
    val saving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class SettingsViewModel(private val userId: String) : ViewModel() {

    private val profileRepo = ProfileRepository()
    private val notifRepo = NotificationsRepository()
    private val authRepo = AuthRepository()

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            runCatching { profileRepo.fetchProfile(userId) }
                .onSuccess { profile ->
                    _state.value = _state.value.copy(
                        loading = false,
                        profile = profile,
                        name = profile?.name ?: "",
                        username = profile?.username ?: "",
                        newUsername = "",
                    )
                }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }

    fun onNameChange(value: String) {
        _state.value = _state.value.copy(name = value)
    }

    fun saveName() {
        val name = _state.value.name.trim()
        if (name.isBlank()) {
            _state.value = _state.value.copy(error = "Name can't be empty")
            return
        }
        viewModelScope.launch {
            runCatching { profileRepo.updateName(userId, name) }
                .onSuccess { _state.value = _state.value.copy(message = "Name updated") }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
            load()
        }
    }

    fun onUsernameChange(value: String) {
        val cleaned = value.lowercase().filter { it.isLetterOrDigit() || it == '_' }.take(30)
        _state.value = _state.value.copy(newUsername = cleaned, usernameAvailable = null)
        if (cleaned.length >= 3) {
            viewModelScope.launch {
                _state.value = _state.value.copy(usernameChecking = true)
                val taken = notifRepo.isUsernameTaken(cleaned)
                _state.value = _state.value.copy(usernameChecking = false, usernameAvailable = !taken)
            }
        }
    }

    fun saveUsername() {
        val username = _state.value.newUsername
        if (username.length < 3 || _state.value.usernameAvailable != true) {
            _state.value = _state.value.copy(error = "Choose a valid available username")
            return
        }
        viewModelScope.launch {
            runCatching { notifRepo.setUsername(username) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        message = "Username updated (2 changes per week)",
                        newUsername = "",
                        usernameAvailable = null,
                    )
                    load()
                }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun deactivate() {
        viewModelScope.launch {
            runCatching { notifRepo.deactivateMyAccount() }
                .onSuccess { _state.value = _state.value.copy(message = "Account deactivated") }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun requestDeletion() {
        viewModelScope.launch {
            runCatching { notifRepo.requestAccountDeletion() }
                .onSuccess { _state.value = _state.value.copy(message = "Deletion scheduled — cancels within 30 days") }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun cancelDeletion() {
        viewModelScope.launch {
            runCatching { notifRepo.cancelAccountDeletion() }
                .onSuccess { _state.value = _state.value.copy(message = "Deletion cancelled") }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { authRepo.signOut() }
            onDone()
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }

    class Factory(private val userId: String) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(userId) as T
    }
}
