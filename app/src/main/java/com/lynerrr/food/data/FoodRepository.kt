package com.lynerrr.food.data

import androidx.room.withTransaction
import com.lynerrr.food.net.HealthDietParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import java.time.LocalDate

class FoodRepository(
    private val db: AppDatabase,
    scope: CoroutineScope,
) {
    private val foods = db.foodDao()
    private val diary = db.diaryDao()
    private val planDao = db.planDao()

    fun weights() = planDao.weights()
    fun profile() = planDao.profile()
    fun dailyKcal() = diary.dailyKcal()

    /** The adaptive plan, recomputed whenever weights, intake or settings change. */
    val plan: Flow<Plan> = combine(planDao.profile(), planDao.weights(), diary.dailyKcal()) { profile, weights, kcal ->
        EnergyModel.compute(profile, weights, kcal.associate { it.day to it.kcal }, LocalDate.now().toEpochDay())
    }.flowOn(Dispatchers.Default).shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    fun targets(day: Long): Flow<Map<String, Double>> = plan.map { it.targets(day) }

    suspend fun setWeight(day: Long, kg: Double) = planDao.upsertWeight(WeightEntity(day, kg))
    suspend fun deleteWeight(day: Long) = planDao.deleteWeight(day)

    /** Saves plan settings; manual calorie/macro norms are cleared so the plan's targets take effect. */
    suspend fun saveProfile(profile: ProfileEntity) = db.withTransaction {
        planDao.upsertProfile(profile)
        foods.clearUserNorms(
            listOf(HealthDietParser.KCAL, HealthDietParser.PROTEIN, HealthDietParser.FAT, HealthDietParser.CARBS)
        )
    }

    fun categories() = foods.categories()
    fun food(id: Int) = foods.foodFlow(id)
    fun foodNutrients(id: Int) = foods.nutrientsFlow(id)
    fun nutrientDefs() = foods.nutrientDefs()
    fun foodCount() = foods.foodCount()
    fun detailedCount() = foods.detailedCount()
    fun usdaCount() = foods.usdaCount()
    fun productCount() = foods.productCount()

    fun allFoods() = foods.allFoods()
    fun foodsWithNutrient(nutrient: String) = foods.withNutrient(nutrient)

    suspend fun foodNutrientsOnce(id: Int) = foods.nutrients(id)
    suspend fun foodOnce(id: Int) = foods.food(id)

    /**
     * Creates (id == null) or updates a custom food. [per100g] holds the editor's nutrients per 100 g; [extra] the
     * ones it doesn't show (name to amount and unit), kept from the food it was edited or copied from.
     * [servingGrams] carries a copied product's serving over. Returns the food's id.
     */
    suspend fun saveCustomFood(
        id: Int?,
        name: String,
        emoji: String,
        per100g: Map<NutrientInfo, Double>,
        barcode: String? = null,
        category: Int = CUSTOM_CATEGORY,
        extra: Map<String, Pair<Double, String>> = emptyMap(),
        servingGrams: Double? = null,
    ): Int =
        db.withTransaction {
            val foodId = id ?: (minOf(foods.minFoodId(), 0) - 1)
            // Editing keeps the food where it is (an AI estimate stays among AI estimates).
            val categoryId = id?.let { foods.food(it)?.categoryId } ?: category
            // The user's own categories; the names are only fallbacks, the UI shows translated labels. A copy of a
            // database food stays in that food's category, whose name mustn't be overwritten.
            if (categoryId < 0) {
                foods.upsertCategories(listOf(CategoryEntity(categoryId, if (categoryId == AI_CATEGORY) "AI estimates" else "My foods")))
            }
            val kcal = per100g.entries.firstOrNull { it.key.siteName == HealthDietParser.KCAL }?.value ?: extra[HealthDietParser.KCAL]?.first
            foods.upsertFood(foodId, name, categoryId, kcal)
            if (servingGrams != null) foods.setServingGrams(foodId, servingGrams)
            foods.setEmoji(foodId, emoji)
            // A barcode with no online match: the next scan of it finds this food.
            if (barcode != null) foods.setProductInfo(foodId, null, barcode, null)
            foods.deleteNutrients(foodId)
            foods.insertNutrients(
                per100g.map { (info, amount) -> FoodNutrientEntity(foodId, info.siteName, amount, info.unit) } +
                    extra.map { (name, v) -> FoodNutrientEntity(foodId, name, v.first, v.second) },
            )
            foods.insertNutrientDefs(per100g.keys.map {
                NutrientEntity(it.siteName, it.group.siteSection, it.order, it.unit, null, null, null)
            })
            foods.markFetched(foodId, System.currentTimeMillis())
            foodId
        }

    /**
     * Creates ([id] null) or updates a recipe: a food of its own whose nutrients per 100 g come from [ingredients]
     * (food id to grams). [cookedGrams] is the finished dish's weight when cooking changed it; otherwise the
     * ingredients' total. The whole dish is its serving size. Returns the recipe's food id.
     */
    suspend fun saveRecipe(id: Int?, name: String, emoji: String, ingredients: List<Pair<Int, Double>>, cookedGrams: Double?): Int =
        db.withTransaction {
            val weight = cookedGrams ?: ingredients.sumOf { it.second }
            val units = foods.nutrientDefsOnce().associate { it.name to it.unit }
            val values = Recipes.per100g(ingredients.map { (food, grams) -> Recipes.Part(foods.nutrients(food), grams) }, weight, units)
            val foodId = id ?: (minOf(foods.minFoodId(), 0) - 1)
            // The name is only a fallback; the UI shows a translated label for this category.
            foods.upsertCategories(listOf(CategoryEntity(RECIPE_CATEGORY, "My recipes")))
            foods.upsertFood(foodId, name, RECIPE_CATEGORY, values[HealthDietParser.KCAL]?.first)
            foods.setEmoji(foodId, emoji)
            foods.setServingGrams(foodId, weight)
            foods.deleteNutrients(foodId)
            foods.insertNutrients(values.map { (n, v) -> FoodNutrientEntity(foodId, n, v.first, v.second) })
            foods.deleteRecipeIngredients(foodId)
            foods.insertRecipeIngredients(ingredients.mapIndexed { i, (food, grams) -> RecipeIngredientEntity(foodId, i, food, grams) })
            foods.markFetched(foodId, System.currentTimeMillis())
            foodId
        }

    suspend fun nutrientDefsOnce() = foods.nutrientDefsOnce()

    fun recipeIngredients(recipeId: Int) = foods.recipeIngredients(recipeId)
    suspend fun recipeIngredientsOnce(recipeId: Int) = foods.recipeIngredientsOnce(recipeId)

    suspend fun deleteFood(id: Int) = foods.deleteFood(id)

    suspend fun setUserNorm(name: String, value: Double?) = foods.setUserNorm(name, value)

    fun diaryItems(day: Long) = diary.itemsForDay(day)
    fun intake(day: Long) = diary.intakeForDay(day)
    fun contributions(day: Long) = diary.contributionsForDay(day)

    /** Returns the new entry's id. */
    suspend fun addToDiary(day: Long, meal: Int, foodId: Int, grams: Double): Long =
        diary.insert(DiaryEntryEntity(epochDay = day, meal = meal, foodId = foodId, grams = grams,
            createdAt = System.currentTimeMillis()))

    fun usedFoods(sinceDay: Long) = diary.usedFoods(sinceDay)

    suspend fun updateEntry(id: Long, grams: Double, meal: Int) = diary.update(id, grams, meal)
    suspend fun deleteEntry(id: Long) = diary.delete(id)

    companion object {
        const val CUSTOM_CATEGORY = -1
        /** Foods logged from AI photo estimates (custom foods, kept apart from the user's own). */
        const val AI_CATEGORY = -2
        /** Recipes: custom foods made from other foods. */
        const val RECIPE_CATEGORY = -3
    }
}
