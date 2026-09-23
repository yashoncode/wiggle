package io.wiggle.domain

import io.wiggle.data.db.ReminderEntity
import java.time.LocalDate

/** Where one of today's logs stands. */
enum class LogStatus {
    /** Written today. */
    Done,

    /** A reminder puts it on today's list and it has not been written yet. */
    Due,

    /** Nothing asked for it today. */
    Idle,
}

/**
 * What the home-screen widget says about today.
 *
 * Pure, so the rules that decide "done", "due" and "how much water is left" can be tested without
 * a device, and so the widget itself stays a drawing.
 */
object TodayOverview {

    fun statusFor(loggedToday: Boolean, dueToday: Boolean): LogStatus = when {
        loggedToday -> LogStatus.Done
        dueToday -> LogStatus.Due
        else -> LogStatus.Idle
    }

    /**
     * Whether [reminder] has a slot on [today].
     *
     * Asked from the start of the day rather than from now, because a weigh-in that was due at
     * 07:00 is still on today's list at noon.
     */
    fun dueToday(reminder: ReminderEntity?, today: LocalDate = LocalDate.now()): Boolean {
        if (reminder == null || !reminder.enabled) return false
        val next = ReminderSchedule.nextOccurrence(reminder, today.atStartOfDay())
        return next?.toLocalDate() == today
    }

    /** Millilitres still to drink, never negative and never past the goal. */
    fun remainingMl(totalMl: Int, goalMl: Int): Int = (goalMl - totalMl).coerceAtLeast(0)
}
