package com.foodmind.foodmind_android.feature.chat

import android.content.Context
import androidx.core.content.edit

interface ChatDraftStore {
    fun load(sessionId: String): String
    fun save(sessionId: String, draft: String)
    fun clear(sessionId: String)
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

    private companion object {
        const val FILE_NAME = "foodmind_chat_drafts"
    }
}
