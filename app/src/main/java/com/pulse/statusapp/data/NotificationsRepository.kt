package com.pulse.statusapp.data

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class NotificationsRepository {

    /** Register/update this device's FCM token. */
    suspend fun registerFcmToken(userId: String, token: String) {
        runCatching {
            PulseClient.supabase.postgrest["fcm_tokens"].upsert(
                buildJsonObject {
                    put("user_id", userId)
                    put("token", token)
                }
            ) { onConflict = "token" }
        }
    }

    suspend fun unregisterFcmToken(token: String) {
        runCatching {
            PulseClient.supabase.postgrest["fcm_tokens"].delete {
                filter { eq("token", token) }
            }
        }
    }

    /* ---------------- Username RPCs ---------------- */

    /** Fast availability check via the indexed RPC. */
    suspend fun isUsernameTaken(username: String): Boolean = runCatching {
        PulseClient.supabase.postgrest.rpc(
            "username_taken",
            buildJsonObject { put("uname", username) }
        ).decodeSingleOrNull<Boolean>() ?: false
    }.getOrDefault(false)

    /** Set my username (enforces uniqueness + the 2x/week cooldown server-side). */
    suspend fun setUsername(username: String) = runCatching {
        PulseClient.supabase.postgrest.rpc(
            "set_my_username",
            buildJsonObject { put("uname", username) }
        )
    }

    /* ---------------- Account lifecycle RPCs ---------------- */

    suspend fun deactivateMyAccount() = runCatching {
        PulseClient.supabase.postgrest.rpc("deactivate_my_account")
    }

    suspend fun reactivateMyAccount() = runCatching {
        PulseClient.supabase.postgrest.rpc("reactivate_my_account")
    }

    suspend fun requestAccountDeletion() = runCatching {
        PulseClient.supabase.postgrest.rpc("request_account_deletion")
    }

    suspend fun cancelAccountDeletion() = runCatching {
        PulseClient.supabase.postgrest.rpc("cancel_account_deletion")
    }
}
