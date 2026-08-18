package com.pulse.statusapp.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.statusapp.data.ConnectionsRepository
import com.pulse.statusapp.data.FriendRow
import com.pulse.statusapp.data.ProfileRepository
import com.pulse.statusapp.data.PulseClient
import com.pulse.statusapp.data.StatusHistoryEntry
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val loading: Boolean = true,
    val connections: List<FriendRow> = emptyList(),
    val pendingInvites: List<FriendRow> = emptyList(),
    val unread: Map<String, Long> = emptyMap(),
    val error: String? = null,
)

class DashboardViewModel(private val userId: String) : ViewModel() {

    private val connectionsRepo = ConnectionsRepository()
    private val profileRepo = ProfileRepository()

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state

    init {
        load()
        startHeartbeat()
        watchProfiles()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            runCatching {
                val connections = connectionsRepo.fetchMyConnections(userId)
                val invites = connectionsRepo.fetchPendingInvites(userId)
                val unread = connectionsRepo.unreadCounts(userId)
                DashboardUiState(loading = false, connections = connections, pendingInvites = invites, unread = unread)
            }.onSuccess { _state.value = it }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }

    /** Realtime: live status changes + unread badge updates from friends. */
    fun watchProfiles() {
        viewModelScope.launch {
            val channel = PulseClient.supabase.channel("public:profiles:dashboard")
            val flow = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "profiles"
            }
            channel.subscribe()
            flow.collect { _ ->
                refreshUnread()
            }
        }
    }

    fun refreshUnread() {
        viewModelScope.launch {
            val unread = connectionsRepo.unreadCounts(userId)
            _state.value = _state.value.copy(unread = unread)
        }
    }

    fun acceptInvite(connectionId: String) {
        viewModelScope.launch {
            runCatching { connectionsRepo.acceptRequest(connectionId, userId) }
            load()
        }
    }

    fun declineInvite(connectionId: String) {
        viewModelScope.launch {
            runCatching { connectionsRepo.declineRequest(connectionId, userId) }
            load()
        }
    }

    fun addFriend(username: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val found = connectionsRepo.findUserByUsername(username.trim())
            if (found == null) {
                onResult("No user found with @$username")
                return@launch
            }
            if (found.id == userId) {
                onResult("That's you!")
                return@launch
            }
            runCatching { connectionsRepo.sendRequest(userId, found.id) }
            onResult("Request sent to @${found.username ?: "user"}")
            load()
        }
    }

    fun updateStatus(emoji: String, text: String, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                profileRepo.updateStatus(userId, emoji, text)
                profileRepo.logStatusHistory(userId, emoji, text)
            }
            onDone()
        }
    }

    /** Heartbeat every 45s while visible — keeps last_seen fresh. */
    fun startHeartbeat() {
        viewModelScope.launch {
            while (true) {
                runCatching { profileRepo.touchLastSeen(userId) }
                delay(45_000)
            }
        }
    }

    fun statusHistory(): List<StatusHistoryEntry> = emptyList()

    override fun onCleared() {
        super.onCleared()
        // Stop realtime when leaving the dashboard
        runCatching { PulseClient.supabase.realtime.removeAllChannels() }
    }

    class Factory(private val userId: String) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DashboardViewModel(userId) as T
    }
}
