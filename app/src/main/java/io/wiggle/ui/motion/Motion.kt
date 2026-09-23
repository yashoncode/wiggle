package io.wiggle.ui.motion

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * True when the user has turned animations off in developer or accessibility settings.
 * Every animated surface in the app reads this and falls back to a plain crossfade.
 */
val LocalReduceMotion: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

@Composable
fun rememberSystemReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return scale == 0f
}

/** The single spring vocabulary for the whole app. */
object Motion {
    /** Card entrances, sheet presentation: visible overshoot. */
    fun <T> bouncy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)

    /** Tab pill, segmented control: snappy with a hint of stretch. */
    fun <T> snappy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)

    /** Press feedback: fast, no overshoot. */
    fun <T> press(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh)

    /** Tap feedback on a surface: quick, with a small overshoot so it reads as a pop. */
    fun <T> pop(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium)

    /** Value changes that must not overshoot, e.g. a progress bar or a water level. */
    fun <T> smooth(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow)

    const val StaggerMillis = 45
    const val CardRiseDp = 16
    const val ChartDrawMillis = 900
    const val PressScale = 0.96f

    /** How far a tapped surface dips before it springs back. */
    const val PopScale = 0.97f

    /** Swaps any spec for a short crossfade-friendly tween when reduce motion is on. */
    fun <T> respecting(reduceMotion: Boolean, spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (reduceMotion) tween(120) else spec

    fun <T> respectingAny(reduceMotion: Boolean, spec: AnimationSpec<T>): AnimationSpec<T> =
        if (reduceMotion) tween(120) else spec
}
