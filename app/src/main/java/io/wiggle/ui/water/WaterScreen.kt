package io.wiggle.ui.water

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wiggle.data.db.WaterEntryEntity
import io.wiggle.domain.VolumeUnit
import io.wiggle.domain.WaterCoach
import io.wiggle.domain.WaterTone
import io.wiggle.domain.formatVolume
import io.wiggle.domain.volumeUnitLabel
import io.wiggle.ui.charts.BarChart
import io.wiggle.ui.components.AccentPill
import io.wiggle.ui.components.CardRow
import io.wiggle.ui.components.GlassButton
import io.wiggle.ui.components.MinTouch
import io.wiggle.ui.components.PrimaryButton
import io.wiggle.ui.components.ProgressBar
import io.wiggle.ui.components.RepeatingIconButton
import io.wiggle.ui.components.RollingText
import io.wiggle.ui.components.ScreenHeader
import io.wiggle.ui.components.SwipeToDelete
import io.wiggle.ui.glass.GlassCard
import io.wiggle.ui.glass.GlassSheet
import io.wiggle.ui.glass.cardEntrance
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.motion.rememberHaptics
import io.wiggle.ui.theme.WiggleTheme
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TimeFormat = DateTimeFormatter.ofPattern("h:mm a")

@Composable
fun WaterScreen(
    onGoalReached: () -> Unit,
    onEditGoal: () -> Unit,
    onCustomAmount: () -> Unit,
    viewModel: WaterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = WiggleTheme.colors
    val haptics = rememberHaptics(state.settings.hapticsEnabled)
    val unit = state.settings.volumeUnit

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader(eyebrow = "Hydration", title = "Water") {
            AccentPill(
                text = "Goal ${formatVolume(state.goalMl, unit)} ${volumeUnitLabel(state.goalMl, unit)}",
                accent = colors.water,
                icon = Lucide.Target,
            )
        }

        BottleCard(state, unit, Modifier.cardEntrance(0))

        QuickAddRow(
            presets = viewModel.presets,
            unit = unit,
            onAdd = { amount ->
                haptics.tick()
                viewModel.add(amount) { haptics.success(); onGoalReached() }
            },
            onCustom = onCustomAmount,
            modifier = Modifier.cardEntrance(1),
        )

        TodayLogCard(
            entries = state.entries,
            unit = unit,
            onDelete = viewModel::delete,
            onUndoLast = viewModel::undoLastAdd,
            modifier = Modifier.cardEntrance(2),
        )

        WeekCard(state, unit, onEditGoal, Modifier.cardEntrance(3))
    }

}

@Composable
private fun BottleCard(state: WaterUiState, unit: VolumeUnit, modifier: Modifier = Modifier) {
    val colors = WiggleTheme.colors
    GlassCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WaterBottle(
                progress = state.progress,
                bubbleKey = state.bubbleKey,
                modifier = Modifier.width(96.dp).height(190.dp),
            )
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("Today", style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingText(
                        text = formatVolume(state.totalMl, unit),
                        style = MaterialTheme.typography.displayMedium,
                        color = colors.ink,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        volumeUnitLabel(state.totalMl, unit),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.inkMuted,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                ProgressBar(
                    progress = state.progress,
                    color = colors.water,
                    trackColor = colors.track,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (state.goalMet) {
                        "${(state.progress * 100).toInt()}% of ${formatVolume(state.goalMl, unit)} ${volumeUnitLabel(state.goalMl, unit)}"
                    } else {
                        "${formatVolume(state.remainingMl, unit)} ${volumeUnitLabel(state.remainingMl, unit)} to go"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.goalMet) colors.water else colors.inkMuted,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        CoachNote(state.totalMl, state.goalMl)
    }
}

/**
 * The line that talks back. Encouragement while you are short, a word when you are done, and a
 * warning once the total passes what the body can sensibly clear in a day.
 */
@Composable
private fun CoachNote(totalMl: Int, goalMl: Int, modifier: Modifier = Modifier) {
    val colors = WiggleTheme.colors
    // Recomputed when the totals change, and again on the hour so a morning line does not
    // still be sitting there at six in the evening.
    val hour = remember(totalMl) { LocalTime.now().hour }
    val message = remember(totalMl, goalMl, hour) {
        WaterCoach.message(totalMl, goalMl, LocalTime.of(hour, 0))
    }
    val accent = when (message.tone) {
        WaterTone.TooMuch -> colors.body
        WaterTone.Plenty -> colors.goal
        WaterTone.Met -> colors.water
        WaterTone.Behind -> colors.goal
        else -> colors.inkMuted
    }
    val icon = when (message.tone) {
        WaterTone.TooMuch -> Lucide.Info
        WaterTone.Plenty -> Lucide.Info
        WaterTone.Met -> Lucide.Sparkles
        else -> Lucide.Droplet
    }

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = if (message.tone == WaterTone.TooMuch) 0.16f else 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, size = 15.dp, tint = accent, strokeWidth = 2.2f)
        Text(
            message.text,
            style = MaterialTheme.typography.bodySmall,
            color = if (message.tone == WaterTone.TooMuch) colors.ink else accent,
        )
    }
}

@Composable
private fun QuickAddRow(
    presets: List<Int>,
    unit: VolumeUnit,
    onAdd: (Int) -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WiggleTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        presets.forEach { amount ->
            GlassButton(
                onClick = { onAdd(amount) },
                modifier = Modifier.weight(1f),
                horizontalPadding = 8.dp,
                contentDescription = "Add ${formatVolume(amount, unit)} ${volumeUnitLabel(amount, unit)}",
            ) {
                Icon(Lucide.Plus, size = 14.dp, tint = colors.water, strokeWidth = 2.8f)
                Text(
                    "${formatVolume(amount, unit)} ${volumeUnitLabel(amount, unit)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.ink,
                    maxLines = 1,
                )
            }
        }
        GlassButton(
            onClick = onCustom,
            modifier = Modifier.width(MinTouch),
            horizontalPadding = 0.dp,
            contentDescription = "Custom amount",
        ) {
            Icon(Lucide.Pencil, size = 18.dp, tint = colors.ink, strokeWidth = 2.2f)
        }
    }
}

@Composable
private fun TodayLogCard(
    entries: List<WaterEntryEntity>,
    unit: VolumeUnit,
    onDelete: (WaterEntryEntity) -> Unit,
    onUndoLast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WiggleTheme.colors
    val zone = remember { ZoneId.systemDefault() }
    GlassCard(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Today's log", style = MaterialTheme.typography.titleMedium, color = colors.ink)
            if (entries.isNotEmpty()) {
                Row(
                    Modifier
                        .padding(start = 8.dp)
                        .height(MinTouch),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassButton(
                        onClick = onUndoLast,
                        height = 36.dp,
                        horizontalPadding = 12.dp,
                        contentDescription = "Undo last drink",
                    ) {
                        Icon(Lucide.Undo, size = 14.dp, tint = colors.inkMuted, strokeWidth = 2.2f)
                        Text("Undo", style = MaterialTheme.typography.labelLarge, color = colors.inkMuted)
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Nothing yet today. Tap a size above.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkFaint,
            )
        } else {
            entries.forEachIndexed { index, entry ->
                SwipeToDelete(onDelete = { onDelete(entry) }) {
                    CardRow(showDivider = index > 0) {
                        Icon(Lucide.Droplet, size = 16.dp, tint = colors.water, strokeWidth = 2.2f)
                        Text(
                            text = "${formatVolume(entry.amountMl, unit)} ${volumeUnitLabel(entry.amountMl, unit)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = Instant.ofEpochMilli(entry.loggedAt).atZone(zone).format(TimeFormat),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.inkMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekCard(
    state: WaterUiState,
    unit: VolumeUnit,
    onEditGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WiggleTheme.colors
    val days = state.week
    val average = days.map { it.totalMl }.average().takeIf { days.isNotEmpty() && !it.isNaN() } ?: 0.0
    val metCount = days.count { it.totalMl >= state.goalMl }

    GlassCard(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("This week", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                Text(
                    "Average ${formatVolume(average.toInt(), unit)} ${volumeUnitLabel(average.toInt(), unit)} · goal met $metCount of 7",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
            }
            GlassButton(
                onClick = onEditGoal,
                height = 36.dp,
                horizontalPadding = 12.dp,
                contentDescription = "Change daily goal",
            ) {
                Text("Goal", style = MaterialTheme.typography.labelLarge, color = colors.ink)
            }
        }
        Spacer(Modifier.height(12.dp))
        BarChart(
            values = days.map { it.totalMl.toDouble() },
            labels = days.map { it.date.dayOfWeek.name.take(1) },
            color = colors.water,
            highlightIndex = days.lastIndex,
            valueLabel = { value -> if (value <= 0) "" else formatVolume(value.toInt(), unit) },
        )
    }
}

/**
 * The custom amount sheet: a stepper for the odd-sized glass or bottle, with a few common vessel
 * sizes still there so the usual case never needs the stepper.
 */
@Composable
fun BoxScope.WaterCustomAmountSheet(
    visible: Boolean,
    unit: VolumeUnit,
    onDismiss: () -> Unit,
    onAdd: (Int) -> Unit,
) {
    val colors = WiggleTheme.colors
    var amount by remember(visible) { mutableIntStateOf(300) }

    GlassSheet(visible = visible, onDismiss = onDismiss, label = "Custom amount") {
        Text("How much?", style = MaterialTheme.typography.titleMedium, color = colors.ink)
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RepeatingIconButton(
                icon = Lucide.Minus,
                contentDescription = "Less",
                onStep = { amount = (amount - 50).coerceAtLeast(50) },
            )
            Row(verticalAlignment = Alignment.Bottom) {
                RollingText(
                    text = formatVolume(amount, unit),
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.ink,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    volumeUnitLabel(amount, unit),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            RepeatingIconButton(
                icon = Lucide.Plus,
                contentDescription = "More",
                onStep = { amount = (amount + 50).coerceAtMost(3000) },
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(200, 330, 750).forEach { preset ->
                GlassButton(
                    onClick = { amount = preset },
                    modifier = Modifier.weight(1f),
                    height = 40.dp,
                    horizontalPadding = 8.dp,
                ) {
                    Text(
                        "${formatVolume(preset, unit)} ${volumeUnitLabel(preset, unit)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (preset == amount) colors.water else colors.inkMuted,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = "Add ${formatVolume(amount, unit)} ${volumeUnitLabel(amount, unit)}",
            onClick = { onAdd(amount) },
            modifier = Modifier.fillMaxWidth(),
            icon = Lucide.Droplet,
            color = colors.water,
        )
    }
}
