package io.wiggle.ui.log

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wiggle.domain.WeightUnit
import io.wiggle.domain.formatSigned
import io.wiggle.ui.components.GlassButton
import io.wiggle.ui.components.DateTimeChips
import io.wiggle.ui.components.MinTouch
import io.wiggle.ui.components.PrimaryButton
import io.wiggle.ui.components.RepeatingIconButton
import io.wiggle.ui.components.RollingNumber
import io.wiggle.ui.components.RulerWheel
import io.wiggle.ui.components.SegmentedControl
import io.wiggle.ui.glass.GlassSheet
import io.wiggle.ui.glass.glass
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.motion.rememberHaptics
import io.wiggle.ui.theme.WiggleTheme
import java.time.LocalDateTime

/**
 * The log sheet. Three ways to set the same number — steppers, the ruler and whatever the last
 * entry was — because on a scale you are usually a tenth away from yesterday, and occasionally
 * somewhere else entirely.
 */
@Composable
fun BoxScope.LogWeightSheet(
    visible: Boolean,
    entryId: Long? = null,
    onDismiss: () -> Unit,
    onSaved: (lowest: Boolean) -> Unit,
    onAddMeasurements: () -> Unit,
    viewModel: LogWeightViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = WiggleTheme.colors
    val haptics = rememberHaptics()

    LaunchedEffect(visible, entryId) {
        if (visible) viewModel.open(entryId)
    }

    GlassSheet(
        visible = visible,
        onDismiss = onDismiss,
        label = if (entryId == null) "Log weight" else "Edit entry",
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.height(MinTouch).clickable(onClickLabel = "Cancel", onClick = onDismiss),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("Cancel", style = MaterialTheme.typography.titleMedium, color = colors.weightSoft)
            }
            Text(
                if (entryId == null) "Log weight" else "Edit entry",
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
            )
            Spacer(Modifier.width(56.dp))
        }

        // --- when -------------------------------------------------------------------------
        DateTimeChips(
            value = state.at,
            onChange = viewModel::setDateTime,
            modifier = Modifier.fillMaxWidth(),
        )

        // --- the number -------------------------------------------------------------------
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RepeatingIconButton(
                icon = Lucide.Minus,
                contentDescription = "Decrease",
                onStep = { viewModel.nudge(-1) },
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RollingNumber(
                    value = state.unit.fromKg(state.weightKg),
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.ink,
                    countUpOnAppear = false,
                )
                Spacer(Modifier.height(6.dp))
                val previous = state.previousKg
                val delta = if (previous != null) {
                    state.unit.fromKg(state.weightKg) - state.unit.fromKg(previous)
                } else {
                    null
                }
                Text(
                    text = when {
                        delta == null -> "First entry"
                        kotlin.math.abs(delta) < 0.05 -> "Same as last entry"
                        else -> "${delta.formatSigned()} ${state.unit.label} since last entry"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        delta == null -> colors.inkMuted
                        delta > 0.05 -> colors.goalSoft
                        delta < -0.05 -> colors.weightSoft
                        else -> colors.inkMuted
                    },
                )
            }
            RepeatingIconButton(
                icon = Lucide.Plus,
                contentDescription = "Increase",
                onStep = { viewModel.nudge(1) },
            )
        }

        // --- the ruler --------------------------------------------------------------------
        RulerWheel(
            value = state.unit.fromKg(state.weightKg),
            onValueChange = { viewModel.setWeightKg(state.unit.toKg(it)) },
            range = state.unit.fromKg(20.0)..state.unit.fromKg(400.0),
            accent = colors.weight,
        )

        // --- unit -------------------------------------------------------------------------
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SegmentedControl(
                options = WeightUnit.entries,
                selected = state.unit,
                onSelect = viewModel::setUnit,
                label = { it.label },
                modifier = Modifier.width(160.dp),
            )
        }

        // --- note -------------------------------------------------------------------------
        Column {
            Text("Note", style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
            Spacer(Modifier.height(6.dp))
            BasicTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.weight),
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(shape = RoundedCornerShape(16.dp), blurRadius = 18.dp, elevation = 4.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (state.note.isEmpty()) {
                        Text(
                            "After workout, before breakfast…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.inkFaint,
                        )
                    }
                    inner()
                },
            )
        }

        // --- measurements link --------------------------------------------------------------
        Row(
            Modifier
                .fillMaxWidth()
                .height(MinTouch)
                .clickable(onClickLabel = "Add body measurements", onClick = onAddMeasurements),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Ruler, size = 18.dp, tint = colors.bodySoft, strokeWidth = 2f)
                Text(
                    "Add body measurements too",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.ink,
                )
            }
            Icon(Lucide.ChevronRight, size = 16.dp, tint = colors.inkFaint, strokeWidth = 2.4f)
        }

        PrimaryButton(
            text = if (state.saving) "Saving…" else "Save",
            onClick = {
                viewModel.save { lowest ->
                    haptics.success()
                    onSaved(lowest)
                }
            },
            enabled = !state.saving && state.ready,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
