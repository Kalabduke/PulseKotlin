package com.pulse.statusapp.ui.auth

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AuthScreen(
    onSignedIn: (userId: String, needsUsername: Boolean) -> Unit,
    vm: AuthViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by vm.state
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        vm.handleGoogleResult(context, result.resultCode, result.data) { userId ->
            onSignedIn(userId, needsUsername = true)
        }
    }

    vm.ensureGoogleClient(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Pulse",
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Share what's on your mind",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(36.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        state.error?.let { err ->
            Spacer(Modifier.height(12.dp))
            Text(
                err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { vm.signInWithEmail(email, password) },
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(if (state.signupMode) "Create account" else "Sign in", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        TextButtonRow(onClick = { vm.toggleMode() }, text = if (state.signupMode) "Already have an account? Sign in" else "New here? Create an account")

        Spacer(Modifier.height(28.dp))
        OutlinedButton(
            onClick = {
                vm.googleClient()?.let { googleLauncher.launch(it.signInIntent) }
            },
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("Continue with Google", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TextButtonRow(onClick: () -> Unit, text: String) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.width(260.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
