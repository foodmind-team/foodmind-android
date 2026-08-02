package com.foodmind.foodmind_android.domain.repository

import com.foodmind.foodmind_android.core.network.FoodMindApiClient

data class GroupItem(
    val id: String,
    val name: String,
    val description: String,
    val status: String,
)

interface GroupRepository {
    suspend fun list(): Result<List<GroupItem>>
}

class GroupRepositoryImpl(private val apiClient: FoodMindApiClient) : GroupRepository {
    override suspend fun list(): Result<List<GroupItem>> = runCatching {
        apiClient.groups().map {
            GroupItem(
                id = it.id ?: it.name.orEmpty(),
                name = it.name ?: "未命名群组",
                description = it.description.orEmpty(),
                status = it.status ?: "ACTIVE",
            )
        }
    }
}
