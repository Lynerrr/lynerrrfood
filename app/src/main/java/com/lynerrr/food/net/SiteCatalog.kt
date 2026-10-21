package com.lynerrr.food.net

/** Facts about health-diet.ru's catalog, shared by the app and the :scraper module. */
object SiteCatalog {
    /** Categories that are noise when suggesting foods: alcohol, baby food, restaurant dishes, fast food. */
    val NOT_SUGGESTED_CATEGORIES = setOf(24508, 24526, 24527, 24529)

    /** The site's own core table; higher ids are mostly translated US data and recipe-book dishes. */
    private const val CORE_TABLE_MAX_ID = 4_000
    private const val PLAIN_NAME_MAX_LENGTH = 32
    private val RECIPE_CODE = Regex("""по \d+-\d+""")

    /**
     * Everyday foods: the site's core table, or a short plain name ("Творог 5%"), as opposed to
     * "Бобы (конский) незрелый, вареный, без соли". Custom foods (negative ids) always count.
     */
    fun isCommon(id: Int, name: String): Boolean = when {
        id < 0 -> true
        RECIPE_CODE.containsMatchIn(name) -> false
        id < CORE_TABLE_MAX_ID -> true
        else -> name.length <= PLAIN_NAME_MAX_LENGTH && name.count { it == ',' } <= 1 && '(' !in name
    }
}
