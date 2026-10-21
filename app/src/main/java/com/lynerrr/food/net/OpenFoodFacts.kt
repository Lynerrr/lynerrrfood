package com.lynerrr.food.net

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

/** A packaged product from Open Food Facts, with nutrients converted to health-diet.ru names and units. */
data class OffProduct(
    val code: String,
    val name: String,
    val brand: String,
    /** App product category id (ProductCategory in the app). */
    val category: Int,
    val scans: Int,
    val servingGrams: Double?,
    /** Site nutrient name -> amount per 100 g in the site's unit. */
    val values: Map<String, Double>,
    /** Known to be sold in Russia or Kazakhstan (country-filtered source, Cyrillic name or local barcode). */
    val local: Boolean,
)

/**
 * Open Food Facts (openfoodfacts.org, ODbL) parsing and lookup, shared by the app (barcode scanner) and the
 * :scraper products builder so both turn OFF data into the same nutrients.
 */
object OpenFoodFacts {
    const val USER_AGENT = "LynerrrFood/1.0 (personal nutrition app; github.com)"

    val FIELDS = listOf(
        "code", "product_name", "product_name_ru", "product_name_en", "brands", "categories_tags",
        "serving_quantity", "unique_scans_n", "nutriments",
    ).joinToString(",")

    /**
     * Open Food Facts nutrient key -> (site nutrient name, factor from OFF's unit to the site's unit).
     * OFF stores every *_100g value in grams, except energy in kcal.
     */
    private val NUTRIENTS = linkedMapOf(
        "energy-kcal" to ("Калорийность" to 1.0),
        "proteins" to ("Белки" to 1.0),
        "fat" to ("Жиры" to 1.0),
        "carbohydrates" to ("Углеводы" to 1.0),
        "sugars" to ("Моно- и дисахариды (сахара)" to 1.0),
        "fiber" to ("Пищевые волокна" to 1.0),
        "saturated-fat" to ("Насыщенные жирные кислоты" to 1.0),
        "monounsaturated-fat" to ("Мононенасыщенные жирные кислоты" to 1.0),
        "polyunsaturated-fat" to ("Полиненасыщенные жирные кислоты" to 1.0),
        "trans-fat" to ("Трансжиры" to 1.0),
        "omega-3-fat" to ("Омега-3 жирные кислоты" to 1.0),
        "omega-6-fat" to ("Омега-6 жирные кислоты" to 1.0),
        "cholesterol" to ("Холестерин" to 1e3),
        "sodium" to ("Натрий, Na" to 1e3),
        "potassium" to ("Калий, K" to 1e3),
        "calcium" to ("Кальций, Ca" to 1e3),
        "magnesium" to ("Магний, Mg" to 1e3),
        "phosphorus" to ("Фосфор, P" to 1e3),
        "iron" to ("Железо, Fe" to 1e3),
        "zinc" to ("Цинк, Zn" to 1e3),
        "manganese" to ("Марганец, Mn" to 1e3),
        "copper" to ("Медь, Cu" to 1e6),
        "selenium" to ("Селен, Se" to 1e6),
        "iodine" to ("Йод, I" to 1e6),
        "vitamin-a" to ("Витамин А, РЭ" to 1e6),
        "vitamin-d" to ("Витамин D, кальциферол" to 1e6),
        "vitamin-e" to ("Витамин Е, альфа токоферол, ТЭ" to 1e3),
        "vitamin-k" to ("Витамин К, филлохинон" to 1e6),
        "vitamin-c" to ("Витамин C, аскорбиновая" to 1e3),
        "vitamin-b1" to ("Витамин В1, тиамин" to 1e3),
        "vitamin-b2" to ("Витамин В2, рибофлавин" to 1e3),
        "vitamin-pp" to ("Витамин РР, НЭ" to 1e3),
        "pantothenic-acid" to ("Витамин В5, пантотеновая" to 1e3),
        "vitamin-b6" to ("Витамин В6, пиридоксин" to 1e3),
        "biotin" to ("Витамин Н, биотин" to 1e6),
        "vitamin-b9" to ("Витамин В9, фолаты" to 1e6),
        "vitamin-b12" to ("Витамин В12, кобаламин" to 1e6),
    )

    /** Site units for the nutrients above; everything else is grams. */
    private val UNITS = mapOf(
        "Калорийность" to "ккал", "Холестерин" to "мг", "Натрий, Na" to "мг", "Калий, K" to "мг", "Кальций, Ca" to "мг",
        "Магний, Mg" to "мг", "Фосфор, P" to "мг", "Железо, Fe" to "мг", "Цинк, Zn" to "мг", "Марганец, Mn" to "мг",
        "Медь, Cu" to "мкг", "Селен, Se" to "мкг", "Йод, I" to "мкг", "Витамин А, РЭ" to "мкг",
        "Витамин D, кальциферол" to "мкг", "Витамин Е, альфа токоферол, ТЭ" to "мг", "Витамин К, филлохинон" to "мкг",
        "Витамин C, аскорбиновая" to "мг", "Витамин В1, тиамин" to "мг", "Витамин В2, рибофлавин" to "мг",
        "Витамин РР, НЭ" to "мг", "Витамин В5, пантотеновая" to "мг", "Витамин В6, пиридоксин" to "мг",
        "Витамин Н, биотин" to "мкг", "Витамин В9, фолаты" to "мкг", "Витамин В12, кобаламин" to "мкг",
    )

    fun unitOf(siteNutrient: String) = UNITS[siteNutrient] ?: "г"

    /**
     * App categories for products (ids match ProductCategory in the app), matched against OFF category tags
     * in this order: first match wins, so "alcohol" beats "beverages" and "ice cream" beats "dairy".
     */
    private val CATEGORY_RULES = listOf(
        9013 to listOf("en:alcoholic-beverages", "en:beers", "en:wines"),
        9007 to listOf("en:ice-creams", "en:frozen-desserts"),
        9002 to listOf("en:fruit-juices", "en:juices-and-nectars", "en:nectars"),
        9001 to listOf("en:beverages", "en:sodas", "en:waters", "en:energy-drinks", "en:teas", "en:coffees"),
        9008 to listOf("en:breakfast-cereals", "en:cereal-bars", "en:mueslis"),
        9004 to listOf("en:biscuits-and-cakes", "en:biscuits", "en:cookies", "en:cakes", "en:pastries", "en:waffles"),
        9003 to listOf("en:chocolates", "en:confectioneries", "en:candies", "en:sweets", "en:chocolate-bars"),
        9005 to listOf("en:salty-snacks", "en:chips-and-fries", "en:crisps", "en:nuts", "en:crackers", "en:snacks"),
        9006 to listOf("en:dairies", "en:yogurts", "en:cheeses", "en:milks", "en:dairy-desserts"),
        9009 to listOf("en:sauces", "en:condiments", "en:ketchup", "en:mayonnaises", "en:spreads"),
        9010 to listOf("en:instant-noodles", "en:meals", "en:noodles", "en:soups", "en:pastas"),
        9011 to listOf("en:meats", "en:sausages", "en:fishes", "en:seafood"),
        9012 to listOf("en:breads", "en:bakery-products"),
    )
    const val OTHER_CATEGORY = 9099

    private val CYRILLIC = Regex("[а-яА-ЯёЁ]")

    /** GS1 barcode prefixes of Russia (460–469), Belarus (481) and Kazakhstan (487). */
    fun isLocalBarcode(code: String) =
        code.length >= 8 && code.take(3).toIntOrNull()?.let { it in 460..469 || it == 481 || it == 487 } == true

    /** Result of looking up one barcode: a usable product, a product without usable nutrition, or nothing. */
    sealed interface Lookup {
        data class Found(val product: OffProduct) : Lookup
        /** OFF knows the product but its nutrition data is missing or implausible. */
        data class NoNutrition(val name: String?, val brand: String?) : Lookup
        data object NotFound : Lookup
    }

    /** Looks up one barcode (blocking; call off the main thread). */
    fun lookup(code: String): Lookup {
        val connection = URL("https://world.openfoodfacts.org/api/v2/product/$code?fields=$FIELDS")
            .openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        val status = connection.responseCode
        if (status == 404) return Lookup.NotFound
        if (status != 200) throw IOException("Open Food Facts answered HTTP $status")
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val p = json.optJSONObject("product")
        if (json.optInt("status") != 1 || p == null) return Lookup.NotFound
        p.put("code", code)
        return parse(p, countryFiltered = true)?.let { Lookup.Found(it) }
            ?: Lookup.NoNutrition(names(p).firstOrNull(), brandOf(p).ifEmpty { null })
    }

    private fun names(p: JSONObject) = listOf("product_name_ru", "product_name", "product_name_en")
        .map { decodeEntities(p.optString(it)).trim().replace(Regex("\\s+"), " ") }
        .filter { it.length >= 2 }

    private fun brandOf(p: JSONObject): String {
        // "COCA-COLA SERVICES SA/NV, Coca-Cola": the shortest entry is usually the consumer brand.
        // The classic API gives brands as one comma-separated string, the search service as a list.
        val brands = p.optJSONArray("brands")?.let { a -> (0 until a.length()).map { a.getString(it) } }
            ?: p.optString("brands").split(',')
        // Prefer brands written in Latin or Cyrillic letters (some entries also list e.g. an Arabic spelling).
        return brands.map { decodeEntities(it).trim() }.filter { it.isNotEmpty() }
            .sortedWith(compareBy({ !it.any { c -> c in 'a'..'z' || c in 'A'..'Z' || CYRILLIC.matches(c.toString()) } }, { it.length }))
            .firstOrNull().orEmpty()
    }

    /** Converts one OFF product (classic API or search service JSON); null if it lacks usable nutrition data. */
    fun parse(p: JSONObject, countryFiltered: Boolean): OffProduct? {
        val code = p.optString("code").takeIf { it.isNotBlank() } ?: return null
        val name = names(p).firstOrNull() ?: return null
        val n = p.optJSONObject("nutriments") ?: return null

        fun per100(key: String): Double? = n.optDouble("${key}_100g").takeIf { !it.isNaN() && it >= 0 }

        val values = LinkedHashMap<String, Double>()
        NUTRIENTS.forEach { (key, target) -> per100(key)?.let { values[target.first] = it * target.second } }
        // Energy is sometimes only given in kJ; sodium sometimes only as salt (salt = sodium × 2.5).
        if ("Калорийность" !in values) per100("energy-kj")?.let { values["Калорийность"] = it / 4.184 }
        if ("Натрий, Na" !in values) per100("salt")?.let { values["Натрий, Na"] = it / 2.5 * 1e3 }
        fillMissingMacros(values)
        if (!isPlausible(values)) return null

        val tags = p.optJSONArray("categories_tags")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        val category = CATEGORY_RULES.firstOrNull { (_, wanted) -> wanted.any { it in tags } }?.first ?: OTHER_CATEGORY
        // Servings under 5 g are almost always "1" meaning one bottle/cup rather than grams.
        val serving = p.optDouble("serving_quantity").takeIf { !it.isNaN() && it in 5.0..1000.0 }
        val local = countryFiltered || CYRILLIC.containsMatchIn(name) || isLocalBarcode(code)
        return OffProduct(code, name, brandOf(p), category, p.optInt("unique_scans_n"), serving, values, local)
    }

    /**
     * Labels often leave a macro out when it's zero (no carbs in sprats, nothing at all in water). If the stated
     * calories are already explained by the macros that are given, the missing ones are taken as 0.
     */
    private fun fillMissingMacros(v: MutableMap<String, Double>) {
        val kcal = v["Калорийность"] ?: return
        val macros = listOf("Белки" to 4.0, "Жиры" to 9.0, "Углеводы" to 4.0)
        val missing = macros.filter { it.first !in v }
        if (missing.isEmpty() || missing.size == macros.size && kcal > 5) return
        val explained = macros.sumOf { (name, k) -> (v[name] ?: 0.0) * k }
        if (abs(explained - kcal) <= maxOf(15.0, kcal * 0.1)) missing.forEach { v[it.first] = 0.0 }
    }

    /** "Lay&#8217;s", "&quot;Песто&quot;" -> plain text (names come HTML-escaped from some sources). */
    private fun decodeEntities(s: String): String = s
        .replace(Regex("&#(\\d+);?")) { String(Character.toChars(it.groupValues[1].toInt())) }
        .replace("&quot;", "\"").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&apos;", "'")

    /** Rejects entries with missing macros or numbers that can't be right (crowd-sourced data has typos). */
    private fun isPlausible(v: Map<String, Double>): Boolean {
        val kcal = v["Калорийность"] ?: return false
        val protein = v["Белки"] ?: return false
        val fat = v["Жиры"] ?: return false
        val carbs = v["Углеводы"] ?: return false
        if (kcal > 900 || protein + fat + carbs > 101) return false
        // Calories should roughly follow from macros; alcohol and sugar alcohols explain small gaps.
        val estimated = protein * 4 + fat * 9 + carbs * 4
        return abs(estimated - kcal) <= maxOf(40.0, kcal * 0.35)
    }
}
