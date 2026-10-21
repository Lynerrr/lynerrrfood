package com.lynerrr.food.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lynerrr.food.R
import com.lynerrr.food.data.MacroSplit
import com.lynerrr.food.data.Ratio
import com.lynerrr.food.data.RatioKind
import com.lynerrr.food.data.RatioStatus
import com.lynerrr.food.data.formatAmount
import kotlin.math.roundToInt

private fun RatioKind.title() = when (this) {
    RatioKind.POTASSIUM_SODIUM -> R.string.ratio_k_na
    RatioKind.OMEGA6_OMEGA3 -> R.string.ratio_o6_o3
    RatioKind.CALCIUM_PHOSPHORUS -> R.string.ratio_ca_p
    RatioKind.CALCIUM_MAGNESIUM -> R.string.ratio_ca_mg
    RatioKind.ZINC_COPPER -> R.string.ratio_zn_cu
    RatioKind.FIBER_PER_1000_KCAL -> R.string.ratio_fiber
    RatioKind.SATURATED_FAT_PERCENT -> R.string.ratio_satfat
}

private fun RatioKind.advice() = when (this) {
    RatioKind.POTASSIUM_SODIUM -> R.string.ratio_k_na_advice
    RatioKind.OMEGA6_OMEGA3 -> R.string.ratio_o6_o3_advice
    RatioKind.CALCIUM_PHOSPHORUS -> R.string.ratio_ca_p_advice
    RatioKind.CALCIUM_MAGNESIUM -> R.string.ratio_ca_mg_advice
    RatioKind.ZINC_COPPER -> R.string.ratio_zn_cu_advice
    RatioKind.FIBER_PER_1000_KCAL -> R.string.ratio_fiber_advice
    RatioKind.SATURATED_FAT_PERCENT -> R.string.ratio_satfat_advice
}

@Composable
private fun Ratio.valueText(): String = when (kind) {
    RatioKind.FIBER_PER_1000_KCAL -> stringResource(R.string.grams_value, formatAmount(value))
    RatioKind.SATURATED_FAT_PERCENT -> stringResource(R.string.percent_of_kcal, value.roundToInt())
    else -> stringResource(R.string.ratio_value, formatAmount(value))
}

/** Status shown as symbol + colour + label, never colour alone. */
@Composable
private fun StatusBadge(status: RatioStatus) {
    val (symbol, color, label) = when (status) {
        RatioStatus.GOOD -> Triple("✓", OnTarget, R.string.status_good)
        RatioStatus.OK -> Triple("!", NearTarget, R.string.status_ok)
        RatioStatus.POOR -> Triple("✕", OffTarget, R.string.status_poor)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        Box(Modifier.size(28.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Text(symbol, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
fun RatiosCard(ratios: List<Ratio>, split: MacroSplit?, modifier: Modifier = Modifier) {
    if (ratios.isEmpty() && split == null) return
    Card(modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Sticker("⚖️", MaterialTheme.colorScheme.surfaceContainerHighest, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.ratios_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.ratios_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        split?.let { MacroSplitBar(it) }
        ratios.forEach { ratio ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(ratio.kind.title()), style = MaterialTheme.typography.bodyMedium)
                    Text(ratio.valueText(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(ratio.kind.advice()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusBadge(ratio.status)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** One stacked bar of where calories come from, with direct labels underneath. */
@Composable
private fun MacroSplitBar(split: MacroSplit) {
    val parts = listOf(
        Triple(R.string.protein, split.protein, MaterialTheme.colorScheme.primary),
        Triple(R.string.fat, split.fat, MaterialTheme.colorScheme.tertiary),
        Triple(R.string.carbs, split.carbs, MaterialTheme.colorScheme.secondary),
    )
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.calories_from), style = MaterialTheme.typography.bodyLarge)
        Row(
            Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            parts.filter { it.second > 0 }.forEach { (_, pct, color) ->
                Box(Modifier.weight(pct.toFloat()).fillMaxHeight().background(color))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            parts.forEach { (label, pct, color) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).background(color, CircleShape))
                    Text("${stringResource(label)} ${pct.roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
