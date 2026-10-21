package com.lynerrr.food.net

import com.lynerrr.food.data.EnergyModel
import com.lynerrr.food.data.ProfileEntity
import com.lynerrr.food.data.WeightEntity
import com.lynerrr.food.data.nutrientKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EnergyModelTest {
    private val profile = ProfileEntity(
        male = true, age = 30, heightCm = 180.0, activity = 1,
        goalKgPerWeek = -0.5, proteinPerKg = 1.8, fatPercent = 30.0, startDay = 0,
    )

    /** Simulates someone whose true expenditure is [tdee], eating [intake] every day, with daily water noise. */
    private fun simulate(tdee: Double, intake: Double, days: Int, logEvery: Int = 1): Pair<List<WeightEntity>, Map<Long, Double>> {
        var fatMass = 80.0
        val weights = ArrayList<WeightEntity>()
        val eaten = HashMap<Long, Double>()
        for (d in 0 until days) {
            val noise = if (d % 2 == 0) 0.6 else -0.6
            weights += WeightEntity(d.toLong(), fatMass + noise)
            if (d % logEvery == 0) eaten[d.toLong()] = intake
            fatMass += (intake - tdee) / EnergyModel.KCAL_PER_KG
        }
        return weights to eaten
    }

    @Test
    fun startsFromFormula() {
        val plan = EnergyModel.compute(profile, listOf(WeightEntity(0, 80.0)), emptyMap(), today = 0)
        val day1 = plan.on(1)!!
        val formula = EnergyModel.formulaTdee(profile, 80.0)
        assertEquals(formula, day1.expenditure!!, 1.0)
        assertEquals(formula - 550, day1.target!!, 1.0)
        assertEquals(0, day1.loggedDays)
    }

    @Test
    fun learnsTrueExpenditure() {
        // Formula says ~2470 for this profile; this person actually burns 3000.
        val (weights, eaten) = simulate(tdee = 3000.0, intake = 2500.0, days = 60)
        val plan = EnergyModel.compute(profile, weights, eaten, today = 59)
        val learned = plan.on(59)!!.expenditure!!
        assertTrue("learned $learned", abs(learned - 3000) < 150)
        assertEquals(learned - 550, plan.on(59)!!.target!!, 1.0)
    }

    @Test
    fun maintenanceStaysPut() {
        val (weights, eaten) = simulate(tdee = 2600.0, intake = 2600.0, days = 45, logEvery = 1)
        val plan = EnergyModel.compute(profile, weights, eaten, today = 44)
        assertTrue(abs(plan.on(44)!!.expenditure!! - 2600) < 100)
        assertTrue(abs(plan.on(44)!!.trend!! - 80.0) < 0.3)
    }

    @Test
    fun targetsScaleWithBodyWeight() {
        val plan = EnergyModel.compute(null, listOf(WeightEntity(0, 60.0)), emptyMap(), today = 0)
        val targets = plan.targets(0)
        assertEquals(39 * 60 / 1000.0, targets.getValue(nutrientKey("Лейцин")), 1e-9)
        assertEquals(15 * 60 / 1000.0, targets.getValue(nutrientKey("Метионин + Цистеин")), 1e-9)
        assertNull(targets[nutrientKey(HealthDietParser.KCAL)])
        assertNull(plan.on(0)!!.expenditure)
    }
}
