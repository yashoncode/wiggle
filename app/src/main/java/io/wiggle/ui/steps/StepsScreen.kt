package io.wiggle.ui.steps

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wiggle.data.StepsAvailability
import io.wiggle.domain.LengthUnit
import io.wiggle.domain.StepStats
import io.wiggle.domain.format
import io.wiggle.ui.SettingsButton
import io.wiggle.ui.charts.BarChart
import io.wiggle.ui.components.AccentPill
import io.wiggle.ui.components.CardRow
import io.wiggle.ui.components.PrimaryButton
import io.wiggle.ui.components.ProgressRing
import io.wiggle.ui.components.ScreenHeader
import io.wiggle.ui.glass.GlassCard
import io.wiggle.ui.glass.GlassDefaults
import io.wiggle.ui.glass.cardEntrance
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.theme.WiggleTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val KM_PER_MILE = 1.609344

/** Steps: today against the goal, the day hour by hour, and the last week. */
@Composable
fun StepsScreen(viewModel: StepsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = WiggleTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader(
            eyebrow = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM")),
            title = "Steps",
        ) {
            if (state.streak >= 2) {
                AccentPill(text = "${state.streak}-day streak", accent = colors.stepsSoft, icon = Lucide.Flame)
            }
            SettingsButton()
        }

        if (!state.counting) ConnectCard(state, viewModel, Modifier.cardEntrance(0))

        TodayCard(state, onSync = viewModel::refresh, modifier = Modifier.cardEntrance(1))
        HoursCard(state, Modifier.cardEntrance(2))
        WeekCard(state, Modifier.cardEntrance(3))
    }
}

/**
 * Shown until steps are flowing: what Health Connect is, how to get the phone's own health app to
 * write into it, and the one button that does the next step.
 */
@Composable
private fun ConnectCard(state: StepsUiState, viewModel: StepsViewModel, modifier: Modifier = Modifier) {
    val colors = WiggleTheme.colors
    val context = LocalContext.current
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(
        contract = androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
        onResult = viewModel::onPermissionResult,
    )

    GlassCard(modifier.fillMaxWidth(), contentPadding = 18.dp) {
        val (title, body) = when {
            state.availability == StepsAvailability.Ready && state.ownerName != null ->
                "Counting for ${state.ownerName}" to
                    "Steps belong to the phone's owner, and this phone's are counted for ${state.ownerName}. " +
                    "Hand them to ${state.profile?.name ?: "this person"} if this is their phone."
            state.availability == StepsAvailability.Unsupported ->
                "Steps need Android 9 or later" to
                    "Health Connect, where your phone keeps its step count, does not run on this version of Android."
            state.availability == StepsAvailability.NeedsInstall ->
                "Install Health Connect" to
                    "Your phone keeps its step count in Health Connect, Google's free health store. " +
                    "Install or update it, then come back to connect."
            else ->
                "Connect your step counter" to
                    "Wiggle reads steps from Health Connect, so they come from your phone's own counter. " +
                    "On Realme, OPPO and OnePlus phones, open OHealth › Settings › Data sharing › Health Connect " +
                    "and allow Steps. On Android 14 and later the phone counts steps there by itself."
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Lucide.Heart, size = 22.dp, tint = colors.stepsSoft, strokeWidth = 2.2f)
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.ink)
        }
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
        Spacer(Modifier.height(14.dp))
        when {
            state.availability == StepsAvailability.Ready && state.ownerName != null -> PrimaryButton(
                text = "Count them for ${state.profile?.name ?: "me"}",
                onClick = { viewModel.claimSteps() },
                modifier = Modifier.fillMaxWidth(),
                color = colors.steps,
                contentColor = colors.background,
            )
            state.availability == StepsAvailability.NeedsInstall -> PrimaryButton(
                text = "Get Health Connect",
                onClick = { runCatching { context.startActivity(viewModel.installIntent()) } },
                modifier = Modifier.fillMaxWidth(),
                color = colors.steps,
                contentColor = colors.background,
            )
            state.availability == StepsAvailability.NeedsPermission ||
                state.availability == StepsAvailability.Ready -> PrimaryButton(
                text = "Connect",
                onClick = { launcher.launch(permissions) },
                modifier = Modifier.fillMaxWidth(),
                icon = Lucide.Activity,
                color = colors.steps,
                contentColor = colors.background,
            )
            else -> Unit
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Read-only and stays on the phone: Wiggle shows your steps and adds walking to your calorie budget.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkFaint,
        )
    }
}

@Composable
private fun TodayCard(state: StepsUiState, onSync: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WiggleTheme.colors
    GlassCard(modifier.fillMaxWidth(), contentPadding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
                ProgressRing(
                    progress = state.today.toFloat() / state.goal.coerceAtLeast(1),
                    color = colors.steps,
                    trackColor = colors.track,
                    strokeWidth = 14.dp,
                    modifier = Modifier.size(156.dp),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(StepStats.grouped(state.today), style = MaterialTheme.typography.headlineMedium, color = colors.ink)
                    Text(
                        "of ${StepStats.grouped(state.goal.toLong())} steps",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.inkMuted,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val miles = state.lengthUnit == LengthUnit.In
                Figure(
                    "Distance",
                    if (miles) "${(state.distanceKm / KM_PER_MILE).format(1)} mi" else "${state.distanceKm.format(1)} km",
                )
                Figure("Burned", "${state.burnedKcal} kcal")
                Figure("Active time", "${state.activeMinutes} min")
            }
        }
        Spacer(Modifier.height(12.dp))
        CardRow(onClick = onSync) {
            Icon(Lucide.Refresh, size = 14.dp, tint = colors.inkMuted, strokeWidth = 2.2f)
            Text(
                syncedLabel(state.syncedAt),
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted,
                modifier = Modifier.weight(1f),
            )
            val left = state.goal - state.today
            Text(
                if (left <= 0) "Goal met" else "${StepStats.grouped(left)} to go",
                style = MaterialTheme.typography.labelMedium,
                color = colors.stepsSoft,
            )
        }
        Text(
            "Distance, calories and active time are estimates from your height and weight.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkFaint,
        )
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = WiggleTheme.colors.inkMuted)
        Text(value, style = MaterialTheme.typography.titleLarge, color = WiggleTheme.colors.ink)
    }
}

private fun syncedLabel(at: Instant?): String {
    if (at == null) return "Not synced yet · tap to sync"
    val minutes = Duration.between(at, Instant.now()).toMinutes()
    return when {
        minutes < 1 -> "Synced from Health Connect · just now"
        minutes < 60 -> "Synced from Health Connect · $minutes min ago"
        else -> "Synced from Health Connect · ${minutes / 60} h ago"
    }
}

@Composable
private fun HoursCard(state: StepsUiState, modifier: Modifier = Modifier) {
    val colors = WiggleTheme.colors
    GlassCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(GlassDefaults.SmallRadius), contentPadding = 16.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Today by hour", style = MaterialTheme.typography.titleMedium, color = colors.ink)
            StepStats.busiestHour(state.hourly)?.let {
                Text("Busiest ${StepStats.hourRange(it)}", style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
            }
        }
        Spacer(Modifier.height(10.dp))
        BarChart(
            values = state.hourly.map { it.toDouble() },
            labels = List(24) { hour ->
                when (hour) {
                    0 -> "12 AM"
                    6 -> "6 AM"
                    12 -> "12 PM"
                    18 -> "6 PM"
                    else -> ""
                }
            },
            color = colors.steps,
            height = 100.dp,
            highlightIndex = java.time.LocalTime.now().hour,
        )
    }
}

@Composable
private fun WeekCard(state: StepsUiState, modifier: Modifier = Modifier) {
    val colors = WiggleTheme.colors
    var selected by remember { mutableIntStateOf(6) }
    val day = state.week.getOrNull(selected)
    GlassCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(GlassDefaults.SmallRadius), contentPadding = 16.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Last 7 days", style = MaterialTheme.typography.titleMedium, color = colors.ink)
            day?.let { (date, steps) ->
                Text(
                    "${if (date == LocalDate.now()) "Today" else date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())} · " +
                        (steps?.let { "${StepStats.grouped(it)} steps" } ?: "no data"),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        BarChart(
            values = state.week.map { it.second?.toDouble() },
            labels = state.week.map { it.first.dayOfWeek.name.take(1) },
            color = colors.steps,
            height = 120.dp,
            highlightIndex = selected,
            goalLine = state.goal.toDouble(),
            onSelect = { selected = it },
        )
    }
}
