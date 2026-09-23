package io.wiggle.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.wiggle.ui.motion.LocalReduceMotion
import io.wiggle.ui.motion.Motion

/**
 * A bare trend line with no axes: the 30-day line on the hero card and the one on each body-part
 * card. It draws itself in once, then stays put.
 */
@Composable
fun Sparkline(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.5.dp,
    showLatestDot: Boolean = true,
    fillArea: Boolean = false,
) {
    val reduceMotion = LocalReduceMotion.current
    val progress = remember { Animatable(0f) }

    LaunchedEffect(values, reduceMotion) {
        progress.snapTo(0f)
        if (reduceMotion) progress.snapTo(1f)
        else progress.animateTo(1f, tween(Motion.ChartDrawMillis))
    }

    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val stroke = strokeWidth.toPx()
        val dotRadius = stroke * 1.7f
        val inset = if (showLatestDot) dotRadius else stroke / 2
        val points = normalize(values, size.width - inset * 2, size.height - inset * 2, inset)
        val line = smoothPath(points)

        if (fillArea) {
            val area = Path().apply {
                addPath(line)
                lineTo(points.last().x, size.height)
                lineTo(points.first().x, size.height)
                close()
            }
            drawPath(
                area,
                Brush.verticalGradient(
                    0f to color.copy(alpha = 0.28f),
                    1f to color.copy(alpha = 0f),
                ),
                alpha = progress.value,
            )
        }

        drawPath(
            path = line.trimmedTo(progress.value),
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        if (showLatestDot && progress.value > 0.98f) {
            drawCircle(color, radius = dotRadius, center = points.last())
        }
    }
}

/** Maps values onto the canvas, largest value at the top. */
internal fun DrawScope.normalize(
    values: List<Double>,
    width: Float,
    height: Float,
    inset: Float,
): List<Offset> {
    val min = values.min()
    val max = values.max()
    val span = (max - min).takeIf { it > 1e-9 } ?: 1.0
    val stepX = if (values.size > 1) width / (values.size - 1) else 0f
    return values.mapIndexed { index, value ->
        Offset(
            x = inset + stepX * index,
            y = inset + (1.0 - (value - min) / span).toFloat() * height,
        )
    }
}

/**
 * A Catmull-Rom style smoothing: each segment gets control points derived from its neighbours,
 * which removes the corner at every data point without letting the curve overshoot the data.
 */
internal fun smoothPath(points: List<Offset>, tension: Float = 0.22f): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    for (i in 0 until points.size - 1) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
        path.cubicTo(
            p1.x + (p2.x - p0.x) * tension,
            p1.y + (p2.y - p0.y) * tension,
            p2.x - (p3.x - p1.x) * tension,
            p2.y - (p3.y - p1.y) * tension,
            p2.x,
            p2.y,
        )
    }
    return path
}

/** Returns the first [fraction] of a path, for the draw-in animation. */
internal fun Path.trimmedTo(fraction: Float): Path {
    if (fraction >= 1f) return this
    if (fraction <= 0f) return Path()
    val measure = PathMeasure().apply { setPath(this@trimmedTo, false) }
    val destination = Path()
    measure.getSegment(0f, measure.length * fraction, destination, true)
    return destination
}
