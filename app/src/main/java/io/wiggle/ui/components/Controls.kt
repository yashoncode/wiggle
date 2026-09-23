package io.wiggle.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.wiggle.ui.glass.GlassDefaults
import io.wiggle.ui.glass.glass
import io.wiggle.ui.glass.pressScale
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.LucideIcon
import io.wiggle.ui.motion.LocalReduceMotion
import io.wiggle.ui.motion.Motion
import io.wiggle.ui.motion.rememberHaptics
import io.wiggle.ui.theme.EyebrowStyle
import io.wiggle.ui.theme.WiggleTheme
import kotlin.math.abs

/** Minimum touch target the whole app holds to. */
val MinTouch = 48.dp

@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.displaySmall,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        // A gutter rather than SpaceBetween: the title already takes the slack through its weight,
        // so SpaceBetween left no gap at all and a long name ran straight into the first button.
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = eyebrow.uppercase(),
                style = EyebrowStyle,
                color = WiggleTheme.colors.inkMuted,
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = titleStyle.copy(color = WiggleTheme.colors.ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = titleStyle.fontSize * 0.6f,
                    maxFontSize = titleStyle.fontSize,
                    stepSize = 1.sp,
                ),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = trailing,
        )
    }
}

/** Solid accent button: the one primary action on a screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: LucideIcon? = null,
    enabled: Boolean = true,
    color: Color = WiggleTheme.colors.weight,
    contentColor: Color = WiggleTheme.colors.onAccent,
    height: Dp = 56.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(height)
            .pressScale(interaction)
            .clip(RoundedCornerShape(if (height <= 44.dp) height / 2 else 18.dp))
            .background(if (enabled) color else color.copy(alpha = 0.35f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, size = 18.dp, tint = contentColor, strokeWidth = 2.6f)
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            textAlign = TextAlign.Center,
        )
    }
}

/** Glass-backed button: secondary actions, chips, icon buttons. */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(percent = 50),
    height: Dp = MinTouch,
    horizontalPadding: Dp = 16.dp,
    enabled: Boolean = true,
    contentDescription: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .defaultMinSize(minWidth = MinTouch, minHeight = MinTouch)
            .height(height)
            .pressScale(interaction)
            .glass(shape = shape, blurRadius = 20.dp, elevation = 8.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun GlassIconButton(
    icon: LucideIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = MinTouch,
    tint: Color = WiggleTheme.colors.ink,
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier.size(size),
        height = size,
        horizontalPadding = 0.dp,
        contentDescription = contentDescription,
    ) {
        Icon(icon, size = size * 0.42f, tint = tint, strokeWidth = 2.2f, contentDescription = contentDescription)
    }
}

/** A small status pill, e.g. "↓ 0.6 kg this week". */
@Composable
fun AccentPill(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    icon: LucideIcon? = null,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(accent.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, size = 13.dp, tint = accent, strokeWidth = 2.6f)
        Text(text, style = MaterialTheme.typography.labelMedium, color = accent)
    }
}

/**
 * iOS-style switch. The knob travels on a spring and the track colour crossfades, so the two
 * halves of the change land together.
 */
@Composable
fun IosToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = WiggleTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val trackWidth = 51.dp
    val trackHeight = 31.dp
    val knob = 27.dp

    val offset = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked, reduceMotion) {
        if (reduceMotion) offset.snapTo(if (checked) 1f else 0f)
        // Loose damping, so the knob overshoots and settles back rather than arriving dead.
        else offset.animateTo(
            targetValue = if (checked) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
        )
    }
    val track by animateColorAsState(
        targetValue = if (checked) colors.toggleOn else colors.track,
        animationSpec = Motion.snappy(),
        label = "toggleTrack",
    )

    Box(
        modifier
            .defaultMinSize(minWidth = MinTouch, minHeight = MinTouch)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = {
                    haptics.toggle(it)
                    onCheckedChange(it)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(trackWidth)
                .height(trackHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (enabled) track else track.copy(alpha = 0.4f)),
        ) {
            Box(
                Modifier
                    .padding(2.dp)
                    .size(knob)
                    // Position and squash both live in the draw phase, so the wobble costs no
                    // recomposition per frame.
                    .graphicsLayer {
                        val travel = (trackWidth - knob - 4.dp).toPx()
                        translationX = travel * offset.value
                        // Moving fast stretches the cap along its travel, the way a real one would.
                        val stretch = (abs(offset.velocity) / 9f).coerceAtMost(0.28f)
                        scaleX = 1f + stretch
                        scaleY = 1f - stretch * 0.5f
                    }
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White),
            )
        }
    }
}

/**
 * Sliding segmented control. The selection is a single glass pill that slides between slots on a
 * spring, matching the tab bar so the two never feel like different widgets.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String = { it.toString() },
    height: Dp = 36.dp,
) {
    val colors = WiggleTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val index = options.indexOf(selected).coerceAtLeast(0)
    val position = remember { Animatable(index.toFloat()) }

    LaunchedEffect(index, reduceMotion) {
        if (reduceMotion) position.snapTo(index.toFloat())
        else position.animateTo(index.toFloat(), Motion.snappy())
    }

    BoxWithConstraints(
        modifier
            .height(height + 8.dp)
            .glass(shape = RoundedCornerShape(14.dp), blurRadius = 18.dp, elevation = 6.dp)
            .padding(4.dp),
    ) {
        val slot = maxWidth / options.size
        Box(
            Modifier
                .offset(x = slot * position.value)
                .width(slot)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (colors.isDark) Color.White.copy(alpha = 0.90f)
                    else colors.ink.copy(alpha = 0.90f)
                ),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { slotIndex, option ->
                val isSelected = slotIndex == index
                Box(
                    Modifier
                        .width(slot)
                        .fillMaxHeight()
                        .selectable(
                            selected = isSelected,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            role = Role.RadioButton,
                            onClick = {
                                if (!isSelected) {
                                    haptics.tick()
                                    onSelect(option)
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) {
                            if (colors.isDark) colors.background else Color.White
                        } else {
                            colors.inkMuted
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A labelled row inside a glass card, with a divider above every row but the first. */
@Composable
fun CardRow(
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = WiggleTheme.colors
    Column(modifier.fillMaxWidth()) {
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.divider)
            )
        }
        val interaction = remember { MutableInteractionSource() }
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MinTouch)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** The rounded square that holds a category icon at the left of a list row. */
@Composable
fun IconBadge(
    icon: LucideIcon,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(GlassDefaults.TinyRadius / 1.7f))
            .background(accent.copy(alpha = 0.20f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, size = size * 0.5f, tint = accent, strokeWidth = 2f)
    }
}
