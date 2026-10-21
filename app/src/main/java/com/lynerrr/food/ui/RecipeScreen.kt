@file:OptIn(ExperimentalMaterial3Api::class)

package com.lynerrr.food.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lynerrr.food.R
import com.lynerrr.food.data.FoodEntity
import com.lynerrr.food.data.FoodNutrientEntity
import com.lynerrr.food.data.FoodRepository
import com.lynerrr.food.data.FoodSearch
import com.lynerrr.food.data.FoodSourceSettings
import com.lynerrr.food.data.Recipes
import com.lynerrr.food.data.allows
import com.lynerrr.food.data.formatAmount
import com.lynerrr.food.net.HealthDietParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class RecipeViewModel(private val repo: FoodRepository, sources: FoodSourceSettings, private val recipeId: Int?) : ViewModel() {
    /** [grams] is the text being typed. */
    data class Ingredient(val food: FoodEntity, val grams: String)

    /** Whole-dish macros (kcal, protein, fat, carbs) and its weight, shown while editing. */
    data class Summary(val ingredientsGrams: Double, val finalGrams: Double, val wholeDish: Map<String, Double>)

    val name = MutableStateFlow("")
    val emoji = MutableStateFlow("🍲")
    val ingredients = MutableStateFlow<List<Ingredient>>(emptyList())
    /** Weight of the finished dish when cooking changed it; empty = the ingredients' total. */
    val cookedGrams = MutableStateFlow("")
    val query = MutableStateFlow("")

    private val index = combine(repo.allFoods(), sources.enabled) { foods, enabled ->
        // Any food with nutrients can go in, except this recipe itself.
        FoodSearch(foods.filter { it.id != recipeId && it.detailsFetchedAt != null && enabled.allows(it.id) })
    }.flowOn(Dispatchers.Default)

    val results = combine(index, query.debounce(150)) { search, q ->
        if (q.isBlank()) emptyList() else search.search(q, null, limit = 8)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val nutrientCache = HashMap<Int, List<FoodNutrientEntity>>()

    val summary = combine(ingredients, cookedGrams) { list, cooked -> list to cooked }
        .mapLatest { (list, cooked) ->
            val parts = list.mapNotNull { ing ->
                val grams = ing.grams.toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                Recipes.Part(nutrientCache.getOrPut(ing.food.id) { repo.foodNutrientsOnce(ing.food.id) }, grams)
            }
            val total = parts.sumOf { it.grams }
            if (total == 0.0) return@mapLatest null
            val final = cooked.toDoubleOrNull()?.takeIf { it > 0 } ?: total
            val per100 = Recipes.per100g(parts, final, emptyMap())
            Summary(total, final, MAIN.associateWith { (per100[it]?.first ?: 0.0) * final / 100 })
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        if (recipeId != null) viewModelScope.launch {
            val recipe = repo.foodOnce(recipeId) ?: return@launch
            name.value = recipe.name
            recipe.emoji?.let { emoji.value = it }
            val parts = repo.recipeIngredientsOnce(recipeId).mapNotNull { r ->
                repo.foodOnce(r.foodId)?.let { Ingredient(it, formatGramsInput(r.grams)) }
            }
            ingredients.value = parts
            // A stored weight different from the ingredients' total was entered as the cooked weight.
            val total = repo.recipeIngredientsOnce(recipeId).sumOf { it.grams }
            recipe.servingGrams?.takeIf { kotlin.math.abs(it - total) > 0.01 }?.let { cookedGrams.value = formatGramsInput(it) }
        }
    }

    fun add(food: FoodEntity) {
        ingredients.update { it + Ingredient(food, "100") }
        query.value = ""
    }

    fun setGrams(index: Int, grams: String) = ingredients.update { list ->
        list.mapIndexed { i, ing -> if (i == index) ing.copy(grams = grams) else ing }
    }

    fun remove(index: Int) = ingredients.update { it.filterIndexed { i, _ -> i != index } }

    val canSave get() = name.value.isNotBlank() && ingredients.value.any { (it.grams.toDoubleOrNull() ?: 0.0) > 0 }

    fun save(onSaved: (Int) -> Unit) = viewModelScope.launch {
        val parts = ingredients.value.mapNotNull { ing -> ing.grams.toDoubleOrNull()?.takeIf { it > 0 }?.let { ing.food.id to it } }
        val cooked = cookedGrams.value.toDoubleOrNull()?.takeIf { it > 0 }
        onSaved(repo.saveRecipe(recipeId, name.value.trim(), emoji.value, parts, cooked))
    }

    companion object {
        val MAIN = listOf(HealthDietParser.KCAL, HealthDietParser.PROTEIN, HealthDietParser.FAT, HealthDietParser.CARBS)
    }
}

private fun formatGramsInput(value: Double) =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(java.util.Locale.ROOT, value)

private val RECIPE_STICKERS = listOf("🍲", "🥗", "🍝", "🍜", "🍛", "🥘", "🍳", "🥞", "🥣", "🍚", "🌯", "🥪", "🍕", "🍰")

@Composable
fun RecipeScreen(recipeId: Int?, onBack: () -> Unit, onSaved: (Int) -> Unit) {
    val vm = appViewModel { RecipeViewModel(it.repository, it.sources, recipeId) }
    val name by vm.name.collectAsStateWithLifecycle()
    val emoji by vm.emoji.collectAsStateWithLifecycle()
    val ingredients by vm.ingredients.collectAsStateWithLifecycle()
    val cooked by vm.cookedGrams.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (recipeId == null) R.string.new_recipe else R.string.edit_recipe)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Sticker(emoji, MaterialTheme.colorScheme.surfaceContainerHighest, size = 56.dp)
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { vm.name.value = it },
                            label = { Text(stringResource(R.string.recipe_name)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RECIPE_STICKERS.forEach { s ->
                            Box(Modifier.clip(CircleShape).clickable { vm.emoji.value = s }) {
                                Sticker(s, if (s == emoji) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, size = 40.dp)
                            }
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text(
                        stringResource(R.string.ingredients),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    if (ingredients.isEmpty()) {
                        Text(
                            stringResource(R.string.ingredients_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = muted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    ingredients.forEachIndexed { i, ing ->
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FoodAvatar(ing.food.emoji, ing.food.categoryId, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(ing.food.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = ing.grams,
                                onValueChange = { t -> sanitizeDecimal(t)?.let { vm.setGrams(i, it) } },
                                suffix = { Text(stringResource(R.string.unit_g)) },
                                singleLine = true,
                                modifier = Modifier.width(96.dp),
                            )
                            IconButton(onClick = { vm.remove(i) }) {
                                Icon(Icons.Default.Close, stringResource(R.string.remove_ingredient), tint = muted)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        placeholder = { Text(stringResource(R.string.add_ingredient)) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    results.forEach { food ->
                        ListItem(
                            leadingContent = { FoodAvatar(food.emoji, food.categoryId, size = 36.dp) },
                            headlineContent = { Text(food.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                food.kcal?.let { Text(stringResource(R.string.kcal_per_100, formatAmount(it))) }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { vm.add(food) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = cooked,
                onValueChange = { t -> sanitizeDecimal(t)?.let { vm.cookedGrams.value = it } },
                label = { Text(stringResource(R.string.cooked_weight)) },
                suffix = { Text(stringResource(R.string.unit_g)) },
                supportingText = {
                    Text(stringResource(R.string.cooked_weight_hint, formatAmount(summary?.ingredientsGrams ?: 0.0)))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            summary?.let { s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.whole_recipe, formatAmount(s.finalGrams)), style = MaterialTheme.typography.titleMedium)
                        Text(macroLine(s.wholeDish, 1.0), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.per_100g), style = MaterialTheme.typography.titleSmall)
                        Text(macroLine(s.wholeDish, 100 / s.finalGrams), style = MaterialTheme.typography.bodyMedium, color = muted)
                        Text(stringResource(R.string.recipe_micros_note), style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }
            }

            Button(onClick = { vm.save(onSaved) }, enabled = vm.canSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save_recipe))
            }
        }
    }
}

@Composable
private fun macroLine(whole: Map<String, Double>, factor: Double): String {
    fun v(n: String) = formatAmount((whole[n] ?: 0.0) * factor)
    return stringResource(
        R.string.macro_line,
        v(HealthDietParser.KCAL), v(HealthDietParser.PROTEIN), v(HealthDietParser.FAT), v(HealthDietParser.CARBS),
    )
}
