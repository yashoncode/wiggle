package io.wiggle.ui.profile

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.wiggle.data.db.ProfileEntity
import io.wiggle.domain.WeightUnit
import io.wiggle.domain.format
import io.wiggle.ui.glass.GlassSheet

/**
 * The multi-account switcher. One install can track several people; this is where you say which
 * one the app is showing.
 */
@Composable
fun BoxScope.ProfileSwitcherSheet(
    visible: Boolean,
    profiles: List<ProfileEntity>,
    activeId: Long,
    unit: WeightUnit,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassSheet(visible = visible, onDismiss = onDismiss, label = "Switch person") {
        ProfileListHeader(
            title = "Who are we tracking?",
            subtitle = "Each person keeps their own weight, measurements, water and reminders.",
        )
        Column(Modifier.fillMaxWidth()) {
            profiles.forEachIndexed { index, profile ->
                ProfileRow(
                    profile = profile,
                    selected = profile.id == activeId,
                    subtitle = profile.subtitle(unit),
                    onClick = { onSelect(profile.id) },
                    showDivider = index > 0,
                )
            }
            AddProfileRow(onClick = onAdd)
        }
    }
}

private fun ProfileEntity.subtitle(unit: WeightUnit): String {
    val goal = goalWeightKg
    return if (goal != null) {
        "Goal ${unit.fromKg(goal).format()} ${unit.label} · ${heightCm.format(0)} cm"
    } else {
        "${heightCm.format(0)} cm"
    }
}
