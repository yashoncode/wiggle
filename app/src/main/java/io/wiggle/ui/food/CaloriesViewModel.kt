package io.wiggle.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.wiggle.data.HealthSteps
import io.wiggle.data.WiggleRepository
import io.wiggle.data.carbsG
import io.wiggle.data.db.FoodEntryEntity
import io.wiggle.data.db.Meal
import io.wiggle.data.fatG
import io.wiggle.data.kcal
import io.wiggle.data.proteinG
import io.wiggle.domain.CalorieBudget
import io.wiggle.domain.Macros
import io.wiggle.domain.Nutrition
import io.wiggle.domain.todayFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class MealGroup(val meal: Meal, val entries: List<FoodEntryEntity>) {
    val kcal: Int get() = entries.sumOf { it.kcal }
}

data class CaloriesUiState(
    val loading: Boolean = true,
    val budget: CalorieBudget? = null,
    val macroGoals: Macros? = null,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val meals: List<MealGroup> = Meal.entries.map { MealGroup(it, emptyList()) },
    /** What an unlogged main meal could still be, so the day lands on the goal. */
    val perMealLeft: Int? = null,
)

@HiltViewModel
class CaloriesViewModel @Inject constructor(
    private val repository: WiggleRepository,
    healthSteps: HealthSteps,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** A swiped-away entry, held so the snackbar can put it back. */
    private val _lastDeleted = MutableStateFlow<FoodEntryEntity?>(null)
    val lastDeleted: StateFlow<FoodEntryEntity?> = _lastDeleted.asStateFlow()

    private val today = todayFlow(zone)

    val state: StateFlow<CaloriesUiState> = combine(
        today.flatMapLatest { repository.foodOn(it, zone) },
        repository.activeProfile,
        repository.latestWeight,
        repository.settings,
        healthSteps.state,
    ) { food, profile, latest, settings, steps ->
        if (profile == null) return@combine CaloriesUiState(loading = false)
        val ownSteps = steps.takeIf { settings.stepsProfileId == profile.id }
        val budget = CalorieBudget(
            goal = Nutrition.goalFor(profile, latest?.weightKg),
            eaten = food.sumOf { it.kcal },
            walked = ownSteps?.let {
                Nutrition.stepKcal(it.today, profile.heightCm, profile.sex, latest?.weightKg)
            } ?: 0,
        )
        val meals = Meal.entries.map { meal -> MealGroup(meal, food.filter { it.meal == meal }) }
        val openMains = meals.count { it.entries.isEmpty() && it.meal != Meal.Snacks }
        CaloriesUiState(
            loading = false,
            budget = budget,
            macroGoals = Nutrition.macroGoals(budget.goal),
            proteinG = food.sumOf { it.proteinG },
            carbsG = food.sumOf { it.carbsG },
            fatG = food.sumOf { it.fatG },
            meals = meals,
            perMealLeft = if (openMains > 0 && budget.left > 0) budget.left / openMains else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaloriesUiState())

    fun delete(entry: FoodEntryEntity) = viewModelScope.launch {
        repository.deleteFood(entry.id)
        _lastDeleted.value = entry
    }

    fun undoDelete() = viewModelScope.launch {
        _lastDeleted.value?.let { repository.restoreFood(it) }
        _lastDeleted.value = null
    }

    fun clearUndo() {
        _lastDeleted.value = null
    }

    companion object {
        /** The meal a new entry most likely belongs to at this time of day. */
        fun mealForNow(now: LocalTime = LocalTime.now()): Meal = when {
            now.isBefore(LocalTime.of(10, 30)) -> Meal.Breakfast
            now.isBefore(LocalTime.of(15, 0)) -> Meal.Lunch
            now.isBefore(LocalTime.of(18, 30)) -> Meal.Snacks
            else -> Meal.Dinner
        }
    }
}
