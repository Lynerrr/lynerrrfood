package com.lynerrr.food.data

import androidx.annotation.StringRes
import com.lynerrr.food.R

/** Categories for packaged products. Ids match the products builder (:scraper runProducts). */
enum class ProductCategory(val id: Int, val nameRu: String, @StringRes val title: Int, val sticker: String) {
    DRINKS(9001, "Газировка и напитки", R.string.pcat_drinks, "🥤"),
    JUICES(9002, "Соки и нектары", R.string.pcat_juices, "🧃"),
    SWEETS(9003, "Шоколад и конфеты", R.string.pcat_sweets, "🍫"),
    BISCUITS(9004, "Печенье и выпечка", R.string.pcat_biscuits, "🍪"),
    SNACKS(9005, "Чипсы, орехи и снеки", R.string.pcat_snacks, "🍟"),
    DAIRY(9006, "Молочные продукты (бренды)", R.string.pcat_dairy, "🥛"),
    ICE_CREAM(9007, "Мороженое", R.string.pcat_ice_cream, "🍦"),
    CEREALS(9008, "Хлопья и батончики", R.string.pcat_cereals, "🥣"),
    SAUCES(9009, "Соусы и пасты", R.string.pcat_sauces, "🥫"),
    MEALS(9010, "Лапша и готовые блюда", R.string.pcat_meals, "🍜"),
    MEAT_FISH(9011, "Мясо и рыба (бренды)", R.string.pcat_meat_fish, "🌭"),
    BREAD(9012, "Хлеб (бренды)", R.string.pcat_bread, "🍞"),
    ALCOHOL(9013, "Алкоголь (бренды)", R.string.pcat_alcohol, "🍺"),
    OTHER(9099, "Прочие продукты", R.string.pcat_other, "📦");

    companion object {
        private val byId = entries.associateBy { it.id }
        fun of(id: Int?): ProductCategory? = id?.let { byId[it] }
    }
}
