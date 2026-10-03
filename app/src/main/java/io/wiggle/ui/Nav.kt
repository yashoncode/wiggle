package io.wiggle.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import io.wiggle.ui.components.GlassIconButton
import io.wiggle.ui.glass.TabItem
import io.wiggle.ui.icons.Lucide

object Routes {
    const val Home = "home"
    const val Food = "food"
    const val Steps = "steps"
    const val Body = "body"
}

/**
 * Four tabs, with Settings a button in every header rather than a fifth tab: it is visited rarely,
 * and the tab bar reads better with room around each label.
 */
val WiggleTabs = listOf(
    TabItem("Home", Lucide.House, Routes.Home) { it.weightSoft },
    TabItem("Food", Lucide.Utensils, Routes.Food) { it.foodSoft },
    TabItem("Steps", Lucide.Activity, Routes.Steps) { it.stepsSoft },
    TabItem("Body", Lucide.Scale, Routes.Body) { it.bodySoft },
)

/** The Food tab's three views. */
enum class FoodSection(val label: String) { Calories("Calories"), Water("Water"), Tablets("Tablets") }

/** The Body tab's two views. */
enum class BodySection(val label: String) { Weight("Weight"), Measurements("Measurements") }

/**
 * Opens Settings from any header. A composition local rather than a parameter, because every
 * screen header carries the button and none of the screens has anything else to say about it.
 */
val LocalOpenSettings: ProvidableCompositionLocal<() -> Unit> = staticCompositionLocalOf { {} }

/** The round settings button at the end of each tab's header. */
@Composable
fun SettingsButton() {
    GlassIconButton(
        icon = Lucide.Settings,
        contentDescription = "Settings",
        onClick = LocalOpenSettings.current,
    )
}

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
