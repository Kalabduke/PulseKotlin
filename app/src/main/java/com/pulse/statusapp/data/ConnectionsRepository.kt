package com.pulse.statusapp.data

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ConnectionsRepository(
    private val profiles: ProfileRepository = ProfileRepository(),
) {

    /** All my connections where I initiated (status connected). */
    suspend fun fetchMyConnections(userId: String): List<FriendRow> {
        val rows = PulseClient.supabase.postgrest["connections"]
            .select {
                filter {
                    eq("user_id", userId)
                    eq("status", "connected")
                }
                order("created_at", ascending = true)
            }
            .decodeList<Connection>()
        return enrich(userId, rows)
    }

    /** Pending invites I received (friend sent me a request). */
    suspend fun fetchPendingInvites(userId: String): List<FriendRow> {
        val rows = PulseClient.supabase.postgrest["connections"]
            .select {
                filter {
                    eq("friend_id", userId)
                    eq("status", "pending")
                }
            }
            .decodeList<Connection>()
        return enrich(userId, rows)
    }

    private suspend fun enrich(userId: String, rows: List<Connection>): List<FriendRow> {
        if (rows.isEmpty()) return emptyList()
        val friendIds = rows.map { it.friendId }
        val profilesById = profiles.fetchProfiles(friendIds).associateBy { it.id }
        return rows.map { c ->
            FriendRow(
                id = c.id,
                friendId = c.friendId,
                status = c.status,
                nickname = c.nickname,
                createdAt = c.createdAt,
                friend = profilesById[c.friendId],
            )
        }
    }

    /** Send a friend request by user id. */
    suspend fun sendRequest(userId: String, friendId: String) {
        PulseClient.supabase.postgrest["connections"].insert(
            buildJsonObject {
                put("user_id", userId)
                put("friend_id", friendId)
                put("status", "pending")
            }
        )
    }

    /** Accept an incoming request (I am the friend_id). */
    suspend fun acceptRequest(connectionId: String, myId: String) {
        PulseClient.supabase.postgrest["connections"].update(
            buildJsonObject { put("status", "connected") }
        ) {
            filter {
                eq("id", connectionId)
                eq("friend_id", myId)
            }
        }
    }

    /** Decline/remove an incoming request (I am the friend_id). */
    suspend fun declineRequest(connectionId: String, myId: String) {
        PulseClient.supabase.postgrest["connections"].delete {
            filter {
                eq("id", connectionId)
                eq("friend_id", myId)
            }
        }
    }

    /** Remove a connection (either direction). */
    suspend fun removeConnection(connectionId: String, myId: String) {
        PulseClient.supabase.postgrest["connections"].delete {
            filter {
                or {
                    and {
                        eq("id", connectionId)
                        eq("user_id", myId)
                    }
                    and {
                        eq("id", connectionId)
                        eq("friend_id", myId)
                    }
                }
            }
        }
    }

    /** Search a user by exact username via the indexed RPC. */
    suspend fun findUserByUsername(username: String): Profile? = runCatching {
        PulseClient.supabase.postgrest.rpc(
            "find_by_username",
            buildJsonObject { put("uname", username) }
        ).decodeSingleOrNull<Profile>()
    }.getOrNull()

    suspend fun setNickname(connectionId: String, nickname: String) {
        PulseClient.supabase.postgrest["connections"].update(
            buildJsonObject { put("nickname", nickname) }
        ) { filter { eq("id", connectionId) } }
    }

    suspend fun unreadCounts(myId: String): Map<String, Long> = runCatching {
        PulseClient.supabase.postgrest.rpc("my_unread_message_counts")
            .decodeList<UnreadCount>()
            .mapNotNull { row -> row.sender?.let { it to (row.cnt ?: 0L) } }
            .toMap()
    }.getOrDefault(emptyMap())
}
