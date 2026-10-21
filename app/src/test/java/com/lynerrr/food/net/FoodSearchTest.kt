package com.lynerrr.food.net

import com.lynerrr.food.data.FoodEntity
import com.lynerrr.food.data.FoodSearch
import com.lynerrr.food.data.RussianStemmer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodSearchTest {
    private val names = listOf(
        "Говядина, фарш",
        "Говядина, вырезка, отварная",
        "Свинина, фарш",
        "Курица, грудка, жареная",
        "Яйцо куриное",
        "Молоко 2,5%",
        "Гречневая крупа",
        "Котлеты из говядины",
    )
    private val search = FoodSearch(names.mapIndexed { i, n -> FoodEntity(i, n, n.lowercase(), null, null, null) })
    private fun find(q: String) = search.search(q, null).map { it.name }

    @Test
    fun stems() {
        assertEquals("говядин", RussianStemmer.stem("говядина"))
        assertEquals("говяж", RussianStemmer.stem("говяжий"))
        assertEquals("фарш", RussianStemmer.stem("фарша"))
    }

    @Test
    fun adjectiveFindsNoun() {
        assertEquals("Говядина, фарш", find("Говяжий фарш").first())
        assertEquals("Курица, грудка, жареная", find("куриная грудка").first())
        assertTrue("Яйцо куриное" in find("курица"))
    }

    @Test
    fun wordOrderAndInflection() {
        assertEquals("Говядина, фарш", find("фарш говядины").first())
        assertEquals("Говядина, вырезка, отварная", find("вареная говядина вырезка").first())
        assertEquals(listOf("Гречневая крупа"), find("гречка"))
    }

    @Test
    fun brandsInCyrillicOrLatin() {
        val products = FoodSearch(listOf(
            FoodEntity(1_000_000_001, "Pepsi", "pepsi", 9001, 42.0, 1L, brand = "PepsiCo"),
            FoodEntity(1_000_000_002, "Coca-Cola Original Taste", "coca-cola original taste", 9001, 42.0, 1L, brand = "Coca-Cola"),
            FoodEntity(1_000_000_003, "Oreo Original", "oreo original", 9004, 480.0, 1L, brand = "Oreo"),
            FoodEntity(1_000_000_004, "Snickers", "snickers", 9003, 490.0, 1L, brand = "Mars"),
        ))
        fun find(q: String) = products.search(q, null).map { it.name }
        assertEquals("Pepsi", find("пепси").first())
        assertEquals("Coca-Cola Original Taste", find("кока кола").first())
        assertEquals("Oreo Original", find("орео").first())
        assertEquals("Snickers", find("сникерс").first())
        assertEquals("Snickers", find("mars").first())
    }

    @Test
    fun partialAndTypos() {
        assertTrue("Молоко 2,5%" in find("мол"))
        assertEquals("Свинина, фарш", find("свинной фарш").first())
    }
}
