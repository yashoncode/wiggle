package io.wiggle.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import io.wiggle.ui.glass.TabItem
import io.wiggle.ui.icons.Lucide

object Routes {
    const val Onboarding = "onboarding"
    const val Today = "today"
    const val Trends = "trends"
    const val Body = "body"
    const val Water = "water"
    const val Alerts = "alerts"
    const val Settings = "settings"
    const val Profiles = "profiles"
    const val MeasureEditor = "measure_editor"
}

val WiggleTabs = listOf(
    TabItem("Today", Lucide.House, Routes.Today),
    TabItem("Weight", Lucide.Scale, Routes.Trends),
    TabItem("Body", Lucide.Ruler, Routes.Body),
    TabItem("Water", Lucide.Droplet, Routes.Water),
    TabItem("Settings", Lucide.Settings, Routes.Settings),
)

/**
 * How far the current screen is scrolled, in pixels. The background reads it at draw time to
 * parallax its glows; screens write to it. Keeping it out of the navigation state means a scroll
 * never recomposes the tab bar or the background.
 */
val LocalScrollOffset: ProvidableCompositionLocal<MutableFloatState> =
    compositionLocalOf { mutableFloatStateOf(0f) }

/** Publishes a screen's scroll position to the background. */
@Composable
fun ReportScroll(state: ScrollState) {
    val offset = LocalScrollOffset.current
    LaunchedEffect(state) {
        snapshotFlow { state.value }.collect { offset.floatValue = it.toFloat() }
    }
}
