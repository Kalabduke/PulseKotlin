package com.pulse.statusapp.data

import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object PulseJson {
    val instance = Json { ignoreUnknownKeys = true; encodeDefaults = true }
}

class MessagesRepository {

    /** Fetch a conversation with a friend, newest-first then reversed for display. */
    suspend fun fetchConversation(myId: String, friendId: String, limit: Int = 60): List<Message> {
        val data = PulseClient.supabase.postgrest["messages"]
            .select {
                filter {
                    or {
                        and {
                            eq("sender_id", myId)
                            eq("recipient_id", friendId)
                        }
                        and {
                            eq("sender_id", friendId)
                            eq("recipient_id", myId)
                        }
                    }
                }
                order("created_at", ascending = false)
                limit(limit)
            }
            .decodeList<Message>()
        return data.reversed()
    }

    /**
     * Lightweight receipt sync — fetches only the receipt-relevant columns so
     * the sender can see read/delivered ticks + reactions without realtime
     * (supabase-kt realtime filters support a single column, so the sender
     * never receives UPDATE events for messages they sent).
     */
    suspend fun fetchReceiptUpdates(myId: String, friendId: String, limit: Int = 60): List<Message> {
        val data = PulseClient.supabase.postgrest["messages"]
            .select("id, sender_id, recipient_id, reactions, read_at, delivered_at, created_at") {
                filter {
                    or {
                        and {
                            eq("sender_id", myId)
                            eq("recipient_id", friendId)
                        }
                        and {
                            eq("sender_id", friendId)
                            eq("recipient_id", myId)
                        }
                    }
                }
                order("created_at", ascending = false)
                limit(limit)
            }
            .decodeList<Message>()
        return data.reversed()
    }

    /** Search messages within a conversation. */
    suspend fun searchConversation(myId: String, friendId: String, query: String): List<Message> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return PulseClient.supabase.postgrest["messages"]
            .select {
                filter {
                    ilike("content_text", "%$q%")
                    or {
                        and {
                            eq("sender_id", myId)
                            eq("recipient_id", friendId)
                        }
                        and {
                            eq("sender_id", friendId)
                            eq("recipient_id", myId)
                        }
                    }
                }
                order("created_at", ascending = true)
                limit(50)
            }
            .decodeList<Message>()
    }

    suspend fun sendMessage(
        myId: String,
        friendId: String,
        text: String? = null,
        imageUrl: String? = null,
        replyToId: String? = null,
    ): Message? = runCatching {
        PulseClient.supabase.postgrest["messages"].insert(
            buildJsonObject {
                put("sender_id", myId)
                put("recipient_id", friendId)
                if (!text.isNullOrBlank()) put("content_text", text)
                if (imageUrl != null) put("image_url", imageUrl)
                if (replyToId != null) put("reply_to_id", replyToId)
            }
        ) { select() }.decodeSingle<Message>()
    }.getOrNull()

    suspend fun deleteMessage(messageId: String) {
        runCatching {
            PulseClient.supabase.postgrest["messages"].delete {
                filter { eq("id", messageId) }
            }
        }
    }

    /** Mark delivered when a message arrives while we're online. */
    suspend fun markDelivered(messageId: String) {
        runCatching {
            PulseClient.supabase.postgrest["messages"].update(
                buildJsonObject { put("delivered_at", java.time.Instant.now().toString()) }
            ) { filter { eq("id", messageId) } }
        }
    }

    /** Mark all messages from friend as read. */
    suspend fun markRead(myId: String, friendId: String) {
        runCatching {
            PulseClient.supabase.postgrest["messages"].update(
                buildJsonObject { put("read_at", java.time.Instant.now().toString()) }
            ) {
                filter {
                    eq("sender_id", friendId)
                    eq("recipient_id", myId)
                    isNull("read_at")
                }
            }
        }
    }

    /** Toggle a reaction via the security-definer RPC. */
    suspend fun toggleReaction(messageId: String, emoji: String) {
        runCatching {
            PulseClient.supabase.postgrest.rpc(
                "toggle_message_reaction",
                buildJsonObject {
                    put("target_message_id", messageId)
                    put("reaction_emoji", emoji)
                }
            )
        }
    }

    /** Upsert typing indicator. */
    suspend fun setTyping(myId: String, friendId: String, typing: Boolean) {
        runCatching {
            if (typing) {
                PulseClient.supabase.postgrest["typing_statuses"].upsert(
                    buildJsonObject {
                        put("from_user_id", myId)
                        put("to_user_id", friendId)
                        put("updated_at", java.time.Instant.now().toString())
                    }
                ) { onConflict = "from_user_id,to_user_id" }
            } else {
                PulseClient.supabase.postgrest["typing_statuses"].delete {
                    filter {
                        eq("from_user_id", myId)
                        eq("to_user_id", friendId)
                    }
                }
            }
        }
    }

    /* ---------------- Realtime ---------------- */

    /** Live inserts into conversations I participate in. */
    suspend fun messagesInsertFlow(myId: String): Flow<PostgresAction.Insert> {
        val channel = PulseClient.supabase.channel("public:messages:insert")
        val flow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter("recipient_id", FilterOperator.EQ, myId)
        }
        channel.subscribe()
        return flow
    }

    /** Live updates (read_at / delivered_at / reactions) to messages I received. */
    suspend fun messagesUpdateFlow(myId: String): Flow<PostgresAction.Update> {
        val channel = PulseClient.supabase.channel("public:messages:update")
        val flow = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "messages"
            filter("recipient_id", FilterOperator.EQ, myId)
        }
        channel.subscribe()
        return flow
    }

    suspend fun messageDeleteFlow(myId: String): Flow<PostgresAction.Delete> {
        val channel = PulseClient.supabase.channel("public:messages:delete")
        val flow = channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
            table = "messages"
            filter("recipient_id", FilterOperator.EQ, myId)
        }
        channel.subscribe()
        return flow
    }

    /** Typing indicators sent to me. */
    suspend fun typingFlow(myId: String): Flow<PostgresAction> {
        val channel = PulseClient.supabase.channel("public:typing")
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "typing_statuses"
            filter("to_user_id", FilterOperator.EQ, myId)
        }
        channel.subscribe()
        return flow
    }

    fun decodeMessage(record: JsonObject): Message =
        PulseJson.instance.decodeFromJsonElement(Message.serializer(), record)
}
