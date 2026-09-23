package io.wiggle.ui.settings

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wiggle.data.db.ProfileEntity
import io.wiggle.data.db.ReminderEntity
import io.wiggle.data.db.ReminderKind
import io.wiggle.data.db.Sex
import io.wiggle.domain.LengthUnit
import io.wiggle.domain.ReminderSchedule
import io.wiggle.domain.VolumeUnit
import io.wiggle.domain.WeightUnit
import io.wiggle.domain.format
import io.wiggle.domain.formatVolume
import io.wiggle.domain.volumeUnitLabel
import io.wiggle.ui.components.GlassButton
import io.wiggle.ui.components.IosToggle
import io.wiggle.ui.components.PrimaryButton
import io.wiggle.ui.components.RepeatingIconButton
import io.wiggle.ui.components.RollingText
import io.wiggle.ui.components.RulerWheel
import io.wiggle.ui.components.SegmentedControl
import io.wiggle.ui.glass.GlassSheet
import io.wiggle.ui.glass.glass
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.theme.WiggleTheme
import java.time.DayOfWeek

/**
 * Every sheet the settings screen can open. They live at the root of the app rather than inside
 * the screen so their blur sees the whole page behind them.
 */
@Composable
fun BoxScope.SettingsSheets(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val open = sheet

    ReminderSheet(
        reminder = (open as? SettingsSheet.Reminder)
            ?.let { current -> state.reminders.firstOrNull { it.kind == current.kind } },
        onDismiss = viewModel::closeSheet,
        onChange = viewModel::saveReminder,
    )

    PersonSheet(
        visible = open is SettingsSheet.AddPerson || open is SettingsSheet.EditPerson,
        existing = if (open is SettingsSheet.EditPerson) state.profile else null,
        lengthUnit = state.settings.lengthUnit,
        weightUnit = state.settings.weightUnit,
        canDelete = state.profiles.size > 1,
        onDismiss = viewModel::closeSheet,
        onSave = { name, heightCm, sex, goalKg ->
            val editing = state.profile
            if (open is SettingsSheet.EditPerson && editing != null) {
                viewModel.updateProfile(
                    editing.copy(name = name, heightCm = heightCm, sex = sex, goalWeightKg = goalKg)
                )
                viewModel.closeSheet()
            } else {
                viewModel.addPerson(name, heightCm, sex, goalKg)
            }
        },
        onDelete = { state.profile?.let(viewModel::deletePerson) },
    )

    WaterGoalSheet(
        visible = open is SettingsSheet.WaterGoal,
        currentMl = state.profile?.dailyWaterGoalMl ?: 2500,
        unit = state.settings.volumeUnit,
        onDismiss = viewModel::closeSheet,
        onSave = { viewModel.setWaterGoal(it); viewModel.closeSheet() },
    )

    GoalWeightSheet(
        visible = open is SettingsSheet.GoalWeight,
        currentKg = state.profile?.goalWeightKg,
        latestKg = state.profile?.startWeightKg,
        unit = state.settings.weightUnit,
        onDismiss = viewModel::closeSheet,
        onSave = { viewModel.setGoalWeight(it); viewModel.closeSheet() },
    )

    ConfirmWipeSheet(
        visible = open is SettingsSheet.ConfirmWipe,
        name = state.profile?.name.orEmpty(),
        onDismiss = viewModel::closeSheet,
        onConfirm = viewModel::wipeData,
    )
}

// --- alerts -------------------------------------------------------------------------------------

@Composable
private fun BoxScope.ReminderSheet(
    reminder: ReminderEntity?,
    onDismiss: () -> Unit,
    onChange: (ReminderEntity) -> Unit,
) {
    val colors = WiggleTheme.colors
    val context = LocalContext.current
    // The last reminder shown, kept so the sheet still has something to draw on the way out.
    val previous = remember { arrayOfNulls<ReminderEntity>(1) }
    if (reminder != null) previous[0] = reminder
    val current = reminder ?: previous[0] ?: return

    GlassSheet(
        visible = reminder != null,
        onDismiss = onDismiss,
        label = ReminderSchedule.title(current.kind),
    ) {
        Text(
            ReminderSchedule.title(current.kind),
            style = MaterialTheme.typography.titleLarge,
            color = colors.ink,
        )
        Text(
            when (current.kind) {
                ReminderKind.WeighIn -> "A nudge to step on the scale, before breakfast if you can."
                ReminderKind.Measurements -> "Tape measurements move slowly, so once a week is plenty."
                ReminderKind.Water -> "Regular nudges through the day, and none once you hit the goal."
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkMuted,
        )

        Spacer(Modifier.height(16.dp))
        SheetRow(if (current.kind == ReminderKind.Water) "Start at" else "Time") {
            TimeButton(current.timeMinutes, context) { onChange(current.copy(timeMinutes = it)) }
        }

        if (current.kind == ReminderKind.Water) {
            SheetRow("Until") {
                TimeButton(current.untilMinutes, context) { onChange(current.copy(untilMinutes = it)) }
            }
            Spacer(Modifier.height(12.dp))
            Text("Every", style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
            Spacer(Modifier.height(6.dp))
            SegmentedControl(
                options = listOf(60, 90, 120, 180),
                selected = current.intervalMinutes,
                onSelect = { onChange(current.copy(intervalMinutes = it)) },
                label = { if (it < 60) "$it m" else "${it / 60} h" },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            SheetRow("Stop once the goal is met") {
                IosToggle(
                    checked = current.pauseWhenGoalMet,
                    onCheckedChange = { onChange(current.copy(pauseWhenGoalMet = it)) },
                )
            }
        } else {
            Spacer(Modifier.height(14.dp))
            Text("Days", style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
            Spacer(Modifier.height(8.dp))
            DayPicker(
                daysMask = current.daysMask,
                onToggle = { day ->
                    onChange(current.copy(daysMask = ReminderSchedule.toggleDay(current.daysMask, day)))
                },
            )
        }

        if (current.kind == ReminderKind.Measurements) {
            Spacer(Modifier.height(14.dp))
            Text("How often", style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
            Spacer(Modifier.height(6.dp))
            SegmentedControl(
                options = listOf(1, 2, 4),
                selected = current.everyNWeeks,
                onSelect = { onChange(current.copy(everyNWeeks = it)) },
                label = { if (it == 1) "Weekly" else "Every $it weeks" },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = if (current.enabled) "Done" else "Turn on",
            onClick = {
                if (!current.enabled) onChange(current.copy(enabled = true))
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Lucide.Bell,
        )
    }
}

@Composable
private fun TimeButton(minutes: Int, context: android.content.Context, onPick: (Int) -> Unit) {
    val colors = WiggleTheme.colors
    GlassButton(
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onPick(hour * 60 + minute) },
                minutes / 60,
                minutes % 60,
                false,
            ).show()
        },
        height = 40.dp,
        horizontalPadding = 14.dp,
        contentDescription = "Change time",
    ) {
        Icon(Lucide.Clock, size = 16.dp, tint = colors.ink, strokeWidth = 2f)
        Text(
            ReminderSchedule.timeLabel(minutes),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.ink,
        )
    }
}

@Composable
private fun DayPicker(daysMask: Int, onToggle: (DayOfWeek) -> Unit) {
    val colors = WiggleTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { day ->
            val on = ReminderSchedule.isDayEnabled(daysMask, day)
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(if (on) colors.weight.copy(alpha = 0.22f) else colors.track)
                    .clickable(
                        role = Role.Checkbox,
                        onClickLabel = day.name.lowercase().replaceFirstChar { it.uppercase() },
                        onClick = { onToggle(day) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.name.take(1),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) colors.weight else colors.inkMuted,
                )
            }
        }
    }
}

// --- person -------------------------------------------------------------------------------------

@Composable
private fun BoxScope.PersonSheet(
    visible: Boolean,
    existing: ProfileEntity?,
    lengthUnit: LengthUnit,
    weightUnit: WeightUnit,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, heightCm: Double, sex: Sex, goalKg: Double?) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = WiggleTheme.colors
    // Keyed on the person so reopening the sheet for someone else starts from their values.
    var name by remember(visible, existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var heightCm by remember(visible, existing?.id) { mutableDoubleStateOf(existing?.heightCm ?: 170.0) }
    var sex by remember(visible, existing?.id) { mutableStateOf(existing?.sex ?: Sex.Unspecified) }
    var goalKg by remember(visible, existing?.id) { mutableStateOf(existing?.goalWeightKg) }

    GlassSheet(
        visible = visible,
        onDismiss = onDismiss,
        label = if (existing == null) "Add someone" else "Edit person",
    ) {
        Text(
            if (existing == null) "Add someone" else "Edit ${existing.name}",
            style = MaterialTheme.typography.titleLarge,
            color = colors.ink,
        )
        Text(
            "Each person keeps their own weights, measurements, water and alerts.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkMuted,
        )

        Spacer(Modifier.height(14.dp))
        BasicTextField(
            value = name,
            onValueChange = { name = it.take(24) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.weight),
            modifier = Modifier
                .fillMaxWidth()
                .glass(shape = RoundedCornerShape(16.dp), blurRadius = 18.dp, elevation = 4.dp)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            decorationBox = { inner ->
                if (name.isEmpty()) {
                    Text(
                        "Name",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.inkFaint,
                    )
                }
                inner()
            },
        )

        Spacer(Modifier.height(16.dp))
        Text("Height", style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            RollingText(
                text = lengthUnit.fromCm(heightCm).format(if (lengthUnit == LengthUnit.Cm) 0 else 1),
                style = MaterialTheme.typography.displaySmall,
                color = colors.ink,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                lengthUnit.label,
                style = MaterialTheme.typography.titleMedium,
                color = colors.inkMuted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        RulerWheel(
            value = lengthUnit.fromCm(heightCm),
            onValueChange = { heightCm = lengthUnit.toCm(it) },
            range = lengthUnit.fromCm(90.0)..lengthUnit.fromCm(230.0),
            step = 0.5,
            accent = colors.weight,
            height = 72.dp,
        )

        Spacer(Modifier.height(14.dp))
        Text(
            "Sex, for the body fat estimate",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkMuted,
        )
        Spacer(Modifier.height(6.dp))
        SegmentedControl(
            options = listOf(Sex.Female, Sex.Male, Sex.Unspecified),
            selected = sex,
            onSelect = { sex = it },
            label = { if (it == Sex.Unspecified) "Prefer not to" else it.name },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        SheetRow("Goal weight") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (goalKg != null) {
                    Text(
                        "${weightUnit.fromKg(goalKg!!).format()} ${weightUnit.label}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.ink,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                GlassButton(
                    onClick = { goalKg = if (goalKg == null) 70.0 else null },
                    height = 36.dp,
                    horizontalPadding = 12.dp,
                ) {
                    Text(
                        if (goalKg == null) "Set" else "Clear",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.inkMuted,
                    )
                }
            }
        }
        if (goalKg != null) {
            RulerWheel(
                value = weightUnit.fromKg(goalKg!!),
                onValueChange = { goalKg = weightUnit.toKg(it) },
                range = weightUnit.fromKg(30.0)..weightUnit.fromKg(250.0),
                accent = colors.goal,
                height = 72.dp,
            )
        }

        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = if (existing == null) "Add person" else "Save",
            onClick = { onSave(name, heightCm, sex, goalKg) },
            modifier = Modifier.fillMaxWidth(),
            icon = Lucide.Check,
        )

        if (existing != null && canDelete) {
            Spacer(Modifier.height(10.dp))
            GlassButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                contentDescription = "Delete ${existing.name} and all their data",
            ) {
                Icon(Lucide.Trash, size = 16.dp, tint = colors.body, strokeWidth = 2.2f)
                Text(
                    "Delete ${existing.name}",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.body,
                )
            }
        }
    }
}

// --- goals --------------------------------------------------------------------------------------

@Composable
private fun BoxScope.WaterGoalSheet(
    visible: Boolean,
    currentMl: Int,
    unit: VolumeUnit,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    val colors = WiggleTheme.colors
    var goal by remember(visible, currentMl) { mutableIntStateOf(currentMl) }

    GlassSheet(visible = visible, onDismiss = onDismiss, label = "Daily water goal") {
        Text("Daily water goal", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RepeatingIconButton(
                icon = Lucide.Minus,
                contentDescription = "Less",
                onStep = { goal = (goal - 100).coerceAtLeast(500) },
            )
            Row(verticalAlignment = Alignment.Bottom) {
                RollingText(
                    text = formatVolume(goal, unit),
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.ink,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    volumeUnitLabel(goal, unit),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            RepeatingIconButton(
                icon = Lucide.Plus,
                contentDescription = "More",
                onStep = { goal = (goal + 100).coerceAtMost(6000) },
            )
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = "Save goal",
            onClick = { onSave(goal) },
            modifier = Modifier.fillMaxWidth(),
            icon = Lucide.Droplet,
            color = colors.water,
        )
    }
}

@Composable
private fun BoxScope.GoalWeightSheet(
    visible: Boolean,
    currentKg: Double?,
    latestKg: Double?,
    unit: WeightUnit,
    onDismiss: () -> Unit,
    onSave: (Double?) -> Unit,
) {
    val colors = WiggleTheme.colors
    var goal by remember(visible, currentKg) {
        mutableDoubleStateOf(currentKg ?: latestKg ?: 70.0)
    }

    GlassSheet(visible = visible, onDismiss = onDismiss, label = "Goal weight") {
        Text("Goal weight", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            RollingText(
                text = unit.fromKg(goal).format(),
                style = MaterialTheme.typography.displayMedium,
                color = colors.ink,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                unit.label,
                style = MaterialTheme.typography.titleMedium,
                color = colors.inkMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        RulerWheel(
            value = unit.fromKg(goal),
            onValueChange = { goal = unit.toKg(it) },
            range = unit.fromKg(30.0)..unit.fromKg(250.0),
            accent = colors.goal,
        )
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = "Save goal",
            onClick = { onSave(goal) },
            modifier = Modifier.fillMaxWidth(),
            icon = Lucide.Target,
            color = colors.goal,
        )
        Spacer(Modifier.height(10.dp))
        GlassButton(
            onClick = { onSave(null) },
            modifier = Modifier.fillMaxWidth(),
            contentDescription = "Remove the goal",
        ) {
            Text("Remove goal", style = MaterialTheme.typography.labelLarge, color = colors.inkMuted)
        }
    }
}

// --- wipe ---------------------------------------------------------------------------------------

@Composable
private fun BoxScope.ConfirmWipeSheet(
    visible: Boolean,
    name: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = WiggleTheme.colors
    GlassSheet(visible = visible, onDismiss = onDismiss, label = "Delete all data") {
        Text("Delete all data", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "Every weight, measurement and drink logged for $name will be removed. " +
                "$name stays, along with their settings. This cannot be undone.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkMuted,
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = "Delete everything",
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            icon = Lucide.Trash,
            color = colors.body,
        )
        Spacer(Modifier.height(10.dp))
        GlassButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            contentDescription = "Keep my data",
        ) {
            Text("Keep it", style = MaterialTheme.typography.labelLarge, color = colors.ink)
        }
    }
}

// --- shared -------------------------------------------------------------------------------------

@Composable
private fun SheetRow(label: String, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = WiggleTheme.colors.inkMuted,
        )
        trailing()
    }
}
