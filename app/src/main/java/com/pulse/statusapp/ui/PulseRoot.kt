package com.pulse.statusapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pulse.statusapp.data.AuthRepository
import com.pulse.statusapp.data.NotificationsRepository
import com.pulse.statusapp.data.PulseClient
import com.pulse.statusapp.ui.auth.AuthScreen
import com.pulse.statusapp.ui.chat.ChatScreen
import com.pulse.statusapp.ui.dashboard.DashboardScreen
import com.pulse.statusapp.ui.onboarding.UsernameScreen
import com.pulse.statusapp.ui.settings.SettingsScreen
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.collectLatest

/** Simple state-based navigation: auth → onboarding → dashboard → chat / settings. */
@Composable
fun PulseRoot() {
    val authRepo = remember { AuthRepository() }
    val notifRepo = remember { NotificationsRepository() }

    var screen by remember { mutableStateOf<Screen>(Screen.Loading) }
    var currentUserId by remember { mutableStateOf<String?>(null) }

    // Track session status — drives auth vs main app.
    LaunchedEffect(Unit) {
        authRepo.sessionStatus.collectLatest { status ->
            val session = PulseClient.supabase.auth.currentSessionOrNull()
            currentUserId = session?.user?.id
            when (status) {
                is SessionStatus.Authenticated -> {
                    val userId = session?.user?.id
                    if (userId != null) {
                        val profile = runCatching {
                            com.pulse.statusapp.data.ProfileRepository().fetchProfile(userId)
                        }.getOrNull()
                        screen = if (profile?.usernameChosen == true) Screen.Dashboard else Screen.Username
                    }
                }
                else -> screen = Screen.Auth
            }
        }
    }

    when (screen) {
        Screen.Loading -> Unit
        Screen.Auth -> AuthScreen(
            onSignedIn = { userId, needsUsername ->
                currentUserId = userId
                screen = if (needsUsername) Screen.Username else Screen.Dashboard
            },
        )
        Screen.Username -> currentUserId?.let { id ->
            UsernameScreen(
                userId = id,
                onDone = { screen = Screen.Dashboard },
            )
        }
        Screen.Dashboard -> currentUserId?.let { id ->
            DashboardScreen(
                userId = id,
                onOpenChat = { friendId, name -> openChatTarget = friendId to name; screen = Screen.Chat },
                onOpenSettings = { screen = Screen.Settings },
            )
        }
        Screen.Chat -> {
            val target = openChatTarget
            if (target != null && currentUserId != null) {
                ChatScreen(
                    myId = currentUserId!!,
                    friendId = target.first,
                    friendName = target.second,
                    onBack = { screen = Screen.Dashboard },
                )
            }
        }
        Screen.Settings -> currentUserId?.let { id ->
            SettingsScreen(
                userId = id,
                onBack = { screen = Screen.Dashboard },
                onSignedOut = { screen = Screen.Auth },
            )
        }
    }
}

private var openChatTarget: Pair<String, String>? = null

private enum class Screen { Loading, Auth, Username, Dashboard, Chat, Settings }
