package io.wiggle.domain

import java.time.LocalDate

/** Rules for reading a step count. Pure, like [Stats], so they are tested without Health Connect. */
object StepStats {

    /**
     * Consecutive days at or over [goal], ending today, or ending yesterday while today is not
     * met yet: the same rule as the weigh-in streak, so a streak is not lost before the day is out.
     */
    fun streak(daily: Map<LocalDate, Long>, goal: Int, today: LocalDate = LocalDate.now()): Int {
        fun met(date: LocalDate) = (daily[date] ?: 0L) >= goal
        var cursor = if (met(today)) today else today.minusDays(1)
        var days = 0
        while (met(cursor)) {
            days++
            cursor = cursor.minusDays(1)
        }
        return days
    }

    /** The hour of the day with the most steps, or null when there are none yet. */
    fun busiestHour(hourly: List<Long>): Int? =
        hourly.indices.maxByOrNull { hourly[it] }?.takeIf { hourly[it] > 0 }

    /** "2–3 PM", "11 AM–12 PM". */
    fun hourRange(hour: Int): String {
        fun label(h: Int): String {
            val clock = if (h % 12 == 0) 12 else h % 12
            return "$clock ${if (h % 24 < 12) "AM" else "PM"}"
        }
        val start = label(hour)
        val end = label(hour + 1)
        // "2–3 PM" when both ends share a half of the day, otherwise both halves are spelled out.
        return if (start.takeLast(2) == end.takeLast(2)) "${start.dropLast(3)}–$end" else "$start–$end"
    }

    /** 7,842 */
    fun grouped(steps: Long): String = "%,d".format(steps)
}
