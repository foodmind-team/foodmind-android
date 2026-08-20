package com.foodmind.foodmind_android

import com.foodmind.foodmind_android.core.network.GroupResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class RecordEditorSupportTest {
    @Test
    fun `group choices include only active groups with backend ids`() {
        val groups = selectableRecordGroups(
            listOf(
                GroupResponse(id = "group-b", name = "Zest", status = "ACTIVE"),
                GroupResponse(id = "group-a", name = "Alpha", status = "ACTIVE"),
                GroupResponse(id = "group-c", name = "Archived", status = "ARCHIVED"),
                GroupResponse(name = "Missing id", status = "ACTIVE"),
            ),
        )

        assertEquals(listOf("group-a", "group-b"), groups.map { it.id })
    }

    @Test
    fun `private record drops stale group and omits currency when price is empty`() {
        val fields = prepareRecordSubmission(
            price = "",
            currency = "SGD",
            rating = "",
            visibility = "PRIVATE",
            groupId = "stale-group",
        ).getOrThrow()

        assertNull(fields.price)
        assertNull(fields.currency)
        assertNull(fields.rating)
        assertNull(fields.groupId)
    }

    @Test
    fun `group record requires an explicit group choice`() {
        val failure = prepareRecordSubmission("4.30", "sgd", "4.5", "GROUP", "")

        assertTrue(failure.isFailure)
        assertEquals("Choose a group for this record.", failure.exceptionOrNull()?.message)
    }

    @Test
    fun `valid money rating and group values are normalized`() {
        val fields = prepareRecordSubmission("4.30", "sgd", "4.5", "GROUP", " group-id ").getOrThrow()

        assertEquals(4.3, fields.price)
        assertEquals("SGD", fields.currency)
        assertEquals(4.5, fields.rating)
        assertEquals("group-id", fields.groupId)
    }

    @Test
    fun `backend validation details replace raw http 400 message`() {
        val failure = HttpException(
            Response.error<Unit>(
                400,
                """{"code":"VALIDATION_ERROR","message":"Invalid fields","fieldErrors":[{"field":"groupId","message":"Choose an active group."}]}"""
                    .toResponseBody("application/json".toMediaType()),
            ),
        )

        assertEquals("Choose an active group.", failure.toRecordSaveMessage())
    }
}
