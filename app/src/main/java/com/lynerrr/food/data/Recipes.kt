package com.lynerrr.food.data

/** Nutrients of a dish made from other foods: how a recipe becomes a food of its own. */
object Recipes {
    /** One ingredient: its nutrients per 100 g and how many grams of it go into the dish. */
    class Part(val per100g: List<FoodNutrientEntity>, val grams: Double)

    /**
     * Nutrients per 100 g of the finished dish weighing [finalGrams] (the ingredients' total, or less when cooking
     * boiled water off), as amount and unit. Each nutrient is summed in its own unit from [units] (name → unit).
     * A nutrient an ingredient doesn't list counts as none of it, as in the diary.
     */
    fun per100g(parts: List<Part>, finalGrams: Double, units: Map<String, String>): Map<String, Pair<Double, String>> {
        val totals = LinkedHashMap<String, Pair<Double, String>>()
        parts.forEach { part ->
            part.per100g.forEach { n ->
                val unit = totals[n.name]?.second ?: units[n.name] ?: n.unit
                val amount = Units.convert(n.amount * part.grams / 100, n.unit, unit) ?: return@forEach
                totals[n.name] = (totals[n.name]?.first ?: 0.0) + amount to unit
            }
        }
        return totals.mapValues { (_, v) -> v.first * 100 / finalGrams to v.second }
    }
}
