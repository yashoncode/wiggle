package io.wiggle.ui.body

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.wiggle.domain.BodyPart
import io.wiggle.ui.motion.LocalReduceMotion
import io.wiggle.ui.motion.Motion
import io.wiggle.ui.theme.WiggleTheme

/**
 * A front-facing figure on a 100x220 grid with a tape band on the part being measured.
 *
 * The figure is filled rather than outlined, and the limbs are drawn as separate round-capped
 * strokes rather than as one silhouette, so an arm band visibly wraps an arm instead of crossing
 * the whole body.
 */
private const val GridWidth = 100f
private const val GridHeight = 220f

// Joint positions on the grid. Everything else is derived from these, so moving a joint moves
// the limb and its band together.
private const val HeadCx = 50f
private const val HeadCy = 15f
private const val HeadR = 10f
private const val NeckTop = 24f
private const val NeckBottom = 33f
private const val ShoulderY = 36f
private const val ShoulderHalf = 17f
private const val ChestY = 50f
private const val WaistY = 76f
private const val WaistHalf = 12f
private const val HipY = 100f
private const val HipHalf = 15f

private const val ArmTopY = 40f
private const val ElbowY = 74f
private const val WristY = 104f
private const val ArmOuterX = 21f
private const val ElbowX = 18f
private const val WristX = 16f

private const val KneeY = 152f
private const val AnkleY = 204f
private const val ThighX = 40f
private const val KneeX = 38f
private const val AnkleX = 37f

/** A tape band: where its centre sits and how far it reaches either side. */
private data class Band(val cx: Float, val cy: Float, val halfWidth: Float)

/**
 * Bands sit on the limb they measure. A bicep band is small and sits on one arm; a chest band
 * spans the torso. Getting this wrong is what made the first version read as a barcode.
 */
private fun bandFor(part: BodyPart): Band = when (part) {
    BodyPart.Neck -> Band(cx = HeadCx, cy = 29f, halfWidth = 8.5f)
    BodyPart.Chest -> Band(cx = HeadCx, cy = ChestY, halfWidth = 19f)
    BodyPart.Waist -> Band(cx = HeadCx, cy = WaistY, halfWidth = 14f)
    BodyPart.Hips -> Band(cx = HeadCx, cy = HipY + 3f, halfWidth = 17f)
    // Upper arm: on the figure's right arm as the viewer sees it, halfway to the elbow.
    BodyPart.Arm -> Band(cx = 22.5f, cy = 57f, halfWidth = 7f)
    BodyPart.Forearm -> Band(cx = 17.5f, cy = 88f, halfWidth = 6f)
    BodyPart.Thigh -> Band(cx = 39.5f, cy = 124f, halfWidth = 9.5f)
    BodyPart.Calf -> Band(cx = 37.5f, cy = 172f, halfWidth = 8f)
}

@Composable
fun BodySilhouette(
    highlighted: BodyPart,
    modifier: Modifier = Modifier,
    accent: Color = WiggleTheme.colors.body,
) {
    val colors = WiggleTheme.colors
    val reduceMotion = LocalReduceMotion.current

    val target = bandFor(highlighted)
    val bandX = remember { Animatable(target.cx) }
    val bandY = remember { Animatable(target.cy) }
    val bandHalfWidth = remember { Animatable(target.halfWidth) }

    LaunchedEffect(highlighted, reduceMotion) {
        if (reduceMotion) bandX.snapTo(target.cx) else bandX.animateTo(target.cx, Motion.bouncy())
    }
    LaunchedEffect(highlighted, reduceMotion) {
        if (reduceMotion) bandY.snapTo(target.cy) else bandY.animateTo(target.cy, Motion.bouncy())
    }
    LaunchedEffect(highlighted, reduceMotion) {
        if (reduceMotion) bandHalfWidth.snapTo(target.halfWidth)
        else bandHalfWidth.animateTo(target.halfWidth, Motion.bouncy())
    }

    val pulse by rememberInfiniteTransition(label = "band").animateFloat(
        initialValue = 0.6f,
        targetValue = if (reduceMotion) 0.6f else 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "bandPulse",
    )

    val torso = remember { torsoPath() }

    Canvas(
        modifier.semantics {
            contentDescription = "Body diagram, ${highlighted.label} highlighted"
        }
    ) {
        val unit = minOf(size.width / GridWidth, size.height / GridHeight)
        val originX = (size.width - GridWidth * unit) / 2
        val originY = (size.height - GridHeight * unit) / 2
        val figure = colors.ink.copy(alpha = 0.26f)

        translate(originX, originY) {
            scale(unit, pivot = Offset.Zero) {
                drawFigure(torso, figure)

                val cx = bandX.value
                val cy = bandY.value
                val half = bandHalfWidth.value
                val bandHeight = 6f

                // A soft glow behind the band so it lifts off the figure.
                drawRoundRect(
                    brush = Brush.radialGradient(
                        0f to accent.copy(alpha = 0.35f * pulse),
                        1f to Color.Transparent,
                        center = Offset(cx, cy),
                        radius = half * 2.2f,
                    ),
                    topLeft = Offset(cx - half * 2.2f, cy - half * 1.4f),
                    size = Size(half * 4.4f, half * 2.8f),
                    cornerRadius = CornerRadius(half),
                )

                // The tape itself.
                drawRoundRect(
                    color = accent.copy(alpha = 0.22f + 0.25f * pulse),
                    topLeft = Offset(cx - half, cy - bandHeight / 2),
                    size = Size(half * 2, bandHeight),
                    cornerRadius = CornerRadius(bandHeight / 2),
                )
                drawLine(
                    color = accent,
                    start = Offset(cx - half, cy),
                    end = Offset(cx + half, cy),
                    strokeWidth = 1.8f,
                    cap = StrokeCap.Round,
                )
                // End stops, so it reads as a tape rather than a stripe.
                listOf(-half, half).forEach { dx ->
                    drawLine(
                        color = accent,
                        start = Offset(cx + dx, cy - bandHeight * 0.85f),
                        end = Offset(cx + dx, cy + bandHeight * 0.85f),
                        strokeWidth = 1.8f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/** Head, neck and trunk as one filled shape. */
private fun torsoPath(): Path = Path().apply {
    // Neck.
    moveTo(HeadCx - 5f, NeckTop)
    lineTo(HeadCx + 5f, NeckTop)
    lineTo(HeadCx + 5f, NeckBottom)
    // Right shoulder, chest, waist, hip.
    cubicTo(
        HeadCx + 11f, NeckBottom + 1f,
        HeadCx + ShoulderHalf, ShoulderY - 1f,
        HeadCx + ShoulderHalf, ShoulderY + 4f,
    )
    cubicTo(
        HeadCx + ShoulderHalf, ChestY + 6f,
        HeadCx + WaistHalf + 1f, WaistY - 8f,
        HeadCx + WaistHalf, WaistY,
    )
    cubicTo(
        HeadCx + WaistHalf - 1f, WaistY + 10f,
        HeadCx + HipHalf, HipY - 10f,
        HeadCx + HipHalf, HipY,
    )
    lineTo(HeadCx + 3f, HipY + 6f)
    lineTo(HeadCx - 3f, HipY + 6f)
    lineTo(HeadCx - HipHalf, HipY)
    // Left side, mirrored.
    cubicTo(
        HeadCx - HipHalf, HipY - 10f,
        HeadCx - WaistHalf + 1f, WaistY + 10f,
        HeadCx - WaistHalf, WaistY,
    )
    cubicTo(
        HeadCx - WaistHalf - 1f, WaistY - 8f,
        HeadCx - ShoulderHalf, ChestY + 6f,
        HeadCx - ShoulderHalf, ShoulderY + 4f,
    )
    cubicTo(
        HeadCx - ShoulderHalf, ShoulderY - 1f,
        HeadCx - 11f, NeckBottom + 1f,
        HeadCx - 5f, NeckBottom,
    )
    close()
}

/**
 * Limbs are round-capped strokes rather than outlines: at this size a stroke of the right width
 * reads as a limb, and it keeps each one a separate shape the band can sit on.
 */
private fun DrawScope.drawFigure(torso: Path, color: Color) {
    drawCircle(color, radius = HeadR, center = Offset(HeadCx, HeadCy))
    drawPath(torso, color)

    val armStroke = Stroke(width = 8.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val legStroke = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    listOf(-1f, 1f).forEach { side ->
        // Upper arm, then forearm, as one two-segment path with a bend at the elbow.
        val arm = Path().apply {
            moveTo(HeadCx + side * (ShoulderHalf - 3f), ArmTopY)
            lineTo(HeadCx + side * (HeadCx - ArmOuterX - 4f), ChestY + 8f)
            lineTo(HeadCx + side * (HeadCx - ElbowX - 4f), ElbowY)
            lineTo(HeadCx + side * (HeadCx - WristX - 4f), WristY)
        }
        drawPath(arm, color, style = armStroke)

        val leg = Path().apply {
            moveTo(HeadCx + side * (HeadCx - ThighX - 4f), HipY + 4f)
            lineTo(HeadCx + side * (HeadCx - KneeX - 4f), KneeY)
            lineTo(HeadCx + side * (HeadCx - AnkleX - 4f), AnkleY)
        }
        drawPath(leg, color, style = legStroke)
    }
}
