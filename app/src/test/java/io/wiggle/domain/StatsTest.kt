package io.wiggle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsTest {

    private val day0 = LocalDate.of(2026, 9, 1)

    private fun series(vararg pairs: Pair<Int, Double>) =
        pairs.map { (offset, kg) -> DayWeight(day0.plusDays(offset.toLong()), kg) }

    @Test
    fun `several weigh-ins on one day collapse to their average`() {
        val daily = Stats.toDaily(
            listOf(
                DayWeight(day0, 80.0),
                DayWeight(day0, 82.0),
                DayWeight(day0.plusDays(1), 79.0),
            )
        )
        assertEquals(2, daily.size)
        assertEquals(81.0, daily[0].kg, 1e-9)
        assertEquals(79.0, daily[1].kg, 1e-9)
    }

    @Test
    fun `moving average uses calendar days, not the last seven entries`() {
        // Two readings 30 days apart. The second one's window reaches back seven calendar days
        // and finds nothing else, so it must equal itself rather than averaging with the first.
        val daily = series(0 to 80.0, 30 to 70.0)
        val avg = Stats.movingAverage(daily)
        assertEquals(80.0, avg[0].value, 1e-9)
        assertEquals(70.0, avg[1].value, 1e-9)
    }

    @Test
    fun `moving average includes every reading inside the seven day window`() {
        val daily = series(0 to 80.0, 1 to 78.0, 2 to 76.0)
        val avg = Stats.movingAverage(daily)
        assertEquals(78.0, avg[2].value, 1e-9)
    }

    @Test
    fun `exponential moving average moves a tenth of the way each step`() {
        val daily = series(0 to 80.0, 1 to 90.0)
        val ema = Stats.exponentialMovingAverage(daily, alpha = 0.1)
        assertEquals(80.0, ema[0].value, 1e-9)
        assertEquals(81.0, ema[1].value, 1e-9)
    }

    @Test
    fun `weekly rate is the regression slope times seven`() {
        // Exactly 0.1 kg lost per day.
        val daily = (0..9).map { DayWeight(day0.plusDays(it.toLong()), 80.0 - 0.1 * it) }
        assertEquals(-0.7, Stats.weeklyRateKg(daily)!!, 1e-9)
    }

    @Test
    fun `a single day has no slope`() {
        assertNull(Stats.dailySlopeKg(series(0 to 80.0)))
    }

    @Test
    fun `goal date divides the remaining distance by the daily slope`() {
        val daily = (0..9).map { DayWeight(day0.plusDays(it.toLong()), 80.0 - 0.1 * it) }
        // Ends at 79.1, goal 78.1, losing 0.1/day, so ten days out.
        val projected = Stats.projectedGoalDate(daily, goalKg = 78.1, today = day0.plusDays(9))
        assertEquals(day0.plusDays(19), projected)
    }

    @Test
    fun `goal date is null when the trend points away from the goal`() {
        val gaining = (0..9).map { DayWeight(day0.plusDays(it.toLong()), 80.0 + 0.1 * it) }
        assertNull(Stats.projectedGoalDate(gaining, goalKg = 70.0, today = day0.plusDays(9)))
    }

    @Test
    fun `bmi and its category`() {
        val bmi = Stats.bmi(72.4, 177.0)!!
        assertEquals(23.1, bmi, 0.05)
        assertEquals(Stats.BmiCategory.Healthy, Stats.bmiCategory(bmi))
        assertEquals(Stats.BmiCategory.Underweight, Stats.bmiCategory(18.0))
        assertEquals(Stats.BmiCategory.Overweight, Stats.bmiCategory(27.0))
        assertEquals(Stats.BmiCategory.Obese, Stats.bmiCategory(31.0))
    }

    @Test
    fun `navy body fat lands in a sane band and needs its inputs`() {
        val male = Stats.navyBodyFatPercent(
            isFemale = false, heightCm = 177.0, neckCm = 37.5, waistCm = 82.5, hipsCm = null,
        )
        assertNotNull(male)
        assertTrue("got $male", male!! in 10.0..22.0)

        val female = Stats.navyBodyFatPercent(
            isFemale = true, heightCm = 165.0, neckCm = 32.0, waistCm = 72.0, hipsCm = 96.0,
        )
        assertNotNull(female)
        assertTrue("got $female", female!! in 18.0..34.0)

        // Female needs hips; without them there is no estimate to give.
        assertNull(
            Stats.navyBodyFatPercent(
                isFemale = true, heightCm = 165.0, neckCm = 32.0, waistCm = 72.0, hipsCm = null,
            )
        )
        // A waist at or below the neck makes the logarithm undefined.
        assertNull(
            Stats.navyBodyFatPercent(
                isFemale = false, heightCm = 177.0, neckCm = 40.0, waistCm = 38.0, hipsCm = null,
            )
        )
    }

    @Test
    fun `streak counts back from today and stops at the first gap`() {
        val today = day0.plusDays(10)
        val daily = Stats.toDaily(
            listOf(8, 9, 10).map { DayWeight(day0.plusDays(it.toLong()), 80.0) } +
                DayWeight(day0.plusDays(5), 80.0)
        )
        assertEquals(3, Stats.currentStreak(daily, today))
    }

    @Test
    fun `streak survives not having weighed in yet today`() {
        val today = day0.plusDays(10)
        val daily = Stats.toDaily(listOf(8, 9).map { DayWeight(day0.plusDays(it.toLong()), 80.0) })
        assertEquals(2, Stats.currentStreak(daily, today))
    }

    @Test
    fun `goal progress is clamped both ways`() {
        assertEquals(0.5f, Stats.goalProgress(80.0, 75.0, 70.0), 1e-6f)
        assertEquals(0f, Stats.goalProgress(80.0, 85.0, 70.0), 1e-6f)
        assertEquals(1f, Stats.goalProgress(80.0, 65.0, 70.0), 1e-6f)
    }

    @Test
    fun `unit conversions round trip`() {
        val kg = 72.4
        assertEquals(kg, WeightUnit.Lb.toKg(WeightUnit.Lb.fromKg(kg)), 1e-9)
        assertEquals(159.6, WeightUnit.Lb.fromKg(kg), 0.1)
        assertEquals(69.7, LengthUnit.In.fromCm(177.0), 0.1)
        assertEquals(250, VolumeUnit.FlOz.toMl(VolumeUnit.FlOz.fromMl(250)))
    }
}
