package com.foodmind.foodmind_android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecommendationFeedbackRequestTest {
    @Test
    fun permanentRejectionUsesDedicatedReasonWithoutExpiry() {
        val request = permanentRejectionRequest("candidate-1")

        assertEquals("candidate-1", request.candidateId)
        assertEquals("REJECTED", request.eventType)
        assertEquals("DO_NOT_RECOMMEND", request.reasonCode)
        assertNull(request.effectiveUntil)
    }
}
