package com.foodmind.foodmind_android

import android.content.Context

data class CookingPreferences(
    val region: String = "SG",
)

object CookingPreferencesStore {
    private const val FILE = "foodmind_cooking_preferences"

    fun load(context: Context): CookingPreferences {
        val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return CookingPreferences(
            region = preferences.getString("region", "SG") ?: "SG",
        )
    }

    fun save(context: Context, value: CookingPreferences) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("region", value.region)
            .remove("dietary")
            .remove("allergens")
            .apply()
    }
}
