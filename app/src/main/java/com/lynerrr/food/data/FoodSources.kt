package com.lynerrr.food.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Databases the app's foods come from; see [FoodIds.source]. [onByDefault]: until the user switches it. */
enum class FoodSource(val key: String, val onByDefault: Boolean = true) {
    HEALTH_DIET("health_diet"),
    // English names next to the Russian ones crowd search and categories; it's there for whoever wants it.
    USDA("usda", onByDefault = false),
    PRODUCTS("products"),
}

/**
 * Which food sources the user wants in search and suggestions. Turning one off only hides its foods there:
 * diary entries and the foods' own pages keep working, and the user's own foods always show.
 */
class FoodSourceSettings(context: Context) {
    private val prefs = context.getSharedPreferences("sources", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(FoodSource.entries.filter { prefs.getBoolean(it.key, it.onByDefault) }.toSet())
    val enabled: StateFlow<Set<FoodSource>> = _enabled.asStateFlow()

    fun setEnabled(source: FoodSource, on: Boolean) {
        prefs.edit().putBoolean(source.key, on).apply()
        _enabled.value = if (on) _enabled.value + source else _enabled.value - source
    }
}

/** Whether a food with this id passes the user's source settings. */
fun Set<FoodSource>.allows(foodId: Int) = FoodIds.source(foodId)?.let { it in this } ?: true
