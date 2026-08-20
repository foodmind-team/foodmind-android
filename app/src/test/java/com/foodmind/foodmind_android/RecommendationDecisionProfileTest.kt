package com.foodmind.foodmind_android

import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationDecisionProfileTest {
    @Test fun rendersNewUserMode() = assertEquals("A balanced starting point", decisionProfileTitle("DEFAULT"))

    @Test fun rendersConstraintMode() = assertEquals("Shaped around your taste and needs", decisionProfileTitle("CONSTRAINT_FOCUSED"))

    @Test fun rendersGroupModeAndPrivacySafeFactor() {
        assertEquals("Informed by people you trust", decisionProfileTitle("GROUP_GUIDED"))
        assertEquals("Authorized group food records", decisionFactorLabel("GROUP_MEMBER_RECORDS"))
    }
}
