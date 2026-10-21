package com.lynerrr.food.scraper.usda

import java.io.File
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipFile

/**
 * Builds app/src/main/assets/usda.dat from USDA FoodData Central "SR Legacy" (public domain, ~7,800 foods with full
 * micronutrients), in the same format as the health-diet.ru asset so the app imports both the same way.
 * Nutrients get health-diet.ru's names and units, mapped and derived exactly as that site did for the US entries it
 * translated (checked against 444 foods present in both). Run: ./gradlew :scraper:runUsda
 */

private const val DOWNLOAD =
    "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip"
private val zipFile = File("scraper/out/usda/sr_legacy.zip")
private val healthDietAsset = File("app/src/main/assets/fooddb.dat")
private val assetFile = File("app/src/main/assets/usda.dat")

/** USDA foods get ids above this (plus their NDB number), below the packaged-products range. Matches FoodIds. */
private const val USDA_BASE = 100_000_000

/** USDA nutrient number → health-diet.ru nutrient name and the factor from USDA's unit to the site's. */
private val NUTRIENTS = mapOf(
    "208" to ("Калорийность" to 1.0), "203" to ("Белки" to 1.0), "204" to ("Жиры" to 1.0),
    "205" to ("Углеводы (общие)" to 1.0), "291" to ("Пищевые волокна" to 1.0), "255" to ("Вода" to 1.0),
    "207" to ("Зола" to 1.0), "221" to ("Алкоголь (этиловый спирт)" to 1.0),
    "269" to ("Моно- и дисахариды (сахара)" to 1.0), "209" to ("Крахмал и декстрины" to 1.0),
    "210" to ("Сахароза" to 1.0), "211" to ("Глюкоза (декстроза)" to 1.0), "212" to ("Фруктоза" to 1.0),
    "213" to ("Лактоза" to 1.0), "214" to ("Мальтоза" to 1.0), "287" to ("Галактоза" to 1.0),
    "262" to ("Кофеин" to 1.0), "263" to ("Теобромин" to 1.0),
    // Minerals.
    "301" to ("Кальций, Ca" to 1.0), "303" to ("Железо, Fe" to 1.0), "304" to ("Магний, Mg" to 1.0),
    "305" to ("Фосфор, P" to 1.0), "306" to ("Калий, K" to 1.0), "307" to ("Натрий, Na" to 1.0),
    "309" to ("Цинк, Zn" to 1.0), "312" to ("Медь, Cu" to 1000.0), "313" to ("Фтор, F" to 1.0),
    "315" to ("Марганец, Mn" to 1.0), "317" to ("Селен, Se" to 1.0),
    // Vitamins.
    "320" to ("Витамин А, РЭ" to 1.0), "319" to ("Ретинол" to 0.001), "321" to ("бета Каротин" to 0.001),
    "322" to ("альфа Каротин" to 1.0), "334" to ("бета Криптоксантин" to 1.0), "337" to ("Ликопин" to 1.0),
    "338" to ("Лютеин + Зеаксантин" to 1.0),
    "323" to ("Витамин Е, альфа токоферол, ТЭ" to 1.0), "573" to ("Витамин E, добавленный" to 1.0),
    "341" to ("бета Токоферол" to 1.0), "342" to ("гамма Токоферол" to 1.0), "343" to ("дельта Токоферол" to 1.0),
    "328" to ("Витамин D, кальциферол" to 1.0), "326" to ("Витамин D3, холекальциферол" to 1.0),
    "325" to ("Витамин D2, эргокальциферол" to 1.0),
    "430" to ("Витамин К, филлохинон" to 1.0), "429" to ("Дигидрофиллохинон" to 1.0),
    "428" to ("Менахинон-4 (МК4)" to 1.0),
    "401" to ("Витамин C, аскорбиновая" to 1.0), "404" to ("Витамин В1, тиамин" to 1.0),
    "405" to ("Витамин В2, рибофлавин" to 1.0), "406" to ("Витамин РР, НЭ" to 1.0),
    "410" to ("Витамин В5, пантотеновая" to 1.0), "415" to ("Витамин В6, пиридоксин" to 1.0),
    "435" to ("Витамин В9, фолаты" to 1.0), "418" to ("Витамин В12, кобаламин" to 1.0),
    "578" to ("Витамин B12, добавленный" to 1.0), "421" to ("Витамин В4, холин" to 1.0), "454" to ("Бетаин" to 1.0),
    // Amino acids.
    "501" to ("Триптофан" to 1.0), "502" to ("Треонин" to 1.0), "503" to ("Изолейцин" to 1.0),
    "504" to ("Лейцин" to 1.0), "505" to ("Лизин" to 1.0), "506" to ("Метионин" to 1.0), "507" to ("Цистеин" to 1.0),
    "508" to ("Фенилаланин" to 1.0), "509" to ("Тирозин" to 1.0), "510" to ("Валин" to 1.0),
    "511" to ("Аргинин" to 1.0), "512" to ("Гистидин" to 1.0), "513" to ("Аланин" to 1.0),
    "514" to ("Аспарагиновая кислота" to 1.0), "515" to ("Глутаминовая кислота" to 1.0), "516" to ("Глицин" to 1.0),
    "517" to ("Пролин" to 1.0), "518" to ("Серин" to 1.0), "521" to ("Гидроксипролин" to 1.0),
    // Sterols and fats.
    "601" to ("Холестерин" to 1.0), "636" to ("Фитостеролы" to 1.0), "638" to ("Стигмастерол" to 1.0),
    "639" to ("Кампестерол" to 1.0), "641" to ("бета Ситостерол" to 1.0),
    "606" to ("Насыщенные жирные кислоты" to 1.0), "645" to ("Мононенасыщенные жирные кислоты" to 1.0),
    "646" to ("Полиненасыщенные жирные кислоты" to 1.0), "605" to ("Трансжиры" to 1.0),
    "693" to ("Моноеновые трансжиры" to 1.0), "695" to ("Полиеновые трансжиры" to 1.0),
    "607" to ("4:0 Масляная" to 1.0), "608" to ("6:0 Капроновая" to 1.0), "609" to ("8:0 Каприловая" to 1.0),
    "610" to ("10:0 Каприновая" to 1.0), "611" to ("12:0 Лауриновая" to 1.0), "696" to ("13:0 Тридекановая" to 1.0),
    "612" to ("14:0 Миристиновая" to 1.0), "652" to ("15:0 Пентадекановая" to 1.0),
    "613" to ("16:0 Пальмитиновая" to 1.0), "653" to ("17:0 Маргариновая" to 1.0),
    "614" to ("18:0 Стеариновая" to 1.0), "615" to ("20:0 Арахиновая" to 1.0), "624" to ("22:0 Бегеновая" to 1.0),
    "654" to ("24:0 Лигноцериновая" to 1.0),
    "625" to ("14:1 Миристолеиновая" to 1.0), "697" to ("15:1 Пентадеценовая" to 1.0),
    "626" to ("16:1 Пальмитолеиновая (ud)" to 1.0), "673" to ("16:1 Пальмитолеиновая, цис" to 1.0),
    "687" to ("17:1 Гептадеценовая" to 1.0), "617" to ("18:1 Олеиновая (ud)" to 1.0),
    "674" to ("18:1 Олеиновая, омега-9, цис" to 1.0), "628" to ("20:1 Гадолеиновая (омега-9)" to 1.0),
    "630" to ("22:1 Эруковая (ud)" to 1.0), "676" to ("22:1 цис" to 1.0), "671" to ("24:1 Нервоновая, цис (омега-9)" to 1.0),
    "618" to ("18:2 Линолевая (ud)" to 1.0), "675" to ("18:2 Омега-6, цис, цис" to 1.0),
    "670" to ("18:2 Конъюгированная линолевая кислота" to 1.0), "666" to ("18:2 прочие изомеры" to 1.0),
    "619" to ("18:3 Линоленовая (ud)" to 1.0), "851" to ("18:3 Омега-3, альфа-линоленовая" to 1.0),
    "685" to ("18:3 Омега-6, гамма-линоленовая" to 1.0), "856" to ("18:3 прочие изомеры" to 1.0),
    "627" to ("18:4 Омега-3, стиоридовая" to 1.0), "672" to ("20:2 Омега-6, эйкозадиеновая" to 1.0),
    "689" to ("20:3 Эйкозатриеновая (ud)" to 1.0), "852" to ("20:3 Омега-3, эйкозатриеновая" to 1.0),
    "620" to ("20:4 Арахидоновая (ud)" to 1.0), "853" to ("20:4 Омега-6, арахидоновая" to 1.0),
    "629" to ("20:5 Омега-3 , эйкозапентаеновая (ЭПК)" to 1.0), "857" to ("21:5 Омега-3, генэйкозапентаеновая" to 1.0),
    "858" to ("22:4 Омега-6, докозатетраеновая" to 1.0), "631" to ("22:5 Омега-3, докозапентаеновая (ДПК)" to 1.0),
    "621" to ("22:6 Омега-3, докозагексаеновая (ДГК)," to 1.0),
    "662" to ("16:1 транс" to 1.0), "663" to ("18:1 Элаидиновая, транс" to 1.0),
    "664" to ("22:1 Брассидовая, транс" to 1.0), "665" to ("18:2 транс-изомер, не определён" to 1.0),
    "669" to ("18:2 транс, транс" to 1.0),
)

/** Sums the site computed itself: digestible carbs (total minus fiber) and omega-3/-6 groups. */
private fun derived(v: Map<String, Double>): Map<String, Double> {
    fun sum(vararg nbr: String) = nbr.mapNotNull { v[it] }.takeIf { it.isNotEmpty() }?.sum()
    return buildMap {
        v["205"]?.let { put("Углеводы", maxOf(0.0, it - (v["291"] ?: 0.0))) }
        sum("619", "627", "629", "631", "621")?.let { put("Омега-3 жирные кислоты", it) }
        sum("618", "620")?.let { put("Омега-6 жирные кислоты", it) }
    }
}

/** USDA food group → the app's category (health-diet.ru ids), refined by name where a group spans several. */
private fun category(group: Int, name: String): Int? {
    val n = name.lowercase()
    return when (group) {
        1 -> if (n.startsWith("egg")) 24520 else 24519
        2 -> 24514
        3 -> 24526
        4 -> 24515
        5 -> 24501
        6 -> if ("sauce" in n || "gravy" in n) 24514 else 24506
        7 -> 24504
        8, 20 -> 24522
        9 -> if ("juice" in n || "nectar" in n) 24511 else 24516
        10 -> 24503
        11 -> if (n.startsWith("mushroom")) 24513 else 24512
        12 -> 24517
        13 -> 24502
        14 -> 24509
        15 -> 24518
        16 -> 24523
        17 -> if (n.startsWith("veal")) 24502 else 24507
        18 -> 24524
        19 -> 24525
        21 -> 24529
        22 -> 24506
        23 -> 24528
        25 -> 24527
        28 -> 24508
        else -> null // American Indian/Alaska Native foods and QC materials stay out
    }
}

fun main() {
    if (!zipFile.exists()) {
        zipFile.parentFile.mkdirs()
        println("Downloading SR Legacy…")
        URI(DOWNLOAD).toURL().openStream().use { input -> zipFile.outputStream().use { input.copyTo(it) } }
    }
    ZipFile(zipFile).use { zip ->
        fun rows(name: String): Sequence<Map<String, String>> = sequence {
            val entry = zip.entries().asSequence().first { it.name.endsWith("/$name") }
            zip.getInputStream(entry).bufferedReader().useLines { lines ->
                val it = lines.iterator()
                val header = parseCsv(it.next())
                while (it.hasNext()) yield(header.zip(parseCsv(it.next())).toMap())
            }
        }
        val nutrientNumber = rows("nutrient.csv").associate { it.getValue("id") to it.getValue("nutrient_nbr") }
        val ndb = rows("sr_legacy_food.csv").associate { it.getValue("fdc_id") to it.getValue("NDB_number").toInt() }
        val values = HashMap<String, HashMap<String, Double>>()
        rows("food_nutrient.csv").forEach { r ->
            val nbr = nutrientNumber[r.getValue("nutrient_id")] ?: return@forEach
            values.getOrPut(r.getValue("fdc_id")) { HashMap() }[nbr] = r.getValue("amount").toDoubleOrNull() ?: return@forEach
        }

        // Nutrient definitions (with daily norms) and categories come from the health-diet.ru asset, so both
        // assets describe nutrients identically whichever the app imports first.
        val hdLines = GZIPInputStream(healthDietAsset.inputStream()).bufferedReader().readLines()
        val defLines = hdLines.filter { it.startsWith("N\t") }
        val index = defLines.associate { line -> line.split('\t').let { it[2] to it[1].toInt() } }

        var written = 0
        var skipped = 0
        val unmapped = HashSet<String>()
        GZIPOutputStream(assetFile.outputStream()).bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("V\t${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))}\n")
            hdLines.filter { it.startsWith("C\t") }.forEach { w.write(it); w.write("\n") }
            defLines.forEach { w.write(it); w.write("\n") }
            rows("food.csv").forEach { food ->
                val fdc = food.getValue("fdc_id")
                val name = food.getValue("description")
                val cat = category(food.getValue("food_category_id").toInt(), name)
                val v = values[fdc]
                if (cat == null || v == null || "208" !in v) { skipped++; return@forEach }
                val named = LinkedHashMap<String, Double>()
                v.forEach { (nbr, amount) ->
                    val (hdName, factor) = NUTRIENTS[nbr] ?: return@forEach
                    named[hdName] = amount * factor
                }
                named += derived(v)
                // Like the site, zeros are left out: they read as "none", the same as not listed.
                val encoded = named.filterValues { it > 0 }.mapNotNull { (n, a) ->
                    val i = index[n] ?: run { unmapped += n; return@mapNotNull null }
                    "$i:${"%.6g".format(java.util.Locale.ROOT, a).trimEnd('0').trimEnd('.')}"
                }
                w.write("F\t${USDA_BASE + ndb.getValue(fdc)}\t$cat\t$name\t${encoded.joinToString(" ")}\n")
                written++
            }
        }
        if (unmapped.isNotEmpty()) println("Nutrients missing from the health-diet definitions: $unmapped")
        println("Wrote ${assetFile.path}: $written foods ($skipped skipped), ${assetFile.length() / 1024} KB")
    }
}

/** One CSV line in USDA's format: every field quoted, quotes doubled inside fields. */
private fun parseCsv(line: String): List<String> {
    val fields = ArrayList<String>()
    val sb = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            quoted && c == '"' && line.getOrNull(i + 1) == '"' -> { sb.append('"'); i++ }
            c == '"' -> quoted = !quoted
            c == ',' && !quoted -> { fields += sb.toString(); sb.clear() }
            else -> sb.append(c)
        }
        i++
    }
    fields += sb.toString()
    return fields
}
