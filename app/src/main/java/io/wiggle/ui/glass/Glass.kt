package io.wiggle.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import io.wiggle.ui.motion.LocalReduceMotion
import io.wiggle.ui.motion.Motion
import io.wiggle.ui.theme.LocalHazeState
import io.wiggle.ui.theme.SupportsBlur
import io.wiggle.ui.theme.WiggleColors
import io.wiggle.ui.theme.WiggleTheme
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

object GlassDefaults {
    val Radius: Dp = 28.dp
    val SmallRadius: Dp = 24.dp
    val TinyRadius: Dp = 20.dp
    val BlurRadius: Dp = 28.dp
    val Elevation: Dp = 14.dp
    const val Noise = 0.06f
}

fun glassStyle(colors: WiggleColors, blurRadius: Dp = GlassDefaults.BlurRadius) = HazeStyle(
    backgroundColor = colors.background,
    tints = listOf(HazeTint(colors.glassFill)),
    blurRadius = blurRadius,
    noiseFactor = GlassDefaults.Noise,
    fallbackTint = HazeTint(colors.glassFillOpaque),
)

/**
 * The one glass recipe: backdrop blur, a translucent fill, a hairline border that is brighter
 * along the top edge, and a soft drop shadow.
 *
 * Below API 31 there is no RenderEffect, so the blur is replaced by a flat translucent fill.
 */
@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(GlassDefaults.Radius),
    blurRadius: Dp = GlassDefaults.BlurRadius,
    elevation: Dp = GlassDefaults.Elevation,
    fillOverride: Color? = null,
    hazeState: HazeState = LocalHazeState.current,
): Modifier {
    val colors = WiggleTheme.colors
    val style = remember(colors, blurRadius) { glassStyle(colors, blurRadius) }
    val borderBrush = remember(colors) {
        Brush.verticalGradient(
            0f to colors.glassHighlight,
            0.16f to colors.glassBorder,
            1f to colors.glassBorder.copy(alpha = colors.glassBorder.alpha * 0.7f),
        )
    }
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.5f),
            spotColor = Color.Black.copy(alpha = 0.6f),
        )
        .clip(shape)
        .then(
            if (SupportsBlur && fillOverride == null) {
                Modifier.hazeEffect(state = hazeState, style = style)
            } else {
                Modifier.background(fillOverride ?: colors.glassFillOpaque)
            }
        )
        .border(1.dp, borderBrush, shape)
}

/**
 * Scales a surface to 0.96 while pressed. Pair with the same [interactionSource] given to the
 * clickable so the press and the scale stay in step.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    scale: Float = Motion.PressScale,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val value by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) scale else 1f,
        animationSpec = Motion.press(),
        label = "pressScale",
    )
    return graphicsLayer { scaleX = value; scaleY = value }
}

/** Three swings, each smaller than the last, ending exactly where the card started. */
private fun wiggleAngle(progress: Float): Float =
    sin(progress * WiggleSwings * 2f * PI.toFloat()) * WiggleDegrees * (1f - progress)

private const val WiggleSwings = 3f
private const val WiggleDegrees = 1.4f
private const val WiggleMillis = 420

/**
 * A short wobble when a surface is tapped, so a card that does nothing still answers a finger.
 *
 * With no [interactionSource] the modifier watches for taps itself, which is what a plain card
 * needs: it adds no click semantics, so a screen reader is not told the card is a button. Pass the
 * source a `clickable` is already using where there is a real action, because that clickable
 * consumes the tap before a second detector could see it.
 */
@Composable
fun Modifier.wiggleOnTap(interactionSource: MutableInteractionSource? = null): Modifier {
    val reduceMotion = LocalReduceMotion.current
    val progress = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    fun play() {
        if (reduceMotion) return
        scope.launch {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(WiggleMillis))
        }
    }

    if (interactionSource != null) {
        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { if (it is PressInteraction.Release) play() }
        }
    }

    return this
        .graphicsLayer { rotationZ = wiggleAngle(progress.value) }
        .then(
            if (interactionSource != null) {
                Modifier
            } else {
                Modifier.pointerInput(Unit) { detectTapGestures { play() } }
            }
        )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlassDefaults.Radius),
    contentPadding: Dp = 20.dp,
    elevation: Dp = GlassDefaults.Elevation,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .wiggleOnTap()
            .glass(shape = shape, elevation = elevation)
            .padding(contentPadding),
        content = content,
    )
}

/** A glass card that presses like a button. */
@Composable
fun TappableGlassCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlassDefaults.SmallRadius),
    contentPadding: Dp = 16.dp,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .wiggleOnTap(interaction)
            .pressScale(interaction)
            .glass(shape = shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = contentDescription,
                onClick = onClick,
            )
            .padding(contentPadding),
        content = content,
    )
}
