package com.lynerrr.food.net

import com.lynerrr.food.data.RankedFood
import com.lynerrr.food.data.SourceRanking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceRankingTest {
    private var nextId = 1
    private fun food(name: String, category: Int, mgPer100g: Double, kcal: Double = 100.0) =
        RankedFood(nextId++, name, category, null, mgPer100g, "мг", kcal)

    // Vitamin C, target 90 mg.
    private val foods = listOf(
        food("Шиповник", 24516, 650.0),
        food("Шиповник сухой", 24516, 1000.0, kcal = 284.0),
        food("Перец сладкий красный", 24512, 200.0, kcal = 27.0),
        food("Петрушка сушеная", 24514, 125.0, kcal = 292.0),
        food("Яблоко", 24516, 4.6, kcal = 47.0),
        food("KRAFT Fruit Drink", 24509, 60.0),
        food("Пюре детское, яблоко-шиповник", 24526, 300.0),
    )

    @Test
    fun filtersAndGroups() {
        val entries = SourceRanking.rank(foods, 90.0 to "мг", perKcal = false, commonOnly = false)
        val names = entries.map { it.best.food.name }
        // Rose hips: 975 mg per 150 g serving beats dried rose hips' 150 mg per 15 g; both become one family.
        assertEquals(listOf("Шиповник", "Перец сладкий красный"), names)
        assertEquals(listOf("Шиповник сухой"), entries[0].variants.map { it.food.name })
        // Dried parsley (3 g serving), apples (15 mg), brands and baby food don't make it.
        assertTrue("Петрушка сушеная" !in names && "Яблоко" !in names)
    }

    @Test
    fun commonFoods() {
        assertTrue(SiteCatalog.isCommon(33, "Баклажан"))
        assertTrue(SiteCatalog.isCommon(13500, "Творог влажный 1% жирности"))
        assertTrue(!SiteCatalog.isCommon(17000, "Бобы (конский) незрелый, вареный, без соли"))
        assertTrue(!SiteCatalog.isCommon(18000, "Мозги бараньи, жаренные на сковороде"))
        assertTrue(!SiteCatalog.isCommon(24100, "Оладьи, по 2-176"))
        assertTrue(SiteCatalog.isCommon(-3, "My protein bar, extra long custom name"))
    }

    @Test
    fun skipsRareOffalButKeepsLiver() {
        val c = listOf(
            RankedFood(1, "Селезенка говяжья", 24502, null, 50.0, "мг", 100.0),
            RankedFood(2, "Печень говяжья", 24502, null, 33.0, "мг", 125.0),
        )
        val names = SourceRanking.rank(c, 90.0 to "мг", perKcal = false, commonOnly = true).map { it.best.food.name }
        assertEquals(listOf("Печень говяжья"), names)
    }

    @Test
    fun servingSizes() {
        assertEquals(30.0, SourceRanking.servingGrams(24519, "Сыр Российский"), 0.0)
        assertEquals(200.0, SourceRanking.servingGrams(24519, "Молоко 2% жирности"), 0.0)
        assertEquals(180.0, SourceRanking.servingGrams(24522, "Гречка отварная"), 0.0)
        assertEquals(60.0, SourceRanking.servingGrams(24522, "Гречневая крупа ядрица"), 0.0)
        assertEquals(15.0, SourceRanking.servingGrams(24514, "Петрушка (зелень)"), 0.0)
        // USDA foods (English names) follow the same rules.
        assertEquals(30.0, SourceRanking.servingGrams(24519, "Cheese, cheddar"), 0.0)
        assertEquals(150.0, SourceRanking.servingGrams(24519, "Cheese, cottage, lowfat, 2% milkfat"), 0.0)
        assertEquals(200.0, SourceRanking.servingGrams(24519, "Milk, whole, 3.25% milkfat"), 0.0)
        assertEquals(15.0, SourceRanking.servingGrams(24519, "Milk, dry, whole"), 0.0)
        assertEquals(15.0, SourceRanking.servingGrams(24516, "Apricots, dried, sulfured, uncooked"), 0.0)
        assertEquals(180.0, SourceRanking.servingGrams(24522, "Rice, white, long-grain, regular, cooked"), 0.0)
        assertEquals(200.0, SourceRanking.servingGrams(24512, "Carrot juice, canned"), 0.0)
        assertEquals(10.0, SourceRanking.servingGrams(24519, "Butter, salted"), 0.0)
        assertEquals(15.0, SourceRanking.servingGrams(24522, "Pasta, dry, unenriched, uncooked"), 0.0)
        assertEquals(3.0, SourceRanking.servingGrams(24514, "Паприка молотая"), 0.0)
        assertEquals(3.0, SourceRanking.servingGrams(24514, "Петрушка сушеная"), 0.0)
        assertEquals(20.0, SourceRanking.servingGrams(24509, "Сироп из шиповника"), 0.0)
        assertEquals(150.0, SourceRanking.servingGrams(24523, "Горошек зелёный"), 0.0)
    }
}
