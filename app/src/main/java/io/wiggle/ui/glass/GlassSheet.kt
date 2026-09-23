package io.wiggle.ui.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeEffect
import io.wiggle.ui.motion.LocalReduceMotion
import io.wiggle.ui.motion.Motion
import io.wiggle.ui.theme.LocalHazeState
import io.wiggle.ui.theme.SupportsBlur
import io.wiggle.ui.theme.WiggleTheme
import kotlinx.coroutines.launch

/**
 * A bottom sheet that springs up while the screen behind it blurs and dims.
 *
 * Material's ModalBottomSheet renders in its own window, which puts it outside the Haze source
 * and leaves the backdrop flat. This one lives in the same layer as the rest of the app, so the
 * blur behind it is the real screen.
 */
@Composable
fun BoxScope.GlassSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Sheet",
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = WiggleTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val hazeState = LocalHazeState.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Drag offset in pixels, added on top of the enter animation.
    val dragOffset = remember { Animatable(0f) }
    LaunchedEffect(visible) { if (visible) dragOffset.snapTo(0f) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.snappy()),
        exit = fadeOut(Motion.snappy()),
        modifier = Modifier.matchParentSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (SupportsBlur) {
                        Modifier.hazeEffect(hazeState, glassStyle(colors, blurRadius = 18.dp))
                    } else {
                        Modifier
                    }
                )
                .background(colors.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Dismiss",
                    onClick = onDismiss,
                ),
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) fadeIn(Motion.snappy()) else slideInVertically(Motion.bouncy()) { it },
        exit = if (reduceMotion) fadeOut(Motion.snappy()) else slideOutVertically(Motion.snappy()) { it },
        modifier = modifier.fillMaxWidth().align(Alignment.BottomCenter),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = dragOffset.value }
                .glass(
                    shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
                    blurRadius = 32.dp,
                    elevation = 28.dp,
                )
                .semantics {
                    dialog()
                    contentDescription = label
                }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            // Resist upward drags so the sheet feels anchored at the top.
                            val next = (dragOffset.value + delta).coerceAtLeast(0f)
                            dragOffset.snapTo(next)
                        }
                    },
                    onDragStopped = { velocity ->
                        val threshold = with(density) { 120.dp.toPx() }
                        if (dragOffset.value > threshold || velocity > 1200f) {
                            onDismiss()
                        } else {
                            dragOffset.animateTo(0f, Motion.bouncy())
                        }
                    },
                )
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .width(40.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.ink.copy(alpha = 0.35f)),
                )
            }
            content()
        }
    }
}
