package io.wiggle.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import dev.chrisbanes.haze.HazeState
import io.wiggle.ui.motion.LocalReduceMotion
import io.wiggle.ui.motion.rememberSystemReduceMotion

val LocalWiggleColors: ProvidableCompositionLocal<WiggleColors> =
    staticCompositionLocalOf { DarkWiggleColors }

/** The single Haze source the whole app blurs against. */
val LocalHazeState: ProvidableCompositionLocal<HazeState> =
    staticCompositionLocalOf { error("No HazeState provided") }

/**
 * True when the platform can do a real backdrop blur. Below API 31 there is no RenderEffect,
 * so glass falls back to a flat translucent fill.
 */
val SupportsBlur: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

enum class ThemeMode { System, Dark, Light }

object WiggleTheme {
    val colors: WiggleColors
        @Composable @ReadOnlyComposable get() = LocalWiggleColors.current
}

@Composable
fun WiggleTheme(
    mode: ThemeMode = ThemeMode.System,
    hazeState: HazeState,
    reduceMotion: Boolean = rememberSystemReduceMotion(),
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    val colors = if (dark) DarkWiggleColors else LightWiggleColors

    // Material3 still backs a few primitives (ripple, text selection), so keep its scheme in sync.
    val material = if (dark) {
        darkColorScheme(
            primary = colors.weight,
            onPrimary = colors.onAccent,
            background = colors.background,
            onBackground = colors.ink,
            surface = colors.background,
            onSurface = colors.ink,
            surfaceVariant = Color.Transparent,
            error = colors.body,
        )
    } else {
        lightColorScheme(
            primary = colors.weight,
            onPrimary = colors.onAccent,
            background = colors.background,
            onBackground = colors.ink,
            surface = colors.background,
            onSurface = colors.ink,
            surfaceVariant = Color.Transparent,
            error = colors.body,
        )
    }

    CompositionLocalProvider(
        LocalWiggleColors provides colors,
        LocalHazeState provides hazeState,
        LocalReduceMotion provides reduceMotion,
        LocalTextStyle provides WiggleTypography.bodyLarge.copy(color = colors.ink),
    ) {
        MaterialTheme(
            colorScheme = material,
            typography = WiggleTypography,
        ) {
            // Inside MaterialTheme, not outside it: Material 3 provides its own ripple as
            // LocalIndication, so anything set further out is overwritten before it reaches a
            // single clickable.
            CompositionLocalProvider(LocalIndication provides NoIndication, content = content)
        }
    }
}

/** Text that defaults to the theme ink colour instead of Material's onSurface. */
@Composable
fun WiggleText(
    text: String,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        style = style,
        color = if (color == Color.Unspecified) WiggleTheme.colors.ink else color,
        modifier = modifier,
        maxLines = maxLines,
    )
}

/**
 * No ripple, anywhere.
 *
 * Material's ripple is a rectangle clipped to the clickable bounds, which on glass cards and
 * pill toggles draws a grey box around the control instead of inside it. Everything here gives
 * its own press feedback — a scale, a knob, a haptic — so the ripple has nothing left to say.
 */
private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : Modifier.Node() {}

    override fun hashCode(): Int = "WiggleNoIndication".hashCode()

    override fun equals(other: Any?): Boolean = other === this
}
