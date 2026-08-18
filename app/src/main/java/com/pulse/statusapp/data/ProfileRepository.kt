package com.pulse.statusapp.data

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ProfileRepository {

    suspend fun fetchProfile(userId: String): Profile? =
        PulseClient.supabase.postgrest["profiles"]
            .select { filter { eq("id", userId) } }
            .decodeSingleOrNull<Profile>()

    /** Fetch friends by id list (used for dashboard + chat header). */
    suspend fun fetchProfiles(ids: List<String>): List<Profile> {
        if (ids.isEmpty()) return emptyList()
        return PulseClient.supabase.postgrest["profiles"]
            .select { filter { `in`("id", ids) } }
            .decodeList<Profile>()
    }

    /** Update display name (does NOT touch nicknames — nickname lives on connections). */
    suspend fun updateName(userId: String, name: String) {
        PulseClient.supabase.postgrest["profiles"].update(
            buildJsonObject { put("name", name) }
        ) { filter { eq("id", userId) } }
    }

    /** Update status (emoji + text + optional image). */
    suspend fun updateStatus(
        userId: String,
        emoji: String,
        text: String,
        imageUrl: String? = null,
    ) {
        val body = buildJsonObject {
            put("status_emoji", emoji)
            put("status_text", text)
            put("updated_at", java.time.Instant.now().toString())
            if (imageUrl != null) put("status_image_url", imageUrl)
        }
        PulseClient.supabase.postgrest["profiles"].update(body) {
            filter { eq("id", userId) }
        }
    }

    /** Heartbeat — keeps last_seen fresh so friends see you online. */
    suspend fun touchLastSeen(userId: String) {
        PulseClient.supabase.postgrest["profiles"].update(
            buildJsonObject { put("last_seen", java.time.Instant.now().toString()) }
        ) { filter { eq("id", userId) } }
    }

    /** Log a status change into status_history for the friends' history view. */
    suspend fun logStatusHistory(
        userId: String,
        emoji: String,
        text: String,
        imageUrl: String? = null,
        mediaType: String? = null,
    ) {
        val row = buildJsonObject {
            put("user_id", userId)
            put("status_emoji", emoji)
            put("status_text", text)
            if (imageUrl != null) put("status_image_url", imageUrl)
            if (mediaType != null) put("status_media_type", mediaType)
        }
        runCatching {
            PulseClient.supabase.postgrest["status_history"].insert(row)
        }
    }

    suspend fun fetchStatusHistory(userId: String, limit: Int = 30): List<StatusHistoryEntry> =
        PulseClient.supabase.postgrest["status_history"]
            .select {
                filter { eq("user_id", userId) }
                order("created_at", ascending = false)
                limit(limit)
            }
            .decodeList<StatusHistoryEntry>()
}
