package io.wiggle.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.wiggle.ui.glass.glass
import io.wiggle.ui.glass.pressScale
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.LucideIcon
import io.wiggle.ui.motion.rememberHaptics
import io.wiggle.ui.theme.WiggleTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A round glass button that fires once on tap and then repeats while held, speeding up the
 * longer it is held — the behaviour of a stepper you can lean on.
 */
@Composable
fun RepeatingIconButton(
    icon: LucideIcon,
    contentDescription: String,
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    tint: Color = WiggleTheme.colors.ink,
) {
    // Read through a holder rather than closing over the parameter: onStep is a fresh lambda on
    // every recomposition, and every step recomposes the caller. Keying the gesture on it tore
    // down the pointer input mid-press, which stranded the press highlight and left the repeat
    // loop running against a stale value.
    val step by rememberUpdatedState(onStep)
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .size(size)
            .pressScale(interaction)
            .glass(shape = CircleShape, blurRadius = 20.dp, elevation = 8.dp)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
                onClick(label = contentDescription) { step(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val press = PressInteraction.Press(down.position)
                    scope.launch { interaction.emit(press) }

                    step()
                    haptics.tick()

                    val repeatJob = scope.launch {
                        // Hold before repeating, then accelerate from 140ms down to 40ms.
                        delay(380)
                        var interval = 140L
                        while (true) {
                            step()
                            haptics.tick()
                            delay(interval)
                            if (interval > 40L) interval -= 12L
                        }
                    }

                    val up = waitForUpOrCancellation()
                    repeatJob.cancel()
                    scope.launch {
                        interaction.emit(
                            if (up != null) PressInteraction.Release(press)
                            else PressInteraction.Cancel(press)
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, size = size * 0.4f, tint = tint, strokeWidth = 2.4f)
    }
}
