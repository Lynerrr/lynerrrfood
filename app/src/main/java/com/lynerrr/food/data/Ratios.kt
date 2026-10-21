package com.lynerrr.food.data

import com.lynerrr.food.net.HealthDietParser

enum class RatioStatus { GOOD, OK, POOR }

/**
 * Nutrient balances worth watching. [format] is how the value reads ("2.1 : 1" or "14 g"); thresholds follow
 * common dietary guidance (WHO, EFSA) and are rough guides, not hard rules.
 */
enum class RatioKind(val numerator: String, val denominator: String) {
    POTASSIUM_SODIUM("Калий, K", "Натрий, Na"),
    OMEGA6_OMEGA3("Омега-6 жирные кислоты", "Омега-3 жирные кислоты"),
    CALCIUM_PHOSPHORUS("Кальций, Ca", "Фосфор, P"),
    CALCIUM_MAGNESIUM("Кальций, Ca", "Магний, Mg"),
    ZINC_COPPER("Цинк, Zn", "Медь, Cu"),
    FIBER_PER_1000_KCAL("Пищевые волокна", HealthDietParser.KCAL),
    SATURATED_FAT_PERCENT("Насыщенные жирные кислоты", HealthDietParser.KCAL),
}

data class Ratio(val kind: RatioKind, val value: Double, val status: RatioStatus)

/** Below this, shares of calories say nothing (a diet soda's "fiber per 1000 kcal"). */
private const val MIN_KCAL_FOR_SHARES = 50.0

/** Share of calories from each macro, in percent. */
data class MacroSplit(val protein: Double, val fat: Double, val carbs: Double)

private fun List<NutrientValue>.amount(name: String, unit: String): Double? {
    val v = firstOrNull { it.name == name } ?: return null
    return Units.convert(v.amount, v.unit, unit)?.takeIf { it > 0 }
}

private fun status(value: Double, good: ClosedRange<Double>, ok: ClosedRange<Double>) = when (value) {
    in good -> RatioStatus.GOOD
    in ok -> RatioStatus.OK
    else -> RatioStatus.POOR
}

fun computeRatios(values: List<NutrientValue>): List<Ratio> = RatioKind.entries.mapNotNull { kind ->
    val (value, st) = when (kind) {
        RatioKind.POTASSIUM_SODIUM -> {
            val r = (values.amount(kind.numerator, "мг") ?: return@mapNotNull null) /
                (values.amount(kind.denominator, "мг") ?: return@mapNotNull null)
            r to status(r, 2.0..Double.MAX_VALUE, 1.0..2.0)
        }
        RatioKind.OMEGA6_OMEGA3 -> {
            val r = (values.amount(kind.numerator, "г") ?: return@mapNotNull null) /
                (values.amount(kind.denominator, "г") ?: return@mapNotNull null)
            r to status(r, 0.0..4.0, 4.0..10.0)
        }
        RatioKind.CALCIUM_PHOSPHORUS -> {
            val r = (values.amount(kind.numerator, "мг") ?: return@mapNotNull null) /
                (values.amount(kind.denominator, "мг") ?: return@mapNotNull null)
            r to status(r, 1.0..2.0, 0.5..3.0)
        }
        RatioKind.CALCIUM_MAGNESIUM -> {
            val r = (values.amount(kind.numerator, "мг") ?: return@mapNotNull null) /
                (values.amount(kind.denominator, "мг") ?: return@mapNotNull null)
            r to status(r, 1.5..2.5, 1.0..3.5)
        }
        RatioKind.ZINC_COPPER -> {
            val r = (values.amount(kind.numerator, "мг") ?: return@mapNotNull null) /
                (values.amount(kind.denominator, "мг") ?: return@mapNotNull null)
            r to status(r, 6.0..15.0, 3.0..20.0)
        }
        RatioKind.FIBER_PER_1000_KCAL -> {
            val kcal = values.amount(kind.denominator, "ккал")?.takeIf { it >= MIN_KCAL_FOR_SHARES } ?: return@mapNotNull null
            val r = (values.amount(kind.numerator, "г") ?: 0.0) / kcal * 1000
            r to status(r, 14.0..Double.MAX_VALUE, 10.0..14.0)
        }
        RatioKind.SATURATED_FAT_PERCENT -> {
            val kcal = values.amount(kind.denominator, "ккал")?.takeIf { it >= MIN_KCAL_FOR_SHARES } ?: return@mapNotNull null
            val r = (values.amount(kind.numerator, "г") ?: return@mapNotNull null) * 9 / kcal * 100
            r to status(r, 0.0..10.0, 10.0..13.0)
        }
    }
    Ratio(kind, value, st)
}

fun macroSplit(values: List<NutrientValue>): MacroSplit? {
    val p = (values.amount(HealthDietParser.PROTEIN, "г") ?: 0.0) * 4
    val f = (values.amount(HealthDietParser.FAT, "г") ?: 0.0) * 9
    val c = (values.amount(HealthDietParser.CARBS, "г") ?: 0.0) * 4
    val total = p + f + c
    if (total <= 0) return null
    return MacroSplit(p / total * 100, f / total * 100, c / total * 100)
}
