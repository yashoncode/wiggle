package io.wiggle.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.wiggle.ui.motion.Motion
import io.wiggle.ui.motion.rememberHaptics
import io.wiggle.ui.theme.Manrope
import io.wiggle.ui.theme.WiggleTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A horizontal ruler you drag to set a value, snapping to a tenth.
 *
 * Drawn rather than built from a LazyRow: the ticks are hundreds of one-pixel lines, and a list
 * of that many composables to scroll one number is a lot of machinery for a straight line.
 *
 * Haptics: a light tick at every tenth, a firmer one when a whole number passes the centre.
 */
@Composable
fun RulerWheel(
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Double> = 20.0..400.0,
    step: Double = 0.1,
    accent: Color = WiggleTheme.colors.weight,
    height: Dp = 84.dp,
    stepWidth: Dp = 12.dp,
) {
    val colors = WiggleTheme.colors
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()

    val stepPx = with(density) { stepWidth.toPx() }
    // Everything runs in "steps from the range start", so snapping is integer arithmetic.
    fun toSteps(v: Double) = ((v - range.start) / step)
    fun toValue(s: Double) = range.start + s * step

    val position = remember { Animatable(toSteps(value).toFloat()) }
    val lastTickedStep = remember { intArrayOf(toSteps(value).roundToInt()) }
    val dragging = remember { booleanArrayOf(false) }

    // Follow external edits (the −/+ buttons, a unit switch) unless the user is mid-drag.
    LaunchedEffect(value, step, range.start) {
        val target = toSteps(value).toFloat()
        if (dragging[0] || abs(position.value - target) <= 0.001f) return@LaunchedEffect
        // A held −/+ button arrives every 40ms, and a spring restarted that often never settles,
        // so the ruler visibly falls behind the number. Single steps go straight there.
        if (abs(position.value - target) <= 1.5f) position.snapTo(target)
        else position.animateTo(target, Motion.snappy())
        // Without this the ruler would refuse to emit the value it was just moved to.
        lastTickedStep[0] = target.roundToInt()
    }

    val maxStep = toSteps(range.endInclusive).toFloat()

    fun emit(steps: Float) {
        val snapped = steps.roundToInt().coerceIn(0, maxStep.toInt())
        if (snapped != lastTickedStep[0]) {
            val crossedWhole = floor(toValue(snapped.toDouble())) != floor(toValue(lastTickedStep[0].toDouble()))
            if (crossedWhole) haptics.select() else haptics.tick()
            lastTickedStep[0] = snapped
            onValueChange(toValue(snapped.toDouble()))
        }
    }

    val labelStyle = remember(colors) {
        TextStyle(fontFamily = Manrope, fontSize = 12.sp, color = colors.inkMuted)
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                contentDescription = "Weight ruler"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.toFloat(),
                    range = range.start.toFloat()..range.endInclusive.toFloat(),
                )
            }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    scope.launch {
                        val next = (position.value - delta / stepPx).coerceIn(0f, maxStep)
                        position.snapTo(next)
                        emit(next)
                    }
                },
                onDragStarted = { dragging[0] = true },
                onDragStopped = { velocity ->
                    // Let the flick carry on, then settle on the nearest tenth.
                    position.animateDecay(
                        initialVelocity = -velocity / stepPx,
                        animationSpec = exponentialDecay(frictionMultiplier = 1.6f),
                    ) {
                        emit(this.value.coerceIn(0f, maxStep))
                    }
                    val settled = position.value.roundToInt().coerceIn(0, maxStep.toInt())
                    position.animateTo(settled.toFloat(), Motion.snappy())
                    emit(settled.toFloat())
                    dragging[0] = false
                },
            )
    ) {
        val centre = size.width / 2
        val current = position.value

        val firstVisible = floor(current - centre / stepPx).toInt().coerceAtLeast(0)
        val lastVisible = ceil(current + centre / stepPx).toInt().coerceAtMost(maxStep.toInt())

        for (s in firstVisible..lastVisible) {
            val x = centre + (s - current) * stepPx
            val v = toValue(s.toDouble())
            // Rounded because 0.1 steps accumulate floating point dust.
            val tenths = Math.round(v * 10)
            val isWhole = tenths % 10 == 0L
            val isHalf = tenths % 5 == 0L

            val tickHeight = when {
                isWhole -> height.toPx() * 0.42f
                isHalf -> height.toPx() * 0.28f
                else -> height.toPx() * 0.18f
            }
            // Fade the ends so the ruler looks like it continues past the card.
            val distance = abs(x - centre) / centre
            val alpha = (1f - distance * distance).coerceIn(0.08f, 1f)

            drawLine(
                color = colors.ink.copy(alpha = alpha * if (isWhole) 0.75f else 0.35f),
                start = Offset(x, height.toPx() * 0.12f),
                end = Offset(x, height.toPx() * 0.12f + tickHeight),
                strokeWidth = if (isWhole) 2f else 1f,
                cap = StrokeCap.Round,
            )

            // Label every whole number. Every fifth is too sparse: the visible window is only a
            // few units wide, so a fifth-only rule leaves the ruler with no numbers at all.
            if (isWhole) {
                val layout = measurer.measure(v.roundToInt().toString(), labelStyle)
                drawText(
                    layout,
                    topLeft = Offset(x - layout.size.width / 2f, height.toPx() * 0.66f),
                    alpha = alpha,
                )
            }
        }

        // Centre indicator.
        drawLine(
            brush = Brush.verticalGradient(
                0f to accent,
                1f to accent.copy(alpha = 0.25f),
            ),
            start = Offset(centre, height.toPx() * 0.06f),
            end = Offset(centre, height.toPx() * 0.62f),
            strokeWidth = 3f,
            cap = StrokeCap.Round,
        )
    }
}
