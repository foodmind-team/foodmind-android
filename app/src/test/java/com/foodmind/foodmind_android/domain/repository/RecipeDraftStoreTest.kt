package com.foodmind.foodmind_android.domain.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeDraftStoreTest {
    @Test
    fun saveUpdatesExistingDraftAndDeleteRemovesIt() {
        val original = RecipeDraftStore.find("salmon")
        assertNotNull(original)

        RecipeDraftStore.save("salmon", "新名字", 3, 25)
        assertEquals("新名字", RecipeDraftStore.find("salmon")?.name)
        assertEquals(3, RecipeDraftStore.find("salmon")?.servings)

        RecipeDraftStore.delete("salmon")
        assertTrue(RecipeDraftStore.find("salmon") == null)
        RecipeDraftStore.save("salmon", original!!.name, original.servings, original.minutes)
    }
}
