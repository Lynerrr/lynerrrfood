package com.lynerrr.food.data

import com.lynerrr.food.R

object Units {
    private val toMicrograms = mapOf("г" to 1_000_000.0, "мг" to 1_000.0, "мкг" to 1.0)

    /** Converts between г/мг/мкг; returns null for incompatible units. */
    fun convert(value: Double, from: String, to: String): Double? {
        if (from == to) return value
        val f = toMicrograms[from] ?: return null
        val t = toMicrograms[to] ?: return null
        return value * f / t
    }
}

data class NutrientValue(
    val name: String,
    val section: String,
    val unit: String,
    val amount: Double,
    /** Daily amount to reach (user override, site norm or lower bound of a range). */
    val target: Double?,
    /** Upper limit not to exceed, e.g. cholesterol. */
    val limit: Double?,
    val userEdited: Boolean,
) {
    /** Fraction of target (or of limit when there's only a limit). */
    val fraction: Double? get() = (target ?: limit)?.takeIf { it > 0 }?.let { amount / it }
    val overLimit: Boolean get() = limit != null && amount > limit
}

/**
 * Combines nutrient definitions with amounts (already scaled to the portion).
 * Targets come from the user's own norm, then [overrides] (plan/body-weight based, keyed by [nutrientKey]),
 * then the site's norm. With [includeMissing], nutrients that have a target are listed even when nothing
 * provided them.
 */
fun buildNutrientValues(
    defs: List<NutrientEntity>,
    amounts: List<NutrientAmount>,
    includeMissing: Boolean,
    overrides: Map<String, Double> = emptyMap(),
): List<NutrientValue> {
    val byName = amounts.groupBy { it.name }
    return defs.mapNotNull { def ->
        val rows = byName[def.name]
        val override = overrides[nutrientKey(def.name)]
        val hasTarget = def.normLow != null || def.normHigh != null || def.userNorm != null || override != null
        if (rows == null && !(includeMissing && hasTarget)) return@mapNotNull null
        val amount = rows.orEmpty().sumOf { Units.convert(it.amount, it.unit, def.unit) ?: 0.0 }
        val replaced = def.userNorm != null || override != null
        NutrientValue(
            name = def.name,
            section = def.section,
            unit = def.unit,
            amount = amount,
            target = def.userNorm ?: override ?: def.normLow,
            limit = def.normHigh.takeIf { !replaced || def.normLow != null },
            userEdited = def.userNorm != null,
        )
    }
}

fun formatAmount(value: Double): String = when {
    value == 0.0 -> "0"
    value >= 100 -> "%.0f".format(value)
    value >= 10 -> "%.1f".format(value).trimEnd('0').trimEnd('.', ',')
    value >= 0.1 -> "%.2f".format(value).trimEnd('0').trimEnd('.', ',')
    else -> "%.3f".format(value).trimEnd('0').trimEnd('.', ',')
}

val MEALS = listOf(R.string.meal_breakfast, R.string.meal_lunch, R.string.meal_dinner, R.string.meal_snacks)
