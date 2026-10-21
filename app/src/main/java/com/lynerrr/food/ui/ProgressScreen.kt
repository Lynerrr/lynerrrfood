@file:OptIn(ExperimentalMaterial3Api::class)

package com.lynerrr.food.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lynerrr.food.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lynerrr.food.data.EnergyModel
import com.lynerrr.food.data.FoodRepository
import com.lynerrr.food.data.Plan
import com.lynerrr.food.data.WeightEntity
import com.lynerrr.food.data.formatAmount
import com.lynerrr.food.data.nutrientKey
import com.lynerrr.food.net.HealthDietParser
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

class ProgressViewModel(private val repo: FoodRepository) : ViewModel() {
    val plan = repo.plan.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val weights = repo.weights().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setWeight(day: Long, kg: Double) = viewModelScope.launch { repo.setWeight(day, kg) }
    fun deleteWeight(day: Long) = viewModelScope.launch { repo.deleteWeight(day) }
}

private const val CHART_DAYS = 60

@Composable
fun ProgressScreen(onBack: () -> Unit, onEditPlan: () -> Unit) {
    val vm = appViewModel { ProgressViewModel(it.repository) }
    val plan by vm.plan.collectAsStateWithLifecycle()
    val weights by vm.weights.collectAsStateWithLifecycle()
    var editingDay by remember { mutableStateOf<Long?>(null) }
    val today = LocalDate.now().toEpochDay()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.progress)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        val p = plan ?: return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { PlanCard(p, today, onEditPlan) }
            item { WeightCard(p, today, onLogWeight = { editingDay = today }) }
            if (p.profile != null) item { ExpenditureCard(p, today) }
            if (weights.isNotEmpty()) {
                item { Text(stringResource(R.string.weigh_ins), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                items(weights.asReversed().take(30), key = { it.epochDay }) { w ->
                    WeighInRow(w, onClick = { editingDay = w.epochDay })
                }
            }
        }
    }

    editingDay?.let { day ->
        val existing = weights.firstOrNull { it.epochDay == day }
        WeightDialog(
            day = day,
            initial = existing?.kg ?: weights.lastOrNull()?.kg,
            onDismiss = { editingDay = null },
            onSave = { vm.setWeight(day, it); editingDay = null },
            onDelete = existing?.let { { vm.deleteWeight(day); editingDay = null } },
        )
    }
}

@Composable
private fun PlanCard(plan: Plan, today: Long, onEditPlan: () -> Unit) {
    val profile = plan.profile
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (profile == null) {
                Text(stringResource(R.string.adaptive_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.adaptive_explainer),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onEditPlan) { Text(stringResource(R.string.setup_plan)) }
                return@Column
            }
            val day = plan.on(today)
            val targets = plan.targets(today)
            Text(stringResource(R.string.target_today), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                day?.target?.let { stringResource(R.string.kcal_value, it.roundToInt().toString()) }
                    ?: stringResource(R.string.log_weight_to_start),
                style = MaterialTheme.typography.displaySmall,
            )
            day?.expenditure?.let { Text(stringResource(R.string.expenditure_estimate, it.roundToInt())) }
            Text(
                when (val n = day?.loggedDays ?: 0) {
                    0 -> stringResource(R.string.learn_formula, EnergyModel.MIN_LOGGED_DAYS)
                    in 1 until EnergyModel.FULL_CONFIDENCE_DAYS ->
                        stringResource(R.string.learn_partial, n, EnergyModel.FULL_CONFIDENCE_DAYS)
                    else -> stringResource(R.string.learn_full, n)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val rate = profile.goalKgPerWeek
            val dailyDelta = (rate * EnergyModel.KCAL_PER_KG / 7).roundToInt()
            Text(
                when {
                    rate < 0 -> stringResource(R.string.goal_lose, formatAmount(-rate), dailyDelta)
                    rate > 0 -> stringResource(R.string.goal_gain, formatAmount(rate), dailyDelta)
                    else -> stringResource(R.string.goal_maintain)
                },
            )
            if (day?.target != null) {
                fun g(name: String) = targets[nutrientKey(name)]?.roundToInt() ?: 0
                Text(
                    stringResource(R.string.macro_targets, g(HealthDietParser.PROTEIN), g(HealthDietParser.FAT), g(HealthDietParser.CARBS)),
                )
            }
            OutlinedButton(onClick = onEditPlan) { Text(stringResource(R.string.edit_plan)) }
        }
    }
}

@Composable
private fun WeightCard(plan: Plan, today: Long, onLogWeight: () -> Unit) {
    val recent = plan.days.filter { it.day in (today - CHART_DAYS + 1)..today }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.weight), style = MaterialTheme.typography.titleMedium)
            val trend = plan.on(today)?.trend
            if (trend == null) {
                Text(stringResource(R.string.no_weigh_ins), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(stringResource(R.string.trend_kg, formatAmount(trend)), style = MaterialTheme.typography.headlineSmall)
                plan.on(today - 7)?.trend?.takeIf { plan.days.first().day <= today - 7 }?.let { weekAgo ->
                    val change = trend - weekAgo
                    Text(
                        stringResource(R.string.weekly_change, (if (change >= 0) "+" else "−") + formatAmount(abs(change))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (recent.any { it.weight != null }) {
                    val kgFormat = stringResource(R.string.kg_value)
                    TimeChart(
                        days = recent.map { it.day },
                        series = listOf(
                            ChartSeries(stringResource(R.string.series_scale), MaterialTheme.colorScheme.outline, SeriesStyle.Dots, recent.map { it.weight }),
                            ChartSeries(stringResource(R.string.series_trend), MaterialTheme.colorScheme.primary, SeriesStyle.Line, recent.map { it.trend }),
                        ),
                        format = { kgFormat.format(formatAmount(it)) },
                    )
                }
            }
            Button(onClick = onLogWeight) { Text(stringResource(R.string.log_todays_weight)) }
        }
    }
}

@Composable
private fun ExpenditureCard(plan: Plan, today: Long) {
    val recent = plan.days.filter { it.day in (today - CHART_DAYS + 1)..today && it.expenditure != null }
    if (recent.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.energy), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.energy_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TimeChart(
                days = recent.map { it.day },
                series = listOf(
                    ChartSeries(stringResource(R.string.series_expenditure), MaterialTheme.colorScheme.primary, SeriesStyle.Line, recent.map { it.expenditure }),
                    ChartSeries(stringResource(R.string.series_target), MaterialTheme.colorScheme.tertiary, SeriesStyle.DashedLine, recent.map { it.target }),
                    ChartSeries(stringResource(R.string.series_eaten), MaterialTheme.colorScheme.outline, SeriesStyle.Dots, recent.map { it.intake }),
                ),
                format = { "${it.roundToInt()}" },
            )
        }
    }
}

@Composable
private fun WeighInRow(weight: WeightEntity, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(dayLabel(weight.epochDay), Modifier.weight(1f))
        Text(stringResource(R.string.kg_value, formatAmount(weight.kg)))
    }
}
