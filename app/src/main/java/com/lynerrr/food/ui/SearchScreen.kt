@file:OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)

package com.lynerrr.food.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lynerrr.food.R
import com.lynerrr.food.data.CategoryEntity
import com.lynerrr.food.data.FoodEntity
import com.lynerrr.food.data.FoodGrouping
import com.lynerrr.food.data.FoodIds
import com.lynerrr.food.data.FoodRepository
import com.lynerrr.food.data.FoodSource
import com.lynerrr.food.data.FoodSearch
import com.lynerrr.food.data.FoodSourceSettings
import com.lynerrr.food.data.ProductCategory
import com.lynerrr.food.data.SyncManager
import com.lynerrr.food.data.SyncState
import com.lynerrr.food.data.UsedFood
import com.lynerrr.food.data.allows
import com.lynerrr.food.data.formatAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The "your foods" lists: what the user eats most, what they logged last, and what they made themselves. */
enum class YourFoodsTab(@StringRes val title: Int) {
    FREQUENT(R.string.tab_frequent),
    RECENT(R.string.tab_recent),
    MINE(R.string.tab_mine),
}

/** A food in the "your foods" lists, with the amount to suggest: what the user logged last time, if anything. */
data class QuickFood(
    val id: Int,
    val name: String,
    val categoryId: Int?,
    val emoji: String?,
    val brand: String?,
    val kcal: Double?,
    val grams: Double?,
)

private fun UsedFood.toQuick() = QuickFood(id, name, categoryId, emoji, brand, kcal, lastGrams)

class SearchViewModel(
    private val repo: FoodRepository,
    val sync: SyncManager,
    sources: FoodSourceSettings,
    private val day: Long,
) : ViewModel() {
    val query = MutableStateFlow("")
    val categoryId = MutableStateFlow<Int?>(null)
    val tab = MutableStateFlow(YourFoodsTab.FREQUENT)

    private val index = combine(repo.allFoods(), sources.enabled) { foods, enabled -> FoodSearch(foods.filter { enabled.allows(it.id) }) }
        .flowOn(Dispatchers.Default)

    val results = combine(index, query.debounce(150), categoryId) { search, q, c -> search.search(q, c) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories = repo.categories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** One source's foods in the open category, as families of variants. */
    data class BrowseSection(val source: FoodSource?, val families: List<FoodGrouping.Family>)

    /**
     * The open category for browsing: by source (the user's own, then health-diet.ru, then USDA, then packaged), each
     * as families of variants. Foods without nutrient data are left out here; search still finds them.
     */
    val browse = combine(repo.allFoods(), sources.enabled, categoryId) { all, enabled, cat ->
        if (cat == null) return@combine emptyList()
        all.filter { it.categoryId == cat && it.detailsFetchedAt != null && enabled.allows(it.id) }
            .groupBy { FoodIds.source(it.id) }
            .toSortedMap(compareBy<FoodSource?> { it?.ordinal ?: -1 })
            .map { (source, foods) -> BrowseSection(source, FoodGrouping.families(foods)) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val used = combine(repo.usedFoods(LocalDate.now().toEpochDay() - HISTORY_DAYS), sources.enabled) { list, enabled ->
        list.filter { enabled.allows(it.id) }
    }

    val yourFoods = combine(tab, used, repo.allFoods()) { t, used, all ->
        when (t) {
            // Eaten on the most days first: a daily breakfast beats one big week of something.
            YourFoodsTab.FREQUENT -> used.sortedWith(compareByDescending<UsedFood> { it.days }.thenByDescending { it.lastUsed })
                .map { it.toQuick() }
            YourFoodsTab.RECENT -> used.sortedByDescending { it.lastUsed }.map { it.toQuick() }
            YourFoodsTab.MINE -> {
                val lastGrams = used.associate { it.id to it.lastGrams }
                // Custom ids count down, so the newest come first.
                all.filter { it.id < 0 }.sortedBy { it.id }
                    .map { QuickFood(it.id, it.name, it.categoryId, it.emoji, it.brand, it.kcal, lastGrams[it.id] ?: it.servingGrams) }
            }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Logs [food] in one tap with its suggested amount, to the meal matching the time of day. */
    fun quickAdd(food: QuickFood, onAdded: (entryId: Long, grams: Double) -> Unit) = viewModelScope.launch {
        val grams = food.grams ?: 100.0
        onAdded(repo.addToDiary(day, currentMeal(), food.id, grams), grams)
    }

    fun undo(entryId: Long) = viewModelScope.launch { repo.deleteEntry(entryId) }

    private companion object {
        const val HISTORY_DAYS = 90
    }
}

/** Rows of "your foods" shown before "show more". */
private const val YOUR_FOODS_SHORT = 6

/** Foods by type in the order people look for them, rather than alphabetically; unknown ones go last. */
private val TYPE_ORDER = listOf(
    24512, 24516, 24513, 24502, 24503, 24501, 24507, 24504, 24518, 24520, 24519, 24522, 24524, 24523, 24517,
    24515, 24514, 24511, 24509, 24525, 24528, 24506, 24529, 24527, 24526, 24508,
)
private val PRODUCT_ORDER = listOf(
    ProductCategory.DAIRY, ProductCategory.MEAT_FISH, ProductCategory.BREAD, ProductCategory.CEREALS, ProductCategory.MEALS,
    ProductCategory.SAUCES, ProductCategory.SNACKS, ProductCategory.SWEETS, ProductCategory.BISCUITS, ProductCategory.ICE_CREAM,
    ProductCategory.DRINKS, ProductCategory.JUICES, ProductCategory.ALCOHOL, ProductCategory.OTHER,
).map { it.id }

@Composable
fun SearchScreen(
    day: Long,
    initialQuery: String,
    onBack: () -> Unit,
    /** [grams]: amount to start with, e.g. what the user logged last time. */
    onOpenFood: (id: Int, grams: Double?) -> Unit,
    onCreateFood: (name: String, barcode: String?) -> Unit,
    onPhotoEstimate: () -> Unit,
    onCreateRecipe: () -> Unit,
) {
    val vm = appViewModel { SearchViewModel(it.repository, it.sync, it.sources, day).apply { query.value = initialQuery } }
    val barcodeVm = appViewModel { BarcodeViewModel(it.products) }
    val context = LocalContext.current
    BarcodeDialogs(barcodeVm, onCreate = { name, code -> onCreateFood(name, code) })
    val query by vm.query.collectAsStateWithLifecycle()
    val categoryId by vm.categoryId.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val syncState by vm.sync.state.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val yourFoods by vm.yourFoods.collectAsStateWithLifecycle()
    val browse by vm.browse.collectAsStateWithLifecycle()
    var openFamilies by remember(categoryId) { mutableStateOf(emptySet<String>()) }
    var showAllYours by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val addedText = stringResource(R.string.quick_added)
    val undoText = stringResource(R.string.undo)

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
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                                    onCode = { code -> barcodeVm.lookUp(code) { id -> onOpenFood(id, null) } },
                                    onError = { message -> barcodeVm.state.value = ScanState.Failed(message) },
                                )
                            },
                            onPhoto = onPhotoEstimate,
                            onCreate = { onCreateFood("", null) },
                            onRecipe = onCreateRecipe,
                        )
                    }

                    item(key = "yours") {
                        SectionTitle(stringResource(R.string.your_foods))
                        Row(
                            Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            YourFoodsTab.entries.forEach { t ->
                                FilterChip(
                                    selected = tab == t,
                                    onClick = { vm.tab.value = t; showAllYours = false },
                                    label = { Text(stringResource(t.title)) },
                                )
                            }
                        }
                    }
                    if (yourFoods.isEmpty()) {
                        item(key = "yours-empty") {
                            Text(
                                stringResource(if (tab == YourFoodsTab.MINE) R.string.your_foods_empty_mine else R.string.your_foods_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                    items(
                        if (showAllYours) yourFoods.take(50) else yourFoods.take(YOUR_FOODS_SHORT),
                        key = { "yours:${tab.name}:${it.id}" },
                    ) { food ->
                        YourFoodRow(
                            food,
                            onOpen = { onOpenFood(food.id, food.grams) },
                            onAdd = {
                                vm.quickAdd(food) { entryId, grams ->
                                    scope.launch {
                                        snackbar.currentSnackbarData?.dismiss()
                                        val result = snackbar.showSnackbar(
                                            addedText.format(formatAmount(grams), food.name),
                                            actionLabel = undoText,
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) vm.undo(entryId)
                                    }
                                }
                            },
                        )
                    }
                    if (yourFoods.size > YOUR_FOODS_SHORT) {
                        item(key = "yours-more") {
                            TextButton(onClick = { showAllYours = !showAllYours }, modifier = Modifier.padding(horizontal = 4.dp)) {
                                Text(
                                    if (showAllYours) stringResource(R.string.show_less)
                                    else stringResource(R.string.show_more, minOf(yourFoods.size, 50) - YOUR_FOODS_SHORT),
                                )
                            }
                        }
                    }

                    // Two separate sources: foods by type (health-diet.ru, USDA) and packaged products (Open Food Facts).
                    // The user's own categories are under "Mine" above.
                    val byType = categories.filter { it.id > 0 && ProductCategory.of(it.id) == null }
                        .sortedBy { TYPE_ORDER.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                    val packaged = categories.filter { ProductCategory.of(it.id) != null }
                        .sortedBy { PRODUCT_ORDER.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                    listOf(R.string.section_basic_foods to byType, R.string.section_packaged to packaged).forEach { (title, list) ->
                        if (list.isEmpty()) return@forEach
                        item(key = "header:$title") { SectionTitle(stringResource(title)) }
                        items(list.chunked(3), key = { row -> "cats:" + row.joinToString(",") { it.id.toString() } }) { row ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                row.forEach { category ->
                                    CategoryTile(category, Modifier.weight(1f)) { vm.categoryId.value = category.id }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    item { Spacer(Modifier.padding(bottom = 16.dp)) }
                } else if (query.isBlank()) {
                    // Browsing a category: variants folded into families, one section per source.
                    if (categoryId == FoodRepository.CUSTOM_CATEGORY) {
                        item { CreateFoodItem(stringResource(R.string.create_food)) { onCreateFood("", null) } }
                    }
                    if (categoryId == FoodRepository.RECIPE_CATEGORY) {
                        item { CreateFoodItem(stringResource(R.string.new_recipe)) { onCreateRecipe() } }
                    }
                    browse.forEach { section ->
                        if (browse.size > 1) {
                            item(key = "src:${section.source}") { SectionTitle(sourceTitle(section.source)) }
                        }
                        section.families.forEach { family ->
                            if (family.foods.size == 1) {
                                val food = family.foods.single()
                                item(key = "food:${food.id}") { FoodRow(food) { onOpenFood(food.id, null) } }
                            } else {
                                val key = "${section.source}:${family.label}"
                                val open = key in openFamilies
                                item(key = "fam:$key") {
                                    FamilyRow(family, open) { openFamilies = if (open) openFamilies - key else openFamilies + key }
                                }
                                if (open) {
                                    items(family.foods, key = { "fam:$key:${it.id}" }) { food ->
                                        FoodRow(food, indented = true) { onOpenFood(food.id, null) }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(results, key = { "food:${it.id}" }) { food -> FoodRow(food) { onOpenFood(food.id, null) } }
                    if (query.isNotBlank()) {
                        item { CreateFoodItem(stringResource(R.string.create_food_named, query.trim())) { onCreateFood(query.trim(), null) } }
                    }
                }
            }
        }
    }
}

/** A food in search results or a category; [indented] when it's a variant under its family's row. */
@Composable
private fun FoodRow(food: FoodEntity, indented: Boolean = false, onClick: () -> Unit) {
    ListItem(
        leadingContent = { FoodAvatar(food.emoji, food.categoryId, size = if (indented) 32.dp else 40.dp) },
        headlineContent = { Text(food.name) },
        supportingContent = {
            val kcal = food.kcal?.let { stringResource(R.string.kcal_per_100, formatAmount(it)) }
            Text(listOfNotNull(food.brand, kcal).joinToString(" · "))
        },
        modifier = Modifier.clickable(onClick = onClick).padding(start = if (indented) 32.dp else 0.dp),
    )
}

/** Variants of one food, folded: name, how many, their calorie range; tap to unfold. */
@Composable
private fun FamilyRow(family: FoodGrouping.Family, open: Boolean, onToggle: () -> Unit) {
    val kcal = family.foods.mapNotNull { it.kcal }
    ListItem(
        leadingContent = { FoodAvatar(null, family.foods.first().categoryId) },
        headlineContent = { Text(family.label, fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            Text(
                listOfNotNull(
                    pluralStringResource(R.plurals.kinds, family.foods.size, family.foods.size),
                    kcal.takeIf { it.isNotEmpty() }?.let { k ->
                        val (lo, hi) = k.min() to k.max()
                        if (formatAmount(lo) == formatAmount(hi)) stringResource(R.string.kcal_per_100, formatAmount(lo))
                        else stringResource(R.string.kcal_range_per_100, formatAmount(lo), formatAmount(hi))
                    },
                ).joinToString(" · "),
            )
        },
        trailingContent = {
            Icon(
                if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                stringResource(if (open) R.string.collapse else R.string.expand),
            )
        },
        modifier = Modifier.clickable(onClick = onToggle),
    )
}

@Composable
private fun sourceTitle(source: FoodSource?): String = when (source) {
    null -> stringResource(R.string.tab_mine)
    FoodSource.HEALTH_DIET -> stringResource(R.string.browse_source_health_diet)
    FoodSource.USDA -> stringResource(R.string.browse_source_usda)
    FoodSource.PRODUCTS -> stringResource(R.string.packaged_products)
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** One of the user's foods: tap to open it with their usual amount, + to log that amount right away. */
@Composable
private fun YourFoodRow(food: QuickFood, onOpen: () -> Unit, onAdd: () -> Unit) {
    ListItem(
        leadingContent = { FoodAvatar(food.emoji, food.categoryId) },
        headlineContent = { Text(food.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(
                    food.grams?.let { stringResource(R.string.usually_grams, formatAmount(it)) },
                    food.kcal?.let { k -> food.grams?.let { stringResource(R.string.kcal_value, formatAmount(k * it / 100)) } },
                ).joinToString(" · "),
            )
        },
        trailingContent = {
            FilledTonalIconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, stringResource(R.string.quick_add))
            }
        },
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

@Composable
private fun CategoryTile(category: CategoryEntity, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(foodSticker(null, category.id), fontSize = 24.sp)
        // Under "Packaged products" the "(brands)" that tells these apart elsewhere is just noise.
        val name = categoryName(category)
        Text(
            if (ProductCategory.of(category.id) != null) name.substringBefore(" (") else name,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            minLines = 2,
        )
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
