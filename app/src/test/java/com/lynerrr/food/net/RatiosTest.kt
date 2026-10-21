package com.lynerrr.food.net

import com.lynerrr.food.data.NutrientValue
import com.lynerrr.food.data.RatioKind
import com.lynerrr.food.data.RatioStatus
import com.lynerrr.food.data.computeRatios
import com.lynerrr.food.data.macroSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RatiosTest {
    private fun v(name: String, amount: Double, unit: String) = NutrientValue(name, "", unit, amount, null, null, false)

    @Test
    fun computesRatiosWithUnitConversion() {
        val day = listOf(
            v("Калий, K", 3000.0, "мг"),
            v("Натрий, Na", 1000.0, "мг"),
            v("Омега-6 жирные кислоты", 12.0, "г"),
            v("Омега-3 жирные кислоты", 1.0, "г"),
            v("Цинк, Zn", 10.0, "мг"),
            v("Медь, Cu", 1000.0, "мкг"), // 1 mg
            v("Калорийность", 2000.0, "ккал"),
            v("Пищевые волокна", 30.0, "г"),
            v("Насыщенные жирные кислоты", 30.0, "г"),
        )
        val ratios = computeRatios(day).associateBy { it.kind }
        assertEquals(3.0, ratios.getValue(RatioKind.POTASSIUM_SODIUM).value, 1e-9)
        assertEquals(RatioStatus.GOOD, ratios.getValue(RatioKind.POTASSIUM_SODIUM).status)
        assertEquals(12.0, ratios.getValue(RatioKind.OMEGA6_OMEGA3).value, 1e-9)
        assertEquals(RatioStatus.POOR, ratios.getValue(RatioKind.OMEGA6_OMEGA3).status)
        assertEquals(10.0, ratios.getValue(RatioKind.ZINC_COPPER).value, 1e-9)
        assertEquals(15.0, ratios.getValue(RatioKind.FIBER_PER_1000_KCAL).value, 1e-9)
        assertEquals(13.5, ratios.getValue(RatioKind.SATURATED_FAT_PERCENT).value, 1e-9)
        assertEquals(RatioStatus.POOR, ratios.getValue(RatioKind.SATURATED_FAT_PERCENT).status)
        // Calcium isn't present, so its ratios are skipped rather than shown as zero.
        assertFalse(RatioKind.CALCIUM_PHOSPHORUS in ratios)
    }

    @Test
    fun noCalorieSharesForNearZeroCalories() {
        val dietSoda = listOf(v("Калорийность", 0.4, "ккал"), v("Пищевые волокна", 0.0, "г"), v("Насыщенные жирные кислоты", 0.0, "г"))
        assertEquals(emptyList<RatioKind>(), computeRatios(dietSoda).map { it.kind })
    }

    @Test
    fun macroSplitAddsUpTo100() {
        val split = macroSplit(listOf(v("Белки", 100.0, "г"), v("Жиры", 50.0, "г"), v("Углеводы", 200.0, "г")))!!
        assertEquals(100.0, split.protein + split.fat + split.carbs, 1e-9)
        assertEquals(400.0 / 1650 * 100, split.protein, 1e-9)
    }
}
