package com.pulse.statusapp.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Profile(
    val id: String,
    val name: String? = null,
    @SerialName("status_emoji") val statusEmoji: String? = "😊",
    @SerialName("status_text") val statusText: String? = "Available",
    @SerialName("status_image_url") val statusImageUrl: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val username: String? = null,
    @SerialName("username_chosen") val usernameChosen: Boolean = false,
    @SerialName("skip_username") val skipUsername: Boolean = false,
    @SerialName("deactivated_at") val deactivatedAt: String? = null,
    @SerialName("deletion_requested_at") val deletionRequestedAt: String? = null,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: username ?: "Pulse user"
}

/** Connection row as seen by the current user (joined with friend profile in queries). */
@Serializable
data class Connection(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("friend_id") val friendId: String,
    val status: String = "pending", // pending | connected
    val nickname: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** A connection enriched with the friend's profile for display. */
@Serializable
data class FriendRow(
    val id: String,
    @SerialName("friend_id") val friendId: String,
    val status: String = "connected",
    val nickname: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val friend: Profile? = null,
) {
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: friend?.displayName ?: "Pulse user"
}

@Serializable
data class StatusHistoryEntry(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("status_emoji") val statusEmoji: String? = null,
    @SerialName("status_text") val statusText: String? = null,
    @SerialName("status_image_url") val statusImageUrl: String? = null,
    @SerialName("status_media_type") val statusMediaType: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Message(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("content_text") val contentText: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("delivered_at") val deliveredAt: String? = null,
    val reactions: JsonObject? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val reactionMap: Map<String, List<String>>
        get() = reactions?.mapValues { (_, v) ->
            (v as? kotlinx.serialization.json.JsonArray)?.map { it.toString().trim('"') } ?: emptyList()
        } ?: emptyMap()
}

/** Reply preview embedded by the `reply` relation select. */
@Serializable
data class ReplyPreview(
    val id: String,
    @SerialName("content_text") val contentText: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("sender_id") val senderId: String? = null,
)

@Serializable
data class TypingStatus(
    @SerialName("from_user_id") val fromUserId: String,
    @SerialName("to_user_id") val toUserId: String,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class FcmToken(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val token: String? = null,
)

@Serializable
data class UnreadCount(
    val sender: String? = null,
    val cnt: Long? = null,
)

@Serializable
data class UsernameCheckResponse(
    val available: Boolean? = null,
    val taken: Boolean? = null,
    val reason: String? = null,
)
