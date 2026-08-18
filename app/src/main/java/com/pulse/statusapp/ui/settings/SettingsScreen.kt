package com.pulse.statusapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.statusapp.data.Profile

@Composable
fun SettingsScreen(
    userId: String,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(userId)),
) {
    val state by vm.state
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        if (state.loading) {
            CircularProgressIndicator(Modifier.padding(24.dp))
            return@Column
        }

        state.profile?.let { profile -> ProfileSection(profile, state.name, vm::onNameChange, vm::saveName) }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        // Username change
        Text(
            "Username",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            "Current: @${state.username}  ·  limited to 2 changes per week",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        OutlinedTextField(
            value = state.newUsername,
            onValueChange = vm::onUsernameChange,
            label = { Text("@new username") },
            singleLine = true,
            isError = state.usernameAvailable == false,
            supportingText = {
                when {
                    state.usernameChecking -> Text("Checking…")
                    state.usernameAvailable == true -> Text("Available ✓", color = MaterialTheme.colorScheme.secondary)
                    state.usernameAvailable == false -> Text("Already taken", color = MaterialTheme.colorScheme.error)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )
        Button(
            onClick = vm::saveUsername,
            enabled = state.usernameAvailable == true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) { Text("Change username") }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        // Account
        Text(
            "Account",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        OutlinedButton(
            onClick = { confirmAction = ConfirmAction.Deactivate },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
        ) { Text("Deactivate account") }
        OutlinedButton(
            onClick = { confirmAction = ConfirmAction.Delete },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
        ) { Text("Delete account", color = MaterialTheme.colorScheme.error) }
        OutlinedButton(
            onClick = { vm.signOut(onSignedOut) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
        ) { Text("Sign out") }

        state.message?.let { msg ->
            Text(
                msg,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            androidx.compose.runtime.LaunchedEffect(msg) { kotlinx.coroutines.delay(4000); vm.clearMessage() }
        }
        state.error?.let { err ->
            Text(
                err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }

    confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(if (action == ConfirmAction.Deactivate) "Deactivate account?" else "Delete account?") },
            text = {
                Text(
                    if (action == ConfirmAction.Deactivate)
                        "Your profile will be hidden and friends won't see you online. You can reactivate anytime."
                    else
                        "This schedules permanent deletion. You can cancel it within 30 days."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (action == ConfirmAction.Deactivate) vm.deactivate()
                        else vm.requestDeletion()
                        confirmAction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(20.dp),
        )
    }
}

@Composable
private fun ProfileSection(
    profile: Profile,
    name: String,
    onNameChange: (String) -> Unit,
    onSaveName: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(
            "Profile",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Status: ${profile.statusEmoji ?: "😊"} ${profile.statusText ?: "Available"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Display name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onSaveName,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        ) { Text("Save name") }
    }
}

private enum class ConfirmAction { Deactivate, Delete }
