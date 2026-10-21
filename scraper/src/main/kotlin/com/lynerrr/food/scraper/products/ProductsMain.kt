package com.lynerrr.food.scraper.products

import com.lynerrr.food.net.OffProduct
import com.lynerrr.food.net.OpenFoodFacts
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPOutputStream

/*
 * Builds the packaged-products database (Pepsi, Oreo, ...) from Open Food Facts, separate from the
 * health-diet.ru data. Products are the most scanned ones in the chosen countries.
 *
 * Output (publishable, ODbL): products/products.dat (gzipped TSV) and products/products.json (manifest),
 * plus a copy of the data in the app's assets.
 *
 * Run: ./gradlew :scraper:runProducts
 */

/**
 * Where products come from. [Popularity] uses Open Food Facts' per-country "most scanned" lists via the classic
 * search API, which is often overloaded (HTTP 503), so it's best effort. [Search] uses the newer search service
 * (search.openfoodfacts.org) with a query, sorted by scans.
 */
private sealed class Source(val key: String, val maxPages: Int) {
    /** Popularity lists carry no country data, and some contain products from far away (e.g. Moroccan dairy). */
    class Popularity(val tag: String, maxPages: Int = 10) : Source(tag, maxPages)
    /** Search queries below all filter by country, so their products are known to be sold here. */
    class Search(key: String, val query: String, maxPages: Int) : Source(key, maxPages)
}

private val SOURCES = listOf(
    Source.Popularity("top-5000-ru-scans-2025"),
    Source.Popularity("top-1000-ru-scans-2024"),
    // Products sold in Russia with Russian labels: local brands (Простоквашино, Доширак...).
    Source.Search("ru-local", "countries_tags:\"en:russia\" AND lang:\"ru\"", maxPages = 10),
    Source.Search("kz", "countries_tags:\"en:kazakhstan\"", maxPages = 10),
    // International brands sold in Russia (Coca-Cola, Nutella...).
    Source.Search("ru-all", "countries_tags:\"en:russia\"", maxPages = 5),
)

private const val CLASSIC_API = "https://world.openfoodfacts.org/api/v2/search"
private const val SEARCH_API = "https://search.openfoodfacts.org/search"
private const val PAGE_SIZE = 100

/** Open Food Facts allows ~10 search requests per minute; stay well under it. */
private const val SEARCH_INTERVAL_MS = 10_000L

private val outDir = File("products")
private val dataFile = File(outDir, "products.dat")
private val manifestFile = File(outDir, "products.json")
private val assetFile = File("app/src/main/assets/products.dat")
private val cacheDir = File("scraper/out/off-cache")

private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()
private var lastRequest = 0L

fun main() {
    cacheDir.mkdirs()
    val products = LinkedHashMap<String, OffProduct>()
    var seen = 0
    var rejected = 0
    for (source in SOURCES) {
        var page = 1
        var pages = 1
        while (page <= pages) {
            val json = fetchPage(source, page)
            if (json == null) {
                println("${source.key}: giving up on page $page, continuing with what we have")
                break
            }
            pages = minOf(source.maxPages, (json.optInt("count") + PAGE_SIZE - 1) / PAGE_SIZE)
            // The classic API returns "products", the search service "hits".
            val items = json.optJSONArray("products") ?: json.getJSONArray("hits")
            for (i in 0 until items.length()) {
                seen++
                val p = OpenFoodFacts.parse(items.getJSONObject(i), countryFiltered = source is Source.Search)
                if (p == null) rejected++ else products.merge(p.code, p) { a, b ->
                    (if (a.scans >= b.scans) a else b).copy(local = a.local || b.local)
                }
            }
            println("${source.key} page $page/$pages · ${products.size} products kept")
            page++
        }
    }

    val foreign = products.values.count { !it.local }
    // The same product often exists under several barcodes (sizes, regions); keep the most scanned.
    val deduped = products.values
        .filter { it.local }
        .groupBy { "${it.brand.lowercase()}|${it.name.lowercase()}" }
        .values.map { group -> group.maxBy { it.scans } }
        .sortedByDescending { it.scans }
    write(deduped)
    println("Seen $seen, rejected $rejected for missing or implausible data, $foreign not sold here, " +
        "${products.size - foreign - deduped.size} duplicates; wrote ${deduped.size} products")
}

/**
 * One search page, cached on disk so re-runs (e.g. after changing filters) don't hit the API again.
 * Returns null when the service keeps failing.
 */
private fun fetchPage(source: Source, page: Int): JSONObject? {
    val cache = File(cacheDir, "${source.key}-$page.json")
    if (cache.exists()) return JSONObject(cache.readText())
    fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)
    val url = when (source) {
        is Source.Popularity -> "$CLASSIC_API?popularity_tags=${enc(source.tag)}&page=$page&page_size=$PAGE_SIZE" +
            "&sort_by=unique_scans_n&fields=${OpenFoodFacts.FIELDS}"
        is Source.Search -> "$SEARCH_API?q=${enc(source.query)}&page=$page&page_size=$PAGE_SIZE" +
            "&sort_by=-unique_scans_n&fields=${OpenFoodFacts.FIELDS}"
    }
    var attempt = 1
    while (true) {
        val wait = lastRequest + SEARCH_INTERVAL_MS - System.currentTimeMillis()
        if (wait > 0) Thread.sleep(wait)
        lastRequest = System.currentTimeMillis()
        val response = try {
            http.send(
                HttpRequest.newBuilder(URI(url)).header("User-Agent", OpenFoodFacts.USER_AGENT).timeout(Duration.ofSeconds(60)).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        } catch (e: java.io.IOException) {
            null
        }
        val body = response?.body()
        if (response?.statusCode() == 200 && body != null && body.trimStart().startsWith("{")) {
            cache.writeText(body)
            return JSONObject(body)
        }
        if (attempt >= 4) return null
        println("  HTTP ${response?.statusCode()} for ${source.key} page $page, retrying in ${attempt * 30}s")
        Thread.sleep(attempt * 30_000L)
        attempt++
    }
}

/**
 * Format (UTF-8 TSV, gzipped):
 *   V  version
 *   L  attribution / license
 *   N  index  siteName  unit
 *   P  barcode  categoryId  scans  brand  name  servingGrams  index:amount ...
 */
private fun write(products: List<OffProduct>) {
    val version = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
    val names = products.flatMap { it.values.keys }.distinct()
    val index = names.withIndex().associate { (i, name) -> name to i }
    fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

    outDir.mkdirs()
    GZIPOutputStream(dataFile.outputStream()).bufferedWriter(Charsets.UTF_8).use { w ->
        w.write("V\t$version\n")
        w.write("L\tData from Open Food Facts (openfoodfacts.org), available under the Open Database License (ODbL)\n")
        names.forEachIndexed { i, name -> w.write("N\t$i\t$name\t${OpenFoodFacts.unitOf(name)}\n") }
        products.forEach { p ->
            val values = p.values.entries.joinToString(" ") { (name, amount) -> "${index.getValue(name)}:${"%.4g".format(java.util.Locale.ROOT, amount)}" }
            w.write("P\t${p.code}\t${p.category}\t${p.scans}\t${clean(p.brand)}\t${clean(p.name)}\t${p.servingGrams ?: ""}\t$values\n")
        }
    }
    manifestFile.writeText(
        JSONObject()
            .put("version", version)
            .put("products", products.size)
            .put("data", dataFile.name)
            .put("source", "Open Food Facts: ${SOURCES.joinToString { it.key }}")
            .put("license", "ODbL 1.0 (https://opendatacommons.org/licenses/odbl/1-0/)")
            .toString(2) + "\n"
    )
    assetFile.parentFile.mkdirs()
    dataFile.copyTo(assetFile, overwrite = true)
    println("${dataFile.path}: ${dataFile.length() / 1024} KB, version $version")
}
