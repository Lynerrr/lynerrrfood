package com.lynerrr.food.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lynerrr.food.FoodApp
import com.lynerrr.food.R
import com.lynerrr.food.data.GroupedNutrient
import com.lynerrr.food.data.MEALS
import com.lynerrr.food.data.NutrientGroup
import com.lynerrr.food.data.NutrientGroupUi
import com.lynerrr.food.data.NutrientValue
import com.lynerrr.food.data.ProductCategory
import com.lynerrr.food.data.SyncResult
import com.lynerrr.food.data.SyncTask
import com.lynerrr.food.data.displayUnit
import com.lynerrr.food.data.formatAmount
import com.lynerrr.food.net.HealthDietParser
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun FoodTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Material "show_chart" icon (not in the core icon set). */
val ShowChartIcon: ImageVector by lazy {
    ImageVector.Builder("ShowChart", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes("M3.5,18.49l6,-6.01 4,4L22,6.92l-1.41,-1.41 -7.09,7.97 -4,-4L2,16.99z"),
            fill = SolidColor(Color.Black),
        )
        .build()
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (FoodApp) -> VM): VM {
    val app = LocalContext.current.applicationContext as FoodApp
    return viewModel(factory = viewModelFactory { initializer { create(app) } })
}

/** The app's current UI locale (follows the in-app language setting). */
@Composable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
fun isRussian(): Boolean = appLocale().language == "ru"

@Composable
fun dayLabel(epochDay: Long): String = when (epochDay - LocalDate.now().toEpochDay()) {
    0L -> stringResource(R.string.today)
    -1L -> stringResource(R.string.yesterday)
    1L -> stringResource(R.string.tomorrow)
    else -> LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("EEE, d MMM", appLocale()))
}

/** Unit in the current language. */
@Composable
fun unitLabel(unit: String): String = displayUnit(unit, isRussian())

/** Accepts digits with one decimal separator; returns null to reject the edit. */
fun sanitizeDecimal(text: String): String? {
    val t = text.replace(',', '.')
    return if (t.isEmpty() || Regex("""\d{0,5}(\.\d{0,3})?""").matches(t)) t else null
}

// ---- Stickers -------------------------------------------------------------------------------------------------

val MEAL_STICKERS = listOf("🍳", "🍲", "🍝", "🍪")

private val CATEGORY_STICKERS = mapOf(
    24501 to "🍗", 24502 to "🥩", 24503 to "🥓", 24504 to "🌭", 24506 to "🥟", 24507 to "🐑", 24508 to "🍷",
    24509 to "🥤", 24511 to "🧃", 24512 to "🥦", 24513 to "🍄", 24514 to "🌿", 24515 to "🫒", 24516 to "🍎",
    24517 to "🥜", 24518 to "🐟", 24519 to "🥛", 24520 to "🥚", 24522 to "🌾", 24523 to "🫘", 24524 to "🍞",
    24525 to "🍰", 24526 to "🍼", 24527 to "🍽️", 24528 to "🍿", 24529 to "🍔", -1 to "⭐", -2 to "✨",
)

fun foodSticker(emoji: String?, categoryId: Int?): String =
    emoji ?: CATEGORY_STICKERS[categoryId] ?: ProductCategory.of(categoryId)?.sticker ?: "🍽️"

/** Pastel tint per nutrient group, so each group's stickers share a colour. */
@Composable
fun groupTint(group: NutrientGroup): Color {
    val hue = when (group) {
        NutrientGroup.ENERGY -> 25f
        NutrientGroup.VITAMINS -> 290f
        NutrientGroup.MINERALS -> 200f
        NutrientGroup.AMINO_ACIDS -> 150f
        NutrientGroup.FATS -> 75f
        NutrientGroup.OTHER -> 0f
    }
    val sat = if (group == NutrientGroup.OTHER) 0f else 1f
    return if (isSystemInDarkTheme()) Color.hsv(hue, 0.45f * sat, 0.32f) else Color.hsv(hue, 0.22f * sat, 0.97f)
}

/** A round sticker holding an emoji or a short code like "B12". */
@Composable
fun Sticker(text: String, background: Color, size: Dp = 40.dp) {
    val isCode = text.any { it.isLetterOrDigit() }
    Box(Modifier.size(size).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            text,
            fontSize = when {
                !isCode -> (size.value * 0.5f).sp
                text.length <= 2 -> (size.value * 0.36f).sp
                text.length == 3 -> (size.value * 0.3f).sp
                else -> (size.value * 0.24f).sp
            },
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun FoodAvatar(emoji: String?, categoryId: Int?, size: Dp = 40.dp) {
    Sticker(foodSticker(emoji, categoryId), MaterialTheme.colorScheme.surfaceContainerHighest, size)
}

// ---- Inputs ---------------------------------------------------------------------------------------------------

@Composable
fun GramsField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { sanitizeDecimal(it)?.let(onValueChange) },
        label = { Text(stringResource(R.string.grams)) },
        suffix = { Text(stringResource(R.string.unit_g)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
fun MealPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        MEALS.forEachIndexed { index, meal ->
            FilterChip(selected = index == selected, onClick = { onSelect(index) },
                label = { Text("${MEAL_STICKERS[index]} ${stringResource(meal)}") })
        }
    }
}

// ---- Nutrient rows --------------------------------------------------------------------------------------------

/**
 * Red → yellow → green as a nutrient approaches its norm. Limits (cholesterol, saturated fat) run the
 * other way, and calories turn orange/red when well over target.
 */
fun barColor(value: NutrientValue, fraction: Double): Color {
    val hue = when {
        value.overLimit -> 0.0
        value.target == null -> 120 * (1 - ((fraction - 0.5) / 0.5).coerceIn(0.0, 1.0))
        value.name == HealthDietParser.KCAL && fraction > 1.25 -> 0.0
        value.name == HealthDietParser.KCAL && fraction > 1.1 -> 30.0
        else -> 120 * fraction.coerceIn(0.0, 1.0)
    }
    return Color.hsv(hue.toFloat(), 0.7f, 0.8f)
}

@Composable
fun NutrientBar(value: NutrientValue, fraction: Double, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    LinearProgressIndicator(
        progress = { fraction.toFloat().coerceIn(0f, 1f) },
        color = barColor(value, fraction),
        drawStopIndicator = {},
        gapSize = 0.dp,
        modifier = modifier.height(height),
    )
}

@Composable
fun targetLabel(value: NutrientValue): String? {
    val unit = unitLabel(value.unit)
    val target = value.target
    val limit = value.limit
    return when {
        target != null && limit != null ->
            stringResource(R.string.target_range, formatAmount(target), "${formatAmount(limit)} $unit")
        target != null -> stringResource(R.string.target_of, "${formatAmount(target)} $unit")
        limit != null -> stringResource(R.string.target_max, "${formatAmount(limit)} $unit")
        else -> null
    }
}

@Composable
fun NutrientRow(item: GroupedNutrient, onClick: (() -> Unit)? = null) {
    val value = item.value
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sticker(item.info.badge, groupTint(item.info.group), size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    item.info.label(isRussian()),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${formatAmount(value.amount)} ${unitLabel(value.unit)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            val fraction = value.fraction ?: return@Column
            Spacer(Modifier.height(4.dp))
            NutrientBar(value, fraction, Modifier.fillMaxWidth())
            Row(Modifier.padding(top = 2.dp)) {
                Text(
                    "${(fraction * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    targetLabel(value).orEmpty() + if (value.userEdited) stringResource(R.string.target_yours) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * One card per nutrient group: sticker header with an "on track" summary, main nutrients, and the niche ones
 * behind a "show more" toggle.
 */
@Composable
fun NutrientGroupCard(
    group: NutrientGroupUi,
    bodyWeight: Double?,
    showMore: Boolean,
    showSummary: Boolean = true,
    onToggleMore: () -> Unit,
    onClick: ((GroupedNutrient) -> Unit)? = null,
    below: (@Composable (GroupedNutrient) -> Unit)? = null,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Sticker(group.group.sticker, groupTint(group.group), size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(group.group.title), style = MaterialTheme.typography.titleMedium)
                if (showSummary && group.trackedCount > 0) {
                    Text(
                        stringResource(R.string.on_track, group.onTrackCount, group.trackedCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        groupNote(group.group, bodyWeight)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        group.primary.forEach { item ->
            NutrientRow(item, onClick?.let { { it(item) } })
            below?.invoke(item)
        }
        if (group.more.isNotEmpty()) {
            AnimatedVisibility(showMore) {
                Column {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    group.more.forEach { item ->
                        NutrientRow(item, onClick?.let { { it(item) } })
                        below?.invoke(item)
                    }
                }
            }
            TextButton(onClick = onToggleMore, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(if (showMore) stringResource(R.string.show_less) else stringResource(R.string.show_more, group.more.size))
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Compact "on track" tiles for each group, e.g. at the top of the day's nutrient screen. */
@Composable
fun GroupOverview(groups: List<NutrientGroupUi>, onSelect: (NutrientGroup) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.filter { it.trackedCount > 0 }.forEach { g ->
            Column(
                Modifier
                    .background(groupTint(g.group), MaterialTheme.shapes.large)
                    .clickable { onSelect(g.group) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .width(92.dp),
            ) {
                Text(g.group.sticker, fontSize = 22.sp)
                Text(stringResource(g.group.shortTitle), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${g.onTrackCount}/${g.trackedCount}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                LinearProgressIndicator(
                    progress = { g.onTrackCount.toFloat() / g.trackedCount },
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
            }
        }
    }
}

/** A compact tile for one macro: sticker, amount, target and bar. */
@Composable
fun MacroTile(value: NutrientValue, sticker: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sticker, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatAmount(value.amount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                " ${unitLabel(value.unit)}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        val fraction = value.fraction
        Text(
            value.target?.let { stringResource(R.string.target_of, formatAmount(it)) } ?: " ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (fraction != null) NutrientBar(value, fraction, Modifier.fillMaxWidth().padding(top = 4.dp), height = 5.dp)
    }
}

/** Calorie ring with the amount eaten and what's left of the target. */
@Composable
fun CalorieHero(kcal: NutrientValue) {
    val fraction = kcal.fraction ?: 0.0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { fraction.toFloat().coerceIn(0f, 1f) },
                color = barColor(kcal, fraction),
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                strokeWidth = 8.dp,
                gapSize = 0.dp,
                modifier = Modifier.size(84.dp),
            )
            Text("🔥", fontSize = 26.sp)
        }
        Spacer(Modifier.width(20.dp))
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatAmount(kcal.amount), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                Text(" ${stringResource(R.string.unit_kcal)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
            }
            kcal.target?.let { target ->
                val left = target - kcal.amount
                Text(
                    if (left >= 0) stringResource(R.string.kcal_left, "${left.roundToInt()}", "${target.roundToInt()}")
                    else stringResource(R.string.kcal_over, "${(-left).roundToInt()}", "${target.roundToInt()}"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun groupNote(group: NutrientGroup, bodyWeight: Double?): String? = when (group) {
    NutrientGroup.MINERALS -> stringResource(R.string.note_minerals)
    NutrientGroup.AMINO_ACIDS -> bodyWeight?.let { stringResource(R.string.note_amino_weight, formatAmount(it)) }
        ?: stringResource(R.string.note_amino_default)
    else -> null
}

@Composable
fun syncTaskLabel(task: SyncTask?): String = when (task) {
    SyncTask.IMPORT, null -> stringResource(R.string.task_import)
    SyncTask.PRODUCTS_IMPORT -> stringResource(R.string.task_products_import)
    SyncTask.PRODUCTS_UPDATE -> stringResource(R.string.task_products_update)
}

@Composable
fun syncResultText(result: SyncResult): String = when (result) {
    is SyncResult.Imported -> stringResource(R.string.result_imported, result.foods)
    is SyncResult.ProductsImported -> stringResource(R.string.result_products_imported, result.products)
    SyncResult.ProductsUpToDate -> stringResource(R.string.result_products_up_to_date)
    SyncResult.Cancelled -> stringResource(R.string.result_cancelled)
    is SyncResult.Failed -> stringResource(R.string.result_failed, result.message)
}
