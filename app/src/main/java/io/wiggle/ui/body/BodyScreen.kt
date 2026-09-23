package io.wiggle.ui.body

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wiggle.domain.LengthUnit
import io.wiggle.domain.format
import io.wiggle.domain.formatSigned
import io.wiggle.ui.charts.Sparkline
import io.wiggle.ui.components.AccentPill
import io.wiggle.ui.components.MinTouch
import io.wiggle.ui.components.PrimaryButton
import io.wiggle.ui.components.ScreenHeader
import io.wiggle.ui.glass.GlassCard
import io.wiggle.ui.glass.GlassDefaults
import io.wiggle.ui.glass.cardEntrance
import io.wiggle.ui.glass.glass
import io.wiggle.ui.glass.popOnTap
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.theme.WiggleTheme
import java.time.format.DateTimeFormatter

@Composable
fun BodyScreen(viewModel: BodyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = WiggleTheme.colors
    val unit = state.settings.lengthUnit

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader(
            eyebrow = state.lastMeasuredOn
                ?.let { "Last measured ${it.format(DateTimeFormatter.ofPattern("d MMM"))}" }
                ?: "No measurements yet",
            title = "Body",
        ) {
            PrimaryButton(
                text = "Measure",
                onClick = viewModel::openEditor,
                icon = Lucide.Plus,
                height = MinTouch,
                color = colors.ink,
                contentColor = colors.background,
            )
        }

        CompositionCard(state, Modifier.cardEntrance(0))

        // Two columns of part cards. A plain grid inside a scrolling column would nest scrolls,
        // so the rows are built by hand.
        val measured = state.parts.filter { it.latestCm != null }
        val shown = if (measured.isEmpty()) state.parts else measured
        shown.chunked(2).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { summary ->
                    PartCard(
                        summary = summary,
                        unit = unit,
                        modifier = Modifier.weight(1f).cardEntrance(rowIndex + 1),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CompositionCard(state: BodyUiState, modifier: Modifier = Modifier) {
    val colors = WiggleTheme.colors
    GlassCard(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GlassDefaults.SmallRadius),
        contentPadding = 16.dp,
    ) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Body fat (est.)", style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
                Text(
                    state.bodyFatPercent?.let { "${it.format()}%" } ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.ink,
                )
                val change = state.bodyFatChange
                Text(
                    when {
                        change == null -> "Needs neck and waist"
                        kotlin.math.abs(change) < 0.05 -> "No change in 30 days"
                        else -> "${change.formatSigned()}% in 30 days"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        change == null -> colors.inkMuted
                        change < 0 -> colors.weightSoft
                        else -> colors.goalSoft
                    },
                )
            }
            Box(
                Modifier
                    .width(1.dp)
                    .height(56.dp)
                    .background(colors.divider),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Waist-to-hip", style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
                Text(
                    state.waistToHip?.format(2) ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.ink,
                )
                Text(
                    state.waistToHipRisk ?: "Needs waist and hips",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.inkMuted,
                )
            }
        }
    }
}

@Composable
private fun PartCard(
    summary: PartSummary,
    unit: LengthUnit,
    modifier: Modifier = Modifier,
) {
    val colors = WiggleTheme.colors
    val change = summary.changeCm
    Column(
        modifier
            .popOnTap()
            .glass(shape = RoundedCornerShape(GlassDefaults.TinyRadius), blurRadius = 22.dp, elevation = 10.dp)
            .padding(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (summary.spec.isCustom) {
                    Icon(Lucide.Sparkles, size = 12.dp, tint = colors.waterSoft, strokeWidth = 2.2f)
                }
                Text(
                    summary.spec.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkMuted,
                    maxLines = 1,
                )
            }
            AccentPill(
                text = when {
                    change == null -> "—"
                    kotlin.math.abs(change) < 0.05 -> "0.0"
                    else -> unit.fromCm(change).formatSigned()
                },
                accent = when {
                    change == null || kotlin.math.abs(change) < 0.05 -> colors.inkMuted
                    change < 0 -> colors.weightSoft
                    else -> colors.goalSoft
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                summary.latestCm?.let { unit.fromCm(it).format() } ?: "—",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.ink,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                unit.label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        if (summary.history.size >= 2) {
            Sparkline(
                values = summary.history,
                color = colors.body,
                strokeWidth = 2.dp,
                showLatestDot = false,
                modifier = Modifier.fillMaxWidth().height(26.dp),
            )
        } else {
            Spacer(Modifier.height(26.dp))
        }
    }
}
