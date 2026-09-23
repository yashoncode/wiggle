package io.wiggle.ui.water

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.wiggle.data.WiggleRepository
import io.wiggle.data.db.ProfileEntity
import io.wiggle.data.db.WaterEntryEntity
import io.wiggle.data.prefs.Settings
import io.wiggle.domain.todayFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** One day's water in the weekly chart. */
data class WaterDay(val date: LocalDate, val totalMl: Int)

data class WaterUiState(
    val loading: Boolean = true,
    val profile: ProfileEntity? = null,
    val settings: Settings = Settings(),
    val totalMl: Int = 0,
    val goalMl: Int = 2500,
    /** Today's log, newest first. */
    val entries: List<WaterEntryEntity> = emptyList(),
    /** The last seven days ending today, oldest first. */
    val week: List<WaterDay> = emptyList(),
    /** Bumped on every add, which is what launches the bubbles in the bottle. */
    val bubbleKey: Long? = null,
) {
    val progress: Float get() = if (goalMl <= 0) 0f else totalMl.toFloat() / goalMl
    val remainingMl: Int get() = (goalMl - totalMl).coerceAtLeast(0)
    val goalMet: Boolean get() = totalMl >= goalMl
}

/** Everything that rolls over at midnight, kept together so it re-queries as one. */
private data class WaterDayData(
    val today: LocalDate,
    val entries: List<WaterEntryEntity>,
    val week: List<WaterEntryEntity>,
)

@HiltViewModel
class WaterViewModel @Inject constructor(
    private val repository: WiggleRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** The preset buttons. Millilitres, converted for display only. */
    val presets = listOf(150, 250, 500)

    private val bubbleKey = MutableStateFlow<Long?>(null)

    /** A swiped-away entry, held so the snackbar can put it back. */
    private val _lastDeleted = MutableStateFlow<WaterEntryEntity?>(null)
    val lastDeleted: StateFlow<WaterEntryEntity?> = _lastDeleted.asStateFlow()

    private val dayData = todayFlow(zone).flatMapLatest { today ->
        combine(
            repository.waterOn(today, zone),
            repository.waterBetween(today.minusDays(6), today.plusDays(1), zone),
        ) { entries, week -> WaterDayData(today, entries, week) }
    }

    val state: StateFlow<WaterUiState> = combine(
        dayData,
        repository.activeProfile,
        repository.settings,
        bubbleKey,
    ) { day, profile, settings, bubbles ->
        val byDate = day.week.groupBy { Instant.ofEpochMilli(it.loggedAt).atZone(zone).toLocalDate() }
        WaterUiState(
            loading = false,
            profile = profile,
            settings = settings,
            totalMl = day.entries.sumOf { it.amountMl },
            goalMl = profile?.dailyWaterGoalMl ?: 2500,
            entries = day.entries,
            week = (6 downTo 0).map { offset ->
                val date = day.today.minusDays(offset.toLong())
                WaterDay(date, byDate[date].orEmpty().sumOf { it.amountMl })
            },
            bubbleKey = bubbles,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WaterUiState())

    /**
     * Logs a drink. [onGoalReached] fires only on the add that crosses the goal, so the confetti
     * comes once a day rather than on every sip after it.
     */
    fun add(amountMl: Int, onGoalReached: () -> Unit = {}) = viewModelScope.launch {
        val profileId = repository.settings.first().activeProfileId
        if (profileId == 0L || amountMl <= 0) return@launch

        val goal = repository.getProfile(profileId)?.dailyWaterGoalMl ?: 2500
        val before = repository.waterTotalToday(profileId, zone)
        repository.addWater(profileId, amountMl)
        bubbleKey.value = System.currentTimeMillis()
        if (before < goal && before + amountMl >= goal) onGoalReached()
    }

    /** Removes the most recent drink, for the tap that was one too many. */
    fun undoLastAdd() = viewModelScope.launch {
        state.value.entries.firstOrNull()?.let { repository.deleteWater(it.id) }
    }

    fun delete(entry: WaterEntryEntity) = viewModelScope.launch {
        repository.deleteWater(entry.id)
        _lastDeleted.value = entry
    }

    fun undoDelete() = viewModelScope.launch {
        _lastDeleted.value?.let { repository.addWater(it.profileId, it.amountMl, it.loggedAt) }
        _lastDeleted.value = null
    }

    fun clearUndo() {
        _lastDeleted.value = null
    }
}
