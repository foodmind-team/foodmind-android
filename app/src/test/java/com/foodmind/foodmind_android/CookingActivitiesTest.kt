package com.foodmind.foodmind_android

import com.foodmind.foodmind_android.core.network.CookingConfirmationQuestionResponse
import com.foodmind.foodmind_android.core.network.CookingQuestionOptionResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookingActivitiesTest {
    private val choiceQuestion = CookingConfirmationQuestionResponse(
        questionId = "repair-strategy",
        fieldPath = "repair_strategy",
        prompt = "Choose how to continue",
        responseType = "CHOICE",
        options = listOf(CookingQuestionOptionResponse("buy", "Buy missing", false)),
        required = true,
    )

    @Test
    fun emptyRequiredAnswersCannotBeSubmitted() {
        assertFalse(canSubmitCookingQuestions(listOf(choiceQuestion), emptyMap(), emptyMap()))
        assertEquals(emptyList<Any>(), buildCookingQuestionAnswers(listOf(choiceQuestion), emptyMap(), emptyMap()))
    }

    @Test
    fun selectedChoiceBuildsNonEmptyRequest() {
        val answers = buildCookingQuestionAnswers(
            listOf(choiceQuestion),
            mapOf(choiceQuestion.questionId to "buy"),
            emptyMap(),
        )

        assertTrue(canSubmitCookingQuestions(listOf(choiceQuestion), mapOf(choiceQuestion.questionId to "buy"), emptyMap()))
        assertEquals("repair-strategy", answers.single().questionId)
        assertEquals("buy", answers.single().value)
    }

    @Test
    fun suggestedTextSatisfiesARequiredQuestion() {
        val textQuestion = choiceQuestion.copy(
            questionId = "servings",
            responseType = "TEXT",
            suggestedValue = "2",
        )

        assertTrue(canSubmitCookingQuestions(listOf(textQuestion), emptyMap(), emptyMap()))
        assertEquals("2", buildCookingQuestionAnswers(listOf(textQuestion), emptyMap(), emptyMap()).single().value)
    }
}
