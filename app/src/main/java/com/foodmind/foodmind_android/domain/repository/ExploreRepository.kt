package com.foodmind.foodmind_android.domain.repository

import com.foodmind.foodmind_android.core.network.ExploreItemResponse
import com.foodmind.foodmind_android.core.network.FoodMindApiClient

data class ExploreItem(
    val id: String,
    val sourceType: String,
    val title: String,
    val subtitle: String,
    val snippet: String,
)

data class ExplorePage(
    val items: List<ExploreItem>,
    val nextCursor: String?,
    val hasNext: Boolean,
)

interface ExploreRepository {
    suspend fun page(after: String? = null, topics: String? = null): Result<ExplorePage>
}

class ExploreRepositoryImpl(private val apiClient: FoodMindApiClient) : ExploreRepository {
    override suspend fun page(after: String?, topics: String?): Result<ExplorePage> = runCatching {
        val response = apiClient.explore(after = after, topics = topics)
        ExplorePage(
            items = response.items.map(ExploreItemResponse::toDomain),
            nextCursor = response.nextCursor,
            hasNext = response.hasNext,
        )
    }
}

private fun ExploreItemResponse.toDomain() = ExploreItem(
    id = sourceId ?: title.orEmpty(),
    sourceType = sourceType ?: "CURATED_PLACE",
    title = title ?: "未命名内容",
    subtitle = subtitle.orEmpty(),
    snippet = snippet.orEmpty(),
)
