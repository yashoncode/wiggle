package io.wiggle.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.wiggle.domain.AppRelease
import io.wiggle.ui.components.PrimaryButton
import io.wiggle.ui.glass.GlassSheet
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.theme.WiggleTheme

/**
 * The update prompt.
 *
 * The same sheet answers the check in Settings when there is nothing to install, because "you are
 * on the latest" is the other half of the question and deserves an answer of its own.
 */
@Composable
fun BoxScope.UpdateSheet(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
) {
    val colors = WiggleTheme.colors
    val release = state.release
    val notesScroll = rememberScrollState()

    GlassSheet(
        visible = release != null || state.upToDate,
        onDismiss = onDismiss,
        label = if (release != null) "Update available" else "Up to date",
    ) {
        if (release == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.Check, size = 18.dp, tint = colors.goal, strokeWidth = 2.4f)
                Spacer(Modifier.width(8.dp))
                Text(
                    "You are on the latest version",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = "Close",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                color = colors.glassPressedFill,
                contentColor = colors.ink,
            )
            return@GlassSheet
        }

        Text(
            "Wiggle ${release.version} is out",
            style = MaterialTheme.typography.titleLarge,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (release.notes.isBlank()) "A new build is ready to install."
            else release.notes,
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkMuted,
            modifier = Modifier
                .fillMaxWidth()
                // Release notes can be any length; the sheet must not grow with them.
                .heightIn(max = 220.dp)
                .verticalScroll(notesScroll),
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(
                text = "Later",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                color = colors.glassPressedFill,
                contentColor = colors.ink,
            )
            PrimaryButton(
                text = "Update",
                onClick = { onDownload(release.downloadUrl) },
                icon = Lucide.Download,
                modifier = Modifier.weight(1.4f),
                color = colors.weight,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Downloads the APK from GitHub. Android will ask before installing it.",
            style = MaterialTheme.typography.labelMedium,
            color = colors.inkFaint,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
