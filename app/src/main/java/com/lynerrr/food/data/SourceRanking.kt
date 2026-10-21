package com.lynerrr.food.data

import com.lynerrr.food.net.SiteCatalog

/**
 * Turns "every food containing nutrient X" into a short list of sensible suggestions:
 * ranked per realistic serving, only foods that are a real source, one entry per food family,
 * and without categories that are noise for this purpose.
 */
object SourceRanking {
    /** A serving gives at least this share of the daily target to count as a source (food-label convention). */
    const val SOURCE_SHARE = 0.15
    const val EXCELLENT_SHARE = 0.30


    /** Organ meats few people eat; liver, heart and tongue stay in. Matched against the first word's stem. */
    private val RARE_OFFAL = listOf("селезенк", "селезёнк", "вилочков", "тимус", "мозг", "вымя", "рубец", "почк", "поджелудочн")

    /** Lungs need an exact match: the same prefix starts "лёгкий" (light), as in light yoghurt. */
    private val LUNGS = setOf("легкие", "лёгкие", "легкое", "лёгкое")

    /** Imported US products keep brand names in capitals ("KRAFT BREYERS ..."). */
    private val BRAND = Regex("[A-Z]{3,}")

    private val SERVING_BY_CATEGORY = mapOf(
        24501 to 150.0, 24502 to 150.0, 24503 to 150.0, 24504 to 50.0, 24506 to 150.0, 24507 to 150.0,
        24509 to 250.0, 24511 to 200.0, 24512 to 150.0, 24513 to 100.0, 24514 to 3.0, 24515 to 10.0,
        24516 to 150.0, 24517 to 30.0, 24518 to 150.0, 24519 to 200.0, 24520 to 100.0, 24522 to 60.0,
        24523 to 60.0, 24524 to 50.0, 24525 to 30.0, 24528 to 30.0,
    )

    // Name stems in Russian (health-diet.ru) and English (USDA).
    private val FRESH_HERBS = listOf(
        "петрушк", "укроп", "базилик", "кинз", "мят", "щавел", "лук зелен", "салат",
        "parsley", "dill", "basil", "coriander", "cilantro", "mint", "sorrel", "chives",
    )
    private val COOKED = listOf("отварн", "варен", "каша", "приготовлен", "тушен")
    /** English words match whole ("uncooked" contains "cooked"). */
    private val COOKED_WORDS = setOf("cooked", "boiled")
    /** Legumes eaten fresh, like a vegetable, rather than weighed dry. */
    private val FRESH_LEGUMES = listOf("свеж", "зелен", "зелён", "стручков", "green", "immature", "sprouted")
    private val DRIED = listOf("сушен", "сухой", "сухая", "сухое", "сухие", "порошок", "сублимир", "dried", "dehydrated", "powder")

    /** Typical grams eaten at once, from the category with a few name-based corrections. */
    fun servingGrams(categoryId: Int?, name: String): Double {
        val n = name.lowercase()
        val tokens = FoodSearch.tokenize(n)
        val first = tokens.firstOrNull().orEmpty()
        return when {
            categoryId == 24514 && DRIED.none { it in n } && FRESH_HERBS.any { it in n } -> 15.0
            categoryId == 24514 -> SERVING_BY_CATEGORY.getValue(24514)
            categoryId in setOf(24522, 24523) && (COOKED.any { it in n } || tokens.any { it in COOKED_WORDS }) -> 180.0
            categoryId == 24523 && FRESH_LEGUMES.any { it in n } -> 150.0
            (DRIED.any { it in n } || "dry" in tokens) && categoryId != 24517 -> 15.0
            first == "cheese" && ("cottage" in tokens || "ricotta" in tokens) -> 150.0
            first in setOf("сыр", "сыры", "cheese") -> 30.0
            first in setOf("масло", "butter") -> 10.0
            first in setOf("сливки", "сметана", "майонез", "соус", "кетчуп", "cream", "sour", "mayonnaise", "sauce", "catsup") -> 30.0
            first == "сироп" || "сироп" in tokens || "syrup" in tokens || "syrups" in tokens -> 20.0
            first == "творог" -> 150.0
            first == "сок" || "сок" in tokens || "juice" in tokens -> 200.0
            else -> SERVING_BY_CATEGORY[categoryId] ?: 100.0
        }
    }

    data class Candidate(
        val food: RankedFood,
        val servingGrams: Double,
        /** Amount in [unit] per serving and per 100 kcal (null without calories). */
        val perServing: Double,
        val per100Kcal: Double?,
        val unit: String,
        /** Share of the daily target one serving gives, when there's a target. */
        val share: Double?,
    )

    data class Entry(val best: Candidate, val variants: List<Candidate>)

    /**
     * @param target daily target and its unit; amounts are converted to that unit. Without a target every
     *   food is kept, since there's nothing to measure "a real source" against.
     */
    fun rank(foods: List<RankedFood>, target: Pair<Double, String>?, perKcal: Boolean, commonOnly: Boolean): List<Entry> {
        val candidates = foods.asSequence()
            .filter { it.categoryId !in SiteCatalog.NOT_SUGGESTED_CATEGORIES && !BRAND.containsMatchIn(it.name) }
            // Suggestions are whole foods; packaged products stay out unless "common only" is off.
            .filter { !commonOnly || (!FoodIds.isProduct(it.id) && SiteCatalog.isCommon(it.id, it.name) && !isRareOffal(it.name)) }
            .mapNotNull { food ->
                val unit = target?.second ?: food.unit
                val per100g = Units.convert(food.amount, food.unit, unit) ?: return@mapNotNull null
                val grams = servingGrams(food.categoryId, food.name)
                val perServing = per100g * grams / 100
                Candidate(
                    food = food,
                    servingGrams = grams,
                    perServing = perServing,
                    per100Kcal = food.kcal?.takeIf { it >= 20 }?.let { per100g / it * 100 },
                    unit = unit,
                    share = target?.let { perServing / it.first },
                )
            }
            .filter { it.share == null || it.share >= SOURCE_SHARE }
            .filter { !perKcal || it.per100Kcal != null }
            .toList()

        fun score(c: Candidate) = if (perKcal) c.per100Kcal ?: 0.0 else c.perServing

        return candidates
            .groupBy { familyKey(it.food.name) }
            .values
            .map { family ->
                val sorted = family.sortedByDescending(::score)
                Entry(sorted.first(), sorted.drop(1))
            }
            .sortedByDescending { score(it.best) }
    }

    private fun isRareOffal(name: String): Boolean {
        val first = FoodSearch.tokenize(name).firstOrNull() ?: return false
        return first in LUNGS || RARE_OFFAL.any { first.startsWith(it) }
    }

    /** Foods sharing their first word's stem ("Шиповник", "Шиповник сухой") form one family. */
    fun familyKey(name: String): String =
        FoodSearch.tokenize(name).firstOrNull()?.let(RussianStemmer::stem) ?: name.lowercase()
}
