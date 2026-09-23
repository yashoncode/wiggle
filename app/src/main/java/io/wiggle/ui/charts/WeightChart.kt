package io.wiggle.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.wiggle.ui.motion.LocalReduceMotion
import io.wiggle.ui.motion.Motion
import io.wiggle.domain.format
import io.wiggle.ui.motion.rememberHaptics
import io.wiggle.ui.theme.Manrope
import io.wiggle.ui.theme.WiggleTheme
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

data class ChartPoint(val date: LocalDate, val value: Double)

/** Resolution the series is resampled to so a range change can morph point-for-point. */
private const val MorphSamples = 80

/**
 * The weight chart: the raw line, its seven-day average, a goal line, an area gradient, a pulsing
 * latest point and press-and-drag scrubbing.
 *
 * Changing the range morphs rather than cuts. Both the old and the new series are resampled onto
 * the same fixed grid, so the line can be interpolated between them even though the two ranges
 * have different numbers of readings and different dates.
 */
@Composable
fun WeightLineChart(
    points: List<ChartPoint>,
    average: List<ChartPoint>,
    goalValue: Double?,
    unitLabel: String,
    modifier: Modifier = Modifier,
    onScrub: (ChartPoint?) -> Unit = {},
) {
    // A Canvas has no semantics of its own, so a screen reader would skip the chart entirely.
    // The summary is what a sighted user takes from a glance: the span and where it ended up.
    val spoken = remember(points, unitLabel) {
        if (points.isEmpty()) "Weight chart, no readings yet"
        else "Weight chart, ${points.size} readings, " +
            "from ${points.first().value.format(1)} to ${points.last().value.format(1)} $unitLabel"
    }
    val colors = WiggleTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    var scrubIndex by remember { mutableStateOf<Int?>(null) }

    // --- morph state ------------------------------------------------------------------------
    val target = remember(points) { resample(points.map { it.value }, MorphSamples) }
    val targetAverage = remember(average) { resample(average.map { it.value }, MorphSamples) }
    val morph = remember { Animatable(0f) }
    var previous by remember { mutableStateOf(target) }
    var previousAverage by remember { mutableStateOf(targetAverage) }
    var current by remember { mutableStateOf(target) }
    var currentAverage by remember { mutableStateOf(targetAverage) }

    LaunchedEffect(target, reduceMotion) {
        previous = current
        previousAverage = currentAverage
        current = target
        currentAverage = targetAverage
        morph.snapTo(0f)
        if (reduceMotion) morph.snapTo(1f) else morph.animateTo(1f, tween(520))
    }

    // --- draw-in and pulse --------------------------------------------------------------------
    val drawIn = remember { Animatable(0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) drawIn.snapTo(1f) else drawIn.animateTo(1f, tween(Motion.ChartDrawMillis))
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = if (reduceMotion) 0f else 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
        label = "pulse",
    )

    val axisStyle = remember(colors) {
        TextStyle(fontFamily = Manrope, fontSize = 11.sp, color = colors.inkFaint)
    }

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(210.dp)
                .semantics { contentDescription = spoken }
                .pointerInput(points) {
                    if (points.size < 2) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val plotLeft = AxisGutterPx(density.density)
                        fun indexAt(x: Float): Int {
                            val width = size.width - plotLeft
                            val fraction = ((x - plotLeft) / width).coerceIn(0f, 1f)
                            return (fraction * (points.size - 1)).roundToInt()
                        }
                        var last = indexAt(down.position.x)
                        scrubIndex = last
                        onScrub(points[last])
                        haptics.select()
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val index = indexAt(change.position.x)
                            if (index != last) {
                                last = index
                                scrubIndex = index
                                onScrub(points[index])
                                haptics.tick()
                            }
                            change.consume()
                        }
                        scrubIndex = null
                        onScrub(null)
                    }
                }
        ) {
            if (current.size < 2) return@Canvas

            val gutter = AxisGutterPx(density.density)
            val bottomGutter = 22.dp.toPx()
            val plotWidth = size.width - gutter
            val plotHeight = size.height - bottomGutter
            val inset = 6.dp.toPx()

            val blended = lerpSeries(previous, current, morph.value)
            val blendedAverage = lerpSeries(previousAverage, currentAverage, morph.value)

            // The goal line shares the value scale, so it has to be inside the bounds.
            val values = blended + blendedAverage + listOfNotNull(goalValue)
            var min = values.min()
            var max = values.max()
            val pad = ((max - min) * 0.12).coerceAtLeast(0.25)
            min -= pad
            max += pad
            val span = (max - min).takeIf { it > 1e-9 } ?: 1.0

            fun yFor(value: Double) =
                inset + ((max - value) / span).toFloat() * (plotHeight - inset * 2)

            fun xFor(fraction: Float) = gutter + fraction * plotWidth

            // --- grid and value labels ---------------------------------------------------
            val ticks = niceTicks(min, max, 3)
            ticks.forEach { tick ->
                val y = yFor(tick)
                drawLine(
                    color = colors.divider,
                    start = Offset(gutter, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
                drawAxisLabel(measurer, tick.roundToInt().toString(), axisStyle, 0f, y - 7.dp.toPx())
            }

            // --- date labels --------------------------------------------------------------
            if (points.size >= 2) {
                val labels = dateLabels(points)
                labels.forEach { (fraction, text) ->
                    val layout = measurer.measure(text, axisStyle)
                    val x = (xFor(fraction) - layout.size.width / 2f)
                        .coerceIn(gutter, size.width - layout.size.width)
                    drawText(layout, topLeft = Offset(x, size.height - bottomGutter + 4.dp.toPx()))
                }
            }

            // --- goal line -----------------------------------------------------------------
            if (goalValue != null) {
                val y = yFor(goalValue)
                drawLine(
                    color = colors.goal,
                    start = Offset(gutter, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f)),
                )
            }

            // --- the line itself -------------------------------------------------------------
            val linePoints = blended.mapIndexed { index, value ->
                Offset(xFor(index.toFloat() / (blended.size - 1)), yFor(value))
            }
            val line = smoothPath(linePoints)

            val area = Path().apply {
                addPath(line)
                lineTo(linePoints.last().x, plotHeight)
                lineTo(linePoints.first().x, plotHeight)
                close()
            }
            drawPath(
                area,
                Brush.verticalGradient(
                    0f to colors.weight.copy(alpha = 0.26f),
                    1f to colors.weight.copy(alpha = 0f),
                    endY = plotHeight,
                ),
                alpha = drawIn.value,
            )

            drawPath(
                path = line.trimmedTo(drawIn.value),
                color = colors.weight,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            // --- seven-day average -----------------------------------------------------------
            if (blendedAverage.size >= 2) {
                val avgPoints = blendedAverage.mapIndexed { index, value ->
                    Offset(xFor(index.toFloat() / (blendedAverage.size - 1)), yFor(value))
                }
                drawPath(
                    path = smoothPath(avgPoints).trimmedTo(drawIn.value),
                    color = colors.ink.copy(alpha = 0.80f),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                    ),
                )
            }

            // --- latest point, pulsing ---------------------------------------------------------
            if (drawIn.value > 0.98f) {
                val last = linePoints.last()
                if (!reduceMotion) {
                    drawCircle(
                        color = colors.weight.copy(alpha = (1f - pulse) * 0.45f),
                        radius = 5.dp.toPx() + pulse * 12.dp.toPx(),
                        center = last,
                    )
                }
                drawCircle(colors.background, radius = 5.dp.toPx(), center = last)
                drawCircle(
                    colors.weight,
                    radius = 5.dp.toPx(),
                    center = last,
                    style = Stroke(width = 2.5.dp.toPx()),
                )
            }

            // --- scrub line ------------------------------------------------------------------
            scrubIndex?.let { index ->
                val fraction = index.toFloat() / (points.size - 1)
                val x = xFor(fraction)
                val y = yFor(points[index].value)
                drawLine(
                    color = colors.ink.copy(alpha = 0.35f),
                    start = Offset(x, 0f),
                    end = Offset(x, plotHeight),
                    strokeWidth = 1.dp.toPx(),
                )
                drawCircle(colors.background, radius = 6.dp.toPx(), center = Offset(x, y))
                drawCircle(colors.weight, radius = 6.dp.toPx(), center = Offset(x, y), style = Stroke(3.dp.toPx()))
            }
        }
    }
}

@Suppress("FunctionName")
private fun AxisGutterPx(density: Float) = 30f * density

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAxisLabel(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    x: Float,
    y: Float,
) {
    drawText(measurer.measure(text, style), topLeft = Offset(x, y))
}

/**
 * Resamples a series onto [count] evenly spaced slots by linear interpolation, so two series of
 * different lengths can be blended index by index.
 */
internal fun resample(values: List<Double>, count: Int): List<Double> {
    if (values.isEmpty()) return List(count) { 0.0 }
    if (values.size == 1) return List(count) { values[0] }
    return List(count) { index ->
        val position = index.toDouble() / (count - 1) * (values.size - 1)
        val low = position.toInt()
        val high = (low + 1).coerceAtMost(values.size - 1)
        val t = position - low
        values[low] * (1 - t) + values[high] * t
    }
}

internal fun lerpSeries(from: List<Double>, to: List<Double>, t: Float): List<Double> {
    if (t >= 1f || from.size != to.size) return to
    return to.mapIndexed { index, value -> from[index] + (value - from[index]) * t }
}

/** Three or four round numbers spanning the range, for the value axis. */
internal fun niceTicks(min: Double, max: Double, count: Int): List<Double> {
    val span = max - min
    if (span <= 0) return listOf(min)
    val rawStep = span / count
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(rawStep)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0)
        .map { it * magnitude }
        .firstOrNull { it >= rawStep } ?: (10 * magnitude)
    val first = Math.ceil(min / step) * step
    return generateSequence(first) { it + step }.takeWhile { it <= max }.toList()
}

/** Evenly spaced date ticks: first, middle and last, unless the range is too short to need three. */
internal fun dateLabels(points: List<ChartPoint>): List<Pair<Float, String>> {
    if (points.size < 2) return emptyList()
    val span = points.first().date.until(points.last().date).toTotalMonths()
    val pattern = if (span >= 2) "MMM" else "d MMM"
    val formatter = java.time.format.DateTimeFormatter.ofPattern(pattern)
    val slots = if (points.size < 8) 2 else 4
    return (0 until slots).map { index ->
        val fraction = index.toFloat() / (slots - 1)
        val point = points[(fraction * (points.size - 1)).roundToInt()]
        fraction to point.date.format(formatter)
    }.distinctBy { it.second }
}

/** True when two doubles are close enough that the chart should treat them as one value. */
internal fun nearlyEqual(a: Double, b: Double) = abs(a - b) < 1e-9
