package com.pulse.statusapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.statusapp.data.Message
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ChatScreen(
    myId: String,
    friendId: String,
    friendName: String,
    onBack: () -> Unit,
    vm: ChatViewModel = viewModel(
        key = "chat-$friendId",
        factory = ChatViewModel.Factory(myId, friendId),
    ),
) {
    val state by vm.state
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var menuMessageId by remember { mutableStateOf<String?>(null) }

    // Start the receipt-sync poll while the chat is on screen; stop it when we
    // leave so the Activity-scoped ViewModel doesn't keep polling forever.
    // Also fire an immediate sync when the app returns from background.
    DisposableEffect(Unit) {
        vm.setPolling(true)
        val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onResume()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            vm.setPolling(false)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(friendName, fontWeight = FontWeight.Bold)
                Text(
                    if (state.friendTyping) "typing…" else "Pulse",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.friendTyping) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.toggleSearch() }) {
                Icon(Icons.Default.Search, "Search")
            }
        }

        if (state.searchActive) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { vm.search(it) },
                placeholder = { Text("Search messages") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            )
            if (state.searchResults.isNotEmpty()) {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(state.searchResults) { msg ->
                        Text(
                            msg.contentText ?: "📎 Media",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (state.searchQuery.isNotBlank()) {
                Text(
                    "No matches",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (!state.searchActive) {
            // Messages list
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.loading && state.messages.isEmpty()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else if (state.messages.isEmpty()) {
                    Text(
                        "No messages yet — say hi!",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(state.messages, key = { it.id }) { msg ->
                            MessageBubble(
                                msg = msg,
                                isMine = msg.senderId == myId,
                                onLongPress = { menuMessageId = msg.id },
                            )
                        }
                    }
                }
            }

            // Reaction quick-bar via dropdown on long-press
            menuMessageId?.let { msgId ->
                val target = state.messages.firstOrNull { it.id == msgId }
                if (target != null) {
                    MessageMenu(
                        message = target,
                        onDismiss = { menuMessageId = null },
                        onReact = { emoji -> vm.toggleReaction(msgId, emoji); menuMessageId = null },
                        onDelete = { vm.deleteMessage(msgId); menuMessageId = null },
                    )
                }
            }
        }

        // Composer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it; vm.onTyping() },
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    vm.clearTyping()
                    vm.send(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank() && !state.sending,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun MessageBubble(
    msg: Message,
    isMine: Boolean,
    onLongPress: () -> Unit,
) {
    val shape = if (isMine) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            onClick = {},
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(vertical = 2.dp),
            shape = shape,
            color = if (isMine) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isMine) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
        ) {
            androidx.compose.foundation.combinedClickable(
                onClick = {},
                onLongClick = onLongPress,
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    msg.contentText?.let { Text(it) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            timeOf(msg.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (isMine) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                ticks(msg),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun ticks(msg: Message): String = when {
    msg.readAt != null -> "✓✓"
    msg.deliveredAt != null -> "✓✓"
    else -> "✓"
}

private fun timeOf(iso: String?): String {
    if (iso == null) return ""
    return runCatching {
        Instant.parse(iso)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault("")
}

@Composable
private fun MessageMenu(
    message: Message,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        listOf("👍", "❤️", "😂", "😮", "😢", "🔥").forEach { emoji ->
            DropdownMenuItem(
                text = { Text("$emoji  React") },
                onClick = { onReact(emoji) },
            )
        }
        androidx.compose.material3.HorizontalDivider()
        DropdownMenuItem(
            text = { Text("🗑  Delete") },
            onClick = onDelete,
        )
    }
}
