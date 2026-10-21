package com.lynerrr.food.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM categories ORDER BY id < 0 DESC, name")
    fun categories(): Flow<List<CategoryEntity>>

    @Upsert
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFoodIgnore(food: FoodEntity): Long

    @Query(
        """UPDATE foods SET name = :name, nameLower = :nameLower,
           categoryId = COALESCE(:categoryId, categoryId), kcal = COALESCE(:kcal, kcal)
           WHERE id = :id"""
    )
    suspend fun updateFoodInfo(id: Int, name: String, nameLower: String, categoryId: Int?, kcal: Double?)

    /** Inserts or updates catalog info without touching cached details (REPLACE would cascade-delete them). */
    @Transaction
    suspend fun upsertFood(id: Int, name: String, categoryId: Int?, kcal: Double?) {
        val lower = name.lowercase()
        if (insertFoodIgnore(FoodEntity(id, name, lower, categoryId, kcal, null)) == -1L) {
            updateFoodInfo(id, name, lower, categoryId, kcal)
        }
    }

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun food(id: Int): FoodEntity?

    @Query("SELECT * FROM foods WHERE id = :id")
    fun foodFlow(id: Int): Flow<FoodEntity?>

    @Query("SELECT * FROM foods")
    fun allFoods(): Flow<List<FoodEntity>>

    @Query("SELECT COALESCE(MIN(id), 0) FROM foods")
    suspend fun minFoodId(): Int

    @Query("SELECT COALESCE(MAX(id), 0) FROM foods")
    suspend fun maxFoodId(): Int

    @Query("SELECT id FROM foods WHERE barcode = :barcode LIMIT 1")
    suspend fun idForBarcode(barcode: String): Int?

    @Query("UPDATE foods SET brand = :brand, barcode = :barcode, servingGrams = :servingGrams WHERE id = :id")
    suspend fun setProductInfo(id: Int, brand: String?, barcode: String, servingGrams: Double?)

    /** Packaged products (ids from FoodIds.PRODUCT_BASE). */
    @Query("SELECT COUNT(*) FROM foods WHERE id >= 1000000000")
    fun productCount(): Flow<Int>

    @Query("UPDATE foods SET emoji = :emoji WHERE id = :id")
    suspend fun setEmoji(id: Int, emoji: String?)

    /** Also removes the food's nutrients and diary entries (cascade). */
    @Query("DELETE FROM foods WHERE id = :id")
    suspend fun deleteFood(id: Int)

    @Query("SELECT * FROM food_nutrients WHERE foodId = :foodId")
    suspend fun nutrients(foodId: Int): List<FoodNutrientEntity>

    /** Every food containing [nutrient], for ranking in Kotlin (a few thousand rows at most). */
    @Query(
        """SELECT f.id, f.name, f.categoryId, f.emoji, n.amount, n.unit, k.amount AS kcal
           FROM food_nutrients n
           JOIN foods f ON f.id = n.foodId
           LEFT JOIN food_nutrients k ON k.foodId = n.foodId AND k.name = 'Калорийность'
           WHERE n.name = :nutrient AND n.amount > 0"""
    )
    fun withNutrient(nutrient: String): Flow<List<RankedFood>>

    @Query("SELECT COUNT(*) FROM foods WHERE id > 0 AND id < 1000000000")
    fun foodCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM foods WHERE detailsFetchedAt IS NOT NULL AND id > 0 AND id < 1000000000")
    fun detailedCount(): Flow<Int>

    @Query("UPDATE foods SET detailsFetchedAt = :time WHERE id = :id")
    suspend fun markFetched(id: Int, time: Long)

    @Query("DELETE FROM food_nutrients WHERE foodId = :foodId")
    suspend fun deleteNutrients(foodId: Int)

    @Insert
    suspend fun insertNutrients(nutrients: List<FoodNutrientEntity>)

    @Query("SELECT * FROM food_nutrients WHERE foodId = :foodId")
    fun nutrientsFlow(foodId: Int): Flow<List<FoodNutrientEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNutrientDefs(defs: List<NutrientEntity>)

    @Query("SELECT * FROM nutrients ORDER BY position")
    fun nutrientDefs(): Flow<List<NutrientEntity>>

    @Query("UPDATE nutrients SET userNorm = :value WHERE name = :name")
    suspend fun setUserNorm(name: String, value: Double?)

    @Query("UPDATE nutrients SET userNorm = NULL WHERE name IN (:names)")
    suspend fun clearUserNorms(names: List<String>)
}

@Dao
interface DiaryDao {
    @Insert
    suspend fun insert(entry: DiaryEntryEntity): Long

    @Query("UPDATE diary SET grams = :grams, meal = :meal WHERE id = :id")
    suspend fun update(id: Long, grams: Double, meal: Int)

    @Query("DELETE FROM diary WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """SELECT d.id, d.epochDay, d.meal, d.foodId, d.grams, f.name AS foodName, f.categoryId, f.emoji,
             (SELECT amount FROM food_nutrients n WHERE n.foodId = d.foodId AND n.name = 'Калорийность') AS kcal,
             (SELECT amount FROM food_nutrients n WHERE n.foodId = d.foodId AND n.name = 'Белки') AS protein,
             (SELECT amount FROM food_nutrients n WHERE n.foodId = d.foodId AND n.name = 'Жиры') AS fat,
             (SELECT amount FROM food_nutrients n WHERE n.foodId = d.foodId AND n.name = 'Углеводы') AS carbs
           FROM diary d JOIN foods f ON f.id = d.foodId
           WHERE d.epochDay = :day
           ORDER BY d.meal, d.createdAt"""
    )
    fun itemsForDay(day: Long): Flow<List<DiaryItem>>

    @Query(
        """SELECT n.name, n.unit, SUM(n.amount * d.grams / 100.0) AS amount
           FROM diary d JOIN food_nutrients n ON n.foodId = d.foodId
           WHERE d.epochDay = :day
           GROUP BY n.name, n.unit"""
    )
    fun intakeForDay(day: Long): Flow<List<NutrientAmount>>

    @Query(
        """SELECT n.name AS nutrient, d.foodId, f.name AS foodName, SUM(d.grams) AS grams, n.unit,
             SUM(n.amount * d.grams / 100.0) AS amount
           FROM diary d JOIN foods f ON f.id = d.foodId JOIN food_nutrients n ON n.foodId = d.foodId
           WHERE d.epochDay = :day
           GROUP BY n.name, d.foodId, n.unit"""
    )
    fun contributionsForDay(day: Long): Flow<List<Contribution>>

    @Query(
        """SELECT d.epochDay AS day, SUM(n.amount * d.grams / 100.0) AS kcal
           FROM diary d JOIN food_nutrients n ON n.foodId = d.foodId AND n.name = 'Калорийность'
           GROUP BY d.epochDay"""
    )
    fun dailyKcal(): Flow<List<DayKcal>>
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM weights ORDER BY epochDay")
    fun weights(): Flow<List<WeightEntity>>

    @Upsert
    suspend fun upsertWeight(weight: WeightEntity)

    @Query("DELETE FROM weights WHERE epochDay = :day")
    suspend fun deleteWeight(day: Long)

    @Query("SELECT * FROM profile WHERE id = 1")
    fun profile(): Flow<ProfileEntity?>

    @Upsert
    suspend fun upsertProfile(profile: ProfileEntity)
}
