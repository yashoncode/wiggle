package io.wiggle.domain

import java.time.LocalDate
import kotlin.math.log10
import kotlin.math.roundToLong

/** One weight reading, already reduced to the day it belongs to. */
data class DayWeight(val date: LocalDate, val kg: Double)

/** A point on a derived series: a calendar day and the value for that day. */
data class SeriesPoint(val date: LocalDate, val value: Double)

object Stats {

    /**
     * Collapses raw entries to one value per calendar day. Several weigh-ins on the same day are
     * averaged, which is what makes the moving average below a calendar average rather than an
     * entry average.
     */
    fun toDaily(entries: List<DayWeight>): List<DayWeight> =
        entries.groupBy { it.date }
            .map { (date, sameDay) -> DayWeight(date, sameDay.sumOf { it.kg } / sameDay.size) }
            .sortedBy { it.date }

    /**
     * Seven-day moving average over **calendar days**, not over the last seven entries.
     *
     * For each day that has a reading, the window is the seven calendar days ending that day. A
     * day inside the window with no reading simply contributes nothing, so a gap widens the
     * window in time rather than reaching further back through the entry list.
     */
    fun movingAverage(daily: List<DayWeight>, windowDays: Int = 7): List<SeriesPoint> {
        if (daily.isEmpty()) return emptyList()
        val byDate = daily.associate { it.date to it.kg }
        return daily.map { day ->
            var sum = 0.0
            var count = 0
            for (offset in 0 until windowDays) {
                byDate[day.date.minusDays(offset.toLong())]?.let { sum += it; count++ }
            }
            SeriesPoint(day.date, sum / count)
        }
    }

    /**
     * Trend weight: an exponential moving average with alpha 0.1, so a single heavy day moves the
     * line by a tenth of its distance rather than dragging it along.
     */
    fun exponentialMovingAverage(daily: List<DayWeight>, alpha: Double = 0.1): List<SeriesPoint> {
        if (daily.isEmpty()) return emptyList()
        var ema = daily.first().kg
        return daily.map { day ->
            ema += alpha * (day.kg - ema)
            SeriesPoint(day.date, ema)
        }
    }

    /**
     * Least-squares slope in kg per day. Null when there are fewer than two distinct days, since a
     * single point has no trend.
     */
    fun dailySlopeKg(daily: List<DayWeight>): Double? {
        if (daily.size < 2) return null
        val origin = daily.first().date.toEpochDay()
        val xs = daily.map { (it.date.toEpochDay() - origin).toDouble() }
        val ys = daily.map { it.kg }
        val meanX = xs.average()
        val meanY = ys.average()
        var numerator = 0.0
        var denominator = 0.0
        for (i in xs.indices) {
            val dx = xs[i] - meanX
            numerator += dx * (ys[i] - meanY)
            denominator += dx * dx
        }
        return if (denominator == 0.0) null else numerator / denominator
    }

    /** Weekly rate of change: the regression slope times seven. */
    fun weeklyRateKg(daily: List<DayWeight>): Double? = dailySlopeKg(daily)?.times(7)

    /**
     * Projected date of reaching [goalKg] at the current slope.
     *
     * Null when there is no trend, when the trend is flat, or when it points away from the goal —
     * projecting a date in those cases would be inventing one.
     */
    fun projectedGoalDate(
        daily: List<DayWeight>,
        goalKg: Double,
        today: LocalDate = LocalDate.now(),
    ): LocalDate? {
        val slope = dailySlopeKg(daily) ?: return null
        val current = daily.lastOrNull()?.kg ?: return null
        val distance = goalKg - current
        if (kotlin.math.abs(distance) < 0.05) return today
        // The slope has to point the same way as the distance, and not be a rounding artefact.
        if (kotlin.math.abs(slope) < 1e-4) return null
        if (distance > 0 != slope > 0) return null
        val days = (distance / slope).roundToLong()
        if (days < 0 || days > 3650) return null
        return today.plusDays(days)
    }

    /** Total change across the window: last reading minus first. */
    fun totalChangeKg(daily: List<DayWeight>): Double? =
        if (daily.size < 2) null else daily.last().kg - daily.first().kg

    /**
     * Average weight per weekday across the window, for the weekday-pattern chart. Index 0 is
     * Monday. A weekday with no readings is null.
     */
    fun weekdayAverages(daily: List<DayWeight>): List<Double?> {
        val buckets = Array(7) { mutableListOf<Double>() }
        daily.forEach { buckets[it.date.dayOfWeek.value - 1].add(it.kg) }
        return buckets.map { if (it.isEmpty()) null else it.average() }
    }

    /**
     * Longest run of consecutive calendar days ending today (or yesterday, so a streak is not
     * lost before the day is over) that have a reading.
     */
    fun currentStreak(daily: List<DayWeight>, today: LocalDate = LocalDate.now()): Int {
        if (daily.isEmpty()) return 0
        val dates = daily.mapTo(HashSet()) { it.date }
        var cursor = if (today in dates) today else today.minusDays(1)
        if (cursor !in dates) return 0
        var streak = 0
        while (cursor in dates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    // --- Body composition -------------------------------------------------------------------

    fun bmi(weightKg: Double, heightCm: Double): Double? {
        if (heightCm <= 0) return null
        val metres = heightCm / 100.0
        return weightKg / (metres * metres)
    }

    enum class BmiCategory(val label: String) {
        Underweight("Underweight"),
        Healthy("Healthy range"),
        Overweight("Overweight"),
        Obese("Obese"),
    }

    fun bmiCategory(bmi: Double): BmiCategory = when {
        bmi < 18.5 -> BmiCategory.Underweight
        bmi < 25.0 -> BmiCategory.Healthy
        bmi < 30.0 -> BmiCategory.Overweight
        else -> BmiCategory.Obese
    }

    /**
     * US Navy body-fat estimate, in percent. This is an estimate from tape measurements, not a
     * measurement — every surface that shows it says so.
     *
     * Male needs waist and neck; female also needs hips. Returns null when a required measurement
     * is missing or the logarithms would be undefined.
     */
    fun navyBodyFatPercent(
        isFemale: Boolean,
        heightCm: Double,
        neckCm: Double?,
        waistCm: Double?,
        hipsCm: Double?,
    ): Double? {
        if (heightCm <= 0) return null
        val neck = neckCm ?: return null
        val waist = waistCm ?: return null
        val result = if (isFemale) {
            val hips = hipsCm ?: return null
            val inner = waist + hips - neck
            if (inner <= 0) return null
            495 / (1.29579 - 0.35004 * log10(inner) + 0.22100 * log10(heightCm)) - 450
        } else {
            val inner = waist - neck
            if (inner <= 0) return null
            495 / (1.0324 - 0.19077 * log10(inner) + 0.15456 * log10(heightCm)) - 450
        }
        return if (result.isFinite() && result in 1.0..75.0) result else null
    }

    fun waistToHipRatio(waistCm: Double?, hipsCm: Double?): Double? {
        val waist = waistCm ?: return null
        val hips = hipsCm ?: return null
        return if (hips <= 0) null else waist / hips
    }

    /** World Health Organization waist-to-hip risk bands. */
    fun whrRisk(ratio: Double, isFemale: Boolean): String = when {
        isFemale -> when {
            ratio < 0.80 -> "Low risk"
            ratio < 0.85 -> "Moderate risk"
            else -> "High risk"
        }
        else -> when {
            ratio < 0.90 -> "Low risk"
            ratio < 1.00 -> "Moderate risk"
            else -> "High risk"
        }
    }

    /** Fraction of the way from [startKg] to [goalKg], clamped to 0..1. */
    fun goalProgress(startKg: Double, currentKg: Double, goalKg: Double): Float {
        val span = goalKg - startKg
        if (kotlin.math.abs(span) < 1e-6) return 1f
        return ((currentKg - startKg) / span).coerceIn(0.0, 1.0).toFloat()
    }
}
