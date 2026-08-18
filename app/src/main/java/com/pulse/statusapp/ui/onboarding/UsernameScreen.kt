package com.pulse.statusapp.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun UsernameScreen(
    userId: String,
    onDone: () -> Unit,
    vm: UsernameViewModel = viewModel(),
) {
    val state by vm.state

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Pick your username",
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "This is how friends find you — it can't be skipped and can only be changed twice a week.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = state.username,
            onValueChange = vm::onUsernameChange,
            label = { Text("@username") },
            singleLine = true,
            isError = state.available == false,
            supportingText = {
                when {
                    state.checking -> Text("Checking availability…")
                    state.available == true -> Text(
                        "@${state.username} is available",
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    state.available == false -> Text(
                        "@${state.username} is already taken",
                        color = MaterialTheme.colorScheme.error,
                    )
                    state.username.isNotEmpty() && state.username.length < 3 ->
                        Text("At least 3 characters")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        state.error?.let { err ->
            Spacer(Modifier.height(12.dp))
            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { vm.save(userId, onDone) },
            enabled = !state.saving && state.available == true,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            if (state.saving) {
                CircularProgressIndicator(Modifier.height(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Text("Continue", fontWeight = FontWeight.Bold)
            }
        }
    }
}
