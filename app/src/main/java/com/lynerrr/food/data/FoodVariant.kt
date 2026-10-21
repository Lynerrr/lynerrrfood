package com.lynerrr.food.data

import com.lynerrr.food.net.HealthDietParser
import kotlin.math.abs

/**
 * Saving a food the user edited from existing data: their own copy of a database food ("my chicken mince, 20 g
 * protein") or a custom food they're changing. Everything they didn't touch carries over, so the copy keeps the
 * original's vitamins and minerals.
 */
object FoodVariant {
    const val CHOLESTEROL = "Холестерин"

    /**
     * The per-100 g values to save, by nutrient name.
     *
     * @param original the food's values before editing, at full precision.
     * @param form what the editor holds now, per 100 g; fields left blank are absent.
     * @param initialForm what the editor showed when it opened (rounded for display), to tell edited fields apart.
     * @param formNames every nutrient the editor shows; the rest are kept from [original] as they are.
     * @param groupOf each nutrient's group.
     *
     * Amino acids are shares of protein and fatty acids shares of fat, so when the user changes protein or fat but
     * not those details, the details follow in proportion. Vitamins, minerals and everything else stay unchanged.
     */
    fun merge(
        original: Map<String, Double>,
        form: Map<String, Double>,
        initialForm: Map<String, Double>,
        formNames: Set<String>,
        groupOf: (String) -> NutrientGroup,
    ): Map<String, Double> {
        fun edited(name: String) = name in formNames && form[name] != initialForm[name]
        fun ratio(name: String) = original[name]?.takeIf { edited(name) && it > 0 }?.let { o -> form[name]?.let { it / o } }
            ?.takeIf { abs(it - 1) > 1e-9 }
        val protein = ratio(HealthDietParser.PROTEIN)
        val fat = ratio(HealthDietParser.FAT)

        return (original.keys + form.keys).mapNotNull { name ->
            // A field the user edited counts as written (cleared = removed); anything else keeps its precise value.
            if (edited(name)) return@mapNotNull form[name]?.let { name to it }
            val value = original[name] ?: form[name] ?: return@mapNotNull null
            val group = groupOf(name)
            val factor = when {
                group == NutrientGroup.AMINO_ACIDS -> protein
                group == NutrientGroup.FATS && name != CHOLESTEROL && name != HealthDietParser.FAT -> fat
                else -> null
            }
            name to value * (factor ?: 1.0)
        }.toMap()
    }
}
