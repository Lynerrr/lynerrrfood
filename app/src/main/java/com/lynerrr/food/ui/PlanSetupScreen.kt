@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.lynerrr.food.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lynerrr.food.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lynerrr.food.data.EnergyModel
import com.lynerrr.food.data.FoodRepository
import com.lynerrr.food.data.ProfileEntity
import com.lynerrr.food.data.WeightEntity
import com.lynerrr.food.data.formatAmount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

class PlanSetupViewModel(private val repo: FoodRepository) : ViewModel() {
    suspend fun load(): Pair<ProfileEntity?, WeightEntity?> = repo.profile().first() to repo.weights().first().lastOrNull()

    fun save(profile: ProfileEntity, weightKg: Double, onDone: () -> Unit) = viewModelScope.launch {
        // Only log a weigh-in if the entered weight differs from the latest one.
        if (repo.weights().first().lastOrNull()?.kg != weightKg) repo.setWeight(LocalDate.now().toEpochDay(), weightKg)
        repo.saveProfile(profile)
        onDone()
    }
}

private val RATES = listOf(0.25, 0.5, 0.75, 1.0)

@Composable
fun PlanSetupScreen(onBack: () -> Unit) {
    val vm = appViewModel { PlanSetupViewModel(it.repository) }
    var loaded by remember { mutableStateOf(false) }
    var existing by remember { mutableStateOf<ProfileEntity?>(null) }
    var male by remember { mutableStateOf(true) }
    var age by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var activity by remember { mutableIntStateOf(1) }
    var direction by remember { mutableIntStateOf(-1) }
    var rate by remember { mutableDoubleStateOf(0.5) }
    var protein by remember { mutableStateOf("1.8") }
    var fat by remember { mutableStateOf("30") }

    LaunchedEffect(Unit) {
        val (profile, lastWeight) = vm.load()
        profile?.let {
            existing = it
            male = it.male
            age = it.age.toString()
            height = formatAmount(it.heightCm).replace(',', '.')
            activity = it.activity
            direction = when { it.goalKgPerWeek < 0 -> -1; it.goalKgPerWeek > 0 -> 1; else -> 0 }
            if (it.goalKgPerWeek != 0.0) rate = abs(it.goalKgPerWeek)
            protein = formatAmount(it.proteinPerKg).replace(',', '.')
            fat = formatAmount(it.fatPercent).replace(',', '.')
        }
        lastWeight?.let { weight = formatAmount(it.kg).replace(',', '.') }
        loaded = true
    }

    val ageV = age.toIntOrNull()?.takeIf { it in 14..100 }
    val heightV = height.toDoubleOrNull()?.takeIf { it in 120.0..250.0 }
    val weightV = weight.toDoubleOrNull()?.takeIf { it in 30.0..300.0 }
    val proteinV = protein.toDoubleOrNull()?.takeIf { it in 0.5..3.5 }
    val fatV = fat.toDoubleOrNull()?.takeIf { it in 15.0..60.0 }
    val valid = loaded && ageV != null && heightV != null && weightV != null && proteinV != null && fatV != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (existing == null) R.string.setup_plan else R.string.edit_plan)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Label(stringResource(R.string.sex_label))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(male, { male = true }, { Text(stringResource(R.string.male)) })
                FilterChip(!male, { male = false }, { Text(stringResource(R.string.female)) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(age, { age = it }, stringResource(R.string.age), stringResource(R.string.unit_years), Modifier.weight(1f))
                NumberField(height, { height = it }, stringResource(R.string.height), stringResource(R.string.unit_cm), Modifier.weight(1f))
                NumberField(weight, { weight = it }, stringResource(R.string.weight), stringResource(R.string.unit_kg), Modifier.weight(1f))
            }

            Label(stringResource(R.string.activity_label))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EnergyModel.ACTIVITY.forEachIndexed { i, (_, name) ->
                    FilterChip(activity == i, { activity = i }, { Text(stringResource(name)) })
                }
            }
            Text(
                stringResource(R.string.activity_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Label(stringResource(R.string.goal))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(direction == -1, { direction = -1 }, { Text(stringResource(R.string.lose)) })
                FilterChip(direction == 0, { direction = 0 }, { Text(stringResource(R.string.maintain)) })
                FilterChip(direction == 1, { direction = 1 }, { Text(stringResource(R.string.gain)) })
            }
            if (direction != 0) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RATES.forEach { r -> FilterChip(rate == r, { rate = r }, { Text(stringResource(R.string.rate_per_week, formatAmount(r))) }) }
                }
                val kcal = (rate * EnergyModel.KCAL_PER_KG / 7).roundToInt()
                Text(
                    stringResource(R.string.rate_kcal, if (direction < 0) "−" else "+", kcal) +
                        (weightV?.let { stringResource(R.string.rate_bodyweight, formatAmount(rate / it * 100)) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Label(stringResource(R.string.macros))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(protein, { protein = it }, stringResource(R.string.protein), stringResource(R.string.unit_g_per_kg), Modifier.weight(1f))
                NumberField(fat, { fat = it }, stringResource(R.string.fat), stringResource(R.string.unit_pct_kcal), Modifier.weight(1f))
            }
            Text(
                stringResource(R.string.macro_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    vm.save(
                        ProfileEntity(
                            male = male,
                            age = ageV!!,
                            heightCm = heightV!!,
                            activity = activity,
                            goalKgPerWeek = direction * rate,
                            proteinPerKg = proteinV!!,
                            fatPercent = fatV!!,
                            startDay = existing?.startDay ?: LocalDate.now().toEpochDay(),
                        ),
                        weightV!!,
                        onDone = onBack,
                    )
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save_plan)) }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, suffix: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { sanitizeDecimal(it)?.let(onChange) },
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
