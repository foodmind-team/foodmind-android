package com.foodmind.foodmind_android.feature.chat

import com.foodmind.foodmind_android.core.network.ChatMessageResponse
import com.foodmind.foodmind_android.core.network.ChatPageResponse
import com.foodmind.foodmind_android.core.network.ChatReferenceResponse
import com.foodmind.foodmind_android.core.network.ChatSessionPageResponse
import com.foodmind.foodmind_android.core.network.ChatSessionResponse
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.SearchPageResponse

interface ChatRepository {
    suspend fun createSession(title: String? = null): ChatSessionResponse
    suspend fun sessions(page: Int = 0): ChatSessionPageResponse
    suspend fun session(sessionId: String): ChatSessionResponse
    suspend fun archiveSession(sessionId: String)
    suspend fun messages(sessionId: String, after: String? = null): ChatPageResponse<ChatMessageResponse>
    suspend fun postMessage(
        sessionId: String,
        idempotencyKey: String,
        content: String,
        referenceIds: List<String> = emptyList(),
        useSessionReferences: Boolean = false,
    ): ChatMessageResponse

    suspend fun search(query: String, types: String? = null): SearchPageResponse
    suspend fun shareReference(
        sessionId: String,
        sourceType: String,
        sourceId: String,
    ): ChatReferenceResponse
}

class DefaultChatRepository(
    private val apiClient: FoodMindApiClient,
) : ChatRepository {
    override suspend fun createSession(title: String?) = apiClient.createChatSession(title)
    override suspend fun sessions(page: Int) = apiClient.chatSessions(page)
    override suspend fun session(sessionId: String) = apiClient.chatSession(sessionId)
    override suspend fun archiveSession(sessionId: String) = apiClient.deleteChatSession(sessionId)
    override suspend fun messages(sessionId: String, after: String?) = apiClient.chatMessages(sessionId, after)
    override suspend fun postMessage(
        sessionId: String,
        idempotencyKey: String,
        content: String,
        referenceIds: List<String>,
        useSessionReferences: Boolean,
    ) = apiClient.postChatMessage(sessionId, idempotencyKey, content, referenceIds, useSessionReferences)

    override suspend fun search(query: String, types: String?) = apiClient.search(query, types)
    override suspend fun shareReference(sessionId: String, sourceType: String, sourceId: String) =
        apiClient.shareChatReference(sessionId, sourceType, sourceId)
}
