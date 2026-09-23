package io.wiggle.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import io.wiggle.ui.motion.LocalReduceMotion
import io.wiggle.ui.theme.WiggleTheme
import kotlin.math.sin
import kotlin.random.Random

private data class Flake(
    val startX: Float,
    val startY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val spin: Float,
    val size: Float,
    val sway: Float,
    val colorIndex: Int,
)

/**
 * Glass confetti: translucent slivers with a bright edge, thrown from the bottom of the screen
 * and falling back under gravity. Drawn as one Canvas, so a burst is a single extra draw pass.
 *
 * With reduce motion on, nothing is drawn at all: a burst of moving shapes is exactly what that
 * setting exists to suppress.
 */
@Composable
fun GlassConfetti(
    burstKey: Any?,
    modifier: Modifier = Modifier,
    pieces: Int = 44,
    durationMillis: Int = 2200,
) {
    val colors = WiggleTheme.colors
    val reduceMotion = LocalReduceMotion.current
    if (reduceMotion || burstKey == null) return

    val flakes = remember(burstKey) {
        val random = Random(burstKey.hashCode())
        List(pieces) {
            Flake(
                startX = 0.15f + random.nextFloat() * 0.7f,
                startY = 1.02f,
                velocityX = (random.nextFloat() - 0.5f) * 0.9f,
                velocityY = -(1.25f + random.nextFloat() * 0.75f),
                spin = (random.nextFloat() - 0.5f) * 1400f,
                size = 6f + random.nextFloat() * 10f,
                sway = random.nextFloat() * 6.28f,
                colorIndex = random.nextInt(4),
            )
        }
    }

    val progress = remember(burstKey) { Animatable(0f) }
    LaunchedEffect(burstKey) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis))
    }

    val palette = listOf(colors.weight, colors.water, colors.goal, colors.body)

    Canvas(modifier.fillMaxSize().semantics { hideFromAccessibility() }) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        // Fade out over the last third so pieces do not vanish mid-flight.
        val alpha = ((1f - t) * 3f).coerceIn(0f, 1f)

        flakes.forEach { flake ->
            val x = (flake.startX + flake.velocityX * t + sin(flake.sway + t * 6f) * 0.03f) * size.width
            val y = (flake.startY + flake.velocityY * t + 1.35f * t * t) * size.height
            if (y > size.height * 1.1f) return@forEach

            val base = palette[flake.colorIndex]
            rotate(degrees = flake.spin * t, pivot = Offset(x, y)) {
                // The body is barely there; the highlight on one edge is what makes it read as glass.
                drawRoundRect(
                    color = base.copy(alpha = 0.38f * alpha),
                    topLeft = Offset(x - flake.size / 2, y - flake.size / 4),
                    size = Size(flake.size, flake.size / 2),
                    cornerRadius = CornerRadius(flake.size / 5),
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.55f * alpha),
                    topLeft = Offset(x - flake.size / 2, y - flake.size / 4),
                    size = Size(flake.size, flake.size / 9),
                    cornerRadius = CornerRadius(flake.size / 9),
                )
            }
        }
    }
}
