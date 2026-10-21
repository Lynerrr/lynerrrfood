package com.lynerrr.food.net

import org.jsoup.nodes.Document

data class ParsedCategory(val id: Int, val name: String)

data class CatalogItem(val id: Int, val name: String, val kcal: Double?)

/** A daily norm from the site: a single target, a lower bound ("min"), an upper limit ("max") or a range. */
data class Norm(val low: Double?, val high: Double?, val unit: String)

data class ParsedNutrient(
    val name: String,
    val section: String,
    val position: Int,
    val amount: Double,
    val unit: String,
    val norm: Norm?,
)

data class ParsedFood(val id: Int, val name: String, val nutrients: List<ParsedNutrient>)

class ParseException(message: String) : Exception(message)

/** Parses pages from health-diet.ru. All nutrient amounts on food pages are per 100 g. */
object HealthDietParser {
    const val BASE_SECTION = "Пищевая ценность"
    const val KCAL = "Калорийность"
    const val PROTEIN = "Белки"
    const val FAT = "Жиры"
    const val CARBS = "Углеводы"

    private val SECTION_ORDER = listOf(
        BASE_SECTION,
        "Витамины",
        "Макроэлементы",
        "Микроэлементы",
        "Усвояемые углеводы",
        "Незаменимые аминокислоты",
        "Заменимые аминокислоты",
        "Жирные кислоты",
        "Стеролы (стерины)",
    )

    private val FOOD_HREF = Regex("""/base_of_food/sostav/(\d+)\.php""")
    private val CATEGORY_HREF = Regex("""/base_of_food/food_(\d+)/""")
    private val NUMBER = Regex("""\d+(?:[.,]\d+)?""")
    private val DIGIT_GAP = Regex("""(?<=\d)[\s ](?=\d)""")

    fun parseCategories(doc: Document): List<ParsedCategory> =
        doc.select("a.mzr-left-menu-item").mapNotNull { a ->
            val id = CATEGORY_HREF.find(a.attr("href"))?.groupValues?.get(1)?.toInt()
                ?: return@mapNotNull null
            val name = a.selectFirst("span")?.text()?.trim().orEmpty()
            if (name.isEmpty()) null else ParsedCategory(id, name)
        }.distinctBy { it.id }

    fun parseCategoryFoods(doc: Document): List<CatalogItem> =
        doc.select("tr").mapNotNull { tr ->
            val a = tr.selectFirst("a[href*=/base_of_food/sostav/]") ?: return@mapNotNull null
            val id = FOOD_HREF.find(a.attr("href"))?.groupValues?.get(1)?.toInt()
                ?: return@mapNotNull null
            val kcal = tr.select("td").getOrNull(1)?.text()?.let(::parseNumber)
            CatalogItem(id, a.text().trim(), kcal)
        }.distinctBy { it.id }

    fun parseFood(doc: Document, id: Int): ParsedFood {
        val table = doc.selectFirst("table.mzr-tc-chemical-table")
            ?: throw ParseException("No nutrient table on page for food $id")

        val nutrients = mutableListOf<ParsedNutrient>()
        var section = BASE_SECTION
        table.select("tr").forEachIndexed { index, tr ->
            val cells = tr.select("td")
            if (cells.size < 3) return@forEachIndexed
            val name = cells[0].text().trim().trimEnd('*').trim()
            val amountText = cells[1].text().trim()
            if (name == "Нутриент") return@forEachIndexed
            if (amountText.isEmpty()) {
                section = name
                return@forEachIndexed
            }
            // Detailed fatty-acid groups ("Насыщенные жирные кислоты" etc.) are value rows that also open a group.
            val cls = cells[0].className()
            if ("mzr-tc-chemical-level-3" in cls && "mzr-tc-chemical-group" in cls) section = "Жирные кислоты"

            val (amount, unit) = parseQuantity(amountText) ?: return@forEachIndexed
            nutrients += ParsedNutrient(
                name = name,
                section = section,
                position = sectionRank(section) * 1000 + index,
                amount = amount,
                unit = unit,
                norm = parseNorm(cells[2].text()),
            )
        }
        if (nutrients.isEmpty()) throw ParseException("Nutrient table for food $id is empty")

        // <h1>Калорийность <span itemprop="name">Яйцо куриное</span>. Химический состав…</h1>
        val name = doc.selectFirst("h1 [itemprop=name]")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.title().removePrefix(KCAL).substringBefore(". Химический").trim()
        return ParsedFood(id, name, nutrients.distinctBy { it.name })
    }

    private fun sectionRank(section: String): Int =
        SECTION_ORDER.indexOf(section).let { if (it < 0) SECTION_ORDER.size else it }

    internal fun parseNumber(text: String): Double? =
        NUMBER.find(text.replace(DIGIT_GAP, ""))?.value?.replace(',', '.')?.toDoubleOrNull()

    /** "12.7 г" -> (12.7, "г") */
    internal fun parseQuantity(text: String): Pair<Double, String>? {
        val clean = text.replace(DIGIT_GAP, "")
        val match = NUMBER.find(clean) ?: return null
        val value = match.value.replace(',', '.').toDoubleOrNull() ?: return null
        return value to clean.substring(match.range.last + 1).trim().lowercase()
    }

    /** Handles "76 г", "max 300 мг", "min 16.8 г", "от 0.9 до 3.7 г" and "~" (no norm). */
    internal fun parseNorm(text: String): Norm? {
        val clean = text.trim().lowercase().replace(DIGIT_GAP, "")
        val numbers = NUMBER.findAll(clean).toList()
        if (numbers.isEmpty()) return null
        val unit = clean.substring(numbers.last().range.last + 1).trim()
        val values = numbers.map { it.value.replace(',', '.').toDouble() }
        return when {
            clean.startsWith("max") -> Norm(null, values[0], unit)
            clean.startsWith("min") -> Norm(values[0], null, unit)
            values.size >= 2 -> Norm(values[0], values[1], unit)
            else -> Norm(values[0], null, unit)
        }
    }
}
