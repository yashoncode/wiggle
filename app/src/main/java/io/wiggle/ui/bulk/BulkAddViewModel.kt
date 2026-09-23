package io.wiggle.ui.bulk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.wiggle.data.WiggleRepository
import io.wiggle.domain.BulkParse
import io.wiggle.domain.BulkParseError
import io.wiggle.domain.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

enum class BulkMode { Grid, Paste }

enum class BulkSpan(val label: String, val days: Long) {
    Week("2 weeks", 14),
    Month("1 month", 30),
    Quarter("3 months", 90),
}

/** One calendar day in the grid: what is already stored, and what the user has typed. */
data class BulkDay(
    val date: LocalDate,
    /** Already in the database, in the display unit, or null for a day with no entry. */
    val existing: Double?,
    /** Exactly what the user typed, kept as text so a half-typed "7" is not read as 7 kg. */
    val typed: String = "",
) {
    val isFilled: Boolean get() = typed.isNotBlank()
}

data class BulkAddUiState(
    val open: Boolean = false,
    val mode: BulkMode = BulkMode.Grid,
    val span: BulkSpan = BulkSpan.Week,
    val unit: WeightUnit = WeightUnit.Kg,
    val days: List<BulkDay> = emptyList(),
    val pasteText: String = "",
    /** True when the pasted dates are day-first (3/4 = 3 April) rather than month-first. */
    val pasteDayFirst: Boolean = true,
    val parseErrors: List<BulkParseError> = emptyList(),
    val parsedCount: Int = 0,
    val saving: Boolean = false,
    val savedCount: Int = 0,
) {
    val pendingCount: Int get() = days.count { it.isFilled }
}

/**
 * Backfilling history.
 *
 * Two ways in, because two different situations: the grid for "I forgot to log last week", and
 * the paste box for "I have two years of this in another app".
 */
@HiltViewModel
class BulkAddViewModel @Inject constructor(
    private val repository: WiggleRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val _state = MutableStateFlow(BulkAddUiState())
    val state: StateFlow<BulkAddUiState> = _state.asStateFlow()

    fun open() = viewModelScope.launch {
        val settings = repository.settings.first()
        _state.value = BulkAddUiState(open = true, unit = settings.weightUnit)
        refreshDays(BulkSpan.Week)
    }

    fun close() = _state.update { it.copy(open = false) }

    fun setMode(mode: BulkMode) = _state.update { it.copy(mode = mode, savedCount = 0) }

    fun setSpan(span: BulkSpan) = viewModelScope.launch {
        _state.update { it.copy(span = span) }
        refreshDays(span)
    }

    /**
     * Rebuilds the grid, keeping anything already typed. A user who types four values then widens
     * the range should not lose them.
     */
    private suspend fun refreshDays(span: BulkSpan) {
        val current = _state.value
        val entries = repository.weightEntries.first()
        val byDate = entries.associateBy {
            Instant.ofEpochMilli(it.measuredAt).atZone(zone).toLocalDate()
        }
        val typed = current.days.filter { it.isFilled }.associate { it.date to it.typed }
        val today = LocalDate.now(zone)

        _state.update { state ->
            state.copy(
                days = (0 until span.days).map { offset ->
                    val date = today.minusDays(offset)
                    BulkDay(
                        date = date,
                        existing = byDate[date]?.let { state.unit.fromKg(it.weightKg) },
                        typed = typed[date].orEmpty(),
                    )
                }
            )
        }
    }

    fun setTyped(date: LocalDate, text: String) = _state.update { state ->
        // Digits and one separator only, so the field cannot hold something unsaveable.
        val cleaned = text.filter { it.isDigit() || it == '.' || it == ',' }
            .replace(',', '.')
            .let { value ->
                val firstDot = value.indexOf('.')
                if (firstDot == -1) value
                else value.substring(0, firstDot + 1) + value.substring(firstDot + 1).replace(".", "")
            }
            .take(6)
        state.copy(
            days = state.days.map { if (it.date == date) it.copy(typed = cleaned) else it },
            savedCount = 0,
        )
    }

    fun clearTyped() = _state.update { state ->
        state.copy(days = state.days.map { it.copy(typed = "") }, savedCount = 0)
    }

    /** Writes every filled row. Each lands at midday, so it sorts sensibly within its day. */
    fun saveGrid(onDone: (Int) -> Unit) = viewModelScope.launch {
        val current = _state.value
        val profileId = repository.settings.first().activeProfileId
        if (profileId == 0L) return@launch
        _state.update { it.copy(saving = true) }

        var saved = 0
        current.days.filter { it.isFilled }.forEach { day ->
            val value = day.typed.toDoubleOrNull() ?: return@forEach
            val kg = current.unit.toKg(value)
            if (kg < 20 || kg > 400) return@forEach
            repository.addWeight(
                profileId = profileId,
                weightKg = kg,
                measuredAt = day.date.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli(),
            )
            saved++
        }

        _state.update { it.copy(saving = false, savedCount = saved) }
        refreshDays(current.span)
        onDone(saved)
    }

    // --- paste ---------------------------------------------------------------------------

    fun setPasteText(text: String) = _state.update { state ->
        val result = BulkParse.parse(
            text = text,
            unit = state.unit,
            dayFirst = state.pasteDayFirst,
            today = LocalDate.now(zone),
        )
        state.copy(
            pasteText = text,
            parsedCount = result.rows.size,
            parseErrors = result.errors.take(6),
            savedCount = 0,
        )
    }

    fun setPasteDayFirst(dayFirst: Boolean) {
        _state.update { it.copy(pasteDayFirst = dayFirst) }
        // Re-read the same text under the new convention.
        setPasteText(_state.value.pasteText)
    }

    fun savePaste(onDone: (Int) -> Unit) = viewModelScope.launch {
        val current = _state.value
        val profileId = repository.settings.first().activeProfileId
        if (profileId == 0L) return@launch
        _state.update { it.copy(saving = true) }

        val result = BulkParse.parse(
            text = current.pasteText,
            unit = current.unit,
            dayFirst = current.pasteDayFirst,
            today = LocalDate.now(zone),
        )
        result.rows.forEach { row ->
            repository.addWeight(
                profileId = profileId,
                weightKg = row.weight,
                measuredAt = row.date.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli(),
                note = row.note,
            )
        }

        _state.update {
            it.copy(saving = false, savedCount = result.rows.size, pasteText = "", parsedCount = 0)
        }
        onDone(result.rows.size)
    }
}
