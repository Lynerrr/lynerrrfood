package com.lynerrr.food.data

/**
 * Groups a category's foods for browsing: variants of one food ("Икра осетровая…", "Икра сельди…") become one family
 * named after their shared first word, and a family too big to scan ("Fish, …" in USDA data) is split by its first
 * two words instead ("Fish, salmon", "Fish, tuna").
 */
object FoodGrouping {
    /** [label] names the family; [foods] has the plainest (shortest) name first. */
    data class Family(val label: String, val foods: List<FoodEntity>)

    /** Families larger than this are split by their first two words. */
    const val SPLIT_ABOVE = 15

    private val WORD = Regex("""[\p{L}\p{N}]+""")

    fun families(foods: List<FoodEntity>): List<Family> =
        foods.groupBy { key(it.name, 1) }.values
            .flatMap { members ->
                if (members.size <= SPLIT_ABOVE) listOf(family(members, 1))
                else members.groupBy { key(it.name, 2) }.values.map { family(it, 2) }
            }
            .sortedBy { it.label.lowercase() }

    /** The first [words] words, stemmed, so "Яблоко" and "Яблоки" land together. */
    private fun key(name: String, words: Int) =
        FoodSearch.tokenize(name).take(words).joinToString(" ") { RussianStemmer.stem(it) }

    private fun family(members: List<FoodEntity>, words: Int): Family {
        val sorted = members.sortedWith(compareBy({ it.name.length }, { it.name }))
        return Family(label(sorted.first().name, words), sorted)
    }

    /** The name up to the end of its [words]-th word, as written ("Fish, salmon"), capitalized. */
    fun label(name: String, words: Int): String {
        val end = WORD.findAll(name).take(words).lastOrNull()?.range?.last ?: return name
        return name.substring(0, end + 1).replaceFirstChar { it.uppercase() }
    }
}
