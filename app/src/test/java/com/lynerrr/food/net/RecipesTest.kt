package com.lynerrr.food.net

import com.lynerrr.food.data.FoodNutrientEntity
import com.lynerrr.food.data.Recipes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipesTest {
    private fun n(name: String, amount: Double, unit: String) = FoodNutrientEntity(0, name, amount, unit)

    private val buckwheat = listOf(n("Калорийность", 308.0, "ккал"), n("Белки", 12.6, "г"), n("Магний, Mg", 200.0, "мг"),
        n("Витамин В1, тиамин", 0.3, "мг"))
    private val mince = listOf(n("Калорийность", 143.0, "ккал"), n("Белки", 17.4, "г"), n("Магний, Mg", 20.0, "мг"),
        // Given in µg here, so it has to be converted before adding.
        n("Витамин В1, тиамин", 80.0, "мкг"), n("Витамин В12, кобаламин", 0.5, "мкг"))
    private val units = mapOf("Калорийность" to "ккал", "Белки" to "г", "Магний, Mg" to "мг", "Витамин В1, тиамин" to "мг",
        "Витамин В12, кобаламин" to "мкг")

    @Test
    fun weighsIngredientsIntoPer100g() {
        // 100 g buckwheat + 250 g mince = 350 g.
        val r = Recipes.per100g(listOf(Recipes.Part(buckwheat, 100.0), Recipes.Part(mince, 250.0)), 350.0, units)
        assertEquals((308 + 143 * 2.5) / 3.5, r.getValue("Калорийность").first, 1e-9)
        assertEquals((12.6 + 17.4 * 2.5) / 3.5, r.getValue("Белки").first, 1e-9)
        assertEquals((200 + 20 * 2.5) / 3.5, r.getValue("Магний, Mg").first, 1e-9)
        // 0.3 mg + 2.5 × 80 µg = 0.5 mg in 350 g.
        assertEquals(0.5 / 3.5, r.getValue("Витамин В1, тиамин").first, 1e-9)
        assertEquals("мг", r.getValue("Витамин В1, тиамин").second)
        // Only the mince has B12; buckwheat counts as none.
        assertEquals(0.5 * 2.5 / 3.5, r.getValue("Витамин В12, кобаламин").first, 1e-9)
        assertNull(r["Витамин C, аскорбиновая"])
    }

    @Test
    fun cookedWeightConcentratesNutrients() {
        // The same 350 g of ingredients cooked down to 300 g: each 100 g holds more.
        val raw = Recipes.per100g(listOf(Recipes.Part(buckwheat, 100.0), Recipes.Part(mince, 250.0)), 350.0, units)
        val cooked = Recipes.per100g(listOf(Recipes.Part(buckwheat, 100.0), Recipes.Part(mince, 250.0)), 300.0, units)
        assertEquals(raw.getValue("Калорийность").first * 350 / 300, cooked.getValue("Калорийность").first, 1e-9)
    }
}
