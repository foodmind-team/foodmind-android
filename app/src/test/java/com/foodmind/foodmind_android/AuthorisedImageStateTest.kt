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

    @Test
    fun backendRelativeImageUsesTheConfiguredApiOrigin() {
        assertEquals(
            "https://foodmind.example/api/v1/catalogue-images/asset-id",
            resolvedAuthorisedImageModel(
                "/api/v1/catalogue-images/asset-id",
                "https://foodmind.example/api/v1/",
            ),
        )
    }

    @Test
    fun signedImageUrlIsNotRewritten() {
        val signedUrl = "https://bucket.s3.ap-southeast-1.amazonaws.com/record.jpg?X-Amz-Signature=test"
        assertEquals(signedUrl, resolvedAuthorisedImageModel(signedUrl, "https://foodmind.example/api/v1/"))
    }
}
