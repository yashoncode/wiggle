package io.wiggle.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.wiggle.ui.motion.LocalReduceMotion
import io.wiggle.ui.motion.Motion

/** Flat progress bar with a rounded track, used for goal progress. */
@Composable
fun ProgressBar(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    val reduceMotion = LocalReduceMotion.current
    val animated = remember { Animatable(0f) }
    LaunchedEffect(progress, reduceMotion) {
        if (reduceMotion) animated.snapTo(progress)
        else animated.animateTo(progress.coerceIn(0f, 1f), Motion.smooth())
    }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(trackColor, cornerRadius = radius)
        if (animated.value > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    0f to color.copy(alpha = 0.75f),
                    1f to color,
                ),
                size = Size(size.width * animated.value.coerceIn(0f, 1f), size.height),
                cornerRadius = radius,
            )
        }
    }
}

/** The ring on the water card: a stroked arc that springs to its new value. */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 7.dp,
) {
    val reduceMotion = LocalReduceMotion.current
    val animated = remember { Animatable(0f) }
    LaunchedEffect(progress, reduceMotion) {
        if (reduceMotion) animated.snapTo(progress)
        else animated.animateTo(progress.coerceIn(0f, 1.5f), Motion.smooth())
    }
    Canvas(modifier) {
        val stroke = strokeWidth.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = Stroke(width = stroke),
        )
        if (animated.value > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated.value.coerceAtMost(1f),
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        // Past the goal, a second lighter sweep shows the overflow without breaking the ring.
        if (animated.value > 1f) {
            val innerDiameter = diameter - stroke * 1.6f
            drawArc(
                color = color.copy(alpha = 0.45f),
                startAngle = -90f,
                sweepAngle = 360f * (animated.value - 1f).coerceAtMost(1f),
                useCenter = false,
                topLeft = Offset(
                    (size.width - innerDiameter) / 2,
                    (size.height - innerDiameter) / 2,
                ),
                size = Size(innerDiameter, innerDiameter),
                style = Stroke(width = stroke * 0.55f, cap = StrokeCap.Round),
            )
        }
    }
}
