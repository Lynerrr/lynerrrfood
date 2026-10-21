@file:OptIn(ExperimentalMaterial3Api::class)

package com.lynerrr.food.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lynerrr.food.R
import com.lynerrr.food.data.BestSources
import com.lynerrr.food.data.FoodRepository
import com.lynerrr.food.data.NutrientCatalog
import com.lynerrr.food.data.SourceGroup
import com.lynerrr.food.data.SourceRanking
import com.lynerrr.food.data.formatAmount
import com.lynerrr.food.data.nutrientKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import kotlin.math.roundToInt

class BestSourcesViewModel(repo: FoodRepository, val nutrient: String) : ViewModel() {
    val perKcal = MutableStateFlow(false)
    val commonOnly = MutableStateFlow(true)

    /** Today's daily target in the nutrient's own unit, to show each food as a share of it. */
    val target = combine(repo.nutrientDefs(), repo.targets(LocalDate.now().toEpochDay())) { defs, targets ->
        val def = defs.firstOrNull { it.name == nutrient }
        def?.let { (it.userNorm ?: targets[nutrientKey(nutrient)] ?: it.normLow ?: it.normHigh)?.to(it.unit) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val groups = combine(repo.foodsWithNutrient(nutrient), target, perKcal, commonOnly) { foods, t, byKcal, common ->
        SourceGroup.group(SourceRanking.rank(foods, t, byKcal, common))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Entries shown per food group before "show more". */
private const val GROUP_SHORT_LIST = 3

@Composable
fun BestSourcesScreen(
    nutrient: String,
    onBack: () -> Unit,
    onOpenFood: (Int) -> Unit,
    onSearch: (String) -> Unit,
) {
    val vm = appViewModel { BestSourcesViewModel(it.repository, nutrient) }
    val perKcal by vm.perKcal.collectAsStateWithLifecycle()
    val commonOnly by vm.commonOnly.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val target by vm.target.collectAsStateWithLifecycle()
    val info = NutrientCatalog.info(nutrient)
    val russian = isRussian()
    val curated = BestSources.curated(nutrient)
    var openGroups by remember { mutableStateOf(emptySet<SourceGroup>()) }
    var openFamilies by remember { mutableStateOf(emptySet<Int>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.best_sources_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Sticker(info.badge, groupTint(info.group), size = 56.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(info.label(russian), style = MaterialTheme.typography.titleLarge)
                        target?.let { (amount, unit) ->
                            Text(
                                stringResource(R.string.daily_target_value, "${formatAmount(amount)} ${unitLabel(unit)}"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (curated != null) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 12.dp)) {
                            Text(
                                stringResource(R.string.well_known_sources),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            (if (russian) curated.noteRu else curated.noteEn)?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                            curated.items.forEach { source ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = source.query != null) { source.query?.let(onSearch) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Sticker(source.sticker, MaterialTheme.colorScheme.surfaceContainerHighest, size = 36.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(if (russian) source.ru else source.en, Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyLarge)
                                    if (source.query != null) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.search_for_it),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.top_in_database), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!perKcal, { vm.perKcal.value = false }, { Text(stringResource(R.string.per_serving)) })
                        FilterChip(perKcal, { vm.perKcal.value = true }, { Text(stringResource(R.string.per_100kcal)) })
                        FilterChip(commonOnly, { vm.commonOnly.value = !commonOnly }, { Text(stringResource(R.string.common_only)) })
                    }
                    Text(
                        stringResource(if (perKcal) R.string.per_100kcal_hint else R.string.per_serving_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (groups.isEmpty()) {
                item {
                    Text(
                        stringResource(if (target != null) R.string.no_good_sources else R.string.no_ranked_foods),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(groups, key = { (group, _) -> group.name }) { (group, entries) ->
                val open = group in openGroups
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Sticker(group.sticker, MaterialTheme.colorScheme.surfaceContainerHighest, size = 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(group.title), style = MaterialTheme.typography.titleMedium)
                        }
                        (if (open) entries else entries.take(GROUP_SHORT_LIST)).forEach { entry ->
                            val id = entry.best.food.id
                            SourceRow(
                                c = entry.best,
                                perKcal = perKcal,
                                onClick = { onOpenFood(id) },
                                variants = entry.variants.size,
                                variantsOpen = id in openFamilies,
                                onToggleVariants = { openFamilies = if (id in openFamilies) openFamilies - id else openFamilies + id },
                            )
                            AnimatedVisibility(id in openFamilies) {
                                Column(Modifier.padding(start = 24.dp)) {
                                    entry.variants.forEach { v ->
                                        SourceRow(v, perKcal, onClick = { onOpenFood(v.food.id) }, variant = true)
                                    }
                                }
                            }
                        }
                        if (entries.size > GROUP_SHORT_LIST) {
                            TextButton(onClick = { openGroups = if (open) openGroups - group else openGroups + group }) {
                                Text(
                                    if (open) stringResource(R.string.show_less)
                                    else stringResource(R.string.show_more, entries.size - GROUP_SHORT_LIST)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Amber for a plain source (15%) shading to green from 50% of the daily target, so the bar reads at a glance
 * how much of the day one serving covers.
 */
private fun shareColor(share: Double): Color {
    val t = ((share - SourceRanking.SOURCE_SHARE) / (0.5 - SourceRanking.SOURCE_SHARE)).coerceIn(0.0, 1.0)
    return Color.hsv((45 + 75 * t).toFloat(), 0.7f, 0.8f)
}

@Composable
private fun SourceRow(
    c: SourceRanking.Candidate,
    perKcal: Boolean,
    onClick: () -> Unit,
    variant: Boolean = false,
    variants: Int = 0,
    variantsOpen: Boolean = false,
    onToggleVariants: () -> Unit = {},
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val share = c.share
    val amount = if (perKcal) {
        stringResource(R.string.per_100kcal_value, "${formatAmount(c.per100Kcal ?: 0.0)} ${unitLabel(c.unit)}")
    } else {
        stringResource(R.string.serving_amount, formatAmount(c.servingGrams), "${formatAmount(c.perServing)} ${unitLabel(c.unit)}")
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FoodAvatar(c.food.emoji, c.food.categoryId, size = if (variant) 32.dp else 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.food.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(amount, style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (variants > 0) {
                    Text(
                        "  " + if (variantsOpen) stringResource(R.string.hide_variants)
                        else stringResource(R.string.more_variants, variants),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.clickable(onClick = onToggleVariants).padding(vertical = 4.dp),
                    )
                }
            }
            if (share != null) {
                LinearProgressIndicator(
                    progress = { share.toFloat().coerceIn(0f, 1f) },
                    color = shareColor(share),
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        if (share != null) {
            // Share of the daily target is the headline; the amount itself is in the line under the name.
            Text(
                "${(share * 100).roundToInt()}%",
                style = if (variant) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = shareColor(share),
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 64.dp),
            )
        } else {
            Text(
                "${formatAmount(if (perKcal) c.per100Kcal ?: 0.0 else c.perServing)} ${unitLabel(c.unit)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
