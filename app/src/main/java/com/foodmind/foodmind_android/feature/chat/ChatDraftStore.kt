package com.foodmind.foodmind_android.feature.chat

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson

interface ChatDraftStore {
    fun load(sessionId: String): String
    fun save(sessionId: String, draft: String)
    fun clear(sessionId: String)
    fun loadOutgoing(sessionId: String): OutgoingChatMessage? = null
    fun saveOutgoing(sessionId: String, outgoing: OutgoingChatMessage) = Unit
    fun clearOutgoing(sessionId: String) = Unit
}

object NoOpChatDraftStore : ChatDraftStore {
    override fun load(sessionId: String) = ""
    override fun save(sessionId: String, draft: String) = Unit
    override fun clear(sessionId: String) = Unit
}

class SharedPreferencesChatDraftStore(context: Context) : ChatDraftStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE,
    )
    private val gson = Gson()

    override fun load(sessionId: String): String =
        preferences.getString(sessionId, "").orEmpty().take(CHAT_MESSAGE_LIMIT)

    override fun save(sessionId: String, draft: String) {
        val bounded = draft.take(CHAT_MESSAGE_LIMIT)
        if (bounded.isBlank()) {
            clear(sessionId)
        } else {
            preferences.edit { putString(sessionId, bounded) }
        }
    }

    override fun clear(sessionId: String) {
        preferences.edit { remove(sessionId) }
    }

    override fun loadOutgoing(sessionId: String): OutgoingChatMessage? {
        val json = preferences.getString(outgoingKey(sessionId), null) ?: return null
        return runCatching {
            val stored = gson.fromJson(json, StoredOutgoingChatMessage::class.java)
            val localId = stored.localId?.takeIf(String::isNotBlank) ?: return@runCatching null
            val idempotencyKey = stored.idempotencyKey?.takeIf(String::isNotBlank) ?: return@runCatching null
            val content = stored.content?.trim()?.takeIf(String::isNotBlank) ?: return@runCatching null
            OutgoingChatMessage(
                localId = localId,
                idempotencyKey = idempotencyKey,
                content = content.take(CHAT_MESSAGE_LIMIT),
                referenceIds = stored.referenceIds.orEmpty().filter(String::isNotBlank).distinct().take(20),
                referenceTitles = stored.referenceTitles.orEmpty().filter(String::isNotBlank).take(20),
                status = OutgoingMessageStatus.FAILED,
            )
        }.getOrNull()
    }

    override fun saveOutgoing(sessionId: String, outgoing: OutgoingChatMessage) {
        val stored = StoredOutgoingChatMessage(
            localId = outgoing.localId,
            idempotencyKey = outgoing.idempotencyKey,
            content = outgoing.content.take(CHAT_MESSAGE_LIMIT),
            referenceIds = outgoing.referenceIds.take(20),
            referenceTitles = outgoing.referenceTitles.take(20),
        )
        preferences.edit { putString(outgoingKey(sessionId), gson.toJson(stored)) }
    }

    override fun clearOutgoing(sessionId: String) {
        preferences.edit { remove(outgoingKey(sessionId)) }
    }

    private fun outgoingKey(sessionId: String) = "$OUTGOING_PREFIX$sessionId"

    private companion object {
        const val FILE_NAME = "foodmind_chat_drafts"
        const val OUTGOING_PREFIX = "outgoing:"
    }
}

private data class StoredOutgoingChatMessage(
    val localId: String? = null,
    val idempotencyKey: String? = null,
    val content: String? = null,
    val referenceIds: List<String>? = null,
    val referenceTitles: List<String>? = null,
)
