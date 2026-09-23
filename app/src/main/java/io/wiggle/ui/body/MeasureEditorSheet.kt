package io.wiggle.ui.body

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.wiggle.domain.BodyPart
import io.wiggle.domain.LengthUnit
import io.wiggle.domain.MeasureSpec
import io.wiggle.domain.format
import io.wiggle.ui.components.DateTimeChips
import io.wiggle.ui.components.MinTouch
import io.wiggle.ui.components.PrimaryButton
import io.wiggle.ui.components.RepeatingIconButton
import io.wiggle.ui.components.RollingNumber
import io.wiggle.ui.components.RulerWheel
import io.wiggle.ui.glass.GlassSheet
import io.wiggle.ui.glass.glass
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.motion.LocalReduceMotion
import io.wiggle.ui.motion.Motion
import io.wiggle.ui.motion.rememberHaptics
import io.wiggle.ui.theme.WiggleTheme

/**
 * The step-by-step tape editor: one measurement at a time, with the figure showing where the
 * tape goes.
 *
 * Every step can be skipped, because most people do not measure all eight, and a half-filled
 * session is still worth keeping. A ninth, tenth and so on can be added from inside the flow.
 */
@Composable
fun BoxScope.MeasureEditorSheet(
    editor: MeasureEditorState,
    unit: LengthUnit,
    onDismiss: () -> Unit,
    onSetValue: (MeasureSpec, Double) -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onGoToStep: (Int) -> Unit,
    onSave: () -> Unit,
    onMeasuredAt: (java.time.LocalDateTime) -> Unit,
    onStartAddType: () -> Unit,
    onCancelAddType: () -> Unit,
    onNewTypeName: (String) -> Unit,
    onNewTypeAnchor: (BodyPart) -> Unit,
    onConfirmAddType: () -> Unit,
    onDeleteCustomType: (Long) -> Unit,
) {
    val colors = WiggleTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val spec = editor.currentSpec
    val value = spec?.let { editor.values[it.key] ?: it.defaultCm } ?: 0.0
    val skipped = spec != null && spec.key in editor.skipped

    GlassSheet(visible = editor.open, onDismiss = onDismiss, label = "Measure your body") {
        if (spec == null) return@GlassSheet

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.height(MinTouch).clickable(onClickLabel = "Cancel", onClick = onDismiss),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("Cancel", style = MaterialTheme.typography.titleMedium, color = colors.bodySoft)
            }
            Text(
                "Step ${editor.stepIndex + 1} of ${editor.specs.size}",
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
            )
            Box(
                Modifier.height(MinTouch).clickable(onClickLabel = "Skip this one", onClick = onSkip),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("Skip", style = MaterialTheme.typography.titleMedium, color = colors.inkMuted)
            }
        }

        // When this session was taken. A tape session is often written up after the fact, so the
        // date and time are part of the flow rather than buried behind an edit.
        DateTimeChips(
            value = editor.measuredAt,
            onChange = onMeasuredAt,
            modifier = Modifier.fillMaxWidth(),
        )

        StepDots(editor, onGoToStep)

        if (editor.addingType) {
            AddTypeForm(
                name = editor.newTypeName,
                anchor = editor.newTypeAnchor,
                onName = onNewTypeName,
                onAnchor = onNewTypeAnchor,
                onCancel = onCancelAddType,
                onConfirm = onConfirmAddType,
            )
            return@GlassSheet
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BodySilhouette(
                highlighted = spec.anchor,
                accent = colors.body,
                modifier = Modifier.size(width = 116.dp, height = 200.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = spec,
                    transitionSpec = {
                        if (reduceMotion) {
                            fadeIn() togetherWith fadeOut()
                        } else {
                            val forward =
                                editor.specs.indexOf(targetState) >= editor.specs.indexOf(initialState)
                            val sign = if (forward) 1 else -1
                            (
                                slideInHorizontally(Motion.snappy()) { w -> sign * w / 3 } +
                                    fadeIn(Motion.snappy())
                                ) togetherWith (
                                slideOutHorizontally(Motion.snappy()) { w -> -sign * w / 3 } +
                                    fadeOut(Motion.snappy())
                                )
                        }
                    },
                    label = "spec",
                ) { target ->
                    Column {
                        Text(
                            target.label,
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.ink,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            target.hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkMuted,
                        )
                        Spacer(Modifier.height(6.dp))
                        if (target.requiredForBodyFat) {
                            LabelRow(
                                icon = Lucide.Info,
                                text = "Used for the body-fat estimate",
                                tint = colors.goalSoft,
                            )
                        }
                        if (target.isCustom) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                LabelRow(
                                    icon = Lucide.Sparkles,
                                    text = "Your own measurement",
                                    tint = colors.waterSoft,
                                )
                                Box(
                                    Modifier
                                        .height(MinTouch)
                                        .clickable(onClickLabel = "Remove this measurement") {
                                            target.customTypeId?.let(onDeleteCustomType)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "Remove",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.bodySoft,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RepeatingIconButton(
                icon = Lucide.Minus,
                contentDescription = "Decrease",
                size = 52.dp,
                onStep = { onSetValue(spec, unit.toCm(unit.fromCm(value) - 0.1)) },
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingNumber(
                        value = unit.fromCm(value),
                        style = MaterialTheme.typography.displayMedium,
                        color = if (skipped) colors.inkFaint else colors.ink,
                        countUpOnAppear = false,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        unit.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.inkMuted,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
                if (skipped) {
                    Text(
                        "Skipped · move the ruler to include it",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.inkFaint,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            RepeatingIconButton(
                icon = Lucide.Plus,
                contentDescription = "Increase",
                size = 52.dp,
                onStep = { onSetValue(spec, unit.toCm(unit.fromCm(value) + 0.1)) },
            )
        }

        RulerWheel(
            value = unit.fromCm(value),
            onValueChange = { onSetValue(spec, unit.toCm(it)) },
            range = unit.fromCm(spec.range.start)..unit.fromCm(spec.range.endInclusive),
            accent = colors.body,
            height = 72.dp,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (editor.stepIndex > 0) {
                PrimaryButton(
                    text = "Back",
                    onClick = onPrevious,
                    modifier = Modifier.weight(1f),
                    color = colors.glassPressedFill,
                    contentColor = colors.ink,
                )
            }
            PrimaryButton(
                text = if (editor.isLastStep) "Save session" else "Next",
                onClick = {
                    if (editor.isLastStep) {
                        haptics.success()
                        onSave()
                    } else {
                        haptics.tick()
                        onNext()
                    }
                },
                icon = if (editor.isLastStep) Lucide.Check else Lucide.ArrowRight,
                enabled = !editor.saving,
                modifier = Modifier.weight(if (editor.stepIndex > 0) 1.4f else 1f),
                color = colors.body,
                contentColor = colors.background,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(MinTouch)
                .clickable(onClickLabel = "Add your own measurement", onClick = onStartAddType),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.Plus, size = 16.dp, tint = colors.waterSoft, strokeWidth = 2.4f)
            Spacer(Modifier.width(7.dp))
            Text(
                "Add your own measurement",
                style = MaterialTheme.typography.labelLarge,
                color = colors.waterSoft,
            )
        }

        val recorded = editor.specs.count { it.key !in editor.skipped }
        Text(
            "$recorded of ${editor.specs.size} will be saved · " +
                "${unit.fromCm(value).format()} ${unit.label} for ${spec.label}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkFaint,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

/** Tappable step dots, so a correction does not mean walking forward through everything. */
@Composable
private fun StepDots(editor: MeasureEditorState, onGoToStep: (Int) -> Unit) {
    val colors = WiggleTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        editor.specs.forEachIndexed { index, spec ->
            val done = index < editor.stepIndex
            val active = index == editor.stepIndex
            Box(
                Modifier
                    .weight(1f)
                    .height(MinTouch / 4)
                    .clickable(onClickLabel = "Go to ${spec.label}") { onGoToStep(index) }
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        when {
                            active -> colors.body
                            done && spec.key !in editor.skipped -> colors.body.copy(alpha = 0.45f)
                            spec.isCustom -> colors.water.copy(alpha = 0.30f)
                            else -> colors.track
                        }
                    ),
            )
        }
    }
}

/** The inline "add your own" form: a name, and which part of the diagram the tape sits on. */
@Composable
private fun AddTypeForm(
    name: String,
    anchor: BodyPart,
    onName: (String) -> Unit,
    onAnchor: (BodyPart) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = WiggleTheme.colors
    val scrollState = rememberScrollState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Add your own measurement",
            style = MaterialTheme.typography.titleLarge,
            color = colors.ink,
        )
        Text(
            "Track anything the eight built-in ones miss — shoulders, wrist, left calf.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkMuted,
        )

        BasicTextField(
            value = name,
            onValueChange = onName,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.body),
            modifier = Modifier
                .fillMaxWidth()
                .glass(shape = RoundedCornerShape(16.dp), blurRadius = 18.dp, elevation = 4.dp)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            decorationBox = { inner ->
                if (name.isEmpty()) {
                    Text(
                        "Name, e.g. Shoulders",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.inkFaint,
                    )
                }
                inner()
            },
        )

        Text(
            "Where on the diagram?",
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkMuted,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BodyPart.entries.forEach { part ->
                val selected = part == anchor
                Box(
                    Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (selected) colors.body else colors.track)
                        .clickable(onClickLabel = "Anchor at ${part.label}") { onAnchor(part) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        part.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) colors.background else colors.inkMuted,
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BodySilhouette(
                highlighted = anchor,
                accent = colors.body,
                modifier = Modifier.size(width = 96.dp, height = 160.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "The tape band will sit here while you take this measurement.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted,
                modifier = Modifier.weight(1f),
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                color = colors.glassPressedFill,
                contentColor = colors.ink,
            )
            PrimaryButton(
                text = "Add",
                onClick = onConfirm,
                icon = Lucide.Check,
                enabled = name.isNotBlank(),
                modifier = Modifier.weight(1.4f),
                color = colors.body,
                contentColor = colors.background,
            )
        }
    }
}

@Composable
private fun LabelRow(
    icon: io.wiggle.ui.icons.LucideIcon,
    text: String,
    tint: androidx.compose.ui.graphics.Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, size = 13.dp, tint = tint, strokeWidth = 2.2f)
        Text(text, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
