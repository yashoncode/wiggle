package io.wiggle.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wiggle.data.StepsAvailability
import io.wiggle.domain.LengthUnit
import io.wiggle.domain.Stats
import io.wiggle.domain.StepStats
import io.wiggle.domain.WeightUnit
import io.wiggle.domain.format
import io.wiggle.domain.formatVolume
import io.wiggle.domain.volumeUnitLabel
import io.wiggle.ui.components.CardRow
import io.wiggle.ui.components.IconBadge
import io.wiggle.ui.components.IosToggle
import io.wiggle.ui.components.MinTouch
import io.wiggle.ui.components.ScreenHeader
import io.wiggle.ui.components.SegmentedControl
import io.wiggle.ui.glass.GlassCard
import io.wiggle.ui.glass.GlassDefaults
import io.wiggle.ui.glass.TappableGlassCard
import io.wiggle.ui.glass.cardEntrance
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.icons.LucideIcon
import io.wiggle.ui.profile.ProfileAvatar
import io.wiggle.ui.theme.EyebrowStyle
import io.wiggle.ui.theme.ThemeMode
import io.wiggle.ui.theme.WiggleTheme
import io.wiggle.ui.today.groupedSigned
import io.wiggle.ui.update.UpdateViewModel

/**
 * Settings, opened from the button in every header: who is tracked, their daily goals, how the app
 * looks, and where the data and alerts live.
 */
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onSwitchPerson: () -> Unit,
    onBulkAdd: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenSteps: () -> Unit,
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader(eyebrow = "Preferences", title = "Settings") {
            Box(
                Modifier
                    .size(MinTouch)
                    .clip(CircleShape)
                    .background(colors.ink)
                    .clickable(role = Role.Button, onClickLabel = "Close settings", onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.X, size = 20.dp, tint = colors.background, strokeWidth = 2.6f, contentDescription = "Close settings")
            }
        }

        // --- person ---------------------------------------------------------------------------
        GlassCard(Modifier.fillMaxWidth().cardEntrance(0), shape = RoundedCornerShape(22.dp), contentPadding = 14.dp) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatar(profile = state.profile, onClick = onSwitchPerson, size = 48.dp)
                Column(Modifier.weight(1f)) {
                    Text(state.profile?.name ?: "No one yet", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                    Text(
                        profileLine(state, settings.weightUnit, settings.lengthUnit),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkMuted,
                    )
                }
            }
            SettingRow(Lucide.Users, colors.weight, "Switch person", "${state.profiles.size} on this phone", onClick = onSwitchPerson) { Chevron() }
            SettingRow(Lucide.Pencil, colors.weight, "Edit details", "Name, height, sex", onClick = { viewModel.openSheet(SettingsSheet.EditPerson) }) { Chevron() }
            SettingRow(Lucide.UserPlus, colors.goal, "Add someone", "Track another person separately", onClick = { viewModel.openSheet(SettingsSheet.AddPerson) }) { Chevron() }
        }

        // --- goals ----------------------------------------------------------------------------
        SectionTitle("Daily goals")
        val volume = settings.volumeUnit
        Row(Modifier.fillMaxWidth().cardEntrance(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GoalTile(
                Lucide.Target, colors.weightSoft,
                state.profile?.goalWeightKg?.let { "${settings.weightUnit.fromKg(it).format()} ${settings.weightUnit.label}" } ?: "Not set",
                "Weight goal", Modifier.weight(1f),
            ) { viewModel.openSheet(SettingsSheet.GoalWeight) }
            GoalTile(
                Lucide.Utensils, colors.foodSoft,
                "${groupedSigned(state.calorieGoal)} kcal",
                if (state.profile?.dailyCalorieGoal == null) "Calories · auto" else "Calories", Modifier.weight(1f),
            ) { viewModel.openSheet(SettingsSheet.CalorieGoal) }
        }
        Row(Modifier.fillMaxWidth().cardEntrance(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GoalTile(
                Lucide.Activity, colors.stepsSoft,
                StepStats.grouped((state.profile?.dailyStepGoal ?: 10_000).toLong()),
                "Steps", Modifier.weight(1f),
            ) { viewModel.openSheet(SettingsSheet.StepGoal) }
            val water = state.profile?.dailyWaterGoalMl ?: 2500
            GoalTile(
                Lucide.Droplet, colors.waterSoft,
                "${formatVolume(water, volume)} ${volumeUnitLabel(water, volume)}",
                "Water", Modifier.weight(1f),
            ) { viewModel.openSheet(SettingsSheet.WaterGoal) }
        }

        // --- display --------------------------------------------------------------------------
        SectionTitle("Display")
        GlassCard(Modifier.fillMaxWidth().cardEntrance(2), shape = RoundedCornerShape(22.dp), contentPadding = 14.dp) {
            SettingRow(Lucide.Ruler, colors.body, "Units", "Weight, length and water", showDivider = false) {
                SegmentedControl(
                    options = listOf(true, false),
                    selected = settings.weightUnit == WeightUnit.Kg,
                    onSelect = viewModel::setMetric,
                    label = { if (it) "kg · cm" else "lb · in" },
                    modifier = Modifier.width(150.dp),
                    height = 30.dp,
                )
            }
            SettingRow(Lucide.Moon, colors.goal, "Theme", "Auto follows the phone") {
                SegmentedControl(
                    options = listOf(ThemeMode.Dark, ThemeMode.Light, ThemeMode.System),
                    selected = settings.themeMode,
                    onSelect = viewModel::setTheme,
                    label = {
                        when (it) {
                            ThemeMode.Dark -> "Dark"
                            ThemeMode.Light -> "Light"
                            ThemeMode.System -> "Auto"
                        }
                    },
                    modifier = Modifier.width(174.dp),
                    height = 30.dp,
                )
            }
            SettingRow(Lucide.Sparkles, colors.steps, "Reduce motion", "Calmer animations everywhere") {
                IosToggle(checked = settings.reduceMotion, onCheckedChange = viewModel::setReduceMotion)
            }
            SettingRow(Lucide.Zap, colors.goal, "Haptics", "Taps and detents you can feel") {
                IosToggle(checked = settings.hapticsEnabled, onCheckedChange = viewModel::setHaptics)
            }
        }

        // --- data and alerts ------------------------------------------------------------------
        SectionTitle("Data & alerts")
        GlassCard(Modifier.fillMaxWidth().cardEntrance(3), shape = RoundedCornerShape(22.dp), contentPadding = 14.dp) {
            val connected = state.steps == StepsAvailability.Ready
            SettingRow(
                Lucide.Heart, colors.tablets, "Health Connect",
                when (state.steps) {
                    StepsAvailability.Ready -> "Reading steps"
                    StepsAvailability.NeedsInstall -> "Needs installing"
                    StepsAvailability.Unsupported -> "Not available on this phone"
                    else -> "Not connected"
                },
                onClick = onOpenSteps,
                showDivider = false,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(if (connected) colors.toggleOn else colors.inkFaint, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (connected) "Connected" else "Connect",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (connected) colors.weightSoft else colors.inkMuted,
                    )
                }
            }
            SettingRow(
                Lucide.Bell, colors.weight, "Reminders",
                "${state.reminders.count { it.enabled }} on",
                onClick = onOpenReminders,
            ) { Chevron() }
            SettingRow(Lucide.Calendar, colors.weight, "Add past data", "Fill gaps, or paste a whole history", onClick = onBulkAdd) { Chevron() }
            SettingRow(Lucide.Download, colors.water, "Export data", "Weight, body and water as CSV", onClick = viewModel::exportCsv) { Chevron() }
            SettingRow(
                Lucide.Trash, colors.body, "Delete all data",
                "Clears this person's entries, keeps the person",
                onClick = { viewModel.openSheet(SettingsSheet.ConfirmWipe) },
                titleColor = colors.body,
            ) { Chevron() }
        }

        // --- about ----------------------------------------------------------------------------
        // Wiggle is not on a store, so checking for a new build is something the app has to offer.
        val updateViewModel: UpdateViewModel = hiltViewModel<UpdateViewModel>()
        val update by updateViewModel.state.collectAsStateWithLifecycle()
        SectionTitle("About")
        GlassCard(Modifier.fillMaxWidth().cardEntrance(4), shape = RoundedCornerShape(22.dp), contentPadding = 14.dp) {
            SettingRow(
                Lucide.Refresh, colors.water, "Check for updates",
                if (update.checking) "Looking…" else "Installs from GitHub releases",
                onClick = { updateViewModel.check() },
                showDivider = false,
            ) { Chevron() }
        }

        Text(
            "Body fat, calories, distance and similar figures are estimates, not clinical measurements.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkFaint,
        )
        Text(
            "Food data: USDA FoodData Central (CC0); UK CoFID 2021, contains public sector information " +
                "licensed under the Open Government Licence v3.0; Indian Nutrient Databank (Vijayakumar et al. " +
                "2024); packaged foods from Open Food Facts (ODbL).",
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkFaint,
        )

        AppFooter(Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}

private fun profileLine(state: SettingsUiState, weightUnit: WeightUnit, lengthUnit: LengthUnit): String {
    val profile = state.profile ?: return "Add someone to start"
    val parts = mutableListOf("${lengthUnit.fromCm(profile.heightCm).format(if (lengthUnit == LengthUnit.Cm) 0 else 1)} ${lengthUnit.label}")
    state.latestKg?.let { kg ->
        parts += "${weightUnit.fromKg(kg).format()} ${weightUnit.label}"
        Stats.bmi(kg, profile.heightCm)?.let { parts += "BMI ${it.format()}" }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun GoalTile(
    icon: LucideIcon,
    accent: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = WiggleTheme.colors
    TappableGlassCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(GlassDefaults.TinyRadius),
        contentPadding = 12.dp,
        contentDescription = "Change $label",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(icon, accent)
            Column {
                Text(value, style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 1)
                Text(label, style = MaterialTheme.typography.bodySmall, color = colors.inkMuted, maxLines = 1)
            }
        }
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
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (version.isBlank()) "Wiggle" else "Wiggle $version",
            style = MaterialTheme.typography.labelMedium,
            color = colors.inkFaint,
        )
        Spacer(Modifier.height(2.dp))
        Text("Made by Yashwanth", style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = EyebrowStyle,
        color = WiggleTheme.colors.inkMuted,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp).semantics { heading() },
    )
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
        IconBadge(icon, accent, size = 32.dp)
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
    Icon(Lucide.ChevronRight, size = 16.dp, tint = WiggleTheme.colors.inkFaint, strokeWidth = 2.4f)
}
