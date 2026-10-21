package com.lynerrr.food.data

/**
 * Ranked, morphology-aware search over the food catalog, held in memory.
 * "говяжий фарш" finds "Говядина, фарш": words are stemmed, word order doesn't matter,
 * adjectives map to their nouns, and partial words and small typos still match.
 */
class FoodSearch(foods: List<FoodEntity>) {
    private class Indexed(val food: FoodEntity, val words: List<String>, val stems: List<String>)

    private val index = foods.map { food ->
        // Brands are searchable too ("pepsi", "простоквашино").
        val words = tokenize(listOfNotNull(food.name, food.brand).joinToString(" "))
        Indexed(food, words, words.map(RussianStemmer::stem))
    }

    fun search(query: String, categoryId: Int?, limit: Int = 300): List<FoodEntity> {
        val candidates = index.filter { categoryId == null || it.food.categoryId == categoryId }
        val queryWords = tokenize(query).filter { it !in STOP_WORDS }
        if (queryWords.isEmpty()) {
            return if (categoryId == null) emptyList() else candidates.map { it.food }.sortedBy { it.name }
        }
        val terms = queryWords.map { word ->
            val stem = RussianStemmer.stem(word)
            Term(word, stem, SYNONYMS[stem].orEmpty(), latinSpellings(word))
        }
        // Long queries may leave one word unmatched (e.g. a cooking method the site words differently).
        val allowedMisses = if (terms.size >= 3) 1 else 0

        return candidates.mapNotNull { item ->
            var total = 0.0
            var misses = 0
            terms.forEach { term ->
                var best = 0.0
                item.stems.forEachIndexed { i, stem ->
                    var s = term.score(item.words[i], stem)
                    if (s > 0 && i == 0) s += 0.3 // the first word is usually the main ingredient
                    if (s > best) best = s
                }
                if (best == 0.0) misses++
                total += best
            }
            if (misses > allowedMisses) null else item to total - item.words.size * 0.02
        }
            .sortedWith(compareByDescending<Pair<Indexed, Double>> { it.second }.thenBy { it.first.food.name.length })
            .take(limit)
            .map { it.first.food }
    }

    /** [latin] holds Latin spellings of a Cyrillic query word, so "пепси" finds "Pepsi". */
    private class Term(val word: String, val stem: String, val synonyms: Set<String>, val latin: List<String>) {
        fun score(nameWord: String, nameStem: String): Double = when {
            nameStem == stem -> 1.0
            nameStem in synonyms -> 0.95
            nameWord in latin -> 0.95
            nameWord.startsWith(word) -> 0.9 // still typing
            latin.any { it.length >= 3 && nameWord.startsWith(it) } -> 0.85
            stem.length >= 3 && nameStem.startsWith(stem) -> 0.85
            commonPrefix(stem, nameStem) >= 4 -> 0.6 // говяж / говядин, курин / куриц
            stem.length >= 5 && nameStem.length >= 5 && withinOneEdit(stem, nameStem) -> 0.5 // typo
            latin.any { it.length >= 5 && withinOneEdit(it, nameWord) } -> 0.5 // сникерс / snickers
            else -> 0.0
        }
    }

    companion object {
        private val SPLIT = Regex("""[^\p{L}\p{N}]+""")
        private val STOP_WORDS = setOf("в", "с", "и", "по", "из", "на", "без", "для", "со", "к", "от")

        /** Groups of words meaning the same food; any word in a group finds the others. */
        private val SYNONYM_GROUPS = listOf(
            listOf("говядина", "говяжий", "говяжья"),
            listOf("свинина", "свиной", "свиная"),
            listOf("курица", "куриный", "куриная", "кура", "цыпленок", "цыплята"),
            listOf("баранина", "бараний", "баранья"),
            listOf("телятина", "телячий", "телячья"),
            listOf("индейка", "индюшиный", "индюшатина"),
            listOf("утка", "утиный", "утятина"),
            listOf("гусь", "гусиный"),
            listOf("кролик", "кроличий", "крольчатина"),
            listOf("рыба", "рыбный"),
            listOf("молоко", "молочный"),
            listOf("яйцо", "яичный", "яйца"),
            listOf("картофель", "картофельный", "картошка"),
            listOf("гречка", "гречневый", "гречневая", "гречиха"),
            listOf("овес", "овсяный", "овсянка"),
            listOf("рис", "рисовый"),
            listOf("пшеница", "пшеничный"),
            listOf("творог", "творожный"),
            listOf("томат", "томатный", "помидор"),
            listOf("морковь", "морковный"),
            listOf("гриб", "грибной"),
            listOf("яблоко", "яблочный"),
            listOf("апельсин", "апельсиновый"),
            listOf("шоколад", "шоколадный"),
            listOf("сливки", "сливочный"),
            listOf("мясо", "мясной"),
            listOf("печень", "печеночный"),
            listOf("горох", "гороховый"),
            listOf("фасоль", "фасолевый"),
            listOf("кукуруза", "кукурузный"),
            listOf("капуста", "капустный"),
            listOf("орех", "ореховый"),
            listOf("кофе", "кофейный"),
            listOf("лосось", "лососевый", "семга"),
            listOf("вареный", "отварной", "отваренный", "варка"),
            listOf("жареный", "обжаренный", "жарка"),
            listOf("запеченный", "печеный", "запекание"),
            listOf("тушеный", "тушение"),
        )

        private val SYNONYMS: Map<String, Set<String>> = buildMap<String, MutableSet<String>> {
            SYNONYM_GROUPS.forEach { group ->
                val stems = group.map(RussianStemmer::stem).toSet()
                stems.forEach { getOrPut(it) { mutableSetOf() } += stems - it }
            }
        }

        private val TRANSLIT = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ж' to "zh", 'з' to "z",
            'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p",
            'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch",
            'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
        )

        /**
         * Latin spellings of a Cyrillic word: plain transliteration plus "k"→"c" ("кола" → "kola", "cola"),
         * since brand names are usually written in Latin letters.
         */
        fun latinSpellings(word: String): List<String> {
            if (word.none { it in TRANSLIT }) return emptyList()
            val plain = word.map { TRANSLIT[it] ?: it.toString() }.joinToString("")
            return listOf(plain, plain.replace('k', 'c')).distinct()
        }

        fun tokenize(text: String): List<String> =
            text.lowercase().replace('ё', 'е').split(SPLIT).filter { it.isNotEmpty() }

        private fun commonPrefix(a: String, b: String): Int {
            val n = minOf(a.length, b.length)
            var i = 0
            while (i < n && a[i] == b[i]) i++
            return i
        }

        private fun withinOneEdit(a: String, b: String): Boolean {
            if (kotlin.math.abs(a.length - b.length) > 1) return false
            var i = 0
            var j = 0
            var edits = 0
            while (i < a.length && j < b.length) {
                if (a[i] == b[j]) { i++; j++; continue }
                if (++edits > 1) return false
                when {
                    a.length > b.length -> i++
                    a.length < b.length -> j++
                    else -> { i++; j++ }
                }
            }
            return edits + (a.length - i) + (b.length - j) <= 1
        }
    }
}
