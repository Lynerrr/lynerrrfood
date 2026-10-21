package com.lynerrr.food.scraper

import com.lynerrr.food.net.HealthDietClient
import com.lynerrr.food.net.HealthDietParser
import com.lynerrr.food.net.ParsedNutrient
import com.lynerrr.food.net.SiteCatalog
import kotlinx.coroutines.delay
import org.jsoup.HttpStatusException
import org.jsoup.nodes.Document
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPOutputStream

/** Pause between requests. The site rate-limits per IP (HTTP 429) after a few thousand quick pages. */
private const val REQUEST_DELAY_MS = 3_000L

/** After a 429, wait this long (times the attempt number) before trying the same page again. */
private const val RATE_LIMIT_PAUSE_MS = 60_000L
private const val MAX_RATE_LIMIT_RETRIES = 5

private val outDir = File("scraper/out")
private val catalogFile = File(outDir, "catalog.tsv")
private val foodsFile = File(outDir, "foods.tsv")
private val failedFile = File(outDir, "failed.txt")
private val assetFile = File("app/src/main/assets/fooddb.dat")

// Separators inside a food line: nutrients by RS, fields by US (never appear in the site's text).
private const val RS = '\u001E'
private const val US = '\u001F'

/** Thrown when the site keeps rate-limiting; progress so far is kept and the run can resume later. */
private class RateLimited : Exception()

/**
 * Args: none = everything (everyday foods first), "common" = everyday foods only, "build" = only rebuild the asset,
 * "saved <dir> [check]" = add food pages saved from a browser (Ctrl+S) in <dir>; "check" only reports.
 */
fun main(args: Array<String>) = runBlocking {
    outDir.mkdirs()
    when (val mode = args.firstOrNull()) {
        "build" -> Unit
        "saved" -> if (!importSaved(File(args[1]), dryRun = args.getOrNull(2) == "check")) return@runBlocking
        else -> scrape(commonOnly = mode == "common")
    }
    buildAsset()
}

/**
 * Reads food pages a person saved from their browser; each page names its food in its canonical link. Prints how many
 * nutrients each one has next to the median of already scraped foods in its category, to spot incomplete pages.
 * Returns whether anything was added.
 */
private fun importSaved(dir: File, dryRun: Boolean): Boolean {
    val catalog = catalogFile.readLines().filter { it.isNotBlank() }.associate { line ->
        val (id, cat, name) = line.split('\t')
        id.toInt() to (cat.toInt() to name)
    }
    val scraped = foodsFile.readLines().filter { it.split('\t').size == 4 }
    val done = scraped.map { it.substringBefore('\t').toInt() }.toHashSet()
    // Typical nutrient count per category, from foods scraped earlier.
    val median = scraped.groupBy({ it.split('\t')[1].toInt() }, { it.substringAfterLast('\t').count { c -> c == RS } + 1 })
        .mapValues { (_, counts) -> counts.sorted()[counts.size / 2] }
    val canonical = Regex("""health-diet\.ru/base_of_food/sostav/(\d+)\.php""")

    var added = 0
    dir.listFiles { f -> f.isFile && f.name.endsWith(".html") }.orEmpty().sortedBy { it.name }.forEach { file ->
        val doc = org.jsoup.Jsoup.parse(file, "UTF-8", "https://health-diet.ru")
        val href = doc.selectFirst("link[rel=canonical]")?.attr("href").orEmpty()
        val id = canonical.find(href)?.groupValues?.get(1)?.toInt() ?: return@forEach // not a food page
        val (cat, catalogName) = catalog[id] ?: return@forEach println("  #$id not in the catalog, skipped")
        val food = try {
            HealthDietParser.parseFood(doc, id)
        } catch (e: Exception) {
            return@forEach println("  #$id $catalogName: no nutrient table (${e.message})")
        }
        val status = if (id in done) "already have" else if (dryRun) "new" else "added"
        println("  #$id ${food.name}: ${food.nutrients.size} nutrients (category median ${median[cat] ?: "?"}), $status")
        if (id in done || dryRun) return@forEach
        foodsFile.appendText("$id\t$cat\t${food.name}\t${food.nutrients.joinToString(RS.toString()) { encode(it) }}\n")
        done += id
        added++
    }
    println("${if (dryRun) "Checked" else "Added"} saved pages; $added new foods, ${catalog.size - done.size} still missing")
    return added > 0
}

private suspend fun scrape(commonOnly: Boolean) {
    val client = HealthDietClient()

    // 1. Catalog: every category page lists all its foods (id, name).
    val catalog = LinkedHashMap<Int, Pair<Int, String>>()
    if (catalogFile.exists()) {
        catalogFile.readLines().forEach { line ->
            val (id, cat, name) = line.split('\t')
            catalog[id.toInt()] = cat.toInt() to name
        }
        println("Catalog: ${catalog.size} foods (cached)")
    } else {
        val first = client.categoryPage(HealthDietClient.DEFAULT_CATEGORY)
        val categories = HealthDietParser.parseCategories(first)
        categories.forEachIndexed { i, category ->
            val page = if (category.id == HealthDietClient.DEFAULT_CATEGORY) first
            else { delay(REQUEST_DELAY_MS); client.categoryPage(category.id) }
            HealthDietParser.parseCategoryFoods(page).forEach { catalog.putIfAbsent(it.id, category.id to it.name) }
            println("Category ${i + 1}/${categories.size}: ${category.name}")
        }
        catalogFile.writeText(catalog.entries.joinToString("") { (id, v) -> "$id\t${v.first}\t${v.second}\n" })
        File(outDir, "categories.tsv").writeText(categories.joinToString("") { "${it.id}\t${it.name}\n" })
    }

    // 2. Food pages, resumable: skip ids already written.
    val done = if (foodsFile.exists()) foodsFile.readLines().map { it.substringBefore('\t').toInt() }.toHashSet() else HashSet()
    // Everyday foods first, so a run cut short by rate limiting still covers what matters.
    val todo = catalog.entries
        .filter { (id, v) -> id !in done && (!commonOnly || isSuggestable(id, v.first, v.second)) }
        .sortedBy { (id, v) -> if (isSuggestable(id, v.first, v.second)) 0 else 1 }
        .map { it.key }
    println("Foods: ${done.size} done, ${todo.size} to go")
    val started = System.currentTimeMillis()
    todo.forEachIndexed { i, id ->
        try {
            val food = HealthDietParser.parseFood(fetchPolitely(client, id), id)
            val (cat, _) = catalog.getValue(id)
            foodsFile.appendText("$id\t$cat\t${food.name}\t${food.nutrients.joinToString(RS.toString()) { encode(it) }}\n")
        } catch (e: RateLimited) {
            println("Still rate limited after ${MAX_RATE_LIMIT_RETRIES} pauses; stopping. Run again later to resume.")
            return
        } catch (e: Exception) {
            failedFile.appendText("$id\t${e.message}\n")
        }
        if ((i + 1) % 50 == 0 || i == todo.lastIndex) {
            val perItem = (System.currentTimeMillis() - started) / (i + 1)
            val left = (todo.size - i - 1) * perItem / 60_000
            println("Foods ${done.size + i + 1}/${catalog.size} · ~$left min left")
        }
        delay(REQUEST_DELAY_MS)
    }
}

private suspend fun fetchPolitely(client: HealthDietClient, id: Int): Document {
    var attempt = 1
    while (true) {
        try {
            return client.foodPage(id)
        } catch (e: HttpStatusException) {
            if (e.statusCode != 429) throw e
            if (attempt > MAX_RATE_LIMIT_RETRIES) throw RateLimited()
            println("Rate limited at food $id, pausing ${RATE_LIMIT_PAUSE_MS * attempt / 1000}s")
            delay(RATE_LIMIT_PAUSE_MS * attempt++)
        }
    }
}

private fun isSuggestable(id: Int, category: Int, name: String) =
    category !in SiteCatalog.NOT_SUGGESTED_CATEGORIES && SiteCatalog.isCommon(id, name)

private fun encode(n: ParsedNutrient) = listOf(
    n.name, n.section, n.position, n.amount, n.unit, n.norm?.low ?: "", n.norm?.high ?: "", n.norm?.unit ?: "",
).joinToString(US.toString())

/**
 * Asset format (UTF-8 TSV, gzipped):
 *   V  version
 *   C  categoryId  name
 *   N  index  name  section  position  unit  normLow  normHigh  normUnit
 *   F  id  categoryId  name  [index:amount[:unit] ...space separated; empty = details not available]
 * Nutrient units are only written per value when they differ from the nutrient's unit.
 */
private fun buildAsset() {
    val categories = File(outDir, "categories.tsv").readLines().map { it.split('\t') }
    val catalog = catalogFile.readLines().filter { it.isNotBlank() }.map { it.split('\t') }
    // A scrape may still be appending, so ignore a half-written last line.
    val scraped = if (foodsFile.exists()) foodsFile.readLines().filter { it.split('\t').size == 4 } else emptyList()

    val defs = LinkedHashMap<String, List<String>>() // name -> fields
    val index = HashMap<String, Int>()
    val foods = LinkedHashMap<Int, String>()

    scraped.forEach { line ->
        val (id, cat, name, nutrients) = line.split('\t', limit = 4)
        val values = nutrients.split(RS).filter { it.isNotEmpty() }.joinToString(" ") { entry ->
            val f = entry.split(US)
            val nutrientName = f[0]
            val i = index.getOrPut(nutrientName) {
                defs[nutrientName] = f
                defs.size - 1
            }
            val defUnit = defs.getValue(nutrientName)[4]
            "$i:${f[3]}" + if (f[4] != defUnit) ":${f[4]}" else ""
        }
        foods[id.toInt()] = "F\t$id\t$cat\t$name\t$values"
    }
    // Catalog foods whose page couldn't be scraped still ship, so they're searchable and load on demand.
    catalog.forEach { (id, cat, name) -> foods.putIfAbsent(id.toInt(), "F\t$id\t$cat\t$name\t") }

    assetFile.parentFile.mkdirs()
    GZIPOutputStream(assetFile.outputStream()).bufferedWriter(Charsets.UTF_8).use { w ->
        // Any new build gets a new version, so the app re-imports it once.
        w.write("V\t${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))}\n")
        categories.forEach { (id, name) -> w.write("C\t$id\t$name\n") }
        defs.values.forEachIndexed { i, f ->
            w.write("N\t$i\t${f[0]}\t${f[1]}\t${f[2]}\t${f[4]}\t${f[5]}\t${f[6]}\t${f[7]}\n")
        }
        foods.values.forEach { w.write(it); w.write("\n") }
    }
    val withDetails = scraped.size
    println("Wrote ${assetFile.path}: ${foods.size} foods ($withDetails with nutrients), ${defs.size} nutrients, " +
        "${assetFile.length() / 1024} KB")
}
