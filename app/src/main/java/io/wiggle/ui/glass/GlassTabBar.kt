package io.wiggle.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.LucideIcon
import io.wiggle.ui.motion.LocalReduceMotion
import io.wiggle.ui.motion.Motion
import io.wiggle.ui.motion.rememberHaptics
import io.wiggle.ui.theme.WiggleTheme
import kotlin.math.abs
import kotlin.math.min

data class TabItem(val label: String, val icon: LucideIcon, val route: String)

val TabBarHeight = 68.dp
val TabBarSideMargin = 16.dp

/**
 * Floating pill tab bar.
 *
 * The selection pill is one animated value: its travel drives a squash-and-stretch, so it
 * elongates along the direction of motion and settles back to round, which is what reads as
 * "liquid".
 */
@Composable
fun GlassTabBar(
    tabs: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WiggleTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val position = remember { Animatable(selectedIndex.toFloat()) }

    LaunchedEffect(selectedIndex, reduceMotion) {
        if (reduceMotion) {
            position.snapTo(selectedIndex.toFloat())
        } else {
            position.animateTo(selectedIndex.toFloat(), Motion.snappy())
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TabBarSideMargin)
            .height(TabBarHeight)
            .glass(shape = RoundedCornerShape(percent = 50), elevation = 20.dp)
    ) {
        val slotWidth = maxWidth / tabs.size
        val pillWidth = slotWidth - 8.dp
        val pillHeight = TabBarHeight - 12.dp

        // Velocity is in slots/second; 1.0 is a fast flick between neighbours.
        val stretch = if (reduceMotion) 0f else min(abs(position.velocity) / 14f, 0.22f)

        Box(
            Modifier
                .offset(x = slotWidth * position.value + (slotWidth - pillWidth) / 2)
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    scaleX = 1f + stretch
                    scaleY = 1f - stretch * 0.55f
                }
                .width(pillWidth)
                .height(pillHeight)
                .background(
                    color = colors.glassHighlight.copy(alpha = if (colors.isDark) 0.16f else 0.75f),
                    shape = RoundedCornerShape(percent = 50),
                )
        )

        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().fillMaxHeight(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                val tint by animateColorAsState(
                    targetValue = if (selected) colors.weightSoft else colors.inkMuted,
                    animationSpec = Motion.snappy(),
                    label = "tabTint",
                )
                val interaction = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .width(slotWidth)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Tab,
                            onClick = {
                                if (!selected) {
                                    haptics.select()
                                    onSelect(index)
                                }
                            },
                        )
                        .pressScale(interaction),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        icon = tab.icon,
                        size = 22.dp,
                        tint = tint,
                        strokeWidth = if (selected) 2.3f else 2f,
                        modifier = Modifier.size(22.dp),
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                    )
                }
            }
        }
    }
}
