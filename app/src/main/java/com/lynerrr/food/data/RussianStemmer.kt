package com.lynerrr.food.data

/** Snowball stemmer for Russian (https://snowballstem.org/algorithms/russian/stemmer.html). */
object RussianStemmer {
    private const val VOWELS = "аеиоуыэюя"

    private val PERFECTIVE_GERUND_1 = listOf("вшись", "вши", "в")
    private val PERFECTIVE_GERUND_2 = listOf("ившись", "ывшись", "ивши", "ывши", "ив", "ыв")
    private val REFLEXIVE = listOf("ся", "сь")
    private val ADJECTIVE = listOf(
        "ими", "ыми", "его", "ого", "ему", "ому", "ее", "ие", "ые", "ое", "ей", "ий", "ый", "ой",
        "ем", "им", "ым", "ом", "их", "ых", "ую", "юю", "ая", "яя", "ою", "ею",
    )
    private val PARTICIPLE_1 = listOf("ем", "нн", "вш", "ющ", "щ")
    private val PARTICIPLE_2 = listOf("ивш", "ывш", "ующ")
    private val VERB_1 = listOf("ла", "на", "ете", "йте", "ли", "й", "л", "ем", "н", "ло", "но", "ет", "ют", "ны", "ть", "ешь", "нно")
    private val VERB_2 = listOf(
        "ила", "ыла", "ена", "ейте", "уйте", "ите", "или", "ыли", "ей", "уй", "ил", "ыл", "им", "ым", "ен",
        "ило", "ыло", "ено", "ят", "ует", "уют", "ит", "ыт", "ены", "ить", "ыть", "ишь", "ую", "ю",
    )
    private val NOUN = listOf(
        "иями", "ями", "ами", "ией", "иям", "ием", "иях", "ев", "ов", "ие", "ье", "еи", "ии", "ей", "ой", "ий",
        "ям", "ем", "ам", "ом", "ах", "ях", "ию", "ью", "ия", "ья", "а", "е", "и", "й", "о", "у", "ы", "ь", "ю", "я",
    )
    private val SUPERLATIVE = listOf("ейше", "ейш")
    private val DERIVATIONAL = listOf("ость", "ост")

    fun stem(word: String): String {
        val w = word.lowercase().replace('ё', 'е')
        val rv = w.indexOfFirst { it in VOWELS }.takeIf { it >= 0 }?.plus(1) ?: return w
        val r2 = regionStart(w, regionStart(w, 0))
        var r = w.substring(rv)

        val gerund = strip(r, PERFECTIVE_GERUND_1, PERFECTIVE_GERUND_2)
        if (gerund != null) {
            r = gerund
        } else {
            r = strip(r, emptyList(), REFLEXIVE) ?: r
            val adjective = strip(r, emptyList(), ADJECTIVE)
            r = if (adjective != null) {
                strip(adjective, PARTICIPLE_1, PARTICIPLE_2) ?: adjective
            } else {
                strip(r, VERB_1, VERB_2) ?: strip(r, emptyList(), NOUN) ?: r
            }
        }

        if (r.endsWith('и')) r = r.dropLast(1)

        DERIVATIONAL.firstOrNull { r.endsWith(it) && rv + r.length - it.length >= r2 }?.let { r = r.dropLast(it.length) }

        val superlative = strip(r, emptyList(), SUPERLATIVE)
        when {
            superlative != null -> r = if (superlative.endsWith("нн")) superlative.dropLast(1) else superlative
            r.endsWith("нн") -> r = r.dropLast(1)
            r.endsWith('ь') -> r = r.dropLast(1)
        }
        return w.substring(0, rv) + r
    }

    /** Start of the region after the first non-vowel that follows a vowel, searching from [from]. */
    private fun regionStart(w: String, from: Int): Int {
        for (i in maxOf(from, 1) until w.length) {
            if (w[i] !in VOWELS && w[i - 1] in VOWELS) return i + 1
        }
        return w.length
    }

    /**
     * Removes the longest matching ending. Endings in [afterAYa] only count when preceded by "а" or "я".
     * Returns null if nothing matched.
     */
    private fun strip(s: String, afterAYa: List<String>, plain: List<String>): String? {
        var best = -1
        for (e in afterAYa) {
            if (e.length > best && s.length > e.length && s.endsWith(e) && s[s.length - e.length - 1] in "ая") best = e.length
        }
        for (e in plain) {
            if (e.length > best && s.endsWith(e)) best = e.length
        }
        return if (best < 0) null else s.dropLast(best)
    }
}
