package io.wiggle.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.wiggle.MainActivity
import io.wiggle.R
import io.wiggle.data.HealthSteps
import io.wiggle.data.WiggleRepository
import io.wiggle.data.db.MedicationEntity
import io.wiggle.data.db.ReminderEntity
import io.wiggle.data.db.ReminderKind
import io.wiggle.di.ApplicationScope
import io.wiggle.domain.Doses
import io.wiggle.domain.ReminderSchedule
import io.wiggle.domain.StepStats
import io.wiggle.domain.formatVolume
import io.wiggle.domain.volumeUnitLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/** How recently water must have been logged for another nudge to be pointless. */
private const val RECENT_WATER_MILLIS = 30 * 60_000L

private const val QUICK_ADD_ML = 250

/** Fewer steps than this in the last hour is what "Time to move" is for. */
private const val MOVE_THRESHOLD_STEPS = 250

/**
 * Hilt rewrites an `@AndroidEntryPoint` receiver's superclass after Kotlin has compiled, so a
 * direct `super.onReceive` call does not resolve — `BroadcastReceiver.onReceive` is abstract at
 * that point. This concrete base is the documented way round it, and calling through it is what
 * performs the field injection.
 */
abstract class HiltBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}

/**
 * Every alarm, notification action and system reschedule trigger lands here.
 *
 * One receiver rather than several: they all need the same repository, the same scheduler and the
 * same goAsync dance, and what each of them does is a handful of lines.
 */
@AndroidEntryPoint
class ReminderReceiver : HiltBroadcastReceiver() {

    @Inject lateinit var repository: WiggleRepository

    @Inject lateinit var scheduler: ReminderScheduler

    @Inject lateinit var notifications: NotificationManager

    @Inject lateinit var healthSteps: HealthSteps

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        // Hilt performs the field injection inside super.onReceive, so nothing below this line
        // works without it.
        super.onReceive(context, intent)
        val pending = goAsync()
        val appContext = context.applicationContext
        appScope.launch {
            try {
                handle(appContext, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> scheduler.sync(repository.allEnabledReminders(), repository.everyMedication())

            Alarms.ACTION_FIRE -> fire(context, intent)

            Alarms.ACTION_SNOOZE -> {
                val target = intent.target() ?: return
                notifications.cancel(notificationId(target.second))
                scheduler.snooze(target.first, target.second)
            }

            Alarms.ACTION_ADD_WATER -> {
                val target = intent.target() ?: return
                notifications.cancel(notificationId(target.second))
                repository.addWater(
                    target.first,
                    intent.getIntExtra(Alarms.EXTRA_AMOUNT_ML, QUICK_ADD_ML),
                )
            }

            Alarms.ACTION_DOSE -> dose(context, intent)

            Alarms.ACTION_DOSE_TAKEN -> {
                val medicationId = intent.getLongExtra(Alarms.EXTRA_MEDICATION_ID, 0L)
                val slot = intent.getIntExtra(Alarms.EXTRA_SLOT, -1)
                if (medicationId == 0L || slot < 0) return
                notifications.cancel(doseNotificationId(medicationId, slot))
                repository.setDoseTaken(medicationId, LocalDate.now(), slot, taken = true)
            }
        }
    }

    private suspend fun fire(context: Context, intent: Intent) {
        val (profileId, kind) = intent.target() ?: return
        val reminder = repository.reminder(profileId, kind) ?: return
        if (!reminder.enabled) return

        val body = when (kind) {
            ReminderKind.Water -> if (waterNudgeWanted(profileId, reminder.pauseWhenGoalMet)) waterBody(profileId) else null
            ReminderKind.Meals -> mealBody(profileId, reminder)
            ReminderKind.Move -> moveBody(profileId)
            ReminderKind.WeighIn -> "Same scale, same time. That is what keeps the trend honest."
            ReminderKind.Measurements -> "Neck, waist and hips. A minute with the tape."
            // Doses ring through ACTION_DOSE, never through this row.
            ReminderKind.Tablets -> null
        }
        if (body != null) notify(context, profileId, kind, body)
        // Re-arm from now, so the slot that just fired is not handed straight back to us.
        scheduler.schedule(reminder)
    }

    /** A water nudge is dropped when the goal is already met or a drink was just logged. */
    private suspend fun waterNudgeWanted(profileId: Long, pauseWhenGoalMet: Boolean): Boolean {
        val lastLogged = repository.lastWaterLoggedAt(profileId)
        if (lastLogged != null && System.currentTimeMillis() - lastLogged < RECENT_WATER_MILLIS) {
            return false
        }
        if (!pauseWhenGoalMet) return true
        val goal = repository.getProfile(profileId)?.dailyWaterGoalMl ?: return true
        return repository.waterTotalToday(profileId) < goal
    }

    private suspend fun waterBody(profileId: Long): String {
        val unit = repository.settings.first().volumeUnit
        val goal = repository.getProfile(profileId)?.dailyWaterGoalMl ?: 0
        val left = (goal - repository.waterTotalToday(profileId)).coerceAtLeast(0)
        return if (left == 0) "Top up whenever you like."
        else "${formatVolume(left, unit)} ${volumeUnitLabel(left, unit)} to go today."
    }

    /** Asks about the meal this time belongs to, and stays quiet when it is already logged. */
    private suspend fun mealBody(profileId: Long, reminder: ReminderEntity): String? {
        val now = LocalTime.now()
        val meal = ReminderSchedule.mealAt(reminder, now.hour * 60 + now.minute)
        if (repository.mealLogged(profileId, meal)) return null
        return "What did you have for ${meal.name.lowercase()}? Add it while you remember."
    }

    /**
     * Quiet after an active hour, and quiet when the hour cannot be read: a move nudge based on a
     * step count that failed to load would be a guess. Steps belong to one person, so only they
     * are nudged.
     */
    private suspend fun moveBody(profileId: Long): String? {
        if (repository.settings.first().stepsProfileId != profileId) return null
        val now = Instant.now()
        val steps = healthSteps.stepsBetween(now.minusSeconds(3600), now) ?: return null
        if (steps >= MOVE_THRESHOLD_STEPS) return null
        return if (steps == 0L) "No steps in the last hour. Stand up and walk for a few minutes."
        else "Only ${StepStats.grouped(steps)} steps in the last hour. A short walk counts."
    }

    /**
     * One tablet dose. Rings at its time, then again every few minutes until it is ticked off or
     * the nag window closes, then moves to tomorrow. A dose alarm left over from a tablet that was
     * deleted or re-timed finds nothing to do and stops.
     */
    private suspend fun dose(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra(Alarms.EXTRA_MEDICATION_ID, 0L)
        val slot = intent.getIntExtra(Alarms.EXTRA_SLOT, -1)
        val medication = repository.medication(medicationId) ?: return
        val times = Doses.parseTimes(medication.times)
        val index = times.indexOf(slot)
        if (index < 0) return
        val reminder = repository.reminder(medication.profileId, ReminderKind.Tablets)
        if (reminder?.enabled != true) return

        val now = LocalDateTime.now()
        val taken = repository.doseTaken(medication.id, now.toLocalDate(), slot)
        val slotTime = now.toLocalDate().atTime(slot / 60, slot % 60)
        if (!taken && !now.isBefore(slotTime)) notifyDose(context, medication, slot)
        scheduler.scheduleDose(
            medication.id,
            index,
            slot,
            Doses.nextAlarm(slot, now, taken, reminder.intervalMinutes),
        )
    }

    private fun notify(context: Context, profileId: Long, kind: ReminderKind, body: String) {
        if (!context.canPostNotifications()) return
        ensureChannels()

        val builder = NotificationCompat.Builder(context, channelId(kind))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ReminderSchedule.title(kind))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openApp(context, profileId, kind))

        when (kind) {
            ReminderKind.Water -> builder.addAction(
                0,
                "Add $QUICK_ADD_ML ml",
                broadcast(context, Alarms.ACTION_ADD_WATER, profileId, kind) {
                    it.putExtra(Alarms.EXTRA_AMOUNT_ML, QUICK_ADD_ML)
                },
            )

            ReminderKind.Move -> Unit

            else -> builder.addAction(0, "Log now", openApp(context, profileId, kind))
        }
        builder.addAction(
            0,
            "Snooze ${Alarms.SNOOZE_MINUTES}m",
            broadcast(context, Alarms.ACTION_SNOOZE, profileId, kind),
        )

        notifications.notify(notificationId(kind), builder.build())
    }

    private fun notifyDose(context: Context, medication: MedicationEntity, slot: Int) {
        if (!context.canPostNotifications()) return
        ensureChannels()
        val id = doseNotificationId(medication.id, slot)
        val body = listOf(ReminderSchedule.timeLabel(slot), medication.note)
            .filter { it.isNotBlank() }
            .joinToString(" · ")

        val taken = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, ReminderReceiver::class.java).apply {
                action = Alarms.ACTION_DOSE_TAKEN
                putExtra(Alarms.EXTRA_MEDICATION_ID, medication.id)
                putExtra(Alarms.EXTRA_SLOT, slot)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId(ReminderKind.Tablets))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time for ${medication.name}")
            .setContentText(body)
            .setAutoCancel(true)
            // Repeats until taken, so only the first one makes a sound.
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(context, medication.profileId, ReminderKind.Tablets))
            .addAction(0, "Taken", taken)
            .build()
        notifications.notify(id, notification)
    }

    private fun openApp(context: Context, profileId: Long, kind: ReminderKind): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(Alarms.EXTRA_OPEN, Alarms.openTargetFor(kind))
            putExtra(Alarms.EXTRA_PROFILE_ID, profileId)
        }
        return PendingIntent.getActivity(
            context,
            notificationId(kind),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun broadcast(
        context: Context,
        action: String,
        profileId: Long,
        kind: ReminderKind,
        extras: (Intent) -> Unit = {},
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            putExtra(Alarms.EXTRA_PROFILE_ID, profileId)
            putExtra(Alarms.EXTRA_KIND, kind.name)
            extras(this)
        }
        return PendingIntent.getBroadcast(
            context,
            action.hashCode() + kind.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** One channel per kind, so water nudges can be silenced without losing the weigh-in. */
    private fun ensureChannels() {
        ReminderKind.entries.forEach { kind ->
            if (notifications.getNotificationChannel(channelId(kind)) != null) return@forEach
            notifications.createNotificationChannel(
                NotificationChannel(
                    channelId(kind),
                    ReminderSchedule.title(kind),
                    if (kind == ReminderKind.Tablets) NotificationManager.IMPORTANCE_HIGH
                    else NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { setShowBadge(false) }
            )
        }
    }
}

private fun Intent.target(): Pair<Long, ReminderKind>? {
    val profileId = getLongExtra(Alarms.EXTRA_PROFILE_ID, 0L)
    val kind = getStringExtra(Alarms.EXTRA_KIND)
        ?.let { name -> ReminderKind.entries.firstOrNull { it.name == name } }
    return if (profileId == 0L || kind == null) null else profileId to kind
}

private fun channelId(kind: ReminderKind): String = "reminder_" + kind.name.lowercase()

private fun notificationId(kind: ReminderKind): Int = 100 + kind.ordinal

private fun doseNotificationId(medicationId: Long, slot: Int): Int =
    10_000 + medicationId.toInt() * 1440 + slot

private fun Context.canPostNotifications(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
