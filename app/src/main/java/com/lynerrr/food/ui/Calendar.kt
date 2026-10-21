@file:OptIn(ExperimentalMaterial3Api::class)

package com.lynerrr.food.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lynerrr.food.R
import com.lynerrr.food.data.formatAmount
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.abs

val OnTarget = Color(0xFF43A047)
val NearTarget = Color(0xFFF9A825)
val OffTarget = Color(0xFFE53935)

/** Status colour for a day's calories as a fraction of its target. */
fun adherenceColor(ratio: Double): Color = when {
    abs(ratio - 1) <= 0.1 -> OnTarget
    abs(ratio - 1) <= 0.25 -> NearTarget
    else -> OffTarget
}

/** Month grid; [adherence] maps logged days to calories ÷ target (null when there's no target). */
@Composable
fun CalendarSheet(
    selected: Long,
    adherence: Map<Long, Double?>,
    weighed: Set<Long>,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        var month by remember { mutableStateOf(YearMonth.from(LocalDate.ofEpochDay(selected))) }
        val locale = appLocale()
        val today = LocalDate.now().toEpochDay()
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.prev_month))
                }
                Text(
                    month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_month))
                }
            }
            Row {
                DayOfWeek.entries.forEach {
                    Text(
                        it.getDisplayName(TextStyle.SHORT, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val offset = month.atDay(1).dayOfWeek.value - 1
            val cells = offset + month.lengthOfMonth()
            for (row in 0 until (cells + 6) / 7) {
                Row {
                    for (col in 0..6) {
                        val dayOfMonth = row * 7 + col - offset + 1
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (dayOfMonth in 1..month.lengthOfMonth()) {
                                val day = month.atDay(dayOfMonth).toEpochDay()
                                DayCell(dayOfMonth, day == selected, day == today, adherence, weighed, day) {
                                    onSelect(day)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Legend(OnTarget, stringResource(R.string.cal_legend_good))
                Legend(NearTarget, "±25%")
                Legend(OffTarget, stringResource(R.string.cal_legend_off))
            }
            Text(
                stringResource(R.string.cal_weight_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(onClick = { onSelect(today) }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.go_today)) }
        }
    }
}

@Composable
private fun DayCell(
    dayOfMonth: Int,
    isSelected: Boolean,
    isToday: Boolean,
    adherence: Map<Long, Double?>,
    weighed: Set<Long>,
    day: Long,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(if (isSelected) colors.primary else Color.Transparent, CircleShape)
            .then(if (isToday && !isSelected) Modifier.border(1.dp, colors.primary, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "$dayOfMonth",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) colors.onPrimary else colors.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (adherence.containsKey(day)) {
                val ratio = adherence[day]
                Box(Modifier.size(6.dp).background(ratio?.let(::adherenceColor) ?: colors.outline, CircleShape))
            }
            if (day in weighed) {
                Box(Modifier.size(8.dp, 2.dp).background(if (isSelected) colors.onPrimary else colors.onSurfaceVariant))
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun WeightDialog(day: Long, initial: Double?, onDismiss: () -> Unit, onSave: (Double) -> Unit, onDelete: (() -> Unit)?) {
    var text by remember { mutableStateOf(initial?.let(::formatAmount)?.replace(',', '.') ?: "") }
    val kg = text.toDoubleOrNull()?.takeIf { it in 20.0..400.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weight_title, dayLabel(day))) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { sanitizeDecimal(it)?.let { v -> text = v } },
                    label = { Text(stringResource(R.string.weight)) },
                    suffix = { Text(stringResource(R.string.unit_kg)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.weigh_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { kg?.let(onSave) }, enabled = kg != null) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
