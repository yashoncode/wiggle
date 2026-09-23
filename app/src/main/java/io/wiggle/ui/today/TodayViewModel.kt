package io.wiggle.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.wiggle.data.WiggleRepository
import io.wiggle.data.db.ProfileEntity
import io.wiggle.data.db.ReminderEntity
import io.wiggle.data.db.ReminderKind
import io.wiggle.data.db.WaterEntryEntity
import io.wiggle.data.db.WeightEntryEntity
import io.wiggle.data.prefs.Settings
import io.wiggle.domain.DayWeight
import io.wiggle.domain.ReminderSchedule
import io.wiggle.domain.Stats
import io.wiggle.domain.todayFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

data class UpcomingReminder(
    val kind: ReminderKind,
    val title: String,
    val subtitle: String,
    val at: LocalDateTime,
)

data class TodayUiState(
    val loading: Boolean = true,
    val profile: ProfileEntity? = null,
    val profiles: List<ProfileEntity> = emptyList(),
    val settings: Settings = Settings(),
    val currentKg: Double? = null,
    val weekChangeKg: Double? = null,
    /** Last 30 calendar days of readings, oldest first, for the hero sparkline. */
    val sparkline: List<Double> = emptyList(),
    val startKg: Double? = null,
    val goalKg: Double? = null,
    val goalProgress: Float = 0f,
    val waterMl: Int = 0,
    val waterGoalMl: Int = 2500,
    val bmi: Double? = null,
    val bmiCategory: Stats.BmiCategory? = null,
    val upcoming: List<UpcomingReminder> = emptyList(),
)

/** Everything that changes with the day rather than with a setting. */
private data class DayData(
    val today: LocalDate,
    val weights: List<WeightEntryEntity>,
    val water: List<WaterEntryEntity>,
    val reminders: List<ReminderEntity>,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: WiggleRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    // Water is re-queried at midnight so "today" does not go stale on a phone left open.
    private val dayData = todayFlow(zone).flatMapLatest { today ->
        combine(
            repository.weightEntries,
            repository.waterOn(today, zone),
            repository.reminders,
        ) { weights, water, reminders -> DayData(today, weights, water, reminders) }
    }

    val state: StateFlow<TodayUiState> = combine(
        dayData,
        repository.activeProfile,
        repository.profiles,
        repository.settings,
    ) { day, profile, profiles, settings ->
        val daily = Stats.toDaily(
            day.weights.map { DayWeight(it.measuredAt.toLocalDate(), it.weightKg) }
        )
        val current = daily.lastOrNull()?.kg

        // Change this week: the latest reading against the closest one seven or more days back.
        val weekAgo = day.today.minusDays(7)
        val reference = daily.lastOrNull { it.date <= weekAgo }
        val weekChange = if (current != null && reference != null) current - reference.kg else null

        val goal = profile?.goalWeightKg
        val start = profile?.startWeightKg ?: daily.firstOrNull()?.kg
        val bmi = if (current != null && profile != null) Stats.bmi(current, profile.heightCm) else null

        TodayUiState(
            loading = false,
            profile = profile,
            profiles = profiles,
            settings = settings,
            currentKg = current,
            weekChangeKg = weekChange,
            sparkline = daily.filter { it.date >= day.today.minusDays(29) }.map { it.kg },
            startKg = start,
            goalKg = goal,
            goalProgress = if (current != null && goal != null && start != null) {
                Stats.goalProgress(start, current, goal)
            } else {
                0f
            },
            waterMl = day.water.sumOf { it.amountMl },
            waterGoalMl = profile?.dailyWaterGoalMl ?: 2500,
            bmi = bmi,
            bmiCategory = bmi?.let(Stats::bmiCategory),
            upcoming = day.reminders
                .mapNotNull { reminder ->
                    ReminderSchedule.nextOccurrence(reminder, zone = zone)?.let { at ->
                        UpcomingReminder(
                            kind = reminder.kind,
                            title = ReminderSchedule.title(reminder.kind),
                            subtitle = relativeLabel(at, reminder.kind),
                            at = at,
                        )
                    }
                }
                .sortedBy { it.at }
                .take(3),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun selectProfile(id: Long) = viewModelScope.launch { repository.selectProfile(id) }

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    private fun relativeLabel(at: LocalDateTime, kind: ReminderKind): String {
        val now = LocalDateTime.now(zone)
        val minutes = Duration.between(now, at).toMinutes()
        val clock = ReminderSchedule.timeLabel(at.hour * 60 + at.minute)
        val whenText = when {
            minutes < 60 -> "in ${minutes.coerceAtLeast(1)} min"
            at.toLocalDate() == now.toLocalDate() -> "Today · $clock"
            at.toLocalDate() == now.toLocalDate().plusDays(1) -> "Tomorrow · $clock"
            else -> "${at.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} · $clock"
        }
        return if (kind == ReminderKind.Water) "250 ml · $whenText" else whenText
    }
}
