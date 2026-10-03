package io.wiggle.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wiggle.data.db.ReminderEntity
import io.wiggle.data.db.ReminderKind
import io.wiggle.domain.ReminderSchedule
import io.wiggle.ui.components.CardRow
import io.wiggle.ui.components.GlassButton
import io.wiggle.ui.components.IconBadge
import io.wiggle.ui.components.IosToggle
import io.wiggle.ui.components.ScreenHeader
import io.wiggle.ui.glass.GlassCard
import io.wiggle.ui.glass.cardEntrance
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.icons.LucideIcon
import io.wiggle.ui.settings.SettingsSheet
import io.wiggle.ui.settings.SettingsViewModel
import io.wiggle.ui.theme.WiggleColors
import io.wiggle.ui.theme.WiggleTheme

/**
 * Every reminder in one place, reached from the bell on Home and from Settings. Each row switches
 * on and off where it is; a tap opens its schedule.
 */
@Composable
fun RemindersScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = WiggleTheme.colors
    val byKind = state.reminders.associateBy { it.kind }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GlassButton(onClick = onBack, height = 40.dp, horizontalPadding = 12.dp, contentDescription = "Back") {
            Icon(Lucide.ChevronLeft, size = 18.dp, tint = colors.ink, strokeWidth = 2.4f)
            Text("Back", style = MaterialTheme.typography.labelLarge, color = colors.ink)
        }
        ScreenHeader(eyebrow = "Notifications", title = "Reminders")

        listOf(
            listOf(ReminderKind.WeighIn, ReminderKind.Measurements),
            listOf(ReminderKind.Water, ReminderKind.Meals),
            listOf(ReminderKind.Move, ReminderKind.Tablets),
        ).forEachIndexed { group, kinds ->
            GlassCard(Modifier.fillMaxWidth().cardEntrance(group), contentPadding = 16.dp) {
                kinds.forEachIndexed { index, kind ->
                    byKind[kind]?.let { reminder ->
                        AlertRow(
                            reminder = reminder,
                            showDivider = index > 0,
                            onToggle = { viewModel.setReminderEnabled(kind, it) },
                            onClick = { viewModel.openSheet(SettingsSheet.Reminder(kind)) },
                        )
                    }
                }
            }
        }

        Text(
            "Reminders arrive even with Wiggle closed. On Realme, OPPO and similar phones, allow Wiggle " +
                "to run in the background (Settings › Battery) so they are not held back.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkFaint,
        )
    }
}

fun reminderIcon(kind: ReminderKind): LucideIcon = when (kind) {
    ReminderKind.WeighIn -> Lucide.Scale
    ReminderKind.Measurements -> Lucide.Ruler
    ReminderKind.Water -> Lucide.Droplet
    ReminderKind.Meals -> Lucide.Utensils
    ReminderKind.Move -> Lucide.Activity
    ReminderKind.Tablets -> Lucide.Pill
}

fun reminderAccent(colors: WiggleColors, kind: ReminderKind): Color = when (kind) {
    ReminderKind.WeighIn -> colors.weight
    ReminderKind.Measurements -> colors.body
    ReminderKind.Water -> colors.water
    ReminderKind.Meals -> colors.food
    ReminderKind.Move -> colors.steps
    ReminderKind.Tablets -> colors.tablets
}

@Composable
private fun AlertRow(
    reminder: ReminderEntity,
    showDivider: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val colors = WiggleTheme.colors
    CardRow(showDivider = showDivider, onClick = onClick) {
        IconBadge(
            if (reminder.enabled) reminderIcon(reminder.kind) else Lucide.BellOff,
            if (reminder.enabled) reminderAccent(colors, reminder.kind) else colors.inkFaint,
        )
        Column(Modifier.weight(1f)) {
            Text(ReminderSchedule.title(reminder.kind), style = MaterialTheme.typography.titleSmall, color = colors.ink)
            Text(reminderSubtitle(reminder), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
        }
        IosToggle(checked = reminder.enabled, onCheckedChange = onToggle)
    }
}

/** What a row says under its title: the schedule in the same words the editor uses. */
fun reminderSubtitle(reminder: ReminderEntity): String = when (reminder.kind) {
    ReminderKind.Water ->
        "Every ${reminder.intervalMinutes} min · ${ReminderSchedule.timeLabel(reminder.timeMinutes)} to " +
            ReminderSchedule.timeLabel(reminder.untilMinutes)

    ReminderKind.Measurements -> {
        val cadence = when (reminder.everyNWeeks) {
            1 -> "Weekly"
            2 -> "Every 2 weeks"
            else -> "Every ${reminder.everyNWeeks} weeks"
        }
        "$cadence · ${ReminderSchedule.dayLabels(reminder.daysMask)} · ${ReminderSchedule.timeLabel(reminder.timeMinutes)}"
    }

    ReminderKind.Meals -> ReminderSchedule.mealTimes(reminder).joinToString(" · ") { ReminderSchedule.timeLabel(it) }

    ReminderKind.Move ->
        "Under 250 steps in an hour · ${ReminderSchedule.timeLabel(reminder.timeMinutes)} to " +
            ReminderSchedule.timeLabel(reminder.untilMinutes)

    ReminderKind.Tablets -> "At each tablet's time · repeats every ${reminder.intervalMinutes} min"

    ReminderKind.WeighIn ->
        "${ReminderSchedule.dayLabels(reminder.daysMask)} · ${ReminderSchedule.timeLabel(reminder.timeMinutes)}"
}.let { if (reminder.enabled) it else "Off · $it" }
