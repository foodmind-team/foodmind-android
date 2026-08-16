package com.foodmind.foodmind_android

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthorisedImageStateTest {
    @Test
    fun nullAndBlankModelsStartEmpty() {
        assertEquals(AuthorisedImageState.EMPTY, initialAuthorisedImageState(null))
        assertEquals(AuthorisedImageState.EMPTY, initialAuthorisedImageState(""))
        assertEquals(AuthorisedImageState.EMPTY, initialAuthorisedImageState("   "))
    }

    @Test
    fun signedUrlAndLocalModelStartLoading() {
        assertEquals(
            AuthorisedImageState.LOADING,
            initialAuthorisedImageState("https://bucket.s3.ap-southeast-1.amazonaws.com/record.jpg?X-Amz-Signature=test"),
        )
        assertEquals(AuthorisedImageState.LOADING, initialAuthorisedImageState(Any()))
    }
}
