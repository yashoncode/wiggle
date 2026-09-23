package io.wiggle.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.wiggle.data.WiggleRepository
import io.wiggle.data.db.ProfileEntity
import io.wiggle.data.db.WeightEntryEntity
import io.wiggle.data.prefs.Settings
import io.wiggle.domain.DayWeight
import io.wiggle.domain.Stats
import io.wiggle.domain.todayFlow
import io.wiggle.ui.charts.ChartPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class TrendRange(val label: String, val days: Long?) {
    Week("1W", 7),
    Month("1M", 30),
    Quarter("3M", 90),
    HalfYear("6M", 182),
    Year("1Y", 365),
    All("All", null),
}

data class TrendsUiState(
    val range: TrendRange = TrendRange.Quarter,
    val settings: Settings = Settings(),
    val profile: ProfileEntity? = null,
    val points: List<ChartPoint> = emptyList(),
    val average: List<ChartPoint> = emptyList(),
    val goalKg: Double? = null,
    val averageKg: Double? = null,
    val rangeLabel: String = "",
    val weeklyRateKg: Double? = null,
    val totalChangeKg: Double? = null,
    val lowestKg: Double? = null,
    val highestKg: Double? = null,
    val projectedGoalDate: LocalDate? = null,
    val distanceToGoalKg: Double? = null,
    /** Average weight per weekday, Monday first; null where there are no readings. */
    val weekdayAverages: List<Double?> = emptyList(),
    val streakDays: Int = 0,
    val history: List<WeightEntryEntity> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val repository: WiggleRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val range = MutableStateFlow(TrendRange.Quarter)

    /** Entries pending deletion, so the undo snackbar can put one back. */
    private val _lastDeleted = MutableStateFlow<WeightEntryEntity?>(null)
    val lastDeleted: StateFlow<WeightEntryEntity?> = _lastDeleted

    val state: StateFlow<TrendsUiState> = combine(
        repository.weightEntries,
        repository.activeProfile,
        repository.settings,
        range,
        todayFlow(zone),
    ) { entries, profile, settings, selectedRange, today ->
        val cutoff = selectedRange.days?.let { today.minusDays(it - 1) }
        val inRange = entries.filter {
            cutoff == null || it.measuredAt.toLocalDate() >= cutoff
        }
        val daily = Stats.toDaily(inRange.map { DayWeight(it.measuredAt.toLocalDate(), it.weightKg) })
        val average = Stats.movingAverage(daily)
        val goal = profile?.goalWeightKg
        val current = daily.lastOrNull()?.kg

        TrendsUiState(
            range = selectedRange,
            settings = settings,
            profile = profile,
            points = daily.map { ChartPoint(it.date, it.kg) },
            average = average.map { ChartPoint(it.date, it.value) },
            goalKg = goal,
            averageKg = daily.takeIf { it.isNotEmpty() }?.map { it.kg }?.average(),
            rangeLabel = rangeLabel(daily.firstOrNull()?.date, daily.lastOrNull()?.date),
            weeklyRateKg = Stats.weeklyRateKg(daily),
            totalChangeKg = Stats.totalChangeKg(daily),
            lowestKg = daily.minOfOrNull { it.kg },
            highestKg = daily.maxOfOrNull { it.kg },
            projectedGoalDate = goal?.let { Stats.projectedGoalDate(daily, it, today) },
            distanceToGoalKg = if (current != null && goal != null) current - goal else null,
            weekdayAverages = Stats.weekdayAverages(daily),
            // The streak is a property of the whole log, not of the selected window.
            streakDays = Stats.currentStreak(
                Stats.toDaily(entries.map { DayWeight(it.measuredAt.toLocalDate(), it.weightKg) }),
                today,
            ),
            history = inRange.sortedByDescending { it.measuredAt },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendsUiState())

    fun setRange(value: TrendRange) {
        range.value = value
    }

    fun delete(entry: WeightEntryEntity) = viewModelScope.launch {
        repository.deleteWeight(entry.id)
        _lastDeleted.value = entry
    }

    fun undoDelete() = viewModelScope.launch {
        _lastDeleted.value?.let { repository.restoreWeight(it) }
        _lastDeleted.value = null
    }

    fun clearUndo() {
        _lastDeleted.value = null
    }

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    private fun rangeLabel(from: LocalDate?, to: LocalDate?): String {
        if (from == null || to == null) return ""
        val short = java.time.format.DateTimeFormatter.ofPattern("d MMM")
        val long = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")
        return "${from.format(short)} – ${to.format(long)}"
    }
}
