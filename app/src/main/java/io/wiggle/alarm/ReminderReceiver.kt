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
import io.wiggle.data.WiggleRepository
import io.wiggle.data.db.ReminderKind
import io.wiggle.di.ApplicationScope
import io.wiggle.domain.ReminderSchedule
import io.wiggle.domain.formatVolume
import io.wiggle.domain.volumeUnitLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How recently water must have been logged for another nudge to be pointless. */
private const val RECENT_WATER_MILLIS = 30 * 60_000L

private const val QUICK_ADD_ML = 250

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
 * One receiver rather than four: they all need the same repository, the same scheduler and the
 * same goAsync dance, and what each of them does is a handful of lines.
 */
@AndroidEntryPoint
class ReminderReceiver : HiltBroadcastReceiver() {

    @Inject lateinit var repository: WiggleRepository

    @Inject lateinit var scheduler: ReminderScheduler

    @Inject lateinit var notifications: NotificationManager

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
            -> scheduler.sync(repository.allEnabledReminders())

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
        }
    }

    private suspend fun fire(context: Context, intent: Intent) {
        val (profileId, kind) = intent.target() ?: return
        val reminder = repository.reminder(profileId, kind) ?: return
        if (!reminder.enabled) return

        if (shouldNotify(profileId, kind, reminder.pauseWhenGoalMet)) {
            notify(context, profileId, kind)
        }
        // Re-arm from now, so the slot that just fired is not handed straight back to us.
        scheduler.schedule(reminder)
    }

    /** A water nudge is dropped when the goal is already met or a drink was just logged. */
    private suspend fun shouldNotify(
        profileId: Long,
        kind: ReminderKind,
        pauseWhenGoalMet: Boolean,
    ): Boolean {
        if (kind != ReminderKind.Water) return true
        val lastLogged = repository.lastWaterLoggedAt(profileId)
        if (lastLogged != null && System.currentTimeMillis() - lastLogged < RECENT_WATER_MILLIS) {
            return false
        }
        if (!pauseWhenGoalMet) return true
        val goal = repository.getProfile(profileId)?.dailyWaterGoalMl ?: return true
        return repository.waterTotalToday(profileId) < goal
    }

    private suspend fun notify(context: Context, profileId: Long, kind: ReminderKind) {
        if (!context.canPostNotifications()) return
        ensureChannels()

        val builder = NotificationCompat.Builder(context, channelId(kind))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ReminderSchedule.title(kind))
            .setContentText(body(kind, profileId))
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

            else -> builder.addAction(0, "Log now", openApp(context, profileId, kind))
        }
        builder.addAction(
            0,
            "Snooze ${Alarms.SNOOZE_MINUTES}m",
            broadcast(context, Alarms.ACTION_SNOOZE, profileId, kind),
        )

        notifications.notify(notificationId(kind), builder.build())
    }

    private suspend fun body(kind: ReminderKind, profileId: Long): String = when (kind) {
        ReminderKind.WeighIn -> "Same scale, same time. That is what keeps the trend honest."
        ReminderKind.Measurements -> "Neck, waist and hips. A minute with the tape."
        ReminderKind.Water -> {
            val unit = repository.settings.first().volumeUnit
            val goal = repository.getProfile(profileId)?.dailyWaterGoalMl ?: 0
            val left = (goal - repository.waterTotalToday(profileId)).coerceAtLeast(0)
            if (left == 0) "Top up whenever you like."
            else "${formatVolume(left, unit)} ${volumeUnitLabel(left, unit)} to go today."
        }
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
                    NotificationManager.IMPORTANCE_DEFAULT,
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

private fun Context.canPostNotifications(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
