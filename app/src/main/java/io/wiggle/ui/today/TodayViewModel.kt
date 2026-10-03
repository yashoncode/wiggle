package io.wiggle.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.wiggle.data.HealthSteps
import io.wiggle.data.StepsState
import io.wiggle.data.WiggleRepository
import io.wiggle.data.db.FoodEntryEntity
import io.wiggle.data.db.ProfileEntity
import io.wiggle.data.db.ReminderEntity
import io.wiggle.data.db.WaterEntryEntity
import io.wiggle.data.db.WeightEntryEntity
import io.wiggle.data.kcal
import io.wiggle.data.prefs.Settings
import io.wiggle.domain.CalorieBudget
import io.wiggle.domain.DayWeight
import io.wiggle.domain.Nutrition
import io.wiggle.domain.ReminderSchedule
import io.wiggle.domain.Stats
import io.wiggle.domain.todayFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

data class TodayUiState(
    val loading: Boolean = true,
    val profile: ProfileEntity? = null,
    val settings: Settings = Settings(),
    val currentKg: Double? = null,
    val weekChangeKg: Double? = null,
    /** Last 30 calendar days of readings, oldest first, for the hero sparkline. */
    val sparkline: List<Double> = emptyList(),
    val startKg: Double? = null,
    val goalKg: Double? = null,
    val goalProgress: Float = 0f,
    val bmi: Double? = null,
    val bmiCategory: Stats.BmiCategory? = null,
    val calories: CalorieBudget? = null,
    /** Null when the steps on this phone are not this person's, or are not connected. */
    val steps: Long? = null,
    val stepGoal: Int = 10_000,
    val waterMl: Int = 0,
    val waterGoalMl: Int = 2500,
    /** Eaten less maintenance and walking over the last seven full days; null with no food logged. */
    val weekBalanceKcal: Int? = null,
    val projectedGoalDate: LocalDate? = null,
    /** Something is set to remind today, which is what the dot on the bell means. */
    val reminderToday: Boolean = false,
)

/** Everything that changes with the day rather than with a setting. */
private data class DayData(
    val today: LocalDate,
    val weights: List<WeightEntryEntity>,
    val water: List<WaterEntryEntity>,
    val food: List<FoodEntryEntity>,
    val reminders: List<ReminderEntity>,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    repository: WiggleRepository,
    healthSteps: HealthSteps,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    // Re-queried at midnight so "today" does not go stale on a phone left open.
    private val dayData = todayFlow(zone).flatMapLatest { today ->
        combine(
            repository.weightEntries,
            repository.waterOn(today, zone),
            repository.foodBetween(today.minusDays(7), today.plusDays(1), zone),
            repository.reminders,
        ) { weights, water, food, reminders -> DayData(today, weights, water, food, reminders) }
    }

    val state: StateFlow<TodayUiState> = combine(
        dayData,
        repository.activeProfile,
        repository.settings,
        healthSteps.state,
    ) { day, profile, settings, steps ->
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
        val ownSteps = steps.takeIf { profile != null && settings.stepsProfileId == profile.id }

        TodayUiState(
            loading = false,
            profile = profile,
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
            bmi = bmi,
            bmiCategory = bmi?.let(Stats::bmiCategory),
            calories = profile?.let { budget(it, current, day, ownSteps) },
            steps = ownSteps?.today,
            stepGoal = profile?.dailyStepGoal ?: 10_000,
            waterMl = day.water.sumOf { it.amountMl },
            waterGoalMl = profile?.dailyWaterGoalMl ?: 2500,
            weekBalanceKcal = profile?.let { weekBalance(it, current, day, ownSteps) },
            projectedGoalDate = goal?.let {
                Stats.projectedGoalDate(daily.filter { d -> d.date >= day.today.minusDays(29) }, it, day.today)
            },
            reminderToday = day.reminders.any {
                ReminderSchedule.nextOccurrence(it, zone = zone)?.toLocalDate() == day.today
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    private fun budget(profile: ProfileEntity, currentKg: Double?, day: DayData, steps: StepsState?) =
        CalorieBudget(
            goal = Nutrition.goalFor(profile, currentKg, day.today),
            eaten = day.food.filter { it.loggedAt.toLocalDate() == day.today }.sumOf { it.kcal },
            walked = steps?.let { Nutrition.stepKcal(it.today, profile.heightCm, profile.sex, currentKg) } ?: 0,
        )

    private fun weekBalance(profile: ProfileEntity, currentKg: Double?, day: DayData, steps: StepsState?): Int? {
        val weight = currentKg ?: return null
        val maintenance = Nutrition.maintenanceKcal(
            weight,
            profile.heightCm,
            Nutrition.age(profile.birthYear, day.today),
            profile.sex,
        ).roundToInt()
        // Finished days only: half of today's meals would read as a deficit that is not there.
        val eaten = day.food.groupBy { it.loggedAt.toLocalDate() }
            .filterKeys { it < day.today }
            .mapValues { (_, entries) -> entries.sumOf { it.kcal } }
        val walked = steps?.daily.orEmpty().mapValues { (_, count) ->
            Nutrition.stepKcal(count, profile.heightCm, profile.sex, weight)
        }
        return Nutrition.balance(eaten, walked, maintenance)
    }

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}
