package com.lynerrr.food.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: Int,
    val name: String,
)

/** Food id ranges: custom foods are negative, health-diet.ru foods small, packaged products from [PRODUCT_BASE]. */
object FoodIds {
    const val PRODUCT_BASE = 1_000_000_000
    fun isProduct(id: Int) = id >= PRODUCT_BASE
}

/**
 * A food from health-diet.ru, a packaged product (Open Food Facts), or one the user created (negative [id]).
 * Nutrients are only present once [detailsFetchedAt] is set.
 */
@Entity(tableName = "foods", indices = [Index("nameLower"), Index("categoryId"), Index("barcode")])
data class FoodEntity(
    @PrimaryKey val id: Int,
    val name: String,
    /** Lowercased name; SQLite's LIKE only case-folds ASCII, so Cyrillic search needs this. */
    val nameLower: String,
    val categoryId: Int?,
    val kcal: Double?,
    val detailsFetchedAt: Long?,
    /** Sticker chosen for a custom food; site foods use their category's. */
    val emoji: String? = null,
    /** Packaged products only. */
    val brand: String? = null,
    val barcode: String? = null,
    /** Typical serving in grams (a can, three cookies), when the product states one. */
    val servingGrams: Double? = null,
) {
    val isCustom get() = id < 0
}

/** Amount of one nutrient per 100 g of a food. */
@Entity(
    tableName = "food_nutrients",
    primaryKeys = ["foodId", "name"],
    foreignKeys = [
        ForeignKey(FoodEntity::class, ["id"], ["foodId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class FoodNutrientEntity(
    val foodId: Int,
    val name: String,
    val amount: Double,
    val unit: String,
)

/** Every nutrient seen on the site, with its section, display order and daily norm. */
@Entity(tableName = "nutrients")
data class NutrientEntity(
    @PrimaryKey val name: String,
    val section: String,
    val position: Int,
    val unit: String,
    val normLow: Double?,
    val normHigh: Double?,
    /** Overrides the site's norm when set by the user. */
    val userNorm: Double?,
)

@Entity(
    tableName = "diary",
    indices = [Index("epochDay"), Index("foodId")],
    foreignKeys = [
        ForeignKey(FoodEntity::class, ["id"], ["foodId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val meal: Int,
    val foodId: Int,
    val grams: Double,
    val createdAt: Long,
)

/** A diary entry joined with its food; nutrient amounts are per 100 g. */
data class DiaryItem(
    val id: Long,
    val epochDay: Long,
    val meal: Int,
    val foodId: Int,
    val grams: Double,
    val foodName: String,
    val categoryId: Int?,
    val emoji: String?,
    val kcal: Double?,
    val protein: Double?,
    val fat: Double?,
    val carbs: Double?,
)

/** Total amount of a nutrient eaten in a day, in [unit]. */
data class NutrientAmount(
    val name: String,
    val unit: String,
    val amount: Double,
)

/** How much of a nutrient one food provided on a day (all its diary entries combined). */
data class Contribution(
    val nutrient: String,
    val foodId: Int,
    val foodName: String,
    val grams: Double,
    val unit: String,
    val amount: Double,
)

@Entity(tableName = "weights")
data class WeightEntity(
    @PrimaryKey val epochDay: Long,
    val kg: Double,
)

/** Settings for the adaptive calorie plan; a single row with id 1. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val male: Boolean,
    val age: Int,
    val heightCm: Double,
    /** Index into [EnergyModel.ACTIVITY]. */
    val activity: Int,
    /** Negative to lose weight. */
    val goalKgPerWeek: Double,
    val proteinPerKg: Double,
    val fatPercent: Double,
    val startDay: Long,
)

data class DayKcal(
    val day: Long,
    val kcal: Double,
)

/** A food ranked by how much of one nutrient it has per 100 g. */
data class RankedFood(
    val id: Int,
    val name: String,
    val categoryId: Int?,
    val emoji: String?,
    val amount: Double,
    val unit: String,
    val kcal: Double?,
)
