package com.foodmind.foodmind_android

import android.content.Context

data class CookingPreferences(
    val region: String = "SG",
    val requiredDietaryTagCodes: Set<String> = emptySet(),
    val avoidAllergenCodes: Set<String> = emptySet(),
)

object CookingPreferencesStore {
    private const val FILE = "foodmind_cooking_preferences"

    fun load(context: Context): CookingPreferences {
        val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return CookingPreferences(
            region = preferences.getString("region", "SG") ?: "SG",
            requiredDietaryTagCodes = preferences.getStringSet("dietary", emptySet()).orEmpty().toSet(),
            avoidAllergenCodes = preferences.getStringSet("allergens", emptySet()).orEmpty().toSet(),
        )
    }

    fun save(context: Context, value: CookingPreferences) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("region", value.region)
            .putStringSet("dietary", value.requiredDietaryTagCodes)
            .putStringSet("allergens", value.avoidAllergenCodes)
            .apply()
    }
}
