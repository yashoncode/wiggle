package io.wiggle.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import dev.chrisbanes.haze.hazeSource
import io.wiggle.ui.motion.LocalReduceMotion
import io.wiggle.ui.theme.LocalHazeState
import io.wiggle.ui.theme.WiggleTheme
import kotlin.math.cos
import kotlin.math.sin

private data class Glow(
    val color: (io.wiggle.ui.theme.WiggleColors) -> Color,
    /** Resting centre as a fraction of the canvas. */
    val cx: Float,
    val cy: Float,
    /** Radius as a fraction of the canvas width. */
    val radius: Float,
    /** Seconds for one full drift loop. */
    val periodSeconds: Int,
    /** Drift amplitude as a fraction of the canvas. */
    val driftX: Float,
    val driftY: Float,
    val phase: Float,
    val alphaScale: Float,
    /** How much this glow lags the scroll, for parallax. */
    val parallax: Float,
)

private val Glows = listOf(
    Glow({ it.glowTeal }, cx = -0.10f, cy = 0.02f, radius = 0.95f, periodSeconds = 21,
        driftX = 0.10f, driftY = 0.07f, phase = 0f, alphaScale = 1.1f, parallax = 0.35f),
    Glow({ it.glowBlue }, cx = 1.05f, cy = 0.44f, radius = 0.85f, periodSeconds = 26,
        driftX = 0.09f, driftY = 0.11f, phase = 2.1f, alphaScale = 1.0f, parallax = 0.20f),
    Glow({ it.glowViolet }, cx = -0.05f, cy = 1.02f, radius = 0.80f, periodSeconds = 18,
        driftX = 0.12f, driftY = 0.06f, phase = 4.2f, alphaScale = 0.70f, parallax = 0.12f),
)

/**
 * The drifting colour field every glass surface blurs against.
 *
 * The glows are radial gradients rather than blurred circles: a real blur of this size costs a
 * full-screen RenderEffect on every frame, and at this softness the two are indistinguishable.
 *
 * [scrollProvider] is read at draw time, not at composition, so scrolling does not recompose
 * the background.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    scrollProvider: () -> Float = { 0f },
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = WiggleTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val hazeState = LocalHazeState.current

    val transition = rememberInfiniteTransition(label = "aurora")
    val drifts = Glows.map { glow ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = if (reduceMotion) 0f else (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(glow.periodSeconds * 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "drift",
        )
    }

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .semantics { hideFromAccessibility() }
                .hazeSource(hazeState)
        ) {
            val w = size.width
            val h = size.height
            val scroll = scrollProvider()

            Glows.forEachIndexed { index, glow ->
                val t = drifts[index].value + glow.phase
                // Breathe: the radius swells and shrinks a tenth on the same loop as the drift.
                val breathe = 1f + 0.10f * sin(t * 0.5f)
                val cx = (glow.cx + glow.driftX * cos(t)) * w
                val cy = (glow.cy + glow.driftY * sin(t * 0.8f)) * h - scroll * glow.parallax
                val radius = glow.radius * w * breathe
                val base = glow.color(colors).copy(alpha = colors.glowAlpha * glow.alphaScale)

                drawCircle(
                    brush = Brush.radialGradient(
                        // A long, soft tail is what sells this as a blurred light rather than a disc.
                        0.00f to base,
                        0.35f to base.copy(alpha = base.alpha * 0.55f),
                        0.65f to base.copy(alpha = base.alpha * 0.18f),
                        1.00f to Color.Transparent,
                        center = Offset(cx, cy),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(cx, cy),
                )
            }

            // A slight vertical darkening keeps text at the bottom of the screen readable
            // once the tab bar sits over it.
            //
            // Drawn over the whole canvas with the fade placed by startY/endY. A gradient brush is
            // laid out in canvas coordinates, not in the coordinates of the rectangle it fills, so
            // giving this a rectangle that starts at 0.6h put the whole fade above the rectangle
            // and painted a hard horizontal seam across the middle of the screen.
            val scrimFrom = h * 0.45f
            val scrim = colors.background.copy(alpha = if (colors.isDark) 0.55f else 0.35f)
            drawRect(
                brush = Brush.verticalGradient(
                    // Eased rather than linear: a straight ramp still shows a faint line where it
                    // leaves transparent, because the eye reads the sudden change in slope.
                    0.00f to Color.Transparent,
                    0.35f to scrim.copy(alpha = scrim.alpha * 0.10f),
                    0.70f to scrim.copy(alpha = scrim.alpha * 0.50f),
                    1.00f to scrim,
                    startY = scrimFrom,
                    endY = h,
                ),
                topLeft = Offset.Zero,
                size = Size(w, h),
            )
        }
        content()
    }
}
