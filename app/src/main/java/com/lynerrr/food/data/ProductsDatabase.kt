package com.lynerrr.food.data

import android.content.Context
import androidx.room.withTransaction
import com.lynerrr.food.net.HealthDietParser
import com.lynerrr.food.net.OffProduct
import com.lynerrr.food.net.OpenFoodFacts
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * The packaged-products database (Open Food Facts), kept apart from health-diet.ru data: its own id range,
 * version and update source. A copy ships in assets; newer versions can be downloaded from a manifest URL
 * (e.g. a GitHub raw link to products/products.json).
 */
class ProductsDatabase(private val context: Context, private val db: AppDatabase) {
    private val prefs = context.getSharedPreferences("products_db", Context.MODE_PRIVATE)

    /** Version of the products currently in the database, e.g. "20261020123000". */
    val version: String? get() = prefs.getString(KEY_VERSION, null)

    private fun bundledVersion(): String? = try {
        openBundled().use { it.readLine()?.removePrefix("V\t") }
    } catch (e: java.io.FileNotFoundException) {
        null
    }

    private fun openBundled() = GZIPInputStream(context.assets.open(ASSET)).bufferedReader(Charsets.UTF_8)

    /** Versions are timestamps, so string order is time order. */
    private fun isNewer(candidate: String?) = candidate != null && (version == null || candidate > version!!)

    fun bundledNeedsImport() = isNewer(bundledVersion())

    suspend fun importBundled(onProgress: (Int, Int) -> Unit): SyncResult =
        import({ context.assets.open(ASSET) }, onProgress)

    /** Downloads and imports the published database if it's newer than what's installed. */
    suspend fun update(manifestUrl: String, onProgress: (Int, Int) -> Unit): SyncResult {
        val manifest = JSONObject(download(URL(manifestUrl)).bufferedReader().use { it.readText() })
        val remoteVersion = manifest.getString("version")
        if (!isNewer(remoteVersion)) return SyncResult.ProductsUpToDate
        // The data file is next to the manifest unless the manifest gives a full URL.
        val dataUrl = URL(URL(manifestUrl), manifest.optString("data", "products.dat"))
        val bytes = download(dataUrl).use { it.readBytes() }
        return import({ bytes.inputStream() }, onProgress)
    }

    private fun download(url: URL): InputStream {
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        if (connection.responseCode != 200) error("HTTP ${connection.responseCode} for $url")
        return connection.inputStream
    }

    private suspend fun import(open: () -> InputStream, onProgress: (Int, Int) -> Unit): SyncResult {
        val total = GZIPInputStream(open()).bufferedReader(Charsets.UTF_8).use { r -> r.lineSequence().count { it.startsWith("P\t") } }
        val foods = db.foodDao()
        foods.upsertCategories(ProductCategory.entries.map { CategoryEntity(it.id, it.nameRu) })

        var nextId = maxOf(foods.maxFoodId() + 1, FoodIds.PRODUCT_BASE)
        val names = ArrayList<String>()
        val units = ArrayList<String>()
        var version = ""
        var done = 0
        val now = System.currentTimeMillis()
        val batch = ArrayList<List<String>>(BATCH)

        suspend fun flush() {
            db.withTransaction {
                batch.forEach { f ->
                    val barcode = f[1]
                    // Existing products keep their id, so diary entries stay attached across updates.
                    val id = foods.idForBarcode(barcode) ?: nextId++
                    val values = f.getOrNull(7).orEmpty().split(' ').filter { it.isNotEmpty() }.map { v ->
                        val (i, amount) = v.split(':')
                        FoodNutrientEntity(id, names[i.toInt()], amount.toDouble(), units[i.toInt()])
                    }
                    store(foods, id, barcode, f[2].toInt(), f[4].ifBlank { null }, f[5], f[6].toDoubleOrNull(), values, now)
                }
            }
            done += batch.size
            batch.clear()
            onProgress(done, total)
        }

        GZIPInputStream(open()).bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence().forEach { line ->
                val f = line.split('\t')
                when (f[0]) {
                    "V" -> version = f[1]
                    "N" -> {
                        names += f[2]
                        units += f[3]
                        val info = NutrientCatalog.info(f[2])
                        // Ignored when health-diet data already defined the nutrient (with its norms).
                        foods.insertNutrientDefs(listOf(NutrientEntity(f[2], info.group.siteSection, info.order, f[3], null, null, null)))
                    }
                    "P" -> {
                        batch += f
                        if (batch.size == BATCH) flush()
                    }
                }
            }
        }
        if (batch.isNotEmpty()) flush()
        prefs.edit().putString(KEY_VERSION, version).apply()
        return SyncResult.ProductsImported(total, version)
    }

    private suspend fun store(
        foods: FoodDao,
        id: Int,
        barcode: String,
        category: Int,
        brand: String?,
        name: String,
        servingGrams: Double?,
        values: List<FoodNutrientEntity>,
        now: Long,
    ) {
        val kcal = values.firstOrNull { it.name == HealthDietParser.KCAL }?.amount
        foods.upsertFood(id, name, category, kcal)
        foods.setProductInfo(id, brand, barcode, servingGrams)
        foods.deleteNutrients(id)
        foods.insertNutrients(values)
        foods.markFetched(id, now)
    }

    /**
     * The food with this barcode, if any (a product or a custom food). A UPC-A code (12 digits) is the same
     * product as its EAN-13 form with a leading 0, so both spellings are tried.
     */
    suspend fun findByBarcode(code: String): Int? {
        val foods = db.foodDao()
        val variants = listOf(code, "0$code", code.removePrefix("0")).distinct()
        return variants.firstNotNullOfOrNull { foods.idForBarcode(it) }
    }

    /** Saves a product looked up online (barcode scan); returns its food id. */
    suspend fun addProduct(product: OffProduct): Int = db.withTransaction {
        val foods = db.foodDao()
        foods.upsertCategories(ProductCategory.entries.map { CategoryEntity(it.id, it.nameRu) })
        val id = foods.idForBarcode(product.code) ?: maxOf(foods.maxFoodId() + 1, FoodIds.PRODUCT_BASE)
        val values = product.values.map { (name, amount) ->
            val unit = OpenFoodFacts.unitOf(name)
            val info = NutrientCatalog.info(name)
            foods.insertNutrientDefs(listOf(NutrientEntity(name, info.group.siteSection, info.order, unit, null, null, null)))
            FoodNutrientEntity(id, name, amount, unit)
        }
        store(foods, id, product.code, product.category, product.brand.ifBlank { null }, product.name,
            product.servingGrams, values, System.currentTimeMillis())
        id
    }

    private companion object {
        // Gzipped TSV, deliberately not named .gz (the build would decompress and rename it).
        const val ASSET = "products.dat"
        const val KEY_VERSION = "version"
        const val BATCH = 250
    }
}
