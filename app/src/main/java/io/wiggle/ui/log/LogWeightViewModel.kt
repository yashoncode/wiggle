package io.wiggle.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.wiggle.data.WiggleRepository
import io.wiggle.domain.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

data class LogWeightUiState(
    val weightKg: Double = 70.0,
    val previousKg: Double? = null,
    val at: LocalDateTime = LocalDateTime.now(),
    val note: String = "",
    val unit: WeightUnit = WeightUnit.Kg,
    val saving: Boolean = false,
    /** Set when a save produced the lowest weight ever, so the sheet can celebrate. */
    val savedAsLowest: Boolean = false,
    val editingEntryId: Long? = null,
    val ready: Boolean = false,
)

@HiltViewModel
class LogWeightViewModel @Inject constructor(
    private val repository: WiggleRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val _state = MutableStateFlow(LogWeightUiState())
    val state: StateFlow<LogWeightUiState> = _state.asStateFlow()

    /** Prepares the sheet: a new entry seeded from the last one, or an existing entry to edit. */
    fun open(entryId: Long? = null) = viewModelScope.launch {
        val settings = repository.settings.first()
        val profileId = settings.activeProfileId
        val last = if (profileId != 0L) repository.getLatestForPrefill(profileId) else null

        if (entryId != null) {
            val entry = repository.getWeight(entryId)
            if (entry != null) {
                _state.value = LogWeightUiState(
                    weightKg = entry.weightKg,
                    previousKg = last?.weightKg,
                    at = Instant.ofEpochMilli(entry.measuredAt).atZone(zone).toLocalDateTime(),
                    note = entry.note.orEmpty(),
                    unit = settings.weightUnit,
                    editingEntryId = entry.id,
                    ready = true,
                )
                return@launch
            }
        }

        _state.value = LogWeightUiState(
            weightKg = last?.weightKg ?: 70.0,
            previousKg = last?.weightKg,
            at = LocalDateTime.now(),
            unit = settings.weightUnit,
            ready = true,
        )
    }

    fun setWeightKg(kg: Double) = _state.update { it.copy(weightKg = kg.coerceIn(20.0, 400.0)) }

    /** Nudges by one step in the currently displayed unit, so a tap moves 0.1 lb in pounds. */
    fun nudge(steps: Int) = _state.update {
        val inUnit = it.unit.fromKg(it.weightKg) + steps * it.unit.step
        it.copy(weightKg = it.unit.toKg(Math.round(inUnit * 10) / 10.0).coerceIn(20.0, 400.0))
    }

    fun setUnit(unit: WeightUnit) = viewModelScope.launch {
        _state.update { it.copy(unit = unit) }
        repository.setWeightUnit(unit)
    }

    fun setNote(note: String) = _state.update { it.copy(note = note) }

    fun setDateTime(at: LocalDateTime) = _state.update { it.copy(at = at) }

    /** Saves and reports whether this was a new lowest, which drives the confetti. */
    fun save(onSaved: (lowest: Boolean) -> Unit) = viewModelScope.launch {
        val current = _state.value
        val profileId = repository.settings.first().activeProfileId
        if (profileId == 0L) return@launch
        _state.update { it.copy(saving = true) }

        val millis = current.at.atZone(zone).toInstant().toEpochMilli()
        val lowest = if (current.editingEntryId != null) {
            repository.getWeight(current.editingEntryId)?.let { existing ->
                repository.updateWeight(
                    existing.copy(
                        weightKg = current.weightKg,
                        measuredAt = millis,
                        note = current.note.takeIf { it.isNotBlank() },
                    )
                )
            }
            false
        } else {
            repository.addWeight(profileId, current.weightKg, millis, current.note)
        }

        _state.update { it.copy(saving = false, savedAsLowest = lowest) }
        onSaved(lowest)
    }
}
