package io.wiggle.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import io.wiggle.domain.format
import io.wiggle.ui.motion.LocalReduceMotion
import io.wiggle.ui.motion.Motion

/**
 * A number whose digits roll individually when it changes, and which counts up from zero the
 * first time it appears.
 *
 * Only the digits that actually differ animate: a weight going 72.4 to 72.3 rolls one column,
 * not four, which is what reads as a mechanical counter rather than a flicker.
 */
@Composable
fun RollingNumber(
    value: Double,
    style: TextStyle,
    modifier: Modifier = Modifier,
    decimals: Int = 1,
    color: Color = Color.Unspecified,
    /** Count up from zero the first time this number appears. */
    countUpOnAppear: Boolean = true,
) {
    val reduceMotion = LocalReduceMotion.current
    val shouldCountUp = countUpOnAppear && !reduceMotion
    val animated = remember { Animatable(if (shouldCountUp) 0f else value.toFloat()) }

    LaunchedEffect(value, reduceMotion) {
        if (reduceMotion) animated.snapTo(value.toFloat())
        else animated.animateTo(value.toFloat(), Motion.smooth())
    }

    RollingText(
        text = animated.value.toDouble().format(decimals),
        style = style,
        color = color,
        modifier = modifier,
    )
}

/** The digit-by-digit part, for values that are not a plain double. */
@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val reduceMotion = LocalReduceMotion.current
    Row(
        // Each digit is its own Text, so without this a screen reader reads "7", "2", ".", "3"
        // one character at a time instead of the number.
        modifier.semantics(mergeDescendants = true) { contentDescription = text },
        verticalAlignment = Alignment.Bottom,
    ) {
        text.forEachIndexed { index, char ->
            // Keying by position gives each column its own animation lane.
            key(index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        if (reduceMotion) {
                            fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                        } else {
                            // A rising digit enters from below, a falling one from above.
                            val direction = if (targetState > initialState) 1 else -1
                            (
                                slideInVertically(Motion.bouncy()) { height -> direction * height } +
                                    fadeIn(Motion.snappy())
                                ) togetherWith (
                                slideOutVertically(Motion.bouncy()) { height -> -direction * height } +
                                    fadeOut(Motion.snappy())
                                )
                        }
                    },
                    label = "digit",
                ) { target ->
                    Text(text = target.toString(), style = style, color = color)
                }
            }
        }
    }
}
