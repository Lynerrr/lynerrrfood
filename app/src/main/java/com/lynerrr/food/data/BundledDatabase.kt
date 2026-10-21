package com.lynerrr.food.data

import android.content.Context
import androidx.room.withTransaction
import com.lynerrr.food.net.HealthDietParser
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.util.zip.GZIPInputStream

/**
 * Imports a food database bundled in assets (built by the :scraper module) into Room: health-diet.ru foods
 * (fooddb.dat) or USDA foods (usda.dat), both in the same format.
 * Only food data is touched: diary, weights, custom foods and the user's own norms stay as they are.
 */
class BundledDatabase(
    private val context: Context,
    private val db: AppDatabase,
    private val asset: String = "fooddb.dat",
    prefsName: String = "bundled_db",
) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private fun open(): BufferedReader? = try {
        GZIPInputStream(context.assets.open(asset)).bufferedReader(Charsets.UTF_8)
    } catch (e: FileNotFoundException) {
        null
    }

    private fun assetVersion(): String? = open()?.use { it.readLine()?.removePrefix("V\t") }

    val importedVersion: String? get() = prefs.getString(KEY_VERSION, null)

    fun needsImport(): Boolean = assetVersion()?.let { it != importedVersion } ?: false

    suspend fun import(onProgress: (done: Int, total: Int) -> Unit): SyncResult {
        val total = open()?.use { r -> r.lineSequence().count { it.startsWith("F\t") } } ?: return SyncResult.Imported(0)
        val foods = db.foodDao()
        val defs = ArrayList<NutrientEntity>()
        var version = ""
        var done = 0
        val fetchedAt = System.currentTimeMillis()
        val batch = ArrayList<List<String>>(BATCH)

        suspend fun flush() {
            db.withTransaction {
                batch.forEach { f -> importFood(foods, f, defs, fetchedAt) }
            }
            done += batch.size
            batch.clear()
            onProgress(done, total)
        }

        open()!!.use { reader ->
            reader.lineSequence().forEach { line ->
                val f = line.split('\t')
                when (f[0]) {
                    "V" -> version = f[1]
                    "C" -> foods.upsertCategories(listOf(CategoryEntity(f[1].toInt(), f[2])))
                    "N" -> {
                        val (name, section, position, unit) = listOf(f[2], f[3], f[4], f[5])
                        val normUnit = f[8]
                        fun norm(v: String) = v.toDoubleOrNull()?.let { Units.convert(it, normUnit, unit) }
                        defs += NutrientEntity(name, section, position.toInt(), unit, norm(f[6]), norm(f[7]), null)
                    }
                    "F" -> {
                        if (batch.isEmpty() && done == 0) foods.insertNutrientDefs(defs)
                        batch += f
                        if (batch.size == BATCH) flush()
                    }
                }
            }
        }
        if (batch.isNotEmpty()) flush()
        prefs.edit().putString(KEY_VERSION, version).apply()
        return SyncResult.Imported(total)
    }

    private suspend fun importFood(foods: FoodDao, f: List<String>, defs: List<NutrientEntity>, fetchedAt: Long) {
        val id = f[1].toInt()
        val values = f.getOrNull(4).orEmpty().split(' ').filter { it.isNotEmpty() }.map { v ->
            val parts = v.split(':')
            val def = defs[parts[0].toInt()]
            FoodNutrientEntity(id, def.name, parts[1].toDouble(), parts.getOrNull(2) ?: def.unit)
        }
        val kcal = values.firstOrNull { it.name == HealthDietParser.KCAL }?.amount
        foods.upsertFood(id, f[3], f[2].toInt(), kcal)
        // Catalog-only entries keep whatever details the app may already have downloaded itself.
        if (values.isEmpty()) return
        foods.deleteNutrients(id)
        foods.insertNutrients(values)
        foods.markFetched(id, fetchedAt)
    }

    private companion object {
        // Assets are gzipped TSV, not named .gz, since the build would decompress and rename them.
        const val KEY_VERSION = "version"
        const val BATCH = 250
    }
}
