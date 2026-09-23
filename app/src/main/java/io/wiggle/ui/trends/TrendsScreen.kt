package io.wiggle.ui.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wiggle.data.db.WeightEntryEntity
import io.wiggle.domain.WeightUnit
import io.wiggle.domain.format
import io.wiggle.domain.formatSigned
import io.wiggle.ui.charts.BarChart
import io.wiggle.ui.charts.ChartPoint
import io.wiggle.ui.charts.WeightLineChart
import androidx.compose.foundation.clickable
import io.wiggle.ui.components.CardRow
import io.wiggle.ui.components.MinTouch
import io.wiggle.ui.components.MinTouch
import io.wiggle.ui.components.PrimaryButton
import io.wiggle.ui.components.ScreenHeader
import io.wiggle.ui.components.SegmentedControl
import io.wiggle.ui.components.SwipeToDelete
import io.wiggle.ui.glass.GlassCard
import io.wiggle.ui.glass.GlassDefaults
import io.wiggle.ui.glass.cardEntrance
import io.wiggle.ui.glass.glass
import io.wiggle.ui.glass.popOnTap
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.theme.WiggleTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TrendsScreen(
    onEditEntry: (Long) -> Unit,
    onBulkAdd: () -> Unit,
    onLogWeight: () -> Unit,
    viewModel: TrendsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = WiggleTheme.colors
    val unit = state.settings.weightUnit
    var scrubbed by remember { mutableStateOf<ChartPoint?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader(eyebrow = "Analytics", title = "Weight") {
            PrimaryButton(
                text = "Log weight",
                onClick = onLogWeight,
                icon = Lucide.Plus,
                height = MinTouch,
                color = colors.ink,
                contentColor = colors.background,
            )
        }

        SegmentedControl(
            options = TrendRange.entries,
            selected = state.range,
            onSelect = viewModel::setRange,
            label = { it.label },
            modifier = Modifier.fillMaxWidth().cardEntrance(0),
        )

        ChartCard(
            state = state,
            unit = unit,
            scrubbed = scrubbed,
            onScrub = { scrubbed = it },
            modifier = Modifier.cardEntrance(1),
        )

        StatGrid(state, unit, Modifier.cardEntrance(2))

        WeekdayCard(state, unit, Modifier.cardEntrance(3))

        HistoryCard(
            entries = state.history,
            unit = unit,
            onDelete = viewModel::delete,
            onEdit = onEditEntry,
            onBulkAdd = onBulkAdd,
            modifier = Modifier.cardEntrance(4),
        )
    }
}

@Composable
private fun ChartCard(
    state: TrendsUiState,
    unit: WeightUnit,
    scrubbed: ChartPoint?,
    onScrub: (ChartPoint?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WiggleTheme.colors
    GlassCard(modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    if (scrubbed != null) "On ${scrubbed.date.format(DateTimeFormatter.ofPattern("d MMM"))}" else "Average",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    val shown = scrubbed?.value ?: state.averageKg
                    Text(
                        shown?.let { unit.fromKg(it).format() } ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.ink,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        unit.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.inkMuted,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                Text(
                    state.rangeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
            }
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LegendItem("Weight", colors.weight, dashed = false)
                LegendItem("7-day avg", colors.ink.copy(alpha = 0.85f), dashed = true)
                if (state.goalKg != null) LegendItem("Goal", colors.goal, dashed = true)
            }
        }

        Spacer(Modifier.height(10.dp))

        if (state.points.size >= 2) {
            WeightLineChart(
                points = state.points,
                average = state.average,
                goalValue = state.goalKg,
                unitLabel = unit.label,
                onScrub = onScrub,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Box(
                Modifier.fillMaxWidth().height(210.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Not enough readings in this range yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkFaint,
                )
            }
        }

        val projected = state.projectedGoalDate
        val distance = state.distanceToGoalKg
        if (projected != null && distance != null) {
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.goal.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Target, size = 16.dp, tint = colors.goalSoft, strokeWidth = 2f)
                Text(
                    "Goal projected ~${projected.format(DateTimeFormatter.ofPattern("d MMM"))} · " +
                        "${unit.fromKg(kotlin.math.abs(distance)).format()} ${unit.label} to go at this pace",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.goalSoft,
                )
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color, dashed: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dashed) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(3) {
                    Box(Modifier.width(4.dp).height(2.dp).background(color, RoundedCornerShape(1.dp)))
                }
            }
        } else {
            Box(Modifier.width(14.dp).height(3.dp).background(color, RoundedCornerShape(2.dp)))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = WiggleTheme.colors.inkMuted,
        )
    }
}

@Composable
private fun StatGrid(state: TrendsUiState, unit: WeightUnit, modifier: Modifier = Modifier) {
    val colors = WiggleTheme.colors
    val rate = state.weeklyRateKg
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Weekly rate",
                rate?.let { "${unit.fromKg(it).formatSigned()} ${unit.label}" } ?: "—",
                if (rate != null && rate <= 0) colors.weightSoft else colors.goalSoft,
                Modifier.weight(1f),
            )
            StatTile(
                "Total change",
                state.totalChangeKg?.let { "${unit.fromKg(it).formatSigned()} ${unit.label}" } ?: "—",
                colors.ink,
                Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Lowest",
                state.lowestKg?.let { "${unit.fromKg(it).format()} ${unit.label}" } ?: "—",
                colors.ink,
                Modifier.weight(1f),
            )
            StatTile(
                "Highest",
                state.highestKg?.let { "${unit.fromKg(it).format()} ${unit.label}" } ?: "—",
                colors.ink,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val colors = WiggleTheme.colors
    Column(
        modifier
            .popOnTap()
            .glass(shape = RoundedCornerShape(GlassDefaults.TinyRadius), blurRadius = 22.dp, elevation = 10.dp)
            .padding(14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = accent, maxLines = 1)
    }
}

@Composable
private fun WeekdayCard(state: TrendsUiState, unit: WeightUnit, modifier: Modifier = Modifier) {
    val colors = WiggleTheme.colors
    GlassCard(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GlassDefaults.SmallRadius),
        contentPadding = 16.dp,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Weekday pattern", style = MaterialTheme.typography.titleSmall, color = colors.ink)
                Text(
                    "Relative to your average in this range",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
            }
            Row(
                Modifier
                    .background(colors.weight.copy(alpha = 0.16f), RoundedCornerShape(percent = 50))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Flame, size = 13.dp, tint = colors.weightSoft, strokeWidth = 2.4f)
                Text(
                    "${state.streakDays}-day streak",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.weightSoft,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        BarChart(
            values = state.weekdayAverages,
            labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
            color = colors.weight,
            centreOnMean = true,
            valueLabel = { unit.fromKg(it).format() },
            height = 128.dp,
        )
    }
}

@Composable
private fun HistoryCard(
    entries: List<WeightEntryEntity>,
    unit: WeightUnit,
    onDelete: (WeightEntryEntity) -> Unit,
    onEdit: (Long) -> Unit,
    onBulkAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WiggleTheme.colors
    val zone = remember { ZoneId.systemDefault() }
    GlassCard(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GlassDefaults.SmallRadius),
        contentPadding = 16.dp,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("History", style = MaterialTheme.typography.titleSmall, color = colors.ink)
                Text(
                    "Swipe an entry left to delete it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
            }
            Row(
                Modifier
                    .height(MinTouch)
                    .clickable(onClickLabel = "Add past entries", onClick = onBulkAdd),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Calendar, size = 14.dp, tint = colors.weightSoft, strokeWidth = 2.2f)
                Text(
                    "Add past",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.weightSoft,
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        if (entries.isEmpty()) {
            Text(
                "No entries in this range.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkFaint,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }

        entries.take(40).forEachIndexed { index, entry ->
            val previous = entries.getOrNull(index + 1)
            val delta = previous?.let { entry.weightKg - it.weightKg }
            SwipeToDelete(onDelete = { onDelete(entry) }) {
                CardRow(showDivider = index > 0, onClick = { onEdit(entry.id) }) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            Instant.ofEpochMilli(entry.measuredAt).atZone(zone)
                                .format(DateTimeFormatter.ofPattern("EEE d MMM · h:mm a")),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.inkMuted,
                        )
                        entry.note?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.inkFaint, maxLines = 1)
                        }
                    }
                    if (delta != null && kotlin.math.abs(delta) >= 0.05) {
                        Box(
                            Modifier
                                .background(
                                    (if (delta < 0) colors.weight else colors.goal).copy(alpha = 0.18f),
                                    RoundedCornerShape(percent = 50),
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                unit.fromKg(delta).formatSigned(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (delta < 0) colors.weightSoft else colors.goalSoft,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        "${unit.fromKg(entry.weightKg).format()} ${unit.label}",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.ink,
                    )
                }
            }
        }

        if (entries.size > 40) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Showing the 40 most recent of ${entries.size}.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkFaint,
            )
        }
    }
}
