@file:OptIn(ExperimentalMaterial3Api::class)

package com.lynerrr.food.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lynerrr.food.R
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lynerrr.food.data.FoodRepository
import com.lynerrr.food.data.FoodVariant
import com.lynerrr.food.data.NutrientCatalog
import com.lynerrr.food.data.NutrientGroup
import com.lynerrr.food.data.NutrientInfo
import com.lynerrr.food.data.formatAmount
import com.lynerrr.food.net.HealthDietParser
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Edits a custom food ([foodId]), or creates one: blank, or as the user's own version of [copyOf]. */
class CustomFoodViewModel(private val repo: FoodRepository, private val foodId: Int?, private val copyOf: Int?) : ViewModel() {
    /** The food being edited or copied: values per 100 g with their units, and each nutrient's group. */
    data class Existing(
        val name: String,
        val emoji: String?,
        val categoryId: Int?,
        val servingGrams: Double?,
        val per100g: Map<String, Double>,
        val units: Map<String, String>,
        val groups: Map<String, NutrientGroup>,
    )

    suspend fun load(): Existing? {
        val id = foodId ?: copyOf ?: return null
        val food = repo.foodOnce(id) ?: return null
        val nutrients = repo.foodNutrientsOnce(id)
        val sections = repo.nutrientDefsOnce().associate { it.name to it.section }
        return Existing(
            food.name, food.emoji, food.categoryId, food.servingGrams,
            nutrients.associate { it.name to it.amount },
            nutrients.associate { it.name to it.unit },
            nutrients.associate { it.name to NutrientCatalog.info(it.name, sections[it.name].orEmpty()).group },
        )
    }

    fun save(
        name: String,
        emoji: String,
        per100g: Map<NutrientInfo, Double>,
        extra: Map<String, Pair<Double, String>>,
        barcode: String?,
        category: Int,
        servingGrams: Double?,
        onSaved: (Int) -> Unit,
    ) = viewModelScope.launch {
        onSaved(repo.saveCustomFood(foodId, name, emoji, per100g, barcode, category, extra, servingGrams))
    }
}

private val STICKER_CHOICES = listOf(
    "⭐", "🍽️", "🥗", "🍲", "🍝", "🍜", "🍛", "🍕", "🍔", "🌯", "🥪", "🍳", "🥞", "🥣", "🍚", "🍞",
    "🥩", "🍗", "🐟", "🥚", "🧀", "🥛", "🍎", "🍌", "🫐", "🥑", "🥦", "🥕", "🥜", "🍫", "🍪", "🍰",
    "🥤", "☕", "🧃", "💊",
)

private val MACROS = listOf(HealthDietParser.PROTEIN, HealthDietParser.FAT, HealthDietParser.CARBS)

/** Optional sections, each collapsed until opened. */
private val OPTIONAL_SECTIONS = listOf(
    R.string.section_fiber_sugars to NutrientCatalog.editable(NutrientGroup.ENERGY)
        .filter { it.siteName != HealthDietParser.KCAL && it.siteName !in MACROS },
    R.string.section_fats to NutrientCatalog.editable(NutrientGroup.FATS),
    R.string.section_vitamins to NutrientCatalog.editable(NutrientGroup.VITAMINS),
    R.string.section_minerals to NutrientCatalog.editable(NutrientGroup.MINERALS),
    R.string.section_amino to NutrientCatalog.editable(NutrientGroup.AMINO_ACIDS),
)

@Composable
fun CustomFoodScreen(
    foodId: Int?,
    initialName: String,
    barcode: String?,
    onBack: () -> Unit,
    onSaved: (Int) -> Unit,
    /** A database food to start from: the user's own version of it, saved as a new food. */
    copyOf: Int? = null,
) {
    val vm = appViewModel { CustomFoodViewModel(it.repository, foodId, copyOf) }
    var existing by remember { mutableStateOf<CustomFoodViewModel.Existing?>(null) }
    // What the form showed when it opened, to tell which fields the user changed.
    var initialForm by remember { mutableStateOf(emptyMap<String, Double>()) }
    val myVersion = stringResource(R.string.my_version)
    var name by remember { mutableStateOf(initialName) }
    var emoji by remember { mutableStateOf("⭐") }
    var basis by remember { mutableStateOf("100") }
    val values = remember { mutableStateMapOf<String, String>() }
    var open by remember { mutableStateOf(emptySet<Int>()) }

    LaunchedEffect(Unit) {
        vm.load()?.let { e ->
            existing = e
            name = if (copyOf != null) "${e.name} ($myVersion)" else e.name
            (e.emoji ?: foodSticker(null, e.categoryId)).let { emoji = it }
            e.per100g.forEach { (k, v) -> values[k] = formatAmount(v).replace(',', '.') }
            initialForm = values.mapNotNull { (k, v) -> v.toDoubleOrNull()?.let { k to it } }.toMap()
            // Open the optional sections that already have values.
            open = OPTIONAL_SECTIONS.filter { (_, items) -> items.any { it.siteName in e.per100g } }.map { it.first }.toSet()
        }
    }

    val basisG = basis.toDoubleOrNull()?.takeIf { it > 0 }
    fun num(siteName: String) = values[siteName]?.toDoubleOrNull()
    val estimatedKcal = MACROS.zip(listOf(4.0, 9.0, 4.0)).sumOf { (n, k) -> (num(n) ?: 0.0) * k }
    val hasEnergy = num(HealthDietParser.KCAL) != null || MACROS.any { num(it) != null }
    val valid = name.isNotBlank() && basisG != null && hasEnergy

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (copyOf != null) R.string.my_version_title else if (foodId == null) R.string.new_food else R.string.edit_food))
                },
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
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.name)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        STICKER_CHOICES.forEach { choice ->
                            Text(
                                choice,
                                fontSize = 24.sp,
                                modifier = Modifier
                                    .size(44.dp)
                                    .then(
                                        if (choice == emoji) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { emoji = choice }
                                    .padding(top = 6.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    barcode?.let {
                        Text(
                            stringResource(R.string.barcode_remembered, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.values_for), Modifier.weight(1f))
                        NumberInput(basis, { basis = it }, stringResource(R.string.amount), stringResource(R.string.unit_g), Modifier.width(140.dp))
                    }
                    Text(
                        stringResource(R.string.values_for_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.energy_macros_section), style = MaterialTheme.typography.titleMedium)
                    NumberInput(
                        values[HealthDietParser.KCAL].orEmpty(), { values[HealthDietParser.KCAL] = it },
                        stringResource(R.string.calories), stringResource(R.string.unit_kcal), Modifier.fillMaxWidth(),
                        hint = if (estimatedKcal > 0 && num(HealthDietParser.KCAL) == null) {
                            stringResource(R.string.kcal_from_macros, estimatedKcal.roundToInt())
                        } else null,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(R.string.protein, R.string.fat, R.string.carbs).zip(MACROS).forEach { (label, site) ->
                            NumberInput(values[site].orEmpty(), { values[site] = it }, stringResource(label), stringResource(R.string.unit_g), Modifier.weight(1f))
                        }
                    }
                    // Changed macros on existing data: offer the calories they add up to.
                    val kcal = num(HealthDietParser.KCAL)
                    val macrosChanged = existing != null && MACROS.any { num(it) != initialForm[it] }
                    if (macrosChanged && kcal != null && estimatedKcal > 0 && kotlin.math.abs(kcal - estimatedKcal) / estimatedKcal > 0.03) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.kcal_suggest, estimatedKcal.roundToInt()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { values[HealthDietParser.KCAL] = estimatedKcal.roundToInt().toString() }) {
                                Text(stringResource(R.string.use_value))
                            }
                        }
                    }
                }
            }

            OPTIONAL_SECTIONS.forEach { (title, items) ->
                val isOpen = title in open
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { open = if (isOpen) open - title else open + title }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        val filled = items.count { values[it.siteName]?.isNotBlank() == true }
                        Text(
                            if (filled > 0) stringResource(R.string.filled_count, filled) else stringResource(R.string.optional),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(if (isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
                    }
                    AnimatedVisibility(isOpen) {
                        Column(
                            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { info ->
                                        NumberInput(
                                            values[info.siteName].orEmpty(), { values[info.siteName] = it },
                                            info.label(isRussian()), unitLabel(info.unit), Modifier.weight(1f),
                                        )
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val factor = 100 / basisG!!
                    val formInfos = NutrientGroup.entries.flatMap { NutrientCatalog.editable(it) }
                    val per100g = formInfos.mapNotNull { info ->
                        val amount = num(info.siteName)
                            ?: estimatedKcal.takeIf { info.siteName == HealthDietParser.KCAL }
                        amount?.let { info to it * factor }
                    }.toMap()
                    val e = existing
                    if (e == null) {
                        vm.save(name.trim(), emoji, per100g, emptyMap(), barcode, FoodRepository.CUSTOM_CATEGORY, null, onSaved)
                    } else {
                        // Keep what the form doesn't show, and let amino/fatty acids follow changed protein/fat.
                        val formNames = formInfos.map { it.siteName }.toSet()
                        val merged = FoodVariant.merge(e.per100g, per100g.mapKeys { it.key.siteName }, initialForm, formNames) {
                            e.groups[it] ?: NutrientCatalog.info(it).group
                        }
                        val byInfo = formInfos.mapNotNull { info -> merged[info.siteName]?.let { info to it } }.toMap()
                        val extra = merged.filterKeys { it !in formNames }
                            .mapValues { (n, v) -> v to (e.units[n] ?: NutrientCatalog.info(n).unit) }
                        vm.save(
                            name.trim(), emoji, byInfo, extra, barcode,
                            category = e.categoryId ?: FoodRepository.CUSTOM_CATEGORY,
                            servingGrams = e.servingGrams.takeIf { copyOf != null },
                            onSaved = onSaved,
                        )
                    }
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save_food)) }
            if (!valid) {
                Text(
                    stringResource(R.string.food_needs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NumberInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    suffix: String,
    modifier: Modifier,
    hint: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { sanitizeDecimal(it)?.let(onChange) },
        label = { Text(label, maxLines = 1) },
        suffix = { Text(suffix) },
        supportingText = hint?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
