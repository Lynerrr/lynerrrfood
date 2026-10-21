package com.lynerrr.food.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Databases the app's foods come from; see [FoodIds.source]. */
enum class FoodSource(val key: String) {
    HEALTH_DIET("health_diet"),
    USDA("usda"),
    PRODUCTS("products"),
}

/**
 * Which food sources the user wants in search and suggestions. Turning one off only hides its foods there:
 * diary entries and the foods' own pages keep working, and the user's own foods always show.
 */
class FoodSourceSettings(context: Context) {
    private val prefs = context.getSharedPreferences("sources", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(FoodSource.entries.filter { prefs.getBoolean(it.key, true) }.toSet())
    val enabled: StateFlow<Set<FoodSource>> = _enabled.asStateFlow()

    fun setEnabled(source: FoodSource, on: Boolean) {
        prefs.edit().putBoolean(source.key, on).apply()
        _enabled.value = if (on) _enabled.value + source else _enabled.value - source
    }
}

/** Whether a food with this id passes the user's source settings. */
fun Set<FoodSource>.allows(foodId: Int) = FoodIds.source(foodId)?.let { it in this } ?: true
