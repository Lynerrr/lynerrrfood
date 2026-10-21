@file:OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)

package com.lynerrr.food.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lynerrr.food.R
import com.lynerrr.food.data.CategoryEntity
import com.lynerrr.food.data.ProductCategory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lynerrr.food.data.FoodRepository
import com.lynerrr.food.data.FoodSearch
import com.lynerrr.food.data.FoodSourceSettings
import com.lynerrr.food.data.allows
import com.lynerrr.food.data.SyncManager
import com.lynerrr.food.data.SyncState
import com.lynerrr.food.data.formatAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SearchViewModel(repo: FoodRepository, val sync: SyncManager, sources: FoodSourceSettings) : ViewModel() {
    val query = MutableStateFlow("")
    val categoryId = MutableStateFlow<Int?>(null)

    private val index = combine(repo.allFoods(), sources.enabled) { foods, enabled -> FoodSearch(foods.filter { enabled.allows(it.id) }) }
        .flowOn(Dispatchers.Default)

    val results = combine(index, query.debounce(150), categoryId) { search, q, c -> search.search(q, c) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories = repo.categories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun SearchScreen(
    initialQuery: String,
    onBack: () -> Unit,
    onOpenFood: (Int) -> Unit,
    onCreateFood: (name: String, barcode: String?) -> Unit,
    onPhotoEstimate: () -> Unit,
    onCreateRecipe: () -> Unit,
) {
    val vm = appViewModel { SearchViewModel(it.repository, it.sync, it.sources).apply { query.value = initialQuery } }
    val barcodeVm = appViewModel { BarcodeViewModel(it.products) }
    val context = LocalContext.current
    BarcodeDialogs(barcodeVm, onCreate = { name, code -> onCreateFood(name, code) })
    val query by vm.query.collectAsStateWithLifecycle()
    val categoryId by vm.categoryId.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val syncState by vm.sync.state.collectAsStateWithLifecycle()

    BackHandler(enabled = categoryId != null) { vm.categoryId.value = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_food)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Default.Clear, stringResource(R.string.clear)) }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            categoryId?.let { id ->
                InputChip(
                    selected = true,
                    onClick = { vm.categoryId.value = null },
                    label = { Text(categories.firstOrNull { it.id == id }?.let { categoryName(it) } ?: stringResource(R.string.category)) },
                    trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.clear_category)) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            // The bundled databases load on first launch; show their progress until search has everything.
            if (syncState.running) ImportProgressCard(syncState)

            LazyColumn(Modifier.fillMaxSize()) {
                if (query.isBlank() && categoryId == null) {
                    item {
                        QuickActions(
                            onScan = {
                                startBarcodeScan(
                                    context,
                                    onCode = { code -> barcodeVm.lookUp(code, onOpenFood) },
                                    onError = { message -> barcodeVm.state.value = ScanState.Failed(message) },
                                )
                            },
                            onPhoto = onPhotoEstimate,
                            onCreate = { onCreateFood("", null) },
                            onRecipe = onCreateRecipe,
                        )
                    }
                    // Two separate sources: packaged products (Open Food Facts) and foods by type (health-diet.ru).
                    val (packaged, basic) = categories.partition { ProductCategory.of(it.id) != null }
                    listOf(R.string.section_packaged to packaged, R.string.section_basic_foods to basic).forEach { (title, list) ->
                        if (list.isEmpty()) return@forEach
                        item(key = "header:$title") {
                            Text(
                                stringResource(title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(list, key = { "cat:${it.id}" }) { category ->
                            ListItem(
                                leadingContent = { FoodAvatar(null, category.id) },
                                headlineContent = { Text(categoryName(category)) },
                                modifier = Modifier.clickable { vm.categoryId.value = category.id },
                            )
                        }
                    }
                } else {
                    if (categoryId == FoodRepository.CUSTOM_CATEGORY && query.isBlank()) {
                        item { CreateFoodItem(stringResource(R.string.create_food)) { onCreateFood("", null) } }
                    }
                    if (categoryId == FoodRepository.RECIPE_CATEGORY && query.isBlank()) {
                        item { CreateFoodItem(stringResource(R.string.new_recipe)) { onCreateRecipe() } }
                    }
                    items(results, key = { "food:${it.id}" }) { food ->
                        ListItem(
                            leadingContent = { FoodAvatar(food.emoji, food.categoryId) },
                            headlineContent = { Text(food.name) },
                            supportingContent = {
                                val kcal = food.kcal?.let { stringResource(R.string.kcal_per_100, formatAmount(it)) }
                                Text(listOfNotNull(food.brand, kcal).joinToString(" · "))
                            },
                            modifier = Modifier.clickable { onOpenFood(food.id) },
                        )
                    }
                    if (query.isNotBlank()) {
                        item { CreateFoodItem(stringResource(R.string.create_food_named, query.trim())) { onCreateFood(query.trim(), null) } }
                    }
                }
            }
        }
    }
}

/** Barcode, AI photo, create-your-own and recipe, as four tiles above the categories. */
@Composable
private fun QuickActions(onScan: () -> Unit, onPhoto: () -> Unit, onCreate: () -> Unit, onRecipe: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            Triple("📷", R.string.action_scan, onScan),
            Triple("✨", R.string.action_photo, onPhoto),
            Triple("➕", R.string.action_create, onCreate),
            Triple("🍲", R.string.action_recipe, onRecipe),
        ).forEach { (sticker, label, action) ->
            Column(
                Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
                    .clickable(onClick = action)
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(sticker, fontSize = 26.sp)
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CreateFoodItem(text: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Sticker("➕", MaterialTheme.colorScheme.primaryContainer) },
        headlineContent = { Text(text, color = MaterialTheme.colorScheme.primary) },
        supportingContent = { Text(stringResource(R.string.create_food_sub)) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ImportProgressCard(state: SyncState) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(syncTaskLabel(state.task), style = MaterialTheme.typography.titleSmall)
            LinearProgressIndicator(
                progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.progress_count, state.done, state.total), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The custom-food category's stored name is a fallback; show it in the current language. */
@Composable
private fun categoryName(category: CategoryEntity): String {
    ProductCategory.of(category.id)?.let { return stringResource(it.title) }
    if (category.id == FoodRepository.AI_CATEGORY) return stringResource(R.string.ai_estimates)
    if (category.id == FoodRepository.RECIPE_CATEGORY) return stringResource(R.string.my_recipes)
    return if (category.id == FoodRepository.CUSTOM_CATEGORY) stringResource(R.string.my_foods) else category.name
}
