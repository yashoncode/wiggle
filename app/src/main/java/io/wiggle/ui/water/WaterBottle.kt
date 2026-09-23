package io.wiggle.ui.water

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.wiggle.ui.motion.LocalReduceMotion
import io.wiggle.ui.motion.Motion
import io.wiggle.ui.theme.WiggleTheme
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** One bubble, launched when water is added. */
private data class Bubble(
    val x: Float,
    val radius: Float,
    val speed: Float,
    val drift: Float,
    val bornAt: Long,
)

/**
 * The water bottle.
 *
 * The fill level springs to its target; the surface is two sine waves of different wavelength and
 * speed added together, which is what stops it reading as a single sliding ripple; each add
 * releases a handful of bubbles; and the whole surface tilts with the phone's roll.
 */
@Composable
fun WaterBottle(
    progress: Float,
    modifier: Modifier = Modifier,
    /** Changes whenever water is added, which is what launches a new set of bubbles. */
    bubbleKey: Any? = null,
) {
    val colors = WiggleTheme.colors
    val reduceMotion = LocalReduceMotion.current

    val level = remember { Animatable(0f) }
    LaunchedEffect(progress, reduceMotion) {
        val target = progress.coerceIn(0f, 1f)
        if (reduceMotion) level.snapTo(target) else level.animateTo(target, Motion.smooth())
    }

    // One phase drives both waves; the second runs at a different multiple of it.
    val phase by rememberInfiniteWavePhase(reduceMotion)

    val tilt = rememberDeviceRoll(enabled = !reduceMotion)

    val bubbles = remember(bubbleKey) {
        if (bubbleKey == null || reduceMotion) {
            emptyList()
        } else {
            val random = Random(bubbleKey.hashCode())
            List(9) {
                Bubble(
                    x = 0.15f + random.nextFloat() * 0.7f,
                    radius = 1.5f + random.nextFloat() * 4f,
                    speed = 0.55f + random.nextFloat() * 0.7f,
                    drift = (random.nextFloat() - 0.5f) * 0.10f,
                    bornAt = it * 70L,
                )
            }
        }
    }
    val bubbleProgress = remember(bubbleKey) { Animatable(0f) }
    LaunchedEffect(bubbleKey) {
        if (bubbles.isNotEmpty()) {
            bubbleProgress.snapTo(0f)
            bubbleProgress.animateTo(1f, tween(1700, easing = LinearEasing))
        }
    }

    Canvas(
        modifier.semantics {
            contentDescription = "Water bottle, ${(progress * 100).toInt()} percent of today's goal"
        }
    ) {
        val strokeWidth = 3.dp.toPx()
        val bottleWidth = size.width * 0.62f
        val neckWidth = bottleWidth * 0.36f
        val shoulder = size.height * 0.16f
        val left = (size.width - bottleWidth) / 2
        val right = left + bottleWidth
        val top = size.height * 0.02f
        val bottom = size.height - strokeWidth

        // --- the bottle outline, as one path reused for both the clip and the stroke ----------
        val bottle = Path().apply {
            val neckLeft = (size.width - neckWidth) / 2
            val neckRight = neckLeft + neckWidth
            moveTo(neckLeft, top)
            lineTo(neckRight, top)
            lineTo(neckRight, shoulder * 0.55f)
            // Shoulder flare.
            cubicTo(
                neckRight, shoulder * 0.8f,
                right, shoulder * 0.7f,
                right, shoulder + size.height * 0.04f,
            )
            lineTo(right, bottom - size.width * 0.10f)
            quadraticTo(right, bottom, right - size.width * 0.10f, bottom)
            lineTo(left + size.width * 0.10f, bottom)
            quadraticTo(left, bottom, left, bottom - size.width * 0.10f)
            lineTo(left, shoulder + size.height * 0.04f)
            cubicTo(
                left, shoulder * 0.7f,
                neckLeft, shoulder * 0.8f,
                neckLeft, shoulder * 0.55f,
            )
            close()
        }

        // --- the water ------------------------------------------------------------------------
        val fillTop = bottom - (bottom - shoulder * 0.9f) * level.value
        val amplitude = (size.height * 0.014f) * (if (reduceMotion) 0f else 1f)

        clipPath(bottle) {
            if (level.value > 0.001f) {
                val water = Path().apply {
                    moveTo(0f, fillTop)
                    val steps = 36
                    for (i in 0..steps) {
                        val t = i.toFloat() / steps
                        val x = t * size.width
                        // Two waves: a long slow one and a short fast one running the other way.
                        val y = fillTop +
                            sin(t * 2 * PI.toFloat() + phase) * amplitude +
                            sin(t * 3.7f * PI.toFloat() - phase * 1.6f) * amplitude * 0.55f +
                            // The tilt lifts one end of the surface and drops the other.
                            (t - 0.5f) * tilt.floatValue * size.height * 0.10f
                        lineTo(x, y)
                    }
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(
                    water,
                    Brush.verticalGradient(
                        0f to colors.water.copy(alpha = 0.85f),
                        1f to colors.water.copy(alpha = 0.45f),
                        startY = fillTop,
                        endY = size.height,
                    ),
                )
                // A brighter line right on the surface reads as the meniscus.
                drawPath(
                    water,
                    color = Color.White.copy(alpha = 0.35f),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }

            // --- bubbles --------------------------------------------------------------------
            bubbles.forEach { bubble ->
                val t = ((bubbleProgress.value * 1700 - bubble.bornAt) / 1500f).coerceIn(0f, 1f)
                if (t <= 0f || t >= 1f) return@forEach
                val y = size.height - (size.height - fillTop) * t * bubble.speed
                if (y < fillTop) return@forEach
                drawCircle(
                    color = Color.White.copy(alpha = (1f - t) * 0.5f),
                    radius = bubble.radius.dp.toPx() / 2,
                    center = Offset(
                        (bubble.x + sin(t * 8f) * bubble.drift) * size.width,
                        y,
                    ),
                )
            }
        }

        // --- glass -----------------------------------------------------------------------------
        drawPath(bottle, colors.ink.copy(alpha = 0.05f))
        drawPath(
            bottle,
            brush = Brush.verticalGradient(
                0f to colors.ink.copy(alpha = 0.40f),
                1f to colors.ink.copy(alpha = 0.18f),
            ),
            style = Stroke(width = strokeWidth),
        )

        // A vertical highlight down the left of the bottle.
        drawRoundRect(
            brush = Brush.horizontalGradient(
                0f to Color.White.copy(alpha = 0.18f),
                1f to Color.Transparent,
            ),
            topLeft = Offset(left + strokeWidth * 2, shoulder + size.height * 0.08f),
            size = Size(bottleWidth * 0.18f, (bottom - shoulder) * 0.72f),
            cornerRadius = CornerRadius(bottleWidth * 0.09f),
        )

        // --- goal tick marks ---------------------------------------------------------------------
        listOf(0.25f, 0.5f, 0.75f).forEach { mark ->
            val y = bottom - (bottom - shoulder * 0.9f) * mark
            drawLine(
                color = colors.ink.copy(alpha = 0.22f),
                start = Offset(right - bottleWidth * 0.18f, y),
                end = Offset(right - strokeWidth * 2, y),
                strokeWidth = 1.5.dp.toPx(),
            )
        }
    }
}

@Composable
private fun rememberInfiniteWavePhase(reduceMotion: Boolean) =
    androidx.compose.animation.core.rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = if (reduceMotion) 0f else (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )

/**
 * The phone's roll, normalised to roughly -1..1 and smoothed, so the water surface tips the way
 * the phone does. Falls back to level when there is no accelerometer.
 */
@Composable
private fun rememberDeviceRoll(enabled: Boolean): androidx.compose.runtime.MutableFloatState {
    val roll = remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current

    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return@DisposableEffect onDispose { }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // x is the left-right axis; gravity is about 9.81, so a quarter g is a firm tilt.
                val target = (-event.values[0] / 9.81f).coerceIn(-1f, 1f)
                // Low-pass, otherwise the surface jitters with every hand tremor.
                roll.floatValue += (target - roll.floatValue) * 0.08f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }
    return roll
}
