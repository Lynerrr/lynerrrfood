package com.lynerrr.food.data

import androidx.annotation.StringRes
import com.lynerrr.food.R

/** Broad food types that best-source suggestions are grouped by, so the list reads "which kind of food" first. */
enum class SourceGroup(@StringRes val title: Int, val sticker: String, val categories: Set<Int>) {
    MEAT(R.string.source_group_meat, "🥩", setOf(24501, 24502, 24503, 24504, 24506, 24507, ProductCategory.MEAT_FISH.id)),
    FISH(R.string.source_group_fish, "🐟", setOf(24518)),
    DAIRY_EGGS(R.string.source_group_dairy_eggs, "🥛", setOf(24519, 24520, ProductCategory.DAIRY.id, ProductCategory.ICE_CREAM.id)),
    VEGETABLES(R.string.source_group_vegetables, "🥦", setOf(24512, 24513, 24514)),
    FRUIT(R.string.source_group_fruit, "🍓", setOf(24511, 24516, ProductCategory.JUICES.id)),
    LEGUMES_NUTS(R.string.source_group_legumes_nuts, "🥜", setOf(24517, 24523)),
    GRAINS(R.string.source_group_grains, "🌾", setOf(24522, 24524, ProductCategory.CEREALS.id, ProductCategory.BREAD.id)),
    FATS(R.string.source_group_fats, "🫒", setOf(24515)),
    OTHER(R.string.source_group_other, "🍽️", emptySet());

    companion object {
        fun of(categoryId: Int?): SourceGroup = entries.firstOrNull { categoryId in it.categories } ?: OTHER

        /** Ranked entries split by group; groups keep the order of their best entry. */
        fun group(entries: List<SourceRanking.Entry>): List<Pair<SourceGroup, List<SourceRanking.Entry>>> =
            entries.groupBy { of(it.best.food.categoryId) }.toList()
    }
}
