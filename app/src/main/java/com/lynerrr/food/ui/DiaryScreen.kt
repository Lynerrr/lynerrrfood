@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)

package com.lynerrr.food.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lynerrr.food.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lynerrr.food.data.DayPlan
import com.lynerrr.food.data.DiaryItem
import com.lynerrr.food.data.FoodRepository
import com.lynerrr.food.data.MEALS
import com.lynerrr.food.data.NutrientValue
import com.lynerrr.food.data.buildNutrientValues
import com.lynerrr.food.data.formatAmount
import com.lynerrr.food.data.nutrientKey
import com.lynerrr.food.net.HealthDietParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class DiaryViewModel(private val repo: FoodRepository) : ViewModel() {
    val day = MutableStateFlow(LocalDate.now().toEpochDay())

    val items = day.flatMapLatest { repo.diaryItems(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Calories and macros for the day against their targets (adaptive plan if set up, else the site's norms). */
    val summary = day.flatMapLatest { d ->
        combine(repo.intake(d), repo.nutrientDefs(), repo.targets(d)) { intake, defs, targets ->
            val main = listOf(HealthDietParser.KCAL, HealthDietParser.PROTEIN, HealthDietParser.FAT, HealthDietParser.CARBS)
            buildNutrientValues(defs.filter { it.name in main }, intake, includeMissing = true, overrides = targets)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dayPlan = combine(day, repo.plan) { d, plan -> plan.on(d)?.takeIf { it.day == d } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val hasPlan = repo.profile().map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Calories ÷ target for each logged day, for the calendar. */
    val adherence = combine(repo.dailyKcal(), repo.plan, repo.nutrientDefs()) { kcal, plan, defs ->
        val def = defs.firstOrNull { it.name == HealthDietParser.KCAL }
        kcal.associate { (d, eaten) ->
            val target = def?.userNorm ?: plan.targets(d)[nutrientKey(HealthDietParser.KCAL)] ?: def?.normLow
            d to target?.let { eaten / it }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val weighed = repo.weights().map { list -> list.map { it.epochDay }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun shiftDay(by: Long) = day.update { it + by }
    fun setWeight(kg: Double) = viewModelScope.launch { repo.setWeight(day.value, kg) }
    fun deleteWeight() = viewModelScope.launch { repo.deleteWeight(day.value) }
    fun update(id: Long, grams: Double, meal: Int) = viewModelScope.launch { repo.updateEntry(id, grams, meal) }
    fun delete(id: Long) = viewModelScope.launch { repo.deleteEntry(id) }
}

@Composable
fun DiaryScreen(
    onAddFood: (day: Long) -> Unit,
    onShowNutrients: (day: Long) -> Unit,
    onOpenProgress: () -> Unit,
    onOpenData: () -> Unit,
) {
    val vm = appViewModel { DiaryViewModel(it.repository) }
    val day by vm.day.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val dayPlan by vm.dayPlan.collectAsStateWithLifecycle()
    val hasPlan by vm.hasPlan.collectAsStateWithLifecycle()
    val adherence by vm.adherence.collectAsStateWithLifecycle()
    val weighed by vm.weighed.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<DiaryItem?>(null) }
    var showCalendar by remember { mutableStateOf(false) }
    var editingWeight by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.food_diary)) },
                actions = {
                    IconButton(onClick = onOpenProgress) { Icon(ShowChartIcon, stringResource(R.string.cd_plan)) }
                    IconButton(onClick = onOpenData) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAddFood(day) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.add_food)) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.shiftDay(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.prev_day))
                    }
                    Row(
                        Modifier.weight(1f).clickable { showCalendar = true }.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.DateRange, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(dayLabel(day), style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = { vm.shiftDay(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_day))
                    }
                }
            }
            item {
                SummaryCard(
                    summary = summary,
                    dayPlan = dayPlan,
                    hasPlan = hasPlan,
                    onShowNutrients = { onShowNutrients(day) },
                    onEditWeight = { editingWeight = true },
                    onSetUpPlan = onOpenProgress,
                )
            }

            if (items.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("🍽️", fontSize = 40.sp)
                        Text(
                            stringResource(R.string.nothing_logged),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(items.groupBy { it.meal }.toSortedMap().entries.toList(), key = { "meal:${it.key}" }) { (meal, entries) ->
                MealCard(meal, entries, onClick = { editing = it })
            }
        }
    }

    if (showCalendar) {
        CalendarSheet(
            selected = day,
            adherence = adherence,
            weighed = weighed,
            onSelect = { vm.day.value = it; showCalendar = false },
            onDismiss = { showCalendar = false },
        )
    }
    if (editingWeight) {
        WeightDialog(
            day = day,
            initial = dayPlan?.weight ?: dayPlan?.trend,
            onDismiss = { editingWeight = false },
            onSave = { vm.setWeight(it); editingWeight = false },
            onDelete = dayPlan?.weight?.let { { vm.deleteWeight(); editingWeight = false } },
        )
    }

    editing?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onDismiss = { editing = null },
            onSave = { grams, meal -> vm.update(entry.id, grams, meal); editing = null },
            onDelete = { vm.delete(entry.id); editing = null },
        )
    }
}

@Composable
private fun SummaryCard(
    summary: List<NutrientValue>,
    dayPlan: DayPlan?,
    hasPlan: Boolean,
    onShowNutrients: () -> Unit,
    onEditWeight: () -> Unit,
    onSetUpPlan: () -> Unit,
) {
    val byName = summary.associateBy { it.name }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            byName[HealthDietParser.KCAL]?.let { CalorieHero(it) }
                ?: Text(stringResource(R.string.add_first_food), style = MaterialTheme.typography.bodyMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(HealthDietParser.PROTEIN, "💪", stringResource(R.string.protein)),
                    Triple(HealthDietParser.FAT, "🧈", stringResource(R.string.fat)),
                    Triple(HealthDietParser.CARBS, "🍞", stringResource(R.string.carbs)),
                ).forEach { (name, sticker, label) ->
                    byName[name]?.let { MacroTile(it, sticker, label, Modifier.weight(1f)) }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
                    .clickable(onClick = onEditWeight)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⚖️", fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        dayPlan?.weight?.let { stringResource(R.string.kg_value, formatAmount(it)) } ?: stringResource(R.string.no_weigh_in),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    dayPlan?.trend?.let {
                        Text(stringResource(R.string.trend_kg, formatAmount(it)), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(stringResource(if (dayPlan?.weight != null) R.string.edit else R.string.log),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }

            if (!hasPlan) {
                Text(
                    stringResource(R.string.setup_plan_link),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onSetUpPlan),
                )
            }

            FilledTonalButton(onClick = onShowNutrients, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.vitamins_more_button))
            }
        }
    }
}

@Composable
private fun MealCard(meal: Int, entries: List<DiaryItem>, onClick: (DiaryItem) -> Unit) {
    val kcal = entries.sumOf { (it.kcal ?: 0.0) * it.grams / 100 }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(MEAL_STICKERS[meal], fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(MEALS[meal]), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.kcal_value, formatAmount(kcal)), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        entries.forEach { DiaryRow(it, onClick = { onClick(it) }) }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun DiaryRow(entry: DiaryItem, onClick: () -> Unit) {
    val factor = entry.grams / 100
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FoodAvatar(entry.emoji, entry.categoryId)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.foodName, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(
                    R.string.entry_macros,
                    formatAmount(entry.grams),
                    formatAmount((entry.protein ?: 0.0) * factor),
                    formatAmount((entry.fat ?: 0.0) * factor),
                    formatAmount((entry.carbs ?: 0.0) * factor),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("${formatAmount((entry.kcal ?: 0.0) * factor)}", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun EditEntryDialog(
    entry: DiaryItem,
    onDismiss: () -> Unit,
    onSave: (grams: Double, meal: Int) -> Unit,
    onDelete: () -> Unit,
) {
    var grams by remember { mutableStateOf(formatAmount(entry.grams)) }
    var meal by remember { mutableIntStateOf(entry.meal) }
    val parsed = grams.toDoubleOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.foodName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GramsField(grams, { grams = it }, Modifier.fillMaxWidth())
                MealPicker(meal) { meal = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let { onSave(it, meal) } }, enabled = parsed != null) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
