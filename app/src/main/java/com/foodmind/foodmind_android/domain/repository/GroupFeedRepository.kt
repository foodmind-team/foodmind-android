package com.foodmind.foodmind_android.domain.repository

import com.foodmind.foodmind_android.core.network.FoodMindApiClient

data class GroupFeedItem(
    val id: String,
    val actor: String,
    val title: String,
    val message: String,
    val sourceType: String,
    val occurredAt: String,
)

data class GroupFeedPage(
    val items: List<GroupFeedItem>,
    val nextCursor: String?,
)

interface GroupFeedRepository {
    suspend fun page(groupId: String, after: String? = null): Result<GroupFeedPage>
}

class GroupFeedRepositoryImpl(private val apiClient: FoodMindApiClient) : GroupFeedRepository {
    override suspend fun page(groupId: String, after: String?): Result<GroupFeedPage> = runCatching {
        val response = apiClient.groupFeed(groupId, after)
        GroupFeedPage(
            items = response.items.map { item ->
                GroupFeedItem(
                    id = item.sourceId ?: item.occurredAt.orEmpty(),
                    actor = item.actorDisplayName ?: "Group member",
                    title = item.mealNameSnapshot ?: "Group activity",
                    message = item.message.orEmpty(),
                    sourceType = item.sourceType ?: "UNKNOWN",
                    occurredAt = item.occurredAt.orEmpty(),
                )
            },
            nextCursor = response.nextCursor,
        )
    }
}
