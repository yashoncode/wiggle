package io.wiggle.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.theme.WiggleTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Two chips — a date and a time — that open the platform pickers.
 *
 * Split rather than combined because the common correction is "right weight, wrong day", and a
 * single chip makes you walk through a time picker you did not want to touch.
 *
 * Both are capped at now: a measurement in the future is always a mis-tap.
 */
@Composable
fun DateTimeChips(
    value: LocalDateTime,
    onChange: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val colors = WiggleTheme.colors
    val context = LocalContext.current

    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassButton(
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        onChange(LocalDateTime.of(LocalDate.of(year, month + 1, day), value.toLocalTime()))
                    },
                    value.year,
                    value.monthValue - 1,
                    value.dayOfMonth,
                ).apply {
                    datePicker.maxDate = System.currentTimeMillis()
                }.show()
            },
            height = 40.dp,
            horizontalPadding = 14.dp,
            contentDescription = "Change date",
        ) {
            Icon(Lucide.Calendar, size = 16.dp, tint = colors.ink, strokeWidth = 2f)
            Text(
                value.toLocalDate().friendlyDate(),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.ink,
            )
        }

        GlassButton(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val candidate = LocalDateTime.of(value.toLocalDate(), LocalTime.of(hour, minute))
                        // Picking today plus a future time would log ahead of now; clamp it.
                        val now = LocalDateTime.now(zone)
                        onChange(if (candidate.isAfter(now)) now else candidate)
                    },
                    value.hour,
                    value.minute,
                    false,
                ).show()
            },
            height = 40.dp,
            horizontalPadding = 14.dp,
            contentDescription = "Change time",
        ) {
            Icon(Lucide.Clock, size = 16.dp, tint = colors.ink, strokeWidth = 2f)
            Text(
                value.format(DateTimeFormatter.ofPattern("h:mm a")),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.ink,
            )
        }
    }
}

fun LocalDate.friendlyDate(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> format(
        DateTimeFormatter.ofPattern(if (year == today.year) "EEE d MMM" else "d MMM yyyy")
    )
}
