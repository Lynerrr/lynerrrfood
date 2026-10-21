@file:OptIn(ExperimentalMaterial3Api::class)

package com.lynerrr.food.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lynerrr.food.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lynerrr.food.data.FoodRepository
import com.lynerrr.food.data.NutrientCatalog
import com.lynerrr.food.data.NutrientGroup
import com.lynerrr.food.data.Ratio
import com.lynerrr.food.data.computeRatios
import com.lynerrr.food.data.macroSplit
import com.lynerrr.food.data.NutrientValue
import com.lynerrr.food.data.groupNutrients
import com.lynerrr.food.data.Units
import com.lynerrr.food.data.buildNutrientValues
import com.lynerrr.food.data.formatAmount
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One food's share of a nutrient on a day, in the nutrient's display unit. */
data class Source(val foodName: String, val grams: Double, val amount: Double)

class NutrientsViewModel(private val repo: FoodRepository, day: Long) : ViewModel() {
    private val values = combine(repo.intake(day), repo.nutrientDefs(), repo.targets(day)) { intake, defs, targets ->
        buildNutrientValues(defs, intake, includeMissing = true, overrides = targets)
    }

    val groups = values.map { groupNutrients(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ratios = values.map { computeRatios(it) to macroSplit(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Ratio>() to null)

    val bodyWeight = repo.plan.map { it.bodyWeight(day) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Nutrient name -> foods that provided it, biggest first. */
    val sources = combine(repo.contributions(day), repo.nutrientDefs()) { contributions, defs ->
        val units = defs.associate { it.name to it.unit }
        contributions.groupBy { it.nutrient }.mapValues { (name, rows) ->
            rows.groupBy { it.foodId }.values.map { food ->
                val amount = food.sumOf { Units.convert(it.amount, it.unit, units[name] ?: it.unit) ?: 0.0 }
                Source(food.first().foodName, food.first().grams, amount)
            }.filter { it.amount > 0 }.sortedByDescending { it.amount }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setNorm(name: String, value: Double?) = viewModelScope.launch { repo.setUserNorm(name, value) }
}

@Composable
fun NutrientsScreen(day: Long, onBack: () -> Unit, onBestSources: (nutrient: String) -> Unit) {
    val vm = appViewModel { NutrientsViewModel(it.repository, day) }
    val groups by vm.groups.collectAsStateWithLifecycle()
    val ratios by vm.ratios.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val bodyWeight by vm.bodyWeight.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    var showMore by remember { mutableStateOf(emptySet<NutrientGroup>()) }
    var editing by remember { mutableStateOf<NutrientValue?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nutrients_title, dayLabel(day))) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupOverview(groups) { group ->
                        val index = groups.indexOfFirst { it.group == group }
                        if (index >= 0) scope.launch { listState.animateScrollToItem(index + 2) }
                    }
                    Text(
                        stringResource(if (groups.isEmpty()) R.string.nutrients_empty else R.string.nutrients_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            item(key = "ratios") {
                RatiosCard(ratios.first, ratios.second, Modifier.padding(horizontal = 16.dp))
            }
            items(groups, key = { it.group }) { group ->
                Box(Modifier.padding(horizontal = 16.dp)) {
                    NutrientGroupCard(
                        group = group,
                        bodyWeight = bodyWeight,
                        showMore = group.group in showMore,
                        onToggleMore = {
                            showMore = if (group.group in showMore) showMore - group.group else showMore + group.group
                        },
                        onClick = { (v) -> expanded = if (v.name in expanded) expanded - v.name else expanded + v.name },
                        below = { (v) ->
                            AnimatedVisibility(v.name in expanded) {
                                SourceList(
                                    v, sources[v.name].orEmpty(),
                                    onEditNorm = { editing = v },
                                    onBestSources = { onBestSources(v.name) },
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    editing?.let { nutrient ->
        NormDialog(
            nutrient = nutrient,
            onDismiss = { editing = null },
            onSave = { vm.setNorm(nutrient.name, it); editing = null },
        )
    }
}

@Composable
private fun SourceList(
    nutrient: NutrientValue,
    sources: List<Source>,
    onEditNorm: () -> Unit,
    onBestSources: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (sources.isEmpty()) {
            Text(stringResource(R.string.no_sources_today), style = MaterialTheme.typography.bodySmall)
        }
        val total = sources.sumOf { it.amount }
        sources.forEach { source ->
            val share = if (total > 0) source.amount / total else 0.0
            Column {
                Row {
                    Text(source.foodName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.source_amount, "${formatAmount(source.amount)} ${unitLabel(nutrient.unit)}", (share * 100).roundToInt()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { share.toFloat() },
                        modifier = Modifier.weight(1f).height(4.dp),
                    )
                    Text(
                        stringResource(R.string.grams_value, formatAmount(source.grams)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FilledTonalButton(onClick = onBestSources) { Text(stringResource(R.string.best_sources_button)) }
            TextButton(onClick = onEditNorm) {
                Text(stringResource(if (nutrient.target != null || nutrient.limit != null) R.string.change_norm else R.string.set_norm))
            }
        }
    }
}

@Composable
private fun NormDialog(nutrient: NutrientValue, onDismiss: () -> Unit, onSave: (Double?) -> Unit) {
    var text by remember { mutableStateOf((nutrient.target ?: nutrient.limit)?.let(::formatAmount) ?: "") }
    val parsed = text.toDoubleOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(NutrientCatalog.info(nutrient.name, nutrient.section).label(isRussian())) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { sanitizeDecimal(it)?.let { v -> text = v } },
                    label = { Text(stringResource(R.string.daily_norm)) },
                    suffix = { Text(unitLabel(nutrient.unit)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(parsed) }, enabled = parsed != null) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (nutrient.userEdited) TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.reset_norm)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
