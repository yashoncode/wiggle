package io.wiggle.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.wiggle.data.db.ReminderEntity
import io.wiggle.data.db.ReminderKind
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

    const val EXTRA_PROFILE_ID = "profileId"
    const val EXTRA_KIND = "kind"
    const val EXTRA_AMOUNT_ML = "amountMl"

    /** Where a notification tap should land in the app. Read by MainActivity. */
    const val EXTRA_OPEN = "io.wiggle.extra.OPEN"
    const val OPEN_WEIGHT = "weight"
    const val OPEN_BODY = "body"
    const val OPEN_WATER = "water"

    const val SNOOZE_MINUTES = 30

    fun openTargetFor(kind: ReminderKind): String = when (kind) {
        ReminderKind.WeighIn -> OPEN_WEIGHT
        ReminderKind.Measurements -> OPEN_BODY
        ReminderKind.Water -> OPEN_WATER
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

    /** Arms every enabled reminder and clears the rest. Safe to call repeatedly. */
    fun sync(reminders: List<ReminderEntity>) {
        reminders.forEach { if (it.enabled) schedule(it) else cancel(it) }
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
}
