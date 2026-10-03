package io.wiggle.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.wiggle.data.WiggleRepository
import io.wiggle.data.db.MedicationEntity
import io.wiggle.data.db.ReminderKind
import io.wiggle.domain.Dose
import io.wiggle.domain.DoseStatus
import io.wiggle.domain.Doses
import io.wiggle.domain.todayFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/** Tablets that run out within this many days get the "running low" card. */
private const val LOW_STOCK_DAYS = 7

data class TabletsUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val doses: List<Dose> = emptyList(),
    /** Each running-low tablet with the days it has left. */
    val lowStock: List<Pair<MedicationEntity, Int>> = emptyList(),
    val remindersOn: Boolean = false,
) {
    val taken: Int get() = doses.count { it.status == DoseStatus.Taken }
}

@HiltViewModel
class TabletsViewModel @Inject constructor(
    private val repository: WiggleRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** The tablet the editor sheet is open on; id 0 is a new one. Null when closed. */
    private val _editing = MutableStateFlow<MedicationEntity?>(null)
    val editing: StateFlow<MedicationEntity?> = _editing.asStateFlow()

    /** Ticks every minute, so a dose turns overdue on screen without anything else changing. */
    private val clock = flow {
        while (true) {
            val now = LocalTime.now()
            emit(now.hour * 60 + now.minute)
            delay(60_000L - now.second * 1000L)
        }
    }

    val state: StateFlow<TabletsUiState> = combine(
        todayFlow(zone).flatMapLatest { day -> repository.dosesOn(day).map { day to it } },
        repository.medications,
        repository.reminders,
        clock,
    ) { (day, logs), medications, reminders, nowMinutes ->
        TabletsUiState(
            loading = false,
            today = day,
            doses = Doses.today(medications, logs, nowMinutes),
            lowStock = medications.mapNotNull { med ->
                Doses.daysLeft(med)?.takeIf { it <= LOW_STOCK_DAYS }?.let { med to it }
            },
            remindersOn = reminders.any { it.kind == ReminderKind.Tablets && it.enabled },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TabletsUiState())

    fun toggle(dose: Dose) = viewModelScope.launch {
        repository.setDoseTaken(
            dose.medication.id,
            state.value.today,
            dose.slotMinutes,
            taken = dose.status != DoseStatus.Taken,
        )
    }

    fun add() {
        _editing.value = MedicationEntity(profileId = 0, name = "", times = "${8 * 60 + 30}")
    }

    fun edit(medication: MedicationEntity) {
        _editing.value = medication
    }

    fun close() {
        _editing.value = null
    }

    fun save(medication: MedicationEntity) = viewModelScope.launch {
        val profileId = repository.settings.first().activeProfileId
        if (profileId == 0L || medication.name.isBlank() || Doses.parseTimes(medication.times).isEmpty()) return@launch
        val count = repository.medications.first().size
        repository.saveMedication(
            medication.copy(
                profileId = profileId,
                name = medication.name.trim(),
                note = medication.note.trim(),
                colorIndex = if (medication.id == 0L) count else medication.colorIndex,
            )
        )
        _editing.value = null
    }

    fun delete(medication: MedicationEntity) = viewModelScope.launch {
        repository.deleteMedication(medication.id)
        _editing.value = null
    }
}
