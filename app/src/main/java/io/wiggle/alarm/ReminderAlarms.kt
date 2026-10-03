package io.wiggle.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.wiggle.data.db.MedicationEntity
import io.wiggle.data.db.ReminderEntity
import io.wiggle.data.db.ReminderKind
import io.wiggle.domain.Doses
import io.wiggle.domain.ReminderSchedule
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Intent wiring shared by the scheduler, the receiver and the notification actions. */
object Alarms {
    const val ACTION_FIRE = "io.wiggle.action.FIRE"
    const val ACTION_SNOOZE = "io.wiggle.action.SNOOZE"
    const val ACTION_ADD_WATER = "io.wiggle.action.ADD_WATER"
    const val ACTION_DOSE = "io.wiggle.action.DOSE"
    const val ACTION_DOSE_TAKEN = "io.wiggle.action.DOSE_TAKEN"

    const val EXTRA_PROFILE_ID = "profileId"
    const val EXTRA_KIND = "kind"
    const val EXTRA_AMOUNT_ML = "amountMl"
    const val EXTRA_MEDICATION_ID = "medicationId"
    const val EXTRA_SLOT = "slot"

    /** Where a notification tap should land in the app. Read by MainActivity. */
    const val EXTRA_OPEN = "io.wiggle.extra.OPEN"
    const val OPEN_WEIGHT = "weight"
    const val OPEN_BODY = "body"
    const val OPEN_WATER = "water"
    const val OPEN_FOOD = "food"
    const val OPEN_STEPS = "steps"
    const val OPEN_TABLETS = "tablets"

    /** Most dose times one tablet can have; each gets its own alarm slot. */
    const val MAX_DOSE_SLOTS = 8

    const val SNOOZE_MINUTES = 30

    fun openTargetFor(kind: ReminderKind): String = when (kind) {
        ReminderKind.WeighIn -> OPEN_WEIGHT
        ReminderKind.Measurements -> OPEN_BODY
        ReminderKind.Water -> OPEN_WATER
        ReminderKind.Meals -> OPEN_FOOD
        ReminderKind.Move -> OPEN_STEPS
        ReminderKind.Tablets -> OPEN_TABLETS
    }
}

/**
 * Puts the times [ReminderSchedule] works out into AlarmManager.
 *
 * One alarm per reminder, re-armed by the receiver after each firing: a repeating alarm cannot
 * express "every other Sunday" or "skip when the goal is already met", and re-arming keeps the
 * schedule honest across daylight saving too.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
) {

    /**
     * Arms every enabled reminder and clears the rest, then does the same for every tablet dose,
     * which only rings while that person's Tablets reminder is on. Safe to call repeatedly.
     */
    fun sync(reminders: List<ReminderEntity>, medications: List<MedicationEntity>) {
        reminders.forEach { if (it.enabled) schedule(it) else cancel(it) }
        val tabletsOn = reminders.filter { it.kind == ReminderKind.Tablets && it.enabled }
            .mapTo(HashSet()) { it.profileId }
        medications.forEach { medication ->
            val times = Doses.parseTimes(medication.times)
            for (index in 0 until Alarms.MAX_DOSE_SLOTS) {
                val slot = times.getOrNull(index)
                if (slot != null && medication.profileId in tabletsOn) {
                    val at = Doses.nextAlarm(slot, LocalDateTime.now(), takenToday = true, repeatMinutes = 0)
                    scheduleDose(medication.id, index, slot, at)
                } else {
                    alarmManager.cancel(dosePending(medication.id, index, slot ?: 0))
                }
            }
        }
    }

    /** One tablet's dose alarm, at its time or at the next nag. */
    fun scheduleDose(medicationId: Long, index: Int, slotMinutes: Int, at: LocalDateTime) {
        setAlarm(
            at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            dosePending(medicationId, index, slotMinutes),
        )
    }

    private fun dosePending(medicationId: Long, index: Int, slotMinutes: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = Alarms.ACTION_DOSE
            putExtra(Alarms.EXTRA_MEDICATION_ID, medicationId)
            putExtra(Alarms.EXTRA_SLOT, slotMinutes)
        }
        return PendingIntent.getBroadcast(
            context,
            doseRequestCode(medicationId, index),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun schedule(reminder: ReminderEntity, anchor: LocalDateTime = LocalDateTime.now()) {
        val next = ReminderSchedule.nextOccurrence(reminder, anchor)
        if (next == null) {
            cancel(reminder)
            return
        }
        setAlarm(next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), firePending(reminder))
    }

    fun snooze(profileId: Long, kind: ReminderKind) {
        val at = System.currentTimeMillis() + Alarms.SNOOZE_MINUTES * 60_000L
        setAlarm(at, pendingIntent(snoozeRequestCode(profileId, kind), profileId, kind))
    }

    fun cancel(reminder: ReminderEntity) {
        alarmManager.cancel(firePending(reminder))
    }

    private fun firePending(reminder: ReminderEntity): PendingIntent =
        pendingIntent(requestCode(reminder.profileId, reminder.kind), reminder.profileId, reminder.kind)

    private fun pendingIntent(requestCode: Int, profileId: Long, kind: ReminderKind): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = Alarms.ACTION_FIRE
            putExtra(Alarms.EXTRA_PROFILE_ID, profileId)
            putExtra(Alarms.EXTRA_KIND, kind.name)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Exact when the system allows it, a ten-minute window when it does not. Losing exactness is
     * better than losing the reminder, and a nudge to drink water does not need to be to the second.
     */
    private fun setAlarm(triggerAtMillis: Long, operation: PendingIntent) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (exact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        } else {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                10 * 60_000L,
                operation,
            )
        }
    }

    private fun requestCode(profileId: Long, kind: ReminderKind): Int =
        (profileId.toInt() * 10) + kind.ordinal

    private fun snoozeRequestCode(profileId: Long, kind: ReminderKind): Int =
        1_000_000 + requestCode(profileId, kind)

    private fun doseRequestCode(medicationId: Long, index: Int): Int =
        2_000_000 + medicationId.toInt() * Alarms.MAX_DOSE_SLOTS + index
}
