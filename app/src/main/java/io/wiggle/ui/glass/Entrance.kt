package io.wiggle.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.wiggle.ui.motion.LocalReduceMotion
import io.wiggle.ui.motion.Motion
import kotlinx.coroutines.delay

/**
 * Fades a card in and lifts it 16dp into place, staggered by 45ms per [index] down the screen.
 *
 * With reduce motion on this becomes a plain fade with no travel and no stagger.
 */
@Composable
fun Modifier.cardEntrance(index: Int = 0, enabled: Boolean = true): Modifier {
    val reduceMotion = LocalReduceMotion.current
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    val riseDistance = with(LocalDensity.current) { Motion.CardRiseDp.dp.toPx() }

    LaunchedEffect(enabled, reduceMotion) {
        if (!enabled) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        if (!reduceMotion) delay(index * Motion.StaggerMillis.toLong())
        progress.animateTo(1f, Motion.respecting(reduceMotion, Motion.bouncy()))
    }

    return graphicsLayer {
        alpha = progress.value
        if (!reduceMotion) translationY = (1f - progress.value) * riseDistance
    }
}
