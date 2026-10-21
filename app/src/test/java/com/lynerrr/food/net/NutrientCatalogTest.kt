package com.lynerrr.food.net

import com.lynerrr.food.data.NutrientCatalog
import com.lynerrr.food.data.NutrientGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NutrientCatalogTest {
    /** Every nutrient name seen on health-diet.ru, as "section\tname". */
    private val siteNutrients = javaClass.classLoader!!.getResource("site_nutrients.tsv")!!.readText()
        .lines().filter { it.isNotBlank() }.map { it.split('\t').let { (s, n) -> s to n } }

    @Test
    fun everySiteNutrientHasAGroupAndLabel() {
        siteNutrients.forEach { (section, name) ->
            val info = NutrientCatalog.info(name, section)
            assertTrue("$name ended up in Other", info.group != NutrientGroup.OTHER)
            // Only individual fatty acids keep their Russian name; everything else has an English label.
            if (!name.first().isDigit()) assertTrue("$name has no English label", info.labelEn.none { it in 'а'..'я' })
        }
    }

    @Test
    fun catalogNamesMatchTheSite() {
        val siteNames = siteNutrients.map { it.second }.toSet()
        NutrientGroup.entries.flatMap { NutrientCatalog.editable(it) }.forEach {
            assertTrue("Custom-food nutrient '${it.siteName}' isn't spelled like the site", it.siteName in siteNames)
        }
    }

    @Test
    fun regroupsMinerals() {
        assertEquals(NutrientGroup.MINERALS, NutrientCatalog.info("Калий, K", "Макроэлементы").group)
        assertEquals(NutrientGroup.MINERALS, NutrientCatalog.info("Железо, Fe", "Микроэлементы").group)
        assertEquals("16:0", NutrientCatalog.info("16:0 Пальмитиновая", "Жирные кислоты").badge)
    }
}
