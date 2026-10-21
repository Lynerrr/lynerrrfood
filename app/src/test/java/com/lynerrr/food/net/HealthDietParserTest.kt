package com.lynerrr.food.net

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthDietParserTest {
    private fun fixture(name: String) =
        Jsoup.parse(javaClass.classLoader!!.getResourceAsStream(name)!!, "UTF-8", "https://health-diet.ru")

    @Test
    fun parsesEggPage() {
        val food = HealthDietParser.parseFood(fixture("food_456.html"), 456)
        assertEquals("Яйцо куриное", food.name)

        val byName = food.nutrients.associateBy { it.name }
        assertEquals(157.0, byName.getValue("Калорийность").amount, 0.0)
        assertEquals("ккал", byName.getValue("Калорийность").unit)
        assertEquals(12.7, byName.getValue("Белки").amount, 0.0)
        assertEquals(Norm(76.0, null, "г"), byName.getValue("Белки").norm)

        val b12 = byName.getValue("Витамин В12, кобаламин")
        assertEquals("Витамины", b12.section)
        assertEquals("мкг", b12.unit)
        assertEquals(0.52, b12.amount, 0.0)

        assertEquals("Макроэлементы", byName.getValue("Кальций, Ca").section)
        assertEquals("Микроэлементы", byName.getValue("Железо, Fe").section)
        assertEquals("Незаменимые аминокислоты", byName.getValue("Аргинин").section)
        assertEquals(Norm(null, 300.0, "мг"), byName.getValue("Холестерин").norm)
        assertEquals("Жирные кислоты", byName.getValue("Насыщенные жирные кислоты").section)
        assertEquals(Norm(0.9, 3.7, "г"), byName.getValue("Омега-3 жирные кислоты").norm)
        assertNull(byName.getValue("Зола").norm)
        assertEquals(80, food.nutrients.size)
    }

    @Test
    fun parsesCategoryPage() {
        val doc = fixture("category_24501.html")
        val categories = HealthDietParser.parseCategories(doc)
        assertEquals(26, categories.size)
        assertTrue(categories.any { it.id == 24520 && it.name == "Яйца и продукты из яиц" })

        val foods = HealthDietParser.parseCategoryFoods(doc)
        assertTrue(foods.size > 400)
        val item = foods.first { it.id == 23889 }
        assertEquals("Биточки паровые из курицы, по 2-24", item.name)
        assertEquals(176.0, item.kcal!!, 0.0)
    }

    @Test
    fun parsesNorms() {
        assertEquals(Norm(1684.0, null, "ккал"), HealthDietParser.parseNorm("1684 ккал"))
        assertEquals(Norm(16.8, null, "г"), HealthDietParser.parseNorm("min 16.8 г"))
        assertEquals(Norm(1000.0, null, "мг"), HealthDietParser.parseNorm("1 000 мг"))
        assertNull(HealthDietParser.parseNorm("~"))
    }
}
