package io.wiggle.ui.bulk

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.wiggle.domain.format
import io.wiggle.ui.components.MinTouch
import io.wiggle.ui.components.PrimaryButton
import io.wiggle.ui.components.SegmentedControl
import io.wiggle.ui.components.friendlyDate
import io.wiggle.ui.glass.GlassSheet
import io.wiggle.ui.glass.glass
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.motion.rememberHaptics
import io.wiggle.ui.theme.WiggleTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Backfilling past entries.
 *
 * The grid is for filling gaps you can see; the paste box is for moving a whole history in from
 * somewhere else. Both write through the same path as a normal weigh-in, so nothing downstream
 * has to know an entry arrived in bulk.
 */
@Composable
fun BoxScope.BulkAddSheet(
    state: BulkAddUiState,
    onDismiss: () -> Unit,
    onMode: (BulkMode) -> Unit,
    onSpan: (BulkSpan) -> Unit,
    onTyped: (LocalDate, String) -> Unit,
    onClearTyped: () -> Unit,
    onSaveGrid: () -> Unit,
    onPasteText: (String) -> Unit,
    onPasteDayFirst: (Boolean) -> Unit,
    onSavePaste: () -> Unit,
) {
    val colors = WiggleTheme.colors
    val haptics = rememberHaptics()

    GlassSheet(visible = state.open, onDismiss = onDismiss, label = "Add past entries") {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.height(MinTouch).clickable(onClickLabel = "Close", onClick = onDismiss),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("Close", style = MaterialTheme.typography.titleMedium, color = colors.weightSoft)
            }
            Text(
                "Add past entries",
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
            )
            Spacer(Modifier.width(52.dp))
        }

        SegmentedControl(
            options = BulkMode.entries,
            selected = state.mode,
            onSelect = onMode,
            label = { if (it == BulkMode.Grid) "Fill the gaps" else "Paste a list" },
            modifier = Modifier.fillMaxWidth(),
        )

        when (state.mode) {
            BulkMode.Grid -> GridMode(state, onSpan, onTyped, onClearTyped, onSaveGrid, haptics::success)
            BulkMode.Paste -> PasteMode(state, onPasteText, onPasteDayFirst, onSavePaste, haptics::success)
        }

        if (state.savedCount > 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.weight.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Check, size = 16.dp, tint = colors.weightSoft, strokeWidth = 2.6f)
                Text(
                    "Saved ${state.savedCount} ${if (state.savedCount == 1) "entry" else "entries"}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.weightSoft,
                )
            }
        }
    }
}

@Composable
private fun GridMode(
    state: BulkAddUiState,
    onSpan: (BulkSpan) -> Unit,
    onTyped: (LocalDate, String) -> Unit,
    onClearTyped: () -> Unit,
    onSave: () -> Unit,
    onSaved: () -> Unit,
) {
    val colors = WiggleTheme.colors

    SegmentedControl(
        options = BulkSpan.entries,
        selected = state.span,
        onSelect = onSpan,
        label = { it.label },
        modifier = Modifier.fillMaxWidth(),
    )

    Text(
        "Type a weight against any day you missed. Days you already logged show their value.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.inkMuted,
    )

    // A list this long has to be lazy, and a lazy list inside the sheet needs its own bounded
    // height or it fights the sheet for space.
    LazyColumn(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .glass(shape = RoundedCornerShape(20.dp), blurRadius = 18.dp, elevation = 6.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        items(state.days, key = { it.date.toEpochDay() }) { day ->
            BulkRow(day = day, unitLabel = state.unit.label, onTyped = onTyped)
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.pendingCount > 0) {
            PrimaryButton(
                text = "Clear",
                onClick = onClearTyped,
                modifier = Modifier.weight(1f),
                color = colors.glassPressedFill,
                contentColor = colors.ink,
            )
        }
        PrimaryButton(
            text = when {
                state.saving -> "Saving…"
                state.pendingCount == 0 -> "Nothing to add yet"
                else -> "Add ${state.pendingCount} ${if (state.pendingCount == 1) "entry" else "entries"}"
            },
            onClick = { onSave(); onSaved() },
            icon = Lucide.Check,
            enabled = state.pendingCount > 0 && !state.saving,
            modifier = Modifier.weight(2f),
        )
    }
}

@Composable
private fun BulkRow(
    day: BulkDay,
    unitLabel: String,
    onTyped: (LocalDate, String) -> Unit,
) {
    val colors = WiggleTheme.colors
    val isWeekend = day.date.dayOfWeek.value >= 6

    Row(
        Modifier
            .fillMaxWidth()
            .height(MinTouch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                day.date.friendlyDate(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isWeekend) colors.inkMuted else colors.ink,
                maxLines = 1,
            )
            Text(
                day.date.format(DateTimeFormatter.ofPattern("EEEE")),
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkFaint,
            )
        }

        if (day.existing != null && !day.isFilled) {
            Text(
                "${day.existing.format()} $unitLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.weightSoft,
            )
            Spacer(Modifier.width(10.dp))
            Icon(Lucide.Check, size = 15.dp, tint = colors.weightSoft, strokeWidth = 2.6f)
        } else {
            BasicTextField(
                value = day.typed,
                onValueChange = { onTyped(day.date, it) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = colors.ink,
                    textAlign = TextAlign.End,
                ),
                cursorBrush = SolidColor(colors.weight),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier
                    .width(96.dp)
                    .background(
                        if (day.isFilled) colors.weight.copy(alpha = 0.14f) else colors.track,
                        RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterEnd) {
                        if (day.typed.isEmpty()) {
                            Text(
                                "— $unitLabel",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.inkFaint,
                            )
                        }
                        inner()
                    }
                },
            )
            if (day.existing != null) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Lucide.Repeat,
                    size = 14.dp,
                    tint = colors.goalSoft,
                    strokeWidth = 2.2f,
                    contentDescription = "Will be added alongside the existing entry",
                )
            }
        }
    }
}

@Composable
private fun PasteMode(
    state: BulkAddUiState,
    onPasteText: (String) -> Unit,
    onDayFirst: (Boolean) -> Unit,
    onSave: () -> Unit,
    onSaved: () -> Unit,
) {
    val colors = WiggleTheme.colors

    Text(
        "Paste one entry per line: a date, then the weight. Commas, semicolons and tabs all work.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.inkMuted,
    )

    BasicTextField(
        value = state.pasteText,
        onValueChange = onPasteText,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.weight),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp, max = 260.dp)
            .glass(shape = RoundedCornerShape(16.dp), blurRadius = 18.dp, elevation = 4.dp)
            .padding(14.dp),
        decorationBox = { inner ->
            if (state.pasteText.isEmpty()) {
                Text(
                    "2026-09-20, 72.4\n2026-09-21, 72.1\n21/09/2026; 72.1 kg",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkFaint,
                )
            }
            inner()
        },
    )

    SegmentedControl(
        options = listOf(true, false),
        selected = state.pasteDayFirst,
        onSelect = onDayFirst,
        label = { if (it) "Day first (3/4 = 3 Apr)" else "Month first (3/4 = 4 Mar)" },
        modifier = Modifier.fillMaxWidth(),
    )

    if (state.parsedCount > 0 || state.parseErrors.isNotEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.track, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "${state.parsedCount} ${if (state.parsedCount == 1) "entry" else "entries"} ready",
                style = MaterialTheme.typography.labelLarge,
                color = if (state.parsedCount > 0) colors.weightSoft else colors.inkMuted,
            )
            state.parseErrors.forEach { error ->
                Text(
                    "Line ${error.lineNumber}: ${error.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.bodySoft,
                )
            }
        }
    }

    PrimaryButton(
        text = when {
            state.saving -> "Importing…"
            state.parsedCount == 0 -> "Nothing to import yet"
            else -> "Import ${state.parsedCount} ${if (state.parsedCount == 1) "entry" else "entries"}"
        },
        onClick = { onSave(); onSaved() },
        icon = Lucide.Download,
        enabled = state.parsedCount > 0 && !state.saving,
        modifier = Modifier.fillMaxWidth(),
    )
}
