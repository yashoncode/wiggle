package io.wiggle.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * Emits the current local date, then again at every local midnight.
 *
 * Screens that say "today" need this: without it, a phone left open overnight keeps showing
 * yesterday's water and yesterday's date until something else happens to recompose.
 */
fun todayFlow(zone: ZoneId = ZoneId.systemDefault()): Flow<LocalDate> = flow {
    while (true) {
        val today = LocalDate.now(zone)
        emit(today)
        val nextMidnight = today.plusDays(1).atStartOfDay(zone).toInstant()
        val wait = Duration.between(java.time.Instant.now(), nextMidnight).toMillis()
        // A second of slack so the next LocalDate.now() is definitely past midnight.
        delay(wait.coerceAtLeast(0) + 1_000)
    }
}
