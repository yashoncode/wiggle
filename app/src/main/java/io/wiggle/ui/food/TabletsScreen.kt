package io.wiggle.ui.food

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wiggle.data.db.MedicationEntity
import io.wiggle.domain.Dose
import io.wiggle.domain.DoseStatus
import io.wiggle.domain.Doses
import io.wiggle.domain.ReminderSchedule
import io.wiggle.ui.components.CardRow
import io.wiggle.ui.components.GlassButton
import io.wiggle.ui.components.IconBadge
import io.wiggle.ui.components.IosToggle
import io.wiggle.ui.components.MinTouch
import io.wiggle.ui.components.PrimaryButton
import io.wiggle.ui.components.ProgressRing
import io.wiggle.ui.components.RepeatingIconButton
import io.wiggle.ui.glass.GlassCard
import io.wiggle.ui.glass.GlassDefaults
import io.wiggle.ui.glass.GlassSheet
import io.wiggle.ui.glass.cardEntrance
import io.wiggle.ui.glass.glass
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.theme.WiggleColors
import io.wiggle.ui.theme.WiggleTheme

/** Food › Tablets: today's doses, ticked off as they are taken, and a word before stock runs out. */
@Composable
fun TabletsScreen(
    onOpenReminders: () -> Unit,
    viewModel: TabletsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = WiggleTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SummaryCard(state, Modifier.cardEntrance(0))

        GlassCard(
            Modifier.fillMaxWidth().cardEntrance(1),
            shape = RoundedCornerShape(GlassDefaults.SmallRadius),
            contentPadding = 16.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Today's schedule", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                Box(
                    Modifier.height(MinTouch).clickable(onClickLabel = "Tablet reminders", onClick = onOpenReminders),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (state.remindersOn) "Reminds until taken" else "Reminders off",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.remindersOn) colors.inkMuted else colors.goalSoft,
                    )
                }
            }
            if (state.doses.isEmpty()) {
                Text(
                    "Add a tablet, vitamin or supplement and when you take it. Wiggle can remind you until each dose is ticked off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkFaint,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            state.doses.forEachIndexed { index, dose ->
                DoseRow(
                    dose = dose,
                    showDivider = index > 0,
                    onToggle = { viewModel.toggle(dose) },
                    onEdit = { viewModel.edit(dose.medication) },
                )
            }
        }

        state.lowStock.forEach { (medication, days) ->
            GlassCard(
                Modifier.fillMaxWidth().cardEntrance(2),
                shape = RoundedCornerShape(GlassDefaults.TinyRadius),
                contentPadding = 14.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(Lucide.Bottle, colors.goalSoft)
                    Column(Modifier.weight(1f)) {
                        Text("${medication.name} running low", style = MaterialTheme.typography.titleSmall, color = colors.ink)
                        Text(
                            "${medication.stock} left · about $days ${if (days == 1) "day" else "days"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkMuted,
                        )
                    }
                    GlassButton(
                        onClick = { viewModel.edit(medication) },
                        height = 36.dp,
                        horizontalPadding = 14.dp,
                        contentDescription = "Update ${medication.name} stock",
                    ) {
                        Text("Restock", style = MaterialTheme.typography.labelLarge, color = colors.ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: TabletsUiState, modifier: Modifier = Modifier) {
    val colors = WiggleTheme.colors
    val overdue = state.doses.firstOrNull { it.status == DoseStatus.Overdue }
    val next = state.doses.firstOrNull { it.status == DoseStatus.Due }
    GlassCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), contentPadding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                ProgressRing(
                    progress = if (state.doses.isEmpty()) 0f else state.taken.toFloat() / state.doses.size,
                    color = colors.tablets,
                    trackColor = colors.track,
                    strokeWidth = 8.dp,
                    modifier = Modifier.size(72.dp),
                )
                Text("${state.taken}/${state.doses.size}", style = MaterialTheme.typography.titleMedium, color = colors.ink)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Taken today", style = MaterialTheme.typography.labelMedium, color = colors.inkMuted)
                Text(
                    when {
                        state.doses.isEmpty() -> "No tablets yet"
                        overdue != null -> "${overdue.medication.name} is overdue"
                        next != null -> "Next: ${next.medication.name}"
                        else -> "All done for today"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                )
                val dose = overdue ?: next
                Text(
                    when {
                        state.doses.isEmpty() -> "Tap Add tablet to start a schedule."
                        dose == null -> "Every dose is ticked off."
                        overdue != null -> "Was due ${detail(dose)}"
                        else -> detail(dose)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        overdue != null -> colors.goalSoft
                        dose == null && state.doses.isNotEmpty() -> colors.weightSoft
                        else -> colors.inkMuted
                    },
                )
            }
        }
    }
}

private fun detail(dose: Dose): String =
    listOf(ReminderSchedule.timeLabel(dose.slotMinutes), dose.medication.note).filter { it.isNotBlank() }.joinToString(" · ")

/** Per-tablet colour, so the same tablet keeps its tile colour across days. */
fun tabletAccent(colors: WiggleColors, index: Int): Color = when (index % 5) {
    0 -> colors.weightSoft
    1 -> colors.goalSoft
    2 -> colors.tabletsSoft
    3 -> colors.stepsSoft
    else -> colors.waterSoft
}

@Composable
private fun DoseRow(dose: Dose, showDivider: Boolean, onToggle: () -> Unit, onEdit: () -> Unit) {
    val colors = WiggleTheme.colors
    val taken = dose.status == DoseStatus.Taken
    val late = dose.status == DoseStatus.Overdue
    CardRow(showDivider = showDivider, onClick = onEdit) {
        IconBadge(Lucide.Pill, tabletAccent(colors, dose.medication.colorIndex), size = 34.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(dose.medication.name, style = MaterialTheme.typography.titleSmall, color = colors.ink, maxLines = 1)
                Text(
                    when (dose.status) {
                        DoseStatus.Taken -> "Taken"
                        DoseStatus.Overdue -> "Overdue"
                        DoseStatus.Due -> "Due"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (dose.status) {
                        DoseStatus.Taken -> colors.weightSoft
                        DoseStatus.Overdue -> colors.goalSoft
                        DoseStatus.Due -> colors.inkMuted
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.track)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            Text(detail(dose), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
        }
        Box(
            Modifier
                .size(MinTouch)
                .clip(CircleShape)
                .clickable(
                    role = Role.Checkbox,
                    onClickLabel = if (taken) "Mark ${dose.medication.name} not taken" else "Mark ${dose.medication.name} taken",
                    onClick = onToggle,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (taken) colors.weight else if (late) colors.goal.copy(alpha = 0.12f) else colors.track)
                    .border(2.dp, if (taken) Color.Transparent else if (late) colors.goal else colors.inkFaint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (taken) Icon(Lucide.Check, size = 18.dp, tint = colors.onAccent, strokeWidth = 3f)
            }
        }
    }
}

/** Adds or edits a tablet: its name, when it is taken, and how many are left. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoxScope.MedicationSheet(viewModel: TabletsViewModel = hiltViewModel()) {
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val colors = WiggleTheme.colors
    val context = LocalContext.current
    // The last tablet shown, kept so the sheet still has something to draw on the way out.
    val previous = remember { arrayOfNulls<MedicationEntity>(1) }
    if (editing != null) previous[0] = editing
    val current = editing ?: previous[0] ?: return

    var name by remember(current) { mutableStateOf(current.name) }
    var note by remember(current) { mutableStateOf(current.note) }
    var times by remember(current) { mutableStateOf(Doses.parseTimes(current.times)) }
    var counted by remember(current) { mutableStateOf(current.stock != null) }
    var stock by remember(current) { mutableStateOf(current.stock ?: 30) }
    var perDose by remember(current) { mutableStateOf(current.perDose) }

    fun pickTime(initial: Int, onPick: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPick(h * 60 + m) }, initial / 60, initial % 60, false).show()
    }

    GlassSheet(
        visible = editing != null,
        onDismiss = viewModel::close,
        label = if (current.id == 0L) "Add tablet" else "Edit ${current.name}",
    ) {
        Text(
            if (current.id == 0L) "Add tablet" else "Edit ${current.name}",
            style = MaterialTheme.typography.titleLarge,
            color = colors.ink,
        )
        SheetField(name, { name = it.take(40) }, "Name, e.g. Vitamin D3")
        SheetField(note, { note = it.take(40) }, "When, e.g. after breakfast")

        Text("Times", style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            times.forEach { minutes ->
                GlassButton(
                    onClick = { pickTime(minutes) { picked -> times = (times - minutes + picked).distinct().sorted() } },
                    height = 40.dp,
                    horizontalPadding = 12.dp,
                    contentDescription = "Change ${ReminderSchedule.timeLabel(minutes)}",
                ) {
                    Icon(Lucide.Clock, size = 15.dp, tint = colors.ink, strokeWidth = 2f)
                    Text(ReminderSchedule.timeLabel(minutes), style = MaterialTheme.typography.labelLarge, color = colors.ink)
                    if (times.size > 1) {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).clickable(onClickLabel = "Remove this time") {
                                times = times - minutes
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Lucide.X, size = 13.dp, tint = colors.inkMuted, strokeWidth = 2.4f) }
                    }
                }
            }
            if (times.size < 8) {
                GlassButton(
                    onClick = { pickTime(20 * 60) { picked -> times = (times + picked).distinct().sorted() } },
                    height = 40.dp,
                    horizontalPadding = 12.dp,
                    contentDescription = "Add a time",
                ) {
                    Icon(Lucide.Plus, size = 15.dp, tint = colors.tabletsSoft, strokeWidth = 2.6f)
                    Text("Time", style = MaterialTheme.typography.labelLarge, color = colors.ink)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Count what's left", style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                Text("Warns a week before it runs out", style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
            }
            IosToggle(checked = counted, onCheckedChange = { counted = it })
        }
        if (counted) {
            Stepper("Left", stock, onChange = { stock = it.coerceIn(0, 999) }, step = 1)
            Stepper("Per dose", perDose, onChange = { perDose = it.coerceIn(1, 6) }, step = 1)
        }

        PrimaryButton(
            text = "Save",
            onClick = {
                viewModel.save(
                    current.copy(
                        name = name,
                        note = note,
                        times = Doses.timesCsv(times),
                        stock = if (counted) stock else null,
                        perDose = perDose,
                    )
                )
            },
            enabled = name.isNotBlank() && times.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            icon = Lucide.Check,
            color = colors.tablets,
            contentColor = colors.background,
        )
        if (current.id != 0L) {
            GlassButton(
                onClick = { viewModel.delete(current) },
                modifier = Modifier.fillMaxWidth(),
                contentDescription = "Delete ${current.name}",
            ) {
                Icon(Lucide.Trash, size = 16.dp, tint = colors.body, strokeWidth = 2.2f)
                Text("Delete ${current.name}", style = MaterialTheme.typography.labelLarge, color = colors.body)
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit, step: Int) {
    val colors = WiggleTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.inkMuted, modifier = Modifier.weight(1f))
        RepeatingIconButton(Lucide.Minus, "Fewer", onStep = { onChange(value - step) }, size = 40.dp)
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, color = colors.ink)
        RepeatingIconButton(Lucide.Plus, "More", onStep = { onChange(value + step) }, size = 40.dp)
    }
}

@Composable
private fun SheetField(value: String, onChange: (String) -> Unit, placeholder: String) {
    val colors = WiggleTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.tablets),
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = RoundedCornerShape(16.dp), blurRadius = 18.dp, elevation = 4.dp)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.titleMedium, color = colors.inkFaint)
            inner()
        },
    )
}
