package io.wiggle.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wiggle.data.db.ReminderKind
import io.wiggle.domain.ReminderSchedule
import io.wiggle.ui.BodySection
import io.wiggle.ui.SettingsButton
import io.wiggle.ui.components.IconBadge
import io.wiggle.ui.components.MinTouch
import io.wiggle.ui.components.PrimaryButton
import io.wiggle.ui.components.ScreenHeader
import io.wiggle.ui.components.SegmentedControl
import io.wiggle.ui.glass.TappableGlassCard
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.settings.SettingsViewModel
import io.wiggle.ui.theme.WiggleTheme
import io.wiggle.ui.trends.TrendsScreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The Body tab: weight and tape measurements under one header. */
@Composable
fun BodyTab(
    section: BodySection,
    onSection: (BodySection) -> Unit,
    onLogWeight: () -> Unit,
    onEditEntry: (Long) -> Unit,
    onBulkAdd: () -> Unit,
    onOpenReminders: () -> Unit,
) {
    val colors = WiggleTheme.colors
    val body: BodyViewModel = hiltViewModel()
    val bodyState by body.state.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader(
            eyebrow = when (section) {
                BodySection.Weight -> LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM"))
                BodySection.Measurements -> bodyState.lastMeasuredOn
                    ?.let { "Measured ${it.format(DateTimeFormatter.ofPattern("d MMM"))}" }
                    ?: "No measurements yet"
            },
            title = "Body",
        ) {
            PrimaryButton(
                text = if (section == BodySection.Weight) "Log" else "Measure",
                onClick = { if (section == BodySection.Weight) onLogWeight() else body.openEditor() },
                icon = Lucide.Plus,
                height = MinTouch,
                color = colors.ink,
                contentColor = colors.background,
            )
            SettingsButton()
        }

        SegmentedControl(
            options = BodySection.entries.toList(),
            selected = section,
            onSelect = onSection,
            label = { it.label },
            modifier = Modifier.fillMaxWidth(),
        )

        when (section) {
            BodySection.Weight -> TrendsScreen(onEditEntry = onEditEntry, onBulkAdd = onBulkAdd)
            BodySection.Measurements -> {
                BodyScreen(viewModel = body)
                NextCheckIn(onOpenReminders)
            }
        }
    }
}

/** When the tape comes out next, and a way to change it. */
@Composable
private fun NextCheckIn(onOpenReminders: () -> Unit) {
    val colors = WiggleTheme.colors
    val settings: SettingsViewModel = hiltViewModel()
    val state by settings.state.collectAsStateWithLifecycle()
    val reminder = state.reminders.firstOrNull { it.kind == ReminderKind.Measurements }
    val next = reminder?.let { ReminderSchedule.nextOccurrence(it) }
    TappableGlassCard(onClick = onOpenReminders, modifier = Modifier.fillMaxWidth(), contentDescription = "Reminders") {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(Lucide.Bell, colors.bodySoft)
            Column(Modifier.weight(1f)) {
                Text(
                    next?.let {
                        "Next check-in ${it.dayOfWeek.name.lowercase().replaceFirstChar(Char::uppercase)}, " +
                            ReminderSchedule.timeLabel(it.hour * 60 + it.minute)
                    } ?: "No check-in reminder",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.ink,
                )
                Text(
                    if (next != null) "Same time, same tape" else "Turn one on to measure on a rhythm",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
            }
            Icon(Lucide.ChevronRight, size = 16.dp, tint = colors.inkFaint, strokeWidth = 2.4f)
        }
    }
}
