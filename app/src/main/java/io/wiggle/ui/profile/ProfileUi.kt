package io.wiggle.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.wiggle.data.db.ProfileEntity
import io.wiggle.ui.components.CardRow
import io.wiggle.ui.components.IconBadge
import io.wiggle.ui.components.MinTouch
import io.wiggle.ui.glass.pressScale
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.theme.Sora
import io.wiggle.ui.theme.WiggleColors
import io.wiggle.ui.theme.WiggleTheme

/** Each person gets a stable accent so the switcher reads at a glance. */
fun profileAccent(colors: WiggleColors, index: Int): Color = when (index % 4) {
    0 -> colors.weight
    1 -> colors.water
    2 -> colors.body
    else -> colors.goal
}

fun profileInitials(name: String): String =
    name.trim().split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

/** The round avatar in the Today header. Tapping it opens the switcher. */
@Composable
fun ProfileAvatar(
    profile: ProfileEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = MinTouch,
) {
    val colors = WiggleTheme.colors
    val accent = profileAccent(colors, profile?.colorIndex ?: 0)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .pressScale(interaction)
            .background(
                brush = Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.22f)),
                ),
                shape = CircleShape,
            )
            .border(1.dp, colors.glassBorder, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Switch person",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = profileInitials(profile?.name ?: "?"),
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = Sora),
            color = colors.ink,
        )
    }
}

/** One row in the switcher list. */
@Composable
fun ProfileRow(
    profile: ProfileEntity,
    selected: Boolean,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val colors = WiggleTheme.colors
    val accent = profileAccent(colors, profile.colorIndex)
    CardRow(modifier = modifier, showDivider = showDivider, onClick = onClick) {
        Box(
            Modifier
                .size(40.dp)
                .background(accent.copy(alpha = 0.22f), CircleShape)
                .border(1.dp, if (selected) accent else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                profileInitials(profile.name),
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = Sora),
                color = accent,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(profile.name, style = MaterialTheme.typography.bodyLarge, color = colors.ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
        }
        if (selected) {
            Icon(Lucide.Check, size = 18.dp, tint = accent, strokeWidth = 2.6f, contentDescription = "Selected")
        }
    }
}

/** "Add someone" row at the bottom of the switcher. */
@Composable
fun AddProfileRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WiggleTheme.colors
    CardRow(modifier = modifier, onClick = onClick) {
        IconBadge(Lucide.UserPlus, colors.inkMuted, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text("Add someone", style = MaterialTheme.typography.bodyLarge, color = colors.ink)
            Text(
                "Track another person on this phone",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted,
            )
        }
        Icon(Lucide.ChevronRight, size = 16.dp, tint = colors.inkFaint, strokeWidth = 2.4f)
    }
}

@Composable
fun ProfileListHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val colors = WiggleTheme.colors
    Column(modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.ink)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
    }
}
