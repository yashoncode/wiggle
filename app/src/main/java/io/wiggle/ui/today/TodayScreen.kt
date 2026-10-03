package io.wiggle.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wiggle.domain.Greeting
import io.wiggle.domain.Stats
import io.wiggle.domain.StepStats
import io.wiggle.domain.WeightUnit
import io.wiggle.domain.format
import io.wiggle.domain.formatSigned
import io.wiggle.domain.formatVolume
import io.wiggle.domain.volumeUnitLabel
import io.wiggle.ui.SettingsButton
import io.wiggle.ui.charts.Sparkline
import io.wiggle.ui.components.AccentPill
import io.wiggle.ui.components.GlassIconButton
import io.wiggle.ui.components.IconBadge
import io.wiggle.ui.components.ProgressBar
import io.wiggle.ui.components.ProgressRing
import io.wiggle.ui.components.RollingNumber
import io.wiggle.ui.components.ScreenHeader
import io.wiggle.ui.glass.GlassDefaults
import io.wiggle.ui.glass.TappableGlassCard
import io.wiggle.ui.glass.cardEntrance
import io.wiggle.ui.glass.glass
import io.wiggle.ui.glass.popOnTap
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.icons.LucideIcon
import io.wiggle.ui.theme.WiggleTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Home: today at a glance. Weight up top, the three daily rings under it, one-tap logging, and a
 * line on where the week is heading.
 */
@Composable
fun TodayScreen(
    onOpenReminders: () -> Unit,
    onOpenWeight: () -> Unit,
    onOpenCalories: () -> Unit,
    onOpenSteps: () -> Unit,
    onOpenWater: () -> Unit,
    onLogWeight: () -> Unit,
    onLogMeal: () -> Unit,
    onLogMeasure: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = WiggleTheme.colors
    val unit = state.settings.weightUnit

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader(
            eyebrow = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM")),
            // Recomputed whenever the header recomposes, so it is never a stale "Good morning".
            title = Greeting.forPerson(state.profile?.name),
            titleStyle = MaterialTheme.typography.headlineMedium,
        ) {
            Box {
                GlassIconButton(
                    icon = Lucide.Bell,
                    contentDescription = if (state.reminderToday) "Reminders, some due today" else "Reminders",
                    onClick = onOpenReminders,
                )
                if (state.reminderToday) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-10).dp, y = 9.dp)
                            .size(9.dp)
                            .background(colors.body, CircleShape),
                    )
                }
            }
            SettingsButton()
        }

        HeroWeightCard(state, unit, onOpenWeight, Modifier.cardEntrance(0))

        DailyRings(state, onOpenCalories, onOpenSteps, onOpenWater, Modifier.cardEntrance(1))

        Row(
            Modifier.fillMaxWidth().cardEntrance(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuickLog("Weight", Lucide.Scale, colors.weightSoft, onLogWeight, Modifier.weight(1f))
            QuickLog("Meal", Lucide.Utensils, colors.foodSoft, onLogMeal, Modifier.weight(1f))
            QuickLog("Water", Lucide.Droplet, colors.waterSoft, onOpenWater, Modifier.weight(1f))
            QuickLog("Measure", Lucide.Ruler, colors.bodySoft, onLogMeasure, Modifier.weight(1f))
        }

        InsightCard(state, unit, onOpenWeight, Modifier.cardEntrance(3))
    }
}

@Composable
private fun HeroWeightCard(
    state: TodayUiState,
    unit: WeightUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WiggleTheme.colors
    TappableGlassCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GlassDefaults.Radius),
        contentPadding = 20.dp,
        contentDescription = "Open weight",
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text("Current weight", style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    if (state.currentKg != null) {
                        RollingNumber(
                            value = unit.fromKg(state.currentKg),
                            style = MaterialTheme.typography.displayMedium,
                            color = colors.ink,
                        )
                    } else {
                        Text("—", style = MaterialTheme.typography.displayMedium, color = colors.inkFaint)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        unit.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.inkMuted,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.weekChangeKg?.let { change ->
                    AccentPill(
                        text = "${unit.fromKg(change).formatSigned()} ${unit.label} this week",
                        accent = if (change <= 0) colors.weightSoft else colors.goalSoft,
                        icon = if (change <= 0) Lucide.ArrowDown else Lucide.ArrowUp,
                    )
                }
                state.bmi?.let { bmi ->
                    val accent = when (state.bmiCategory) {
                        Stats.BmiCategory.Healthy -> colors.weightSoft
                        Stats.BmiCategory.Obese -> colors.bodySoft
                        else -> colors.goalSoft
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(accent, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "BMI ${bmi.format()} · ${state.bmiCategory?.label.orEmpty()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.inkMuted,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state.sparkline.size >= 2) {
            Sparkline(
                values = state.sparkline,
                color = colors.weight,
                fillArea = true,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            )
        } else {
            Text(
                "Log a couple of days to see your trend.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkFaint,
                modifier = Modifier.height(56.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        val start = state.startKg
        val goal = state.goalKg
        if (start != null && goal != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Start ${unit.fromKg(start).format()} ${unit.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
                Text(
                    "${(state.goalProgress * 100).toInt()}% to goal",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.ink,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Goal ${unit.fromKg(goal).format()} ${unit.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
            }
            Spacer(Modifier.height(6.dp))
            ProgressBar(state.goalProgress, colors.weight, colors.track)
        } else {
            Text(
                "Set a goal weight in Settings to track progress.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkFaint,
            )
        }
    }
}

/** Calories left, steps and water, as three rings that each open their own screen. */
@Composable
private fun DailyRings(
    state: TodayUiState,
    onCalories: () -> Unit,
    onSteps: () -> Unit,
    onWater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WiggleTheme.colors
    val unit = state.settings.volumeUnit
    Row(
        modifier
            .fillMaxWidth()
            .glass(shape = RoundedCornerShape(GlassDefaults.SmallRadius))
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        val calories = state.calories
        Ring(
            progress = calories?.progress ?: 0f,
            color = colors.food,
            iconTint = colors.foodSoft,
            icon = Lucide.Utensils,
            value = calories?.let { groupedSigned(it.left) } ?: "—",
            label = if ((calories?.left ?: 0) < 0) "kcal over" else "kcal left",
            onClick = onCalories,
            modifier = Modifier.weight(1f),
        )
        val steps = state.steps
        Ring(
            progress = if (steps == null) 0f else steps.toFloat() / state.stepGoal.coerceAtLeast(1),
            color = colors.steps,
            iconTint = colors.stepsSoft,
            icon = Lucide.Activity,
            value = steps?.let(StepStats::grouped) ?: "—",
            label = if (steps == null) "connect steps" else "of ${compactThousands(state.stepGoal)} steps",
            onClick = onSteps,
            modifier = Modifier.weight(1f),
        )
        Ring(
            progress = if (state.waterGoalMl > 0) state.waterMl.toFloat() / state.waterGoalMl else 0f,
            color = colors.water,
            iconTint = colors.waterSoft,
            icon = Lucide.Droplet,
            value = "${formatVolume(state.waterMl, unit)} ${volumeUnitLabel(state.waterMl, unit)}",
            label = "of ${formatVolume(state.waterGoalMl, unit)} ${volumeUnitLabel(state.waterGoalMl, unit)} water",
            onClick = onWater,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Ring(
    progress: Float,
    color: Color,
    iconTint: Color,
    icon: LucideIcon,
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WiggleTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .popOnTap(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$value $label" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
            ProgressRing(
                progress = progress,
                color = color,
                trackColor = colors.track,
                modifier = Modifier.size(66.dp),
            )
            Icon(icon, size = 20.dp, tint = iconTint, strokeWidth = 2f)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 1)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun QuickLog(
    label: String,
    icon: LucideIcon,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WiggleTheme.colors
    TappableGlassCard(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(GlassDefaults.TinyRadius),
        contentPadding = 0.dp,
        contentDescription = "Log $label",
    ) {
        Column(
            Modifier.fillMaxWidth().height(72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            IconBadge(icon, accent, size = 30.dp)
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.ink)
        }
    }
}

@Composable
private fun InsightCard(
    state: TodayUiState,
    unit: WeightUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WiggleTheme.colors
    val balance = state.weekBalanceKcal
    val projected = state.projectedGoalDate
    val goal = state.goalKg
    TappableGlassCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GlassDefaults.TinyRadius),
        contentDescription = "Open weight trend",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(Lucide.Target, colors.goalSoft)
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        balance == null -> "This week"
                        balance <= 0 -> "${groupedSigned(balance)} kcal this week"
                        else -> "+${groupedSigned(balance)} kcal this week"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.ink,
                )
                Text(
                    when {
                        projected != null && goal != null ->
                            "On pace for ${unit.fromKg(goal).format()} ${unit.label} around " +
                                projected.format(DateTimeFormatter.ofPattern("d MMM"))
                        balance == null -> "Log meals to see your weekly balance."
                        else -> "Keep logging to see when you reach your goal."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
            }
            Icon(Lucide.ChevronRight, size = 16.dp, tint = colors.inkFaint, strokeWidth = 2.4f)
        }
    }
}

/** 1,240 or −320, with a real minus sign. */
fun groupedSigned(value: Int): String =
    if (value < 0) "−" + StepStats.grouped(abs(value).toLong()) else StepStats.grouped(value.toLong())

/** 10k, 8.5k, 900. */
fun compactThousands(value: Int): String = when {
    value >= 1000 && value % 1000 == 0 -> "${value / 1000}k"
    value >= 1000 -> "${(value / 100) / 10.0}k"
    else -> value.toString()
}
