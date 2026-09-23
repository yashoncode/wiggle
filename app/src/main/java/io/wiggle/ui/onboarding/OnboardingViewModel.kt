package io.wiggle.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.wiggle.data.WiggleRepository
import io.wiggle.data.db.Sex
import io.wiggle.domain.LengthUnit
import io.wiggle.domain.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The setup steps, in order. */
enum class OnboardingStep { Welcome, Name, Body, Goal, FirstWeight }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val name: String = "",
    val weightUnit: WeightUnit = WeightUnit.Kg,
    val lengthUnit: LengthUnit = LengthUnit.Cm,
    val heightCm: Double = 170.0,
    val sex: Sex = Sex.Unspecified,
    val goalKg: Double? = null,
    val currentKg: Double? = null,
    val saving: Boolean = false,
) {
    val stepIndex: Int get() = OnboardingStep.entries.indexOf(step)
    val canContinue: Boolean
        get() = step != OnboardingStep.Name || name.isNotBlank()
}

/**
 * First run.
 *
 * Every step can be skipped: someone who just wants to see the app should not have to answer
 * five questions first, and everything asked here is editable later in Settings.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: WiggleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun setName(value: String) = _state.update { it.copy(name = value.take(24)) }
    fun setWeightUnit(unit: WeightUnit) = _state.update { it.copy(weightUnit = unit) }
    fun setLengthUnit(unit: LengthUnit) = _state.update { it.copy(lengthUnit = unit) }
    fun setHeight(cm: Double) = _state.update { it.copy(heightCm = cm) }
    fun setSex(sex: Sex) = _state.update { it.copy(sex = sex) }
    fun setGoal(kg: Double?) = _state.update { it.copy(goalKg = kg) }
    fun setCurrent(kg: Double?) = _state.update { it.copy(currentKg = kg) }

    fun back() = _state.update { state ->
        val previous = OnboardingStep.entries.getOrNull(state.stepIndex - 1) ?: return@update state
        state.copy(step = previous)
    }

    /** Moves on, or finishes when there is nothing after this step. */
    fun next(onDone: () -> Unit) {
        val state = _state.value
        val following = OnboardingStep.entries.getOrNull(state.stepIndex + 1)
        if (following == null) finish(onDone) else _state.update { it.copy(step = following) }
    }

    /** Skips the rest of setup, keeping whatever has been answered so far. */
    fun skip(onDone: () -> Unit) = finish(onDone)

    private fun finish(onDone: () -> Unit) = viewModelScope.launch {
        val state = _state.value
        if (state.saving) return@launch
        _state.update { it.copy(saving = true) }

        val id = repository.createProfile(
            name = state.name.trim().ifBlank { "Me" },
            heightCm = state.heightCm,
            sex = state.sex,
            goalWeightKg = state.goalKg,
        )
        repository.selectProfile(id)
        repository.setWeightUnit(state.weightUnit)
        repository.setLengthUnit(state.lengthUnit)
        state.currentKg?.let { kg ->
            repository.addWeight(profileId = id, weightKg = kg, measuredAt = System.currentTimeMillis())
        }
        repository.setOnboardingComplete(true)
        onDone()
    }
}
