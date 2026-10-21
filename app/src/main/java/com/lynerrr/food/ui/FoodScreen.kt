@file:OptIn(ExperimentalMaterial3Api::class)

package com.lynerrr.food.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lynerrr.food.data.FoodIds
import com.lynerrr.food.data.NutrientGroup
import com.lynerrr.food.data.Ratio
import com.lynerrr.food.data.computeRatios
import com.lynerrr.food.data.macroSplit
import com.lynerrr.food.data.formatAmount
import com.lynerrr.food.data.groupNutrients
import com.lynerrr.food.net.HealthDietParser
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lynerrr.food.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lynerrr.food.data.FoodRepository
import com.lynerrr.food.data.NutrientAmount
import com.lynerrr.food.data.buildNutrientValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime


class FoodViewModel(private val repo: FoodRepository, private val foodId: Int, private val day: Long) : ViewModel() {
    val food = repo.food(foodId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val ingredients = repo.recipeIngredients(foodId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val grams = MutableStateFlow("100")
    val meal = MutableStateFlow(
        LocalTime.now().hour.let { h -> if (h < 11) 0 else if (h < 16) 1 else if (h < 21) 2 else 3 }
    )

    val values = combine(repo.foodNutrients(foodId), repo.nutrientDefs(), grams, repo.targets(day)) { nutrients, defs, g, targets ->
        val factor = (g.toDoubleOrNull() ?: 0.0) / 100
        val amounts = nutrients.map { NutrientAmount(it.name, it.unit, it.amount * factor) }
        buildNutrientValues(defs, amounts, includeMissing = false, overrides = targets)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groups = values.map { groupNutrients(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ratios = values.map { computeRatios(it) to macroSplit(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Ratio>() to null)

    init {
        // Packaged products start at their stated serving (a can, three cookies) instead of 100 g.
        viewModelScope.launch {
            repo.food(foodId).firstOrNull { it != null }?.servingGrams?.let { grams.value = formatGrams(it) }
        }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        repo.deleteFood(foodId)
        onDone()
    }

    fun add(onDone: () -> Unit) = viewModelScope.launch {
        val g = grams.value.toDoubleOrNull()?.takeIf { it > 0 } ?: return@launch
        repo.addToDiary(day, meal.value, foodId, g)
        onDone()
    }
}

@Composable
fun FoodScreen(
    foodId: Int,
    day: Long,
    onBack: () -> Unit,
    onAdded: () -> Unit,
    /** [recipe]: edit in the recipe editor rather than the custom-food one. */
    onEdit: (recipe: Boolean) -> Unit,
    onBestSources: (nutrient: String) -> Unit,
) {
    val vm = appViewModel { FoodViewModel(it.repository, foodId, day) }
    val food by vm.food.collectAsStateWithLifecycle()
    val values by vm.values.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val ratios by vm.ratios.collectAsStateWithLifecycle()
    val grams by vm.grams.collectAsStateWithLifecycle()
    val meal by vm.meal.collectAsStateWithLifecycle()
    var showMore by remember { mutableStateOf(emptySet<NutrientGroup>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val hasDetails = food?.detailsFetchedAt != null
    val custom = foodId < 0
    val recipe = food?.categoryId == FoodRepository.RECIPE_CATEGORY
    val ingredients by vm.ingredients.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (custom) {
                        IconButton(onClick = { onEdit(recipe) }) { Icon(Icons.Default.Edit, stringResource(R.string.edit_food)) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete_food)) }
                    }
                },
            )
        },
    ) { padding ->
        if (!hasDetails) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                // Every bundled food ships with its nutrients; a food without them has no data to show.
                if (food == null) CircularProgressIndicator()
                else Text(stringResource(R.string.no_details), modifier = Modifier.padding(24.dp))
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FoodAvatar(food?.emoji, food?.categoryId, size = 56.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(food?.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
                        food?.brand?.let {
                            Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            (food?.kcal?.let { stringResource(R.string.kcal_per_100_long, formatAmount(it)) } ?: "") +
                                if (recipe) stringResource(R.string.your_recipe) else if (custom) stringResource(R.string.your_food) else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (ingredients.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.ingredients), style = MaterialTheme.typography.titleMedium)
                            ingredients.forEach { ing ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FoodAvatar(ing.emoji, ing.categoryId, size = 32.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(ing.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        stringResource(R.string.grams_value, formatGrams(ing.grams)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (food?.let { FoodIds.isProduct(it.id) } == true) {
                item {
                    Text(
                        stringResource(R.string.packaged_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GramsField(grams, { vm.grams.value = it }, Modifier.fillMaxWidth())
                        food?.servingGrams?.let { serving ->
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = grams == formatGrams(serving),
                                    onClick = { vm.grams.value = formatGrams(serving) },
                                    label = {
                                        Text(stringResource(if (recipe) R.string.whole_recipe_chip else R.string.serving_chip, formatGrams(serving)))
                                    },
                                )
                                if (recipe) {
                                    FilterChip(
                                        selected = grams == formatGrams(serving / 2),
                                        onClick = { vm.grams.value = formatGrams(serving / 2) },
                                        label = { Text(stringResource(R.string.half_recipe_chip, formatGrams(serving / 2))) },
                                    )
                                }
                                FilterChip(
                                    selected = grams == "100",
                                    onClick = { vm.grams.value = "100" },
                                    label = { Text(stringResource(R.string.hundred_grams_chip)) },
                                )
                            }
                        }
                        MealPicker(meal) { vm.meal.value = it }
                        Button(
                            onClick = { vm.add(onAdded) },
                            enabled = (grams.toDoubleOrNull() ?: 0.0) > 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.add_to_day, dayLabel(day).lowercase())) }
                    }
                }
            }
            item {
                val macros = values.associateBy { it.name }
                macros[HealthDietParser.KCAL]?.let { CalorieHero(it) }
            }
            item {
                val macros = values.associateBy { it.name }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple(HealthDietParser.PROTEIN, "💪", stringResource(R.string.protein)),
                        Triple(HealthDietParser.FAT, "🧈", stringResource(R.string.fat)),
                        Triple(HealthDietParser.CARBS, "🍞", stringResource(R.string.carbs)),
                    ).forEach { (name, sticker, label) ->
                        macros[name]?.let { MacroTile(it, sticker, label, Modifier.weight(1f)) }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.share_of_targets, grams.ifEmpty { "0" }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "ratios") { RatiosCard(ratios.first, ratios.second) }
            items(groups, key = { it.group }) { group ->
                NutrientGroupCard(
                    group = group,
                    bodyWeight = null,
                    showMore = group.group in showMore,
                    showSummary = false,
                    onToggleMore = {
                        showMore = if (group.group in showMore) showMore - group.group else showMore + group.group
                    },
                    onClick = { (v) -> onBestSources(v.name) },
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_food_q, food?.name.orEmpty())) },
            text = { Text(stringResource(R.string.delete_food_warning)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Grams as typed in the field: "330", "33.5" (dot, no trailing zeros). */
private fun formatGrams(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(java.util.Locale.ROOT, value)
