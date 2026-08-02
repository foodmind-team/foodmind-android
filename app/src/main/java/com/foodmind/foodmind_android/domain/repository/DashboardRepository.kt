package com.foodmind.foodmind_android.domain.repository

import com.foodmind.foodmind_android.core.network.FoodMindApiClient

data class DashboardMetric(
    val code: String,
    val label: String,
    val value: String,
    val unit: String,
    val period: String,
)

data class DashboardData(val empty: Boolean, val metrics: List<DashboardMetric>)

interface DashboardRepository {
    suspend fun load(from: String, to: String): Result<DashboardData>
}

class DashboardRepositoryImpl(private val apiClient: FoodMindApiClient) : DashboardRepository {
    override suspend fun load(from: String, to: String): Result<DashboardData> = runCatching {
        val response = apiClient.dashboard(from, to)
        DashboardData(
            empty = response.empty,
            metrics = response.metrics.map {
                DashboardMetric(
                    code = it.code ?: "UNKNOWN",
                    label = it.label ?: it.code ?: "Metrics",
                    value = it.value?.let { value -> if (value % 1 == 0.0) value.toLong().toString() else "%.1f".format(value) } ?: "—",
                    unit = it.currency ?: it.unit.orEmpty(),
                    period = it.period.orEmpty(),
                )
            },
        )
    }
}
