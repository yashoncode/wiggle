package io.wiggle.domain

import io.wiggle.data.db.ReminderEntity
import io.wiggle.data.db.ReminderKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Works out when a reminder next fires. Both the alarm scheduler and the "Up next" list read
 * from here, so what the user is told and what actually gets scheduled cannot drift apart.
 */
object ReminderSchedule {

    /** Bit 0 is Monday, matching [DayOfWeek.getValue] minus one. */
    fun isDayEnabled(daysMask: Int, day: DayOfWeek): Boolean =
        (daysMask shr (day.value - 1)) and 1 == 1

    fun toggleDay(daysMask: Int, day: DayOfWeek): Int = daysMask xor (1 shl (day.value - 1))

    fun dayLabels(daysMask: Int): String = when {
        daysMask == 0b1111111 -> "Every day"
        daysMask == 0b0011111 -> "Weekdays"
        daysMask == 0b1100000 -> "Weekends"
        daysMask == 0 -> "No days"
        else -> DayOfWeek.entries
            .filter { isDayEnabled(daysMask, it) }
            .joinToString(" ") { it.name.take(1) + it.name[1].lowercaseChar() }
    }

    fun timeLabel(minutes: Int): String {
        val time = LocalTime.of(minutes / 60, minutes % 60)
        return time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
    }

    /**
     * The next moment this reminder fires, or null when it is off or has no active days.
     *
     * [anchor] is the moment to search forward from, which lets the alarm scheduler ask for the
     * slot after the one that just fired instead of getting the same one back.
     */
    fun nextOccurrence(
        reminder: ReminderEntity,
        anchor: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalDateTime? {
        if (!reminder.enabled) return null
        return when (reminder.kind) {
            ReminderKind.WeighIn -> nextDaily(reminder.daysMask, reminder.timeMinutes, anchor)
            ReminderKind.Measurements -> nextEveryNWeeks(reminder, anchor)
            ReminderKind.Water -> nextWaterSlot(reminder, anchor)
        }
    }

    private fun nextDaily(daysMask: Int, timeMinutes: Int, anchor: LocalDateTime): LocalDateTime? {
        if (daysMask == 0) return null
        val time = LocalTime.of(timeMinutes / 60, timeMinutes % 60)
        for (offset in 0..7) {
            val date = anchor.toLocalDate().plusDays(offset.toLong())
            if (!isDayEnabled(daysMask, date.dayOfWeek)) continue
            val candidate = LocalDateTime.of(date, time)
            if (candidate.isAfter(anchor)) return candidate
        }
        return null
    }

    /**
     * Measurements repeat every N weeks on a chosen weekday. The cycle is anchored to epoch week
     * zero so it stays put across reschedules rather than resetting every time the app restarts.
     */
    private fun nextEveryNWeeks(reminder: ReminderEntity, anchor: LocalDateTime): LocalDateTime? {
        if (reminder.daysMask == 0) return null
        val every = reminder.everyNWeeks.coerceAtLeast(1)
        val time = LocalTime.of(reminder.timeMinutes / 60, reminder.timeMinutes % 60)
        for (offset in 0..(7 * every * 2)) {
            val date = anchor.toLocalDate().plusDays(offset.toLong())
            if (!isDayEnabled(reminder.daysMask, date.dayOfWeek)) continue
            if (weekIndex(date) % every != 0L) continue
            val candidate = LocalDateTime.of(date, time)
            if (candidate.isAfter(anchor)) return candidate
        }
        return null
    }

    private fun weekIndex(date: LocalDate): Long = date.toEpochDay() / 7

    /**
     * Water nudges land on a fixed grid inside the active window: start, start + interval, and so
     * on. A fixed grid means a reminder logged at an odd time does not shift every later slot.
     */
    private fun nextWaterSlot(reminder: ReminderEntity, anchor: LocalDateTime): LocalDateTime? {
        val interval = reminder.intervalMinutes.coerceAtLeast(15)
        val start = reminder.timeMinutes
        val end = reminder.untilMinutes
        if (end <= start) return null

        for (dayOffset in 0..1) {
            val date = anchor.toLocalDate().plusDays(dayOffset.toLong())
            var minutes = start
            while (minutes <= end) {
                val candidate = LocalDateTime.of(date, LocalTime.of(minutes / 60, minutes % 60))
                if (candidate.isAfter(anchor)) return candidate
                minutes += interval
            }
        }
        return null
    }

    fun title(kind: ReminderKind): String = when (kind) {
        ReminderKind.WeighIn -> "Weigh-in"
        ReminderKind.Measurements -> "Body measurements"
        ReminderKind.Water -> "Drink water"
    }
}
