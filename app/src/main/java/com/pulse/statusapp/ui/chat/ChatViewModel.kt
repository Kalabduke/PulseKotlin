package com.pulse.statusapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.statusapp.data.Message
import com.pulse.statusapp.data.MessagesRepository
import com.pulse.statusapp.data.PulseJson
import io.github.jan.supabase.realtime.PostgresAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val loading: Boolean = true,
    val friendTyping: Boolean = false,
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Message> = emptyList(),
    val sending: Boolean = false,
    val error: String? = null,
)

class ChatViewModel(
    private val myId: String,
    private val friendId: String,
) : ViewModel() {

    private val repo = MessagesRepository()

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    private var typingJob: Job? = null
    private var receiptPollJob: Job? = null
    private var lastTypingPing = 0L

    init {
        load()
        listenRealtime()
        // Receipt polling is started by the screen (setPolling(true)) so it
        // stops when the user leaves the chat.
    }

    /**
     * supabase-kt realtime filters are single-column, so the sender never gets
     * UPDATE events for read_at/delivered_at/reactions on messages they sent.
     * Poll a lightweight receipt query every 3s (like the web app's fallback)
     * and merge only the receipt fields into local state.
     *
     * The ViewModel is Activity-scoped (state-based nav, no NavHost), so the
     * poll MUST be started/stopped explicitly from the screen's lifecycle —
     * otherwise it would keep polling forever after leaving the chat.
     */
    fun setPolling(enabled: Boolean) {
        receiptPollJob?.cancel()
        receiptPollJob = null
        if (!enabled) return
        receiptPollJob = viewModelScope.launch {
            while (true) {
                delay(3000)
                runCatching { repo.fetchReceiptUpdates(myId, friendId) }
                    .onSuccess { mergeReceipts(it) }
                    // Network blips shouldn't kill the poll — just try again next tick.
            }
        }
    }

    /** Fires when the app returns from background — sync missed events immediately. */
    fun onResume() {
        viewModelScope.launch {
            runCatching { repo.fetchReceiptUpdates(myId, friendId) }
                .onSuccess { mergeReceipts(it) }
        }
    }

    private fun mergeReceipts(latest: List<Message>) {
        val byId = latest.associateBy { it.id }
        _state.value = _state.value.copy(
            messages = _state.value.messages.map { existing ->
                val fresh = byId[existing.id] ?: return@map existing
                existing.copy(
                    readAt = fresh.readAt ?: existing.readAt,
                    deliveredAt = fresh.deliveredAt ?: existing.deliveredAt,
                    reactions = fresh.reactions ?: existing.reactions,
                )
            }
        )
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            runCatching {
                repo.fetchConversation(myId, friendId)
            }.onSuccess { msgs ->
                _state.value = _state.value.copy(loading = false, messages = msgs)
                repo.markRead(myId, friendId)
            }.onFailure {
                _state.value = _state.value.copy(loading = false, error = it.message)
            }
        }
    }

    private fun listenRealtime() {
        viewModelScope.launch {
            repo.messagesInsertFlow(myId).collect { change ->
                val msg = decode(change.record)
                // Ignore our own echo — we optimistically appended it
                if (msg.senderId != myId) {
                    val current = _state.value.messages.toMutableList()
                    if (current.none { it.id == msg.id }) {
                        current.add(msg)
                        _state.value = _state.value.copy(messages = current.sortedBy { it.createdAt })
                        repo.markRead(myId, friendId)
                        repo.markDelivered(msg.id)
                    }
                }
            }
        }
        viewModelScope.launch {
            repo.messagesUpdateFlow(myId).collect { change ->
                val updated = decode(change.record)
                _state.value = _state.value.copy(
                    messages = _state.value.messages.map {
                        if (it.id == updated.id) updated else it
                    }
                )
            }
        }
        viewModelScope.launch {
            repo.messageDeleteFlow(myId).collect { change ->
                val id = change.oldRecord["id"]?.toString()?.trim('"') ?: return@collect
                _state.value = _state.value.copy(
                    messages = _state.value.messages.filterNot { it.id == id }
                )
            }
        }
        viewModelScope.launch {
            repo.typingFlow(myId).collect { change ->
                val record = when (change) {
                    is PostgresAction.Insert -> change.record
                    is PostgresAction.Update -> change.record
                    else -> return@collect
                }
                val fromUser = record["from_user_id"]?.toString()?.trim('"')
                val updatedAt = record["updated_at"]?.toString()?.trim('"')
                _state.value = _state.value.copy(
                    friendTyping = fromUser == friendId && isRecent(updatedAt)
                )
                // Auto-clear after 2.5s if no update arrives
                launch { delay(2500); _state.value = _state.value.copy(friendTyping = false) }
            }
        }
    }

    fun send(text: String) {
        if (text.isBlank() || _state.value.sending) return
        _state.value = _state.value.copy(sending = true)
        viewModelScope.launch {
            val msg = repo.sendMessage(myId, friendId, text = text)
            msg?.let {
                val current = _state.value.messages.toMutableList()
                if (current.none { m -> m.id == it.id }) {
                    current.add(it)
                    _state.value = _state.value.copy(
                        messages = current.sortedBy { m -> m.createdAt },
                        sending = false,
                    )
                } else {
                    _state.value = _state.value.copy(sending = false)
                }
            } ?: run { _state.value = _state.value.copy(sending = false) }
            repo.setTyping(myId, friendId, false)
        }
    }



    fun onTyping() {
        val now = System.currentTimeMillis()
        if (now - lastTypingPing < 3000) return
        lastTypingPing = now
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            repo.setTyping(myId, friendId, true)
        }
    }

    fun clearTyping() {
        typingJob?.cancel()
        viewModelScope.launch { repo.setTyping(myId, friendId, false) }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        viewModelScope.launch { repo.toggleReaction(messageId, emoji) }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repo.deleteMessage(messageId)
            _state.value = _state.value.copy(
                messages = _state.value.messages.filterNot { it.id == messageId }
            )
        }
    }

    fun toggleSearch() {
        _state.value = _state.value.copy(
            searchActive = !_state.value.searchActive,
            searchQuery = "",
            searchResults = emptyList(),
        )
    }

    fun search(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        if (query.isBlank()) {
            _state.value = _state.value.copy(searchResults = emptyList())
            return
        }
        viewModelScope.launch {
            val results = repo.searchConversation(myId, friendId, query)
            _state.value = _state.value.copy(searchResults = results)
        }
    }

    private fun decode(record: JsonObject): Message =
        PulseJson.instance.decodeFromJsonElement(Message.serializer(), record)

    private fun isRecent(iso: String?): Boolean {
        if (iso == null) return false
        return runCatching {
            val t = java.time.Instant.parse(iso)
            java.time.Duration.between(t, java.time.Instant.now()).seconds < 10
        }.getOrDefault(false)
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { repo.setTyping(myId, friendId, false) }
    }

    class Factory(
        private val myId: String,
        private val friendId: String,
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(myId, friendId) as T
    }
}
