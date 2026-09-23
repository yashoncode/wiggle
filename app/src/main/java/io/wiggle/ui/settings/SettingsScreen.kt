package io.wiggle.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wiggle.data.db.ReminderEntity
import io.wiggle.data.db.ReminderKind
import io.wiggle.domain.LengthUnit
import io.wiggle.domain.ReminderSchedule
import io.wiggle.domain.VolumeUnit
import io.wiggle.domain.WeightUnit
import io.wiggle.domain.format
import io.wiggle.domain.formatVolume
import io.wiggle.domain.volumeUnitLabel
import io.wiggle.ui.components.CardRow
import io.wiggle.ui.components.IconBadge
import io.wiggle.ui.components.IosToggle
import io.wiggle.ui.components.ScreenHeader
import io.wiggle.ui.components.SegmentedControl
import io.wiggle.ui.glass.GlassCard
import io.wiggle.ui.glass.cardEntrance
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.icons.LucideIcon
import io.wiggle.ui.profile.ProfileAvatar
import io.wiggle.ui.theme.ThemeMode
import io.wiggle.ui.theme.WiggleTheme
import io.wiggle.ui.update.UpdateViewModel

/**
 * Settings, which is also where the alerts live.
 *
 * Reminders used to have a tab of their own, but there are three of them and they are configured
 * once, so they sit here with everything else you set and forget.
 */
@Composable
fun SettingsScreen(
    onSwitchPerson: () -> Unit,
    onBulkAdd: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = WiggleTheme.colors
    val settings = state.settings
    val context = LocalContext.current

    // The export writes its files first, then arrives here to be handed to the share sheet.
    val exportFiles by viewModel.exportFiles.collectAsStateWithLifecycle()
    LaunchedEffect(exportFiles) {
        if (exportFiles.isEmpty()) return@LaunchedEffect
        val share = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/csv"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(exportFiles))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "Export Wiggle data"))
        viewModel.exportHandled()
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader(eyebrow = "Wiggle", title = "Settings")

        // --- person ---------------------------------------------------------------------------
        GlassCard(Modifier.fillMaxWidth().cardEntrance(0)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatar(profile = state.profile, onClick = onSwitchPerson, size = 52.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        state.profile?.name ?: "No one yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink,
                    )
                    Text(
                        state.profile?.let { profile ->
                            val height = settings.lengthUnit.fromCm(profile.heightCm)
                            "${height.format(if (settings.lengthUnit == LengthUnit.Cm) 0 else 1)} ${settings.lengthUnit.label} · ${state.profiles.size} tracked here"
                        } ?: "Add someone to start",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkMuted,
                    )
                }
            }
            SettingRow(
                icon = Lucide.Users,
                accent = colors.weight,
                title = "Switch person",
                subtitle = "${state.profiles.size} on this phone",
                onClick = onSwitchPerson,
                showDivider = true,
            ) { Chevron() }
            SettingRow(
                icon = Lucide.Pencil,
                accent = colors.weight,
                title = "Edit details",
                subtitle = "Name, height, sex",
                onClick = { viewModel.openSheet(SettingsSheet.EditPerson) },
            ) { Chevron() }
            SettingRow(
                icon = Lucide.UserPlus,
                accent = colors.goal,
                title = "Add someone",
                subtitle = "Track another person separately",
                onClick = { viewModel.openSheet(SettingsSheet.AddPerson) },
            ) { Chevron() }
        }

        // --- alerts ---------------------------------------------------------------------------
        GlassCard(Modifier.fillMaxWidth().cardEntrance(1)) {
            SectionTitle("Alerts")
            state.reminders.forEachIndexed { index, reminder ->
                AlertRow(
                    reminder = reminder,
                    showDivider = index > 0,
                    onToggle = { viewModel.setReminderEnabled(reminder.kind, it) },
                    onClick = { viewModel.openSheet(SettingsSheet.Reminder(reminder.kind)) },
                )
            }
        }

        // --- appearance -----------------------------------------------------------------------
        GlassCard(Modifier.fillMaxWidth().cardEntrance(2)) {
            SectionTitle("Appearance")
            Spacer(Modifier.height(10.dp))
            SegmentedControl(
                options = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark),
                selected = settings.themeMode,
                onSelect = viewModel::setTheme,
                label = { mode ->
                    when (mode) {
                        ThemeMode.System -> "System"
                        ThemeMode.Light -> "Light"
                        ThemeMode.Dark -> "Dark"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when (settings.themeMode) {
                    ThemeMode.System -> "Follows your phone's light or dark setting."
                    ThemeMode.Light -> "Always light, whatever the phone is set to."
                    ThemeMode.Dark -> "Always dark, whatever the phone is set to."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted,
            )
            SettingRow(
                icon = Lucide.Zap,
                accent = colors.goal,
                title = "Haptics",
                subtitle = "Taps and detents you can feel",
                showDivider = true,
            ) {
                IosToggle(checked = settings.hapticsEnabled, onCheckedChange = viewModel::setHaptics)
            }
        }

        // --- units ----------------------------------------------------------------------------
        GlassCard(Modifier.fillMaxWidth().cardEntrance(3)) {
            SectionTitle("Units")
            Spacer(Modifier.height(10.dp))
            UnitRow("Weight") {
                SegmentedControl(
                    options = WeightUnit.entries.toList(),
                    selected = settings.weightUnit,
                    onSelect = viewModel::setWeightUnit,
                    label = { it.label },
                    modifier = Modifier.fillMaxWidth(0.55f),
                )
            }
            UnitRow("Length") {
                SegmentedControl(
                    options = LengthUnit.entries.toList(),
                    selected = settings.lengthUnit,
                    onSelect = viewModel::setLengthUnit,
                    label = { it.label },
                    modifier = Modifier.fillMaxWidth(0.55f),
                )
            }
            UnitRow("Water") {
                SegmentedControl(
                    options = VolumeUnit.entries.toList(),
                    selected = settings.volumeUnit,
                    onSelect = viewModel::setVolumeUnit,
                    label = { it.label },
                    modifier = Modifier.fillMaxWidth(0.55f),
                )
            }
        }

        // --- goals ----------------------------------------------------------------------------
        GlassCard(Modifier.fillMaxWidth().cardEntrance(4)) {
            SectionTitle("Goals")
            SettingRow(
                icon = Lucide.Target,
                accent = colors.weight,
                title = "Goal weight",
                subtitle = state.profile?.goalWeightKg?.let {
                    "${settings.weightUnit.fromKg(it).format()} ${settings.weightUnit.label}"
                } ?: "Not set",
                onClick = { viewModel.openSheet(SettingsSheet.GoalWeight) },
                showDivider = true,
            ) { Chevron() }
            SettingRow(
                icon = Lucide.Droplet,
                accent = colors.water,
                title = "Daily water",
                subtitle = state.profile?.dailyWaterGoalMl?.let { ml ->
                    "${formatVolume(ml, settings.volumeUnit)} ${volumeUnitLabel(ml, settings.volumeUnit)}"
                } ?: "2.5 L",
                onClick = { viewModel.openSheet(SettingsSheet.WaterGoal) },
            ) { Chevron() }
        }

        // --- data -----------------------------------------------------------------------------
        GlassCard(Modifier.fillMaxWidth().cardEntrance(5)) {
            SectionTitle("Data")
            SettingRow(
                icon = Lucide.Calendar,
                accent = colors.weight,
                title = "Add past data",
                subtitle = "Fill gaps, or paste a whole history",
                onClick = onBulkAdd,
                showDivider = true,
            ) { Chevron() }
            SettingRow(
                icon = Lucide.Share,
                accent = colors.goal,
                title = "Export CSV",
                subtitle = "Weight, body and water as spreadsheet files",
                onClick = viewModel::exportCsv,
                showDivider = true,
            ) { Chevron() }
            SettingRow(
                icon = Lucide.Trash,
                accent = colors.body,
                title = "Delete all data",
                subtitle = "Clears this person's entries, keeps the person",
                onClick = { viewModel.openSheet(SettingsSheet.ConfirmWipe) },
                titleColor = colors.body,
            ) { Chevron() }
        }

        // --- about ----------------------------------------------------------------------------
        // Wiggle is not on a store, so checking for a new build is something the app has to offer.
        val updateViewModel: UpdateViewModel = hiltViewModel<UpdateViewModel>()
        val update by updateViewModel.state.collectAsStateWithLifecycle()
        GlassCard(Modifier.fillMaxWidth().cardEntrance(6)) {
            SectionTitle("About")
            SettingRow(
                icon = Lucide.Download,
                accent = colors.water,
                title = "Check for updates",
                subtitle = if (update.checking) "Looking…" else "Installs from GitHub releases",
                onClick = { updateViewModel.check() },
                showDivider = false,
            ) { Chevron() }
        }

        Text(
            "Body fat and similar figures are estimates from tape measurements, not clinical measurements.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkFaint,
            modifier = Modifier.fillMaxWidth(),
        )

        AppFooter(Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}

/** Version and byline, read from the installed package so it can never drift from the build. */
@Composable
private fun AppFooter(modifier: Modifier = Modifier) {
    val colors = WiggleTheme.colors
    val context = LocalContext.current
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (version.isBlank()) "Wiggle" else "Wiggle $version",
            style = MaterialTheme.typography.labelMedium,
            color = colors.inkFaint,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Made by Yashwanth",
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkFaint,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = WiggleTheme.colors.ink)
}

@Composable
private fun UnitRow(label: String, control: @Composable () -> Unit) {
    val colors = WiggleTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.inkMuted)
        control()
    }
}

@Composable
private fun SettingRow(
    icon: LucideIcon,
    accent: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    titleColor: Color = Color.Unspecified,
    trailing: @Composable () -> Unit = {},
) {
    val colors = WiggleTheme.colors
    CardRow(modifier = modifier, showDivider = showDivider, onClick = onClick) {
        IconBadge(icon, accent)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (titleColor == Color.Unspecified) colors.ink else titleColor,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
        }
        trailing()
    }
}

@Composable
private fun Chevron() {
    Icon(
        Lucide.ChevronRight,
        size = 16.dp,
        tint = WiggleTheme.colors.inkFaint,
        strokeWidth = 2.4f,
    )
}

@Composable
private fun AlertRow(
    reminder: ReminderEntity,
    showDivider: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val colors = WiggleTheme.colors
    val accent = when (reminder.kind) {
        ReminderKind.WeighIn -> colors.weight
        ReminderKind.Measurements -> colors.body
        ReminderKind.Water -> colors.water
    }
    val icon = when (reminder.kind) {
        ReminderKind.WeighIn -> Lucide.Scale
        ReminderKind.Measurements -> Lucide.Ruler
        ReminderKind.Water -> Lucide.Droplet
    }
    SettingRow(
        icon = if (reminder.enabled) icon else Lucide.BellOff,
        accent = if (reminder.enabled) accent else colors.inkFaint,
        title = ReminderSchedule.title(reminder.kind),
        subtitle = reminderSubtitle(reminder),
        onClick = onClick,
        showDivider = showDivider,
    ) {
        IosToggle(checked = reminder.enabled, onCheckedChange = onToggle)
    }
}

/** What the row says under its title: the schedule in the same words the editor uses. */
private fun reminderSubtitle(reminder: ReminderEntity): String = when {
    !reminder.enabled -> "Off"
    reminder.kind == ReminderKind.Water ->
        "Every ${reminder.intervalMinutes} min · ${ReminderSchedule.timeLabel(reminder.timeMinutes)} to ${ReminderSchedule.timeLabel(reminder.untilMinutes)}"

    reminder.kind == ReminderKind.Measurements -> {
        val cadence = when (reminder.everyNWeeks) {
            1 -> "Weekly"
            2 -> "Every 2 weeks"
            else -> "Every ${reminder.everyNWeeks} weeks"
        }
        "$cadence · ${ReminderSchedule.dayLabels(reminder.daysMask)} · ${ReminderSchedule.timeLabel(reminder.timeMinutes)}"
    }

    else -> "${ReminderSchedule.dayLabels(reminder.daysMask)} · ${ReminderSchedule.timeLabel(reminder.timeMinutes)}"
}
