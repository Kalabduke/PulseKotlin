package com.pulse.statusapp.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthRepository {

    val sessionStatus: Flow<SessionStatus>
        get() = PulseClient.supabase.auth.sessionStatus

    suspend fun signUp(email: String, password: String) {
        PulseClient.supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signIn(email: String, password: String) {
        PulseClient.supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    /** Google sign-in via ID token from play-services-auth. */
    suspend fun signInWithGoogle(idToken: String) {
        PulseClient.supabase.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
        }
    }

    suspend fun signOut() {
        PulseClient.supabase.auth.signOut()
    }

    suspend fun currentUserId(): String? =
        PulseClient.supabase.auth.currentSessionOrNull()?.user?.id

    /** Automatically create a profile row for OAuth signups (the DB trigger only covers email signups). */
    suspend fun ensureProfile(id: String, name: String?) {
        val existing = PulseClient.supabase.postgrest["profiles"]
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull<Profile>()
        if (existing == null) {
            PulseClient.supabase.postgrest["profiles"].insert(
                buildJsonObject {
                    put("id", id)
                    name?.let { put("name", it) }
                }
            )
        }
    }
}
