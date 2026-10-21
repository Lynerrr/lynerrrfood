package com.lynerrr.food.data

import com.lynerrr.food.R
import com.lynerrr.food.net.HealthDietParser
import kotlin.math.max

/** One day of the plan. Expenditure and target only use data from *before* this day. */
data class DayPlan(
    val day: Long,
    val weight: Double?,
    /** Smoothed weight; daily scale readings swing with water and food, the trend doesn't. */
    val trend: Double?,
    val intake: Double?,
    val expenditure: Double?,
    val target: Double?,
    /** Food-logged days in the window the expenditure was learned from (0 = formula only). */
    val loggedDays: Int,
)

class Plan(val profile: ProfileEntity?, val days: List<DayPlan>) {
    private val first = days.firstOrNull()?.day

    /** Plan for [day]; days outside the computed range use the nearest computed day. */
    fun on(day: Long): DayPlan? = first?.let { days[(day - it).toInt().coerceIn(0, days.lastIndex)] }

    fun bodyWeight(day: Long): Double? = on(day)?.trend

    /** Per-nutrient targets keyed by [nutrientKey], overriding the site's generic norms. */
    fun targets(day: Long): Map<String, Double> {
        val plan = on(day)
        val weight = plan?.trend ?: DEFAULT_WEIGHT
        val targets = AMINO_ACID_MG_PER_KG.mapValues { it.value * weight / 1000 }.toMutableMap()
        val target = plan?.target
        if (profile != null && target != null) {
            val protein = profile.proteinPerKg * weight
            val fat = target * profile.fatPercent / 100 / 9
            targets[nutrientKey(HealthDietParser.KCAL)] = target
            targets[nutrientKey(HealthDietParser.PROTEIN)] = protein
            targets[nutrientKey(HealthDietParser.FAT)] = fat
            targets[nutrientKey(HealthDietParser.CARBS)] = max(0.0, (target - protein * 4 - fat * 9) / 4)
        }
        return targets
    }

    companion object {
        const val DEFAULT_WEIGHT = 70.0

        /**
         * WHO/FAO/UNU 2007 adult requirements for essential amino acids, mg per kg of body weight per day.
         * Keys match the site's row names via [nutrientKey].
         */
        val AMINO_ACID_MG_PER_KG = mapOf(
            "Гистидин" to 10.0,
            "Изолейцин" to 20.0,
            "Лейцин" to 39.0,
            "Лизин" to 30.0,
            "Метионин" to 10.4,
            "Метионин + Цистеин" to 15.0,
            "Фенилаланин+Тирозин" to 25.0,
            "Треонин" to 15.0,
            "Триптофан" to 4.0,
            "Валин" to 26.0,
        ).mapKeys { nutrientKey(it.key) }
    }
}

fun nutrientKey(name: String) = name.lowercase().replace(" ", "").trimEnd('*')

/**
 * MacroFactor-style adaptive expenditure.
 *
 * 1. Trend weight: exponential moving average of scale weights.
 * 2. Observed expenditure over the last [WINDOW] days = average logged intake − change in trend weight × 7700 kcal/kg ÷ days.
 * 3. Blend with the Mifflin-St Jeor formula estimate, trusting the observation more as logged days accumulate,
 *    then smooth so the target moves gradually.
 * 4. Calorie target = expenditure + goal rate × 7700 / 7.
 */
object EnergyModel {
    const val KCAL_PER_KG = 7700.0
    private const val TREND_ALPHA = 0.1
    private const val EXPENDITURE_ALPHA = 0.15
    const val WINDOW = 21
    const val MIN_LOGGED_DAYS = 7
    const val FULL_CONFIDENCE_DAYS = 14
    private const val MIN_SPAN_DAYS = 7

    val ACTIVITY = listOf(
        1.2 to R.string.activity_sedentary,
        1.375 to R.string.activity_light,
        1.55 to R.string.activity_moderate,
        1.725 to R.string.activity_very_active,
        1.9 to R.string.activity_athlete,
    )

    fun formulaTdee(profile: ProfileEntity, weightKg: Double): Double {
        val bmr = 10 * weightKg + 6.25 * profile.heightCm - 5 * profile.age + if (profile.male) 5 else -161
        return bmr * ACTIVITY[profile.activity.coerceIn(ACTIVITY.indices)].first
    }

    fun minTarget(profile: ProfileEntity) = if (profile.male) 1500.0 else 1200.0

    fun compute(profile: ProfileEntity?, weights: List<WeightEntity>, intake: Map<Long, Double>, today: Long): Plan {
        val weightByDay = weights.associate { it.epochDay to it.kg }
        val dataDays = weightByDay.keys + intake.keys + listOfNotNull(profile?.startDay)
        val start = dataDays.minOrNull() ?: return Plan(profile, emptyList())
        val end = max(today + 1, dataDays.max())

        val trends = ArrayList<Double?>()
        val days = ArrayList<DayPlan>()
        var trend: Double? = null
        var expenditure: Double? = null

        for (day in start..end) {
            val i = (day - start).toInt()
            // Expenditure for this day is learned from the window ending yesterday.
            var loggedDays = 0
            if (profile != null) {
                learn(profile, trends, intake, start, i)?.let { (raw, logged) ->
                    expenditure = expenditure?.let { it + EXPENDITURE_ALPHA * (raw - it) } ?: raw
                    loggedDays = logged
                }
            }
            val target = expenditure?.let { e ->
                max(minTarget(profile!!), e + profile.goalKgPerWeek * KCAL_PER_KG / 7)
            }

            weightByDay[day]?.let { w -> trend = trend?.let { it + TREND_ALPHA * (w - it) } ?: w }
            trends += trend
            days += DayPlan(day, weightByDay[day], trend, intake[day], expenditure, target, loggedDays)
        }
        return Plan(profile, days)
    }

    /** Raw expenditure estimate for day index [i] from days [i - WINDOW, i - 1], or null without any weight yet. */
    private fun learn(
        profile: ProfileEntity,
        trends: List<Double?>,
        intake: Map<Long, Double>,
        start: Long,
        i: Int,
    ): Pair<Double, Int>? {
        val latest = trends.lastOrNull() ?: return null
        val prior = formulaTdee(profile, latest)
        val windowStart = trends.indices.drop(max(0, i - WINDOW)).firstOrNull { trends[it] != null } ?: return prior to 0
        val span = (i - 1) - windowStart
        val logged = (windowStart until i).mapNotNull { intake[start + it] }
        if (span < MIN_SPAN_DAYS || logged.size < MIN_LOGGED_DAYS) return prior to 0

        val trendChange = latest - trends[windowStart]!!
        val observed = (logged.average() - trendChange * KCAL_PER_KG / span).coerceIn(800.0, 6000.0)
        val confidence = ((logged.size - MIN_LOGGED_DAYS + 1).toDouble() /
            (FULL_CONFIDENCE_DAYS - MIN_LOGGED_DAYS + 1)).coerceIn(0.0, 1.0)
        return prior * (1 - confidence) + observed * confidence to logged.size
    }
}
