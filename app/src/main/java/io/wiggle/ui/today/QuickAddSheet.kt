package io.wiggle.ui.today

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.wiggle.ui.components.CardRow
import io.wiggle.ui.components.IconBadge
import io.wiggle.ui.glass.GlassSheet
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.icons.LucideIcon
import io.wiggle.ui.theme.WiggleTheme

/**
 * What the plus in the Today header opens: the three things there are to log, rather than three
 * buttons in a header that only has room for one.
 */
@Composable
fun BoxScope.QuickAddSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onLogWeight: () -> Unit,
    onLogBody: () -> Unit,
    onLogWater: () -> Unit,
) {
    val colors = WiggleTheme.colors
    GlassSheet(visible = visible, onDismiss = onDismiss, label = "Log something") {
        Text("Log something", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Spacer(Modifier.height(10.dp))
        QuickAddRow(
            icon = Lucide.Scale,
            accent = colors.weight,
            title = "Weight",
            subtitle = "Today's weigh-in, or an earlier one",
            showDivider = false,
            onClick = onLogWeight,
        )
        QuickAddRow(
            icon = Lucide.Ruler,
            accent = colors.body,
            title = "Body measurements",
            subtitle = "Tape session, backdatable",
            onClick = onLogBody,
        )
        QuickAddRow(
            icon = Lucide.Droplet,
            accent = colors.water,
            title = "Water",
            subtitle = "A glass, a bottle, or your own amount",
            onClick = onLogWater,
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun QuickAddRow(
    icon: LucideIcon,
    accent: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    val colors = WiggleTheme.colors
    CardRow(showDivider = showDivider, onClick = onClick) {
        IconBadge(icon, accent, size = 42.dp)
        Column(Modifier.fillMaxWidth(0.8f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
        }
        Icon(Lucide.ChevronRight, size = 16.dp, tint = colors.inkFaint, strokeWidth = 2.4f)
    }
}
