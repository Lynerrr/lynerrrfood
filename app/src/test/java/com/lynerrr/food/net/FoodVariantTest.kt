package com.lynerrr.food.net

import com.lynerrr.food.data.FoodVariant
import com.lynerrr.food.data.NutrientGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FoodVariantTest {
    private val groups = mapOf(
        "Белки" to NutrientGroup.ENERGY, "Жиры" to NutrientGroup.ENERGY, "Калорийность" to NutrientGroup.ENERGY,
        "Лейцин" to NutrientGroup.AMINO_ACIDS, "Насыщенные жирные кислоты" to NutrientGroup.FATS,
        "16:0 Пальмитиновая" to NutrientGroup.FATS, "Холестерин" to NutrientGroup.FATS,
        "Витамин В12, кобаламин" to NutrientGroup.VITAMINS, "Железо, Fe" to NutrientGroup.MINERALS,
    )

    // Database chicken mince: 17.4 g protein, 15 g fat, plus details the editor doesn't all show.
    private val original = mapOf(
        "Калорийность" to 143.0, "Белки" to 17.4, "Жиры" to 15.0, "Лейцин" to 1.3527, "Насыщенные жирные кислоты" to 4.1,
        "16:0 Пальмитиновая" to 3.0, "Холестерин" to 86.0, "Витамин В12, кобаламин" to 0.56, "Железо, Fe" to 0.82,
    )
    // The editor shows macros, saturated fat, B12 and iron, rounded for display.
    private val formNames = setOf("Калорийность", "Белки", "Жиры", "Насыщенные жирные кислоты", "Витамин В12, кобаламин", "Железо, Fe")
    private val shown = mapOf("Калорийность" to 143.0, "Белки" to 17.4, "Жиры" to 15.0, "Насыщенные жирные кислоты" to 4.1,
        "Витамин В12, кобаламин" to 0.56, "Железо, Fe" to 0.82)

    private fun merge(form: Map<String, Double>) =
        FoodVariant.merge(original, form, shown, formNames) { groups.getValue(it) }

    @Test
    fun macrosChangeAndDetailsFollow() {
        // The user's mince: 20 g protein, 5 g fat, 125 kcal; nothing else touched.
        val r = merge(shown + mapOf("Белки" to 20.0, "Жиры" to 5.0, "Калорийность" to 125.0))
        assertEquals(20.0, r.getValue("Белки"), 1e-9)
        assertEquals(5.0, r.getValue("Жиры"), 1e-9)
        assertEquals(125.0, r.getValue("Калорийность"), 1e-9)
        // Amino acids follow protein, fatty acids follow fat (shown or not); cholesterol doesn't.
        assertEquals(1.3527 * 20 / 17.4, r.getValue("Лейцин"), 1e-9)
        assertEquals(4.1 / 3, r.getValue("Насыщенные жирные кислоты"), 1e-9)
        assertEquals(1.0, r.getValue("16:0 Пальмитиновая"), 1e-9)
        assertEquals(86.0, r.getValue("Холестерин"), 1e-9)
        // Vitamins and minerals stay as they were.
        assertEquals(0.56, r.getValue("Витамин В12, кобаламин"), 1e-9)
        assertEquals(0.82, r.getValue("Железо, Fe"), 1e-9)
    }

    @Test
    fun editedDetailsWinAndClearedFieldsGo() {
        val r = merge(shown + mapOf("Жиры" to 5.0, "Насыщенные жирные кислоты" to 2.0) - "Железо, Fe")
        assertEquals(2.0, r.getValue("Насыщенные жирные кислоты"), 1e-9) // typed by the user, not scaled
        assertFalse("Железо, Fe" in r)
    }

    @Test
    fun untouchedKeepsFullPrecision() {
        // The form shows 1.35 for 1.3527; saving without edits must not round it.
        val r = FoodVariant.merge(original, shown, shown, formNames + "Лейцин") { groups.getValue(it) }
        assertEquals(original, r)
    }
}
