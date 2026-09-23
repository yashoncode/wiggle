package io.wiggle.domain

import java.time.LocalTime

/** The line at the top of Today: the time of day, and who is being tracked. */
object Greeting {

    fun forTime(now: LocalTime = LocalTime.now()): String = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        // Past ten at night and before five, "evening" stops being true.
        else -> "Good night"
    }

    /**
     * Only the first name: "Good afternoon, Yashwanth Kumar" is a mouthful, and the header line
     * shrinks to fit whatever is left.
     */
    fun forPerson(name: String?, now: LocalTime = LocalTime.now()): String {
        val first = name?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
            ?: return forTime(now)
        return "${forTime(now)}, $first"
    }
}
