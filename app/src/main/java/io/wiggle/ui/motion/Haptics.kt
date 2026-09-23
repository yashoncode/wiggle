package io.wiggle.ui.motion

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Thin wrapper over [View.performHapticFeedback]. The tick-grade effects the ruler and the chart
 * scrubber need only exist on newer API levels, so each one here degrades to the nearest older
 * effect rather than going silent.
 */
@Immutable
class Haptics(private val view: View, private val enabled: Boolean) {

    private fun perform(constant: Int) {
        if (enabled) view.performHapticFeedback(constant)
    }

    /** A light detent: one ruler notch, one chart data point. */
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    /** A firmer detent: a whole number on the ruler, a tab change. */
    fun select() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
    )

    fun longPress() = perform(HapticFeedbackConstants.LONG_PRESS)

    /** Save succeeded, goal reached. */
    fun success() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
    )

    fun reject() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
    )

    fun toggle(on: Boolean) = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
    )
}

@Composable
fun rememberHaptics(enabled: Boolean = true): Haptics {
    val view = LocalView.current
    return remember(view, enabled) { Haptics(view, enabled) }
}
