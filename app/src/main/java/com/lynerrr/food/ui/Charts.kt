package com.lynerrr.food.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lynerrr.food.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class SeriesStyle { Line, DashedLine, Dots }

class ChartSeries(val label: String, val color: Color, val style: SeriesStyle, val values: List<Double?>)

private val axisDate = DateTimeFormatter.ofPattern("d MMM")

/**
 * A small time-series chart over consecutive [days]: one y-axis, recessive grid, tap or drag to read a day's
 * values in the line above the plot.
 */
@Composable
fun TimeChart(days: List<Long>, series: List<ChartSeries>, format: (Double) -> String, modifier: Modifier = Modifier) {
    var selected by remember(days) { mutableStateOf<Int?>(null) }
    val all = series.flatMap { it.values.filterNotNull() }
    if (days.isEmpty() || all.isEmpty()) return

    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val cursor = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerLow

    val pad = ((all.max() - all.min()) * 0.1).coerceAtLeast(0.5)
    val yMin = all.min() - pad
    val yMax = all.max() + pad

    Column(modifier) {
        val i = selected
        Text(
            if (i == null) stringResource(R.string.chart_hint)
            else "${dayLabel(days[i])}: " + series.joinToString(" · ") { s -> "${s.label} ${s.values[i]?.let(format) ?: "—"}" },
            style = MaterialTheme.typography.bodySmall,
            color = if (i == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .pointerInput(days) {
                    val left = 44.dp.toPx()
                    fun index(x: Float) =
                        (((x - left) / (size.width - left)) * (days.size - 1)).toInt().coerceIn(0, days.lastIndex)
                    detectTapGestures { selected = index(it.x) }
                }
                .pointerInput(days) {
                    val left = 44.dp.toPx()
                    fun index(x: Float) =
                        (((x - left) / (size.width - left)) * (days.size - 1)).toInt().coerceIn(0, days.lastIndex)
                    detectHorizontalDragGestures { change, _ -> selected = index(change.position.x) }
                },
        ) {
            val left = 44.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 18.dp.toPx()
            fun x(index: Int) = left + (size.width - left) * if (days.size == 1) 0.5f else index.toFloat() / days.lastIndex
            fun y(v: Double) = bottom - ((v - yMin) / (yMax - yMin)).toFloat() * (bottom - top)

            // Recessive grid with three labelled values.
            for (k in 0..2) {
                val v = yMin + (yMax - yMin) * (0.1 + 0.4 * k)
                drawLine(grid, Offset(left, y(v)), Offset(size.width, y(v)), strokeWidth = 1f)
                val text = measurer.measure(format(v), labelStyle)
                drawText(text, topLeft = Offset(0f, y(v) - text.size.height / 2))
            }
            listOf(0, days.lastIndex).distinct().forEach { index ->
                val text = measurer.measure(LocalDate.ofEpochDay(days[index]).format(axisDate), labelStyle)
                val tx = (x(index) - if (index == 0) 0f else text.size.width.toFloat()).coerceAtLeast(left)
                drawText(text, topLeft = Offset(tx, bottom + 4.dp.toPx()))
            }

            series.forEach { s ->
                when (s.style) {
                    SeriesStyle.Dots -> s.values.forEachIndexed { index, v ->
                        if (v != null) drawCircle(s.color, 3.dp.toPx(), Offset(x(index), y(v)))
                    }
                    SeriesStyle.Line, SeriesStyle.DashedLine -> {
                        val path = Path()
                        var drawing = false
                        s.values.forEachIndexed { index, v ->
                            if (v == null) {
                                drawing = false
                            } else if (!drawing) {
                                path.moveTo(x(index), y(v)); drawing = true
                            } else {
                                path.lineTo(x(index), y(v))
                            }
                        }
                        drawPath(
                            path, s.color,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = if (s.style == SeriesStyle.DashedLine) {
                                    PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                                } else null,
                            ),
                        )
                    }
                }
            }

            selected?.let { index ->
                drawLine(cursor, Offset(x(index), top), Offset(x(index), bottom), strokeWidth = 1.dp.toPx())
                series.forEach { s ->
                    s.values[index]?.let { v ->
                        drawCircle(surface, 6.dp.toPx(), Offset(x(index), y(v)))
                        drawCircle(s.color, 4.dp.toPx(), Offset(x(index), y(v)))
                    }
                }
            }
        }
        if (series.size >= 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                series.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(if (s.style == SeriesStyle.Dots) 8.dp else 12.dp, if (s.style == SeriesStyle.Dots) 8.dp else 3.dp)
                            .background(s.color, if (s.style == SeriesStyle.Dots) CircleShape else MaterialTheme.shapes.extraSmall))
                        Text(s.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
