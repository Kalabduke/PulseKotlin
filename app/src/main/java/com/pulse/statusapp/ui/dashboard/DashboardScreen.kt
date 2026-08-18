package com.pulse.statusapp.ui.dashboard

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.statusapp.data.FriendRow
import java.time.Instant
import java.time.temporal.ChronoUnit

@Composable
fun DashboardScreen(
    userId: String,
    onOpenChat: (friendId: String, name: String) -> Unit,
    onOpenSettings: () -> Unit,
    vm: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory(userId)),
) {
    val state by vm.state

    var showStatusDialog by remember { mutableStateOf(false) }
    var addFriendQuery by remember { mutableStateOf("") }
    var addResult by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Pulse",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showStatusDialog = true }) {
                Icon(Icons.AutoMirrored.Filled.Send, "Update status", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, "Settings")
            }
        }

        // Add friend bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = addFriendQuery,
                onValueChange = { addFriendQuery = it },
                label = { Text("@username") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = {
                    if (addFriendQuery.isNotBlank()) {
                        vm.addFriend(addFriendQuery) { addResult = it }
                        addFriendQuery = ""
                    }
                },
                enabled = addFriendQuery.isNotBlank(),
            ) { Text("Add") }
        }
        addResult?.let { msg ->
            Text(
                msg,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        when {
            state.loading && state.connections.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Pending invites
                if (state.pendingInvites.isNotEmpty()) {
                    item {
                        Text(
                            "Pending invites",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(state.pendingInvites) { invite ->
                        FriendCard(
                            friend = invite,
                            unread = 0,
                            onClick = {},
                            trailing = {
                                Row {
                                    TextButton(onClick = { vm.declineInvite(invite.id) }) { Text("Decline") }
                                    Button(onClick = { vm.acceptInvite(invite.id) }) { Text("Accept") }
                                }
                            },
                        )
                    }
                }

                item {
                    Text(
                        "Friends",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.connections.isEmpty()) {
                    item {
                        Text(
                            "No friends yet — add someone by username above.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                items(state.connections, key = { it.id }) { friend ->
                    val unread = state.unread[friend.friendId] ?: 0L
                    FriendCard(
                        friend = friend,
                        unread = unread,
                        onClick = { onOpenChat(friend.friendId, friend.displayName) },
                        trailing = null,
                    )
                }
            }
        }
    }

    if (showStatusDialog) {
        StatusUpdateDialog(
            initialText = "",
            onDismiss = { showStatusDialog = false },
            onSave = { emoji, text ->
                vm.updateStatus(emoji, text) { showStatusDialog = false }
            },
        )
    }
}

@Composable
private fun FriendCard(
    friend: FriendRow,
    unread: Long,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)?,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(friend.friend?.statusEmoji ?: "😊", fontSize = 22.sp)
                }
                if (isOnline(friend.friend?.lastSeen)) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF31D158))
                            .align(Alignment.BottomEnd),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(friend.displayName, fontWeight = FontWeight.SemiBold)
                Text(
                    friend.friend?.statusText ?: "Available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (unread > 0) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(unread.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            trailing?.invoke()
        }
    }
}

private fun isOnline(lastSeen: String?): Boolean {
    if (lastSeen == null) return false
    return runCatching {
        val seen = Instant.parse(lastSeen)
        ChronoUnit.MINUTES.between(seen, Instant.now()) < 3
    }.getOrDefault(false)
}
