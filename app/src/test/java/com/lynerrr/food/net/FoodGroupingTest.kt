package com.lynerrr.food.net

import com.lynerrr.food.data.FoodEntity
import com.lynerrr.food.data.FoodGrouping
import org.junit.Assert.assertEquals
import org.junit.Test

class FoodGroupingTest {
    private var nextId = 1
    private fun food(name: String) = FoodEntity(nextId++, name, name.lowercase(), 24518, 100.0, 1L)

    @Test
    fun variantsShareAFamily() {
        val families = FoodGrouping.families(listOf(
            food("Икра осетровая зернистая, соленая"), food("Икра сельди тихоокеанской, свежая"), food("Вобла"),
            food("Вобла каспийская вяленая"), food("Бычок-песочник"),
        ))
        assertEquals(listOf("Бычок", "Вобла", "Икра"), families.map { it.label })
        // The plainest name comes first.
        assertEquals("Вобла", families[1].foods.first().name)
        assertEquals(2, families[2].foods.size)
    }

    @Test
    fun bigFamiliesSplitByTwoWords() {
        val salmon = (1..10).map { food("Fish, salmon, Atlantic, variant $it") }
        val tuna = (1..8).map { food("Fish, tuna, light, variant $it") }
        val families = FoodGrouping.families(salmon + tuna + food("Fish, wolffish, raw"))
        assertEquals(listOf("Fish, salmon", "Fish, tuna", "Fish, wolffish"), families.map { it.label })
        assertEquals(10, families[0].foods.size)
    }

    @Test
    fun labelsKeepTheOriginalSpelling() {
        assertEquals("Fish, salmon", FoodGrouping.label("Fish, salmon, Atlantic, raw", 2))
        assertEquals("Икра", FoodGrouping.label("икра осетровая", 1))
    }
}
