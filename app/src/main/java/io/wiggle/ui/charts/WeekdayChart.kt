package io.wiggle.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.wiggle.domain.format
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.wiggle.ui.motion.LocalReduceMotion
import io.wiggle.ui.motion.Motion
import io.wiggle.ui.theme.Manrope
import io.wiggle.ui.theme.WiggleTheme

/**
 * Bars for a set of labelled buckets: the weekday pattern on Trends and the weekly water chart.
 *
 * Bars are drawn from each bucket's deviation from the mean rather than from zero, because on a
 * weight chart a bar from zero is 99% identical height and tells you nothing.
 */
@Composable
fun BarChart(
    values: List<Double?>,
    labels: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    /** Centre the bars on the mean instead of on zero. */
    centreOnMean: Boolean = false,
    highlightIndex: Int? = null,
    valueLabel: ((Double) -> String)? = null,
) {
    val colors = WiggleTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val measurer = rememberTextMeasurer()
    val grow = remember { Animatable(0f) }

    LaunchedEffect(values, reduceMotion) {
        grow.snapTo(0f)
        if (reduceMotion) grow.snapTo(1f) else grow.animateTo(1f, Motion.bouncy())
    }

    val labelStyle = remember(colors) {
        TextStyle(fontFamily = Manrope, fontSize = 11.sp, color = colors.inkFaint)
    }
    val valueStyle = remember(colors) {
        TextStyle(fontFamily = Manrope, fontSize = 10.sp, color = colors.inkMuted)
    }

    // Spoken as a list of label/value pairs: the same information the bars carry, in words.
    val spoken = remember(values, labels, valueLabel) {
        labels.indices.joinToString(", ") { index ->
            val value = values.getOrNull(index)
            val text = value?.let { valueLabel?.invoke(it) ?: it.format(1) } ?: "no data"
            "${labels[index]} $text"
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = spoken }
    ) {
        val present = values.filterNotNull()
        if (present.isEmpty()) return@Canvas

        val labelBand = 18.dp.toPx()
        val valueBand = if (valueLabel != null) 14.dp.toPx() else 0f
        val plotHeight = size.height - labelBand - valueBand
        val slot = size.width / values.size
        val barWidth = (slot * 0.52f).coerceAtMost(28.dp.toPx())

        val mean = present.average()
        val maxDeviation = present.maxOf { kotlin.math.abs(it - mean) }.takeIf { it > 1e-6 } ?: 1.0
        val maxValue = present.max().takeIf { it > 1e-6 } ?: 1.0

        values.forEachIndexed { index, value ->
            val centreX = slot * index + slot / 2
            if (value == null) {
                // An empty bucket gets a hairline so the week still reads as seven slots.
                drawRoundRect(
                    color = colors.track,
                    topLeft = Offset(centreX - barWidth / 2, valueBand + plotHeight - 3.dp.toPx()),
                    size = Size(barWidth, 3.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            } else {
                val fraction = if (centreOnMean) {
                    // 0.5 is the mean; deviation pushes the bar up or down from there.
                    (0.5 + (value - mean) / maxDeviation * 0.42).toFloat()
                } else {
                    (value / maxValue).toFloat()
                }
                val barHeight = (plotHeight * fraction * grow.value).coerceAtLeast(3.dp.toPx())
                val top = valueBand + plotHeight - barHeight
                val highlighted = index == highlightIndex
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to color.copy(alpha = if (highlighted) 1f else 0.85f),
                        1f to color.copy(alpha = if (highlighted) 0.55f else 0.30f),
                    ),
                    topLeft = Offset(centreX - barWidth / 2, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2.6f),
                )
                if (valueLabel != null) {
                    val layout = measurer.measure(valueLabel(value), valueStyle)
                    drawText(
                        layout,
                        topLeft = Offset(centreX - layout.size.width / 2f, top - valueBand),
                        alpha = grow.value,
                    )
                }
            }

            labels.getOrNull(index)?.let { label ->
                val layout = measurer.measure(label, labelStyle)
                drawText(
                    layout,
                    topLeft = Offset(
                        centreX - layout.size.width / 2f,
                        size.height - labelBand + 2.dp.toPx(),
                    ),
                )
            }
        }
    }
}
