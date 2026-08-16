package com.foodmind.foodmind_android

import com.foodmind.foodmind_android.core.network.DashboardMetricResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardInsightsTest {
    @Test
    fun `latest insight keeps the returned period value without combining`() {
        val metric = latestInsightMetric(
            listOf(
                dashboardMetric(period = "2026-08-03", value = 11.0),
                dashboardMetric(period = "2026-08-10", value = 7.0),
            ),
            setOf("FOOD_COUNT"),
        )

        assertEquals(7.0, metric?.value ?: 0.0, 0.0)
    }

    @Test
    fun `display preserves rate rating money and empty semantics`() {
        assertEquals("No data", formatInsightMetric(dashboardMetric(value = null, empty = true)))
        assertEquals("72.5%", formatInsightMetric(dashboardMetric(value = 0.725, unit = "RATE")))
        assertEquals("4.3", formatInsightMetric(dashboardMetric(value = 4.25, unit = "RATING")))
        assertEquals("SGD 92.7", formatInsightMetric(dashboardMetric(value = 92.7, unit = "MONEY", currency = "SGD")))
    }

    private fun dashboardMetric(
        period: String = "2026-08-10",
        value: Double? = 4.0,
        unit: String = "COUNT",
        empty: Boolean = false,
        currency: String? = null,
    ) = DashboardMetricResponse(
        code = "FOOD_COUNT",
        label = "Food records",
        period = period,
        value = value,
        unit = unit,
        currency = currency,
        empty = empty,
    )
}
