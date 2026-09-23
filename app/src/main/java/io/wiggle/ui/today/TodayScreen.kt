package io.wiggle.ui.today

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wiggle.data.db.ReminderKind
import io.wiggle.domain.Greeting
import io.wiggle.domain.Stats
import io.wiggle.domain.WeightUnit
import io.wiggle.domain.format
import io.wiggle.domain.formatSigned
import io.wiggle.ui.charts.Sparkline
import io.wiggle.ui.components.AccentPill
import io.wiggle.ui.components.CardRow
import io.wiggle.ui.components.IconBadge
import io.wiggle.ui.components.MinTouch
import io.wiggle.ui.components.ProgressBar
import io.wiggle.ui.components.ProgressRing
import io.wiggle.ui.components.RollingNumber
import io.wiggle.ui.components.ScreenHeader
import io.wiggle.ui.glass.GlassCard
import io.wiggle.ui.glass.GlassDefaults
import io.wiggle.ui.glass.TappableGlassCard
import io.wiggle.ui.glass.cardEntrance
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.profile.ProfileAvatar
import io.wiggle.ui.theme.WiggleTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(
    onQuickAdd: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenWater: () -> Unit,
    onOpenTrends: () -> Unit,
    onOpenReminders: () -> Unit,
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
            // One plus, three things to log: the sheet it opens asks which.
            Box(
                Modifier
                    .size(MinTouch)
                    .clip(CircleShape)
                    .background(colors.ink)
                    .clickable(onClickLabel = "Add an entry", onClick = onQuickAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Lucide.Plus,
                    size = 22.dp,
                    tint = colors.background,
                    strokeWidth = 2.6f,
                    contentDescription = "Add an entry",
                )
            }
            ProfileAvatar(profile = state.profile, onClick = onOpenProfiles)
        }

        HeroWeightCard(state, unit, Modifier.cardEntrance(0))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WaterCard(
                waterMl = state.waterMl,
                goalMl = state.waterGoalMl,
                onClick = onOpenWater,
                modifier = Modifier.weight(1f).cardEntrance(1),
            )
            BmiCard(
                bmi = state.bmi,
                category = state.bmiCategory,
                onClick = onOpenTrends,
                modifier = Modifier.weight(1f).cardEntrance(2),
            )
        }

        UpNextCard(state.upcoming, onOpenReminders, Modifier.cardEntrance(3))
    }
}

@Composable
private fun HeroWeightCard(
    state: TodayUiState,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
) {
    val colors = WiggleTheme.colors
    GlassCard(modifier.fillMaxWidth()) {
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
            state.weekChangeKg?.let { change ->
                AccentPill(
                    text = "${unit.fromKg(change).formatSigned()} ${unit.label} this week",
                    accent = if (change <= 0) colors.weightSoft else colors.goalSoft,
                    icon = if (change <= 0) Lucide.ArrowDown else Lucide.ArrowUp,
                )
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

@Composable
private fun WaterCard(
    waterMl: Int,
    goalMl: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WiggleTheme.colors
    TappableGlassCard(
        onClick = onClick,
        modifier = modifier,
        contentDescription = "Open water",
    ) {
        Text("Water", style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = if (goalMl > 0) waterMl.toFloat() / goalMl else 0f,
                color = colors.water,
                trackColor = colors.track,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingNumber(
                        value = waterMl / 1000.0,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.ink,
                    )
                    Text(" L", style = MaterialTheme.typography.titleSmall, color = colors.ink)
                }
                Text(
                    "of ${(goalMl / 1000.0).format()} L",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
            }
        }
    }
}

@Composable
private fun BmiCard(
    bmi: Double?,
    category: Stats.BmiCategory?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WiggleTheme.colors
    val accent = when (category) {
        Stats.BmiCategory.Healthy -> colors.weightSoft
        Stats.BmiCategory.Underweight, Stats.BmiCategory.Overweight -> colors.goalSoft
        Stats.BmiCategory.Obese -> colors.bodySoft
        null -> colors.inkFaint
    }
    TappableGlassCard(
        onClick = onClick,
        modifier = modifier,
        contentDescription = "Open trends",
    ) {
        Text("BMI", style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
        Spacer(Modifier.height(6.dp))
        if (bmi != null) {
            RollingNumber(
                value = bmi,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.ink,
            )
        } else {
            Text("—", style = MaterialTheme.typography.headlineMedium, color = colors.inkFaint)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(accent, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                category?.label ?: "No reading yet",
                style = MaterialTheme.typography.bodySmall,
                color = accent,
            )
        }
    }
}

@Composable
private fun UpNextCard(
    upcoming: List<UpcomingReminder>,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WiggleTheme.colors
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GlassDefaults.SmallRadius),
        contentPadding = 16.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().height(MinTouch),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Up next", style = MaterialTheme.typography.titleSmall, color = colors.ink)
            Box(
                Modifier
                    .height(MinTouch)
                    .clickable(onClickLabel = "Edit reminders", onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Text("Edit", style = MaterialTheme.typography.labelLarge, color = colors.weightSoft)
            }
        }
        if (upcoming.isEmpty()) {
            Text(
                "No reminders switched on yet.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkFaint,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        upcoming.forEach { item ->
            val accent = when (item.kind) {
                ReminderKind.Water -> colors.waterSoft
                ReminderKind.WeighIn -> colors.weightSoft
                ReminderKind.Measurements -> colors.bodySoft
            }
            val icon = when (item.kind) {
                ReminderKind.Water -> Lucide.Droplet
                ReminderKind.WeighIn -> Lucide.Scale
                ReminderKind.Measurements -> Lucide.Ruler
            }
            CardRow(onClick = onEdit) {
                IconBadge(icon, accent)
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
                }
                Icon(Lucide.ChevronRight, size = 16.dp, tint = colors.inkFaint, strokeWidth = 2.4f)
            }
        }
    }
}
