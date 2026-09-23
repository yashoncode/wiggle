package io.wiggle.ui.components

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.wiggle.ui.glass.glass
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.motion.LocalReduceMotion
import io.wiggle.ui.motion.Motion
import io.wiggle.ui.motion.rememberHaptics
import io.wiggle.ui.theme.WiggleTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Swipe a row left to delete it. The row does not disappear until the drag passes a threshold,
 * and the delete itself is undoable, so a mis-swipe costs a tap rather than an entry.
 */
@Composable
fun SwipeToDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = WiggleTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val armed = remember { booleanArrayOf(false) }

    Box(modifier.fillMaxWidth()) {
        // The plate behind the row, revealed only as the row slides away. Both the plate and the
        // icon read the offset inside a graphicsLayer, so a drag repaints without recomposing —
        // and, more importantly, neither is visible at rest, where it would sit on top of the
        // row's own content.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = progressOf(offset.value, density.density) }
                .clip(RoundedCornerShape(16.dp))
                .background(colors.body.copy(alpha = 0.18f)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Icon(
                Lucide.Trash,
                size = 20.dp,
                tint = colors.bodySoft,
                strokeWidth = 2f,
                modifier = Modifier.padding(end = 20.dp),
                contentDescription = null,
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offset.value }
                .draggable(
                    enabled = enabled,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            // Only leftward travel; rightward is resisted to zero.
                            val next = (offset.value + delta).coerceAtMost(0f)
                            offset.snapTo(next)
                            val threshold = with(density) { -96.dp.toPx() }
                            if (next < threshold && !armed[0]) {
                                armed[0] = true
                                haptics.select()
                            } else if (next >= threshold && armed[0]) {
                                armed[0] = false
                            }
                        }
                    },
                    onDragStopped = {
                        if (armed[0]) {
                            val width = with(density) { 600.dp.toPx() }
                            if (!reduceMotion) offset.animateTo(-width, Motion.snappy())
                            onDelete()
                            armed[0] = false
                            offset.snapTo(0f)
                        } else {
                            offset.animateTo(0f, Motion.bouncy())
                        }
                    },
                ),
        ) {
            content()
        }
    }
}

private fun progressOf(offset: Float, density: Float): Float =
    (abs(offset) / (96f * density)).coerceIn(0f, 1f)

/**
 * The undo snackbar. Appears on a glass plate above the tab bar and times itself out, which is
 * also when the deletion becomes permanent as far as the user is concerned.
 */
@Composable
fun BoxScope.UndoSnackbar(
    visible: Boolean,
    message: String,
    onUndo: () -> Unit,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
    timeoutMillis: Long = 4500,
) {
    val colors = WiggleTheme.colors

    LaunchedEffect(visible, message) {
        if (visible) {
            delay(timeoutMillis)
            onTimeout()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(Motion.bouncy()) { it } + fadeIn(Motion.snappy()),
        exit = slideOutVertically(Motion.snappy()) { it } + fadeOut(Motion.snappy()),
        modifier = modifier.align(Alignment.BottomCenter),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                // Announced when it appears: it carries the only chance to undo, and it times out.
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 16.dp)
                .glass(shape = RoundedCornerShape(20.dp), blurRadius = 24.dp, elevation = 16.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, style = MaterialTheme.typography.bodyLarge, color = colors.ink)
            Row(
                Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(onClickLabel = "Undo", onClick = onUndo)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Undo, size = 16.dp, tint = colors.weightSoft, strokeWidth = 2.2f)
                Text("Undo", style = MaterialTheme.typography.labelLarge, color = colors.weightSoft)
            }
        }
    }
}
