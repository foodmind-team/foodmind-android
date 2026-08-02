package com.foodmind.foodmind_android.domain.repository

import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.HistoryEntryResponse

data class HistoryEntry(
    val id: String,
    val title: String,
    val context: String,
    val occurredAt: String,
    val type: String,
)

interface HistoryRepository {
    suspend fun list(from: String, to: String): Result<List<HistoryEntry>>
}

class HistoryRepositoryImpl(private val apiClient: FoodMindApiClient) : HistoryRepository {
    override suspend fun list(from: String, to: String): Result<List<HistoryEntry>> = runCatching {
        apiClient.history(from, to).entries.map { it.toDomain() }
    }
}

private fun HistoryEntryResponse.toDomain(): HistoryEntry = HistoryEntry(
    id = sourceId ?: occurredAt.orEmpty(),
    title = title ?: "未命名记录",
    context = context.orEmpty(),
    occurredAt = occurredAt.orEmpty(),
    type = sourceType ?: "UNKNOWN",
)
