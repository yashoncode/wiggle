package io.wiggle.domain

import io.wiggle.data.db.ReminderEntity
import io.wiggle.data.db.ReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class TodayOverviewTest {

    private val monday: LocalDate = LocalDate.of(2026, 9, 21)

    private fun weighIn(enabled: Boolean = true, daysMask: Int = 0b1111111) = ReminderEntity(
        profileId = 1,
        kind = ReminderKind.WeighIn,
        enabled = enabled,
        timeMinutes = 7 * 60,
        daysMask = daysMask,
    )

    @Test
    fun `logged today wins over everything else`() {
        assertEquals(LogStatus.Done, TodayOverview.statusFor(loggedToday = true, dueToday = true))
        assertEquals(LogStatus.Done, TodayOverview.statusFor(loggedToday = true, dueToday = false))
    }

    @Test
    fun `unlogged and asked for is due`() {
        assertEquals(LogStatus.Due, TodayOverview.statusFor(loggedToday = false, dueToday = true))
    }

    @Test
    fun `unlogged and unasked is idle`() {
        assertEquals(LogStatus.Idle, TodayOverview.statusFor(loggedToday = false, dueToday = false))
    }

    @Test
    fun `a switched off reminder is never due`() {
        assertFalse(TodayOverview.dueToday(weighIn(enabled = false), monday))
        assertFalse(TodayOverview.dueToday(null, monday))
    }

    @Test
    fun `a reminder set for today is due today`() {
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)
        assertTrue(TodayOverview.dueToday(weighIn(), monday))
    }

    @Test
    fun `a slot earlier today still counts as due`() {
        // 07:00 has passed by the afternoon, and the weigh-in is still owed.
        assertTrue(TodayOverview.dueToday(weighIn(daysMask = 0b0000001), monday))
    }

    @Test
    fun `a reminder set for other days is not due`() {
        // Sunday only.
        assertFalse(TodayOverview.dueToday(weighIn(daysMask = 1 shl 6), monday))
    }

    @Test
    fun `water left never goes negative`() {
        assertEquals(700, TodayOverview.remainingMl(1800, 2500))
        assertEquals(0, TodayOverview.remainingMl(2500, 2500))
        assertEquals(0, TodayOverview.remainingMl(3000, 2500))
    }
}
