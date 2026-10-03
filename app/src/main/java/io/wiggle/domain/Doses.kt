package io.wiggle.domain

import io.wiggle.data.db.DoseLogEntity
import io.wiggle.data.db.MedicationEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class DoseStatus { Taken, Overdue, Due }

/** One dose on today's list: a medication at one of its times. */
data class Dose(
    val medication: MedicationEntity,
    val slotMinutes: Int,
    val status: DoseStatus,
)

/**
 * Tablet schedule rules. Pure, so "overdue", "days left" and when the next nag fires are all tested
 * without an alarm clock.
 */
object Doses {

    /** How long after its time a dose that has not been ticked off keeps reminding. */
    const val NAG_WINDOW_MINUTES = 120

    fun parseTimes(csv: String): List<Int> =
        csv.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 0 until 24 * 60 }.distinct().sorted()

    fun timesCsv(times: List<Int>): String = times.distinct().sorted().joinToString(",")

    /** Today's doses in time order; one not taken is overdue once its time has passed. */
    fun today(
        medications: List<MedicationEntity>,
        taken: List<DoseLogEntity>,
        nowMinutes: Int,
    ): List<Dose> {
        val done = taken.mapTo(HashSet()) { it.medicationId to it.slotMinutes }
        return medications.flatMap { medication ->
            parseTimes(medication.times).map { slot ->
                val status = when {
                    (medication.id to slot) in done -> DoseStatus.Taken
                    slot < nowMinutes -> DoseStatus.Overdue
                    else -> DoseStatus.Due
                }
                Dose(medication, slot, status)
            }
        }.sortedWith(compareBy({ it.slotMinutes }, { it.medication.name }))
    }

    /** Whole days the counted stock lasts at the current schedule, or null when not counted. */
    fun daysLeft(medication: MedicationEntity): Int? {
        val stock = medication.stock ?: return null
        val perDay = parseTimes(medication.times).size * medication.perDose.coerceAtLeast(1)
        if (perDay == 0) return null
        return stock / perDay
    }

    /**
     * When the alarm for one dose should next go off.
     *
     * Before the dose: at its time. After it, while it is still not taken and inside the nag window:
     * again in [repeatMinutes]. Otherwise: tomorrow at its time.
     */
    fun nextAlarm(
        slotMinutes: Int,
        now: LocalDateTime,
        takenToday: Boolean,
        repeatMinutes: Int,
    ): LocalDateTime {
        val slot = LocalDateTime.of(now.toLocalDate(), LocalTime.of(slotMinutes / 60, slotMinutes % 60))
        if (now.isBefore(slot)) return slot
        val nagUntil = slot.plusMinutes(NAG_WINDOW_MINUTES.toLong())
        val nextNag = now.plusMinutes(repeatMinutes.coerceAtLeast(5).toLong())
        if (!takenToday && nextNag.isBefore(nagUntil)) return nextNag
        return slot.plusDays(1)
    }

    fun epochDay(date: LocalDate = LocalDate.now()): Long = date.toEpochDay()
}
