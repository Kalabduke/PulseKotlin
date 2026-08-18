package com.pulse.statusapp.ui.auth

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.pulse.statusapp.data.AuthRepository
import com.pulse.statusapp.data.PulseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val signupMode: Boolean = false,
)

class AuthViewModel : ViewModel() {

    private val repo = AuthRepository()
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    private var googleSignInClient: GoogleSignInClient? = null

    fun ensureGoogleClient(context: Context) {
        if (googleSignInClient == null) {
            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(com.pulse.statusapp.R.string.google_web_client_id))
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(context, options)
        }
    }

    fun googleClient() = googleSignInClient

    fun toggleMode() {
        _state.value = _state.value.copy(signupMode = !_state.value.signupMode, error = null)
    }

    fun signInWithEmail(email: String, password: String) {
        if (email.isBlank() || password.length < 6) {
            _state.value = _state.value.copy(error = "Enter a valid email and password (6+ chars)")
            return
        }
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching {
                if (_state.value.signupMode) repo.signUp(email, password)
                else repo.signIn(email, password)
            }.onFailure {
                _state.value = _state.value.copy(loading = false, error = friendlyError(it))
            }.onSuccess {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    /** Called with the Activity result from the Google sign-in intent. */
    fun handleGoogleResult(
        context: Context,
        resultCode: Int,
        data: android.content.Intent?,
        onSuccess: (userId: String) -> Unit,
    ) {
        if (resultCode != Activity.RESULT_OK) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                    ?: error("Google sign-in returned no ID token")
                repo.signInWithGoogle(idToken)
                val userId = repo.currentUserId() ?: error("Sign-in failed")
                repo.ensureProfile(userId, account.displayName)
                userId
            }.onSuccess { userId ->
                _state.value = _state.value.copy(loading = false)
                onSuccess(userId)
            }.onFailure {
                _state.value = _state.value.copy(loading = false, error = friendlyError(it))
            }
        }
    }

    private fun friendlyError(t: Throwable): String {
        val msg = t.message ?: t.javaClass.simpleName
        return when {
            msg.contains("Invalid login", true) -> "Wrong email or password"
            msg.contains("already registered", true) -> "That email is already registered — try signing in"
            msg.contains("rate limit", true) || msg.contains("over_request_rate_limit", true) -> "Too many attempts — wait a bit and try again"
            else -> msg
        }
    }
}
