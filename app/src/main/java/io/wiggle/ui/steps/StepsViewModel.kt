package io.wiggle.ui.steps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.wiggle.data.HealthSteps
import io.wiggle.data.StepsAvailability
import io.wiggle.data.WiggleRepository
import io.wiggle.data.db.ProfileEntity
import io.wiggle.domain.LengthUnit
import io.wiggle.domain.Nutrition
import io.wiggle.domain.StepStats
import io.wiggle.domain.todayFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class StepsUiState(
    val availability: StepsAvailability = StepsAvailability.Checking,
    val profile: ProfileEntity? = null,
    /** Who the phone's steps belong to, when that is someone other than the person shown. */
    val ownerName: String? = null,
    val today: Long = 0,
    val goal: Int = 10_000,
    val hourly: List<Long> = List(24) { 0L },
    /** The last seven days ending today; null where nothing was recorded. */
    val week: List<Pair<LocalDate, Long?>> = emptyList(),
    val streak: Int = 0,
    val distanceKm: Double = 0.0,
    val burnedKcal: Int = 0,
    val activeMinutes: Int = 0,
    val lengthUnit: LengthUnit = LengthUnit.Cm,
    val syncedAt: Instant? = null,
) {
    /** True when this person's steps are on screen. */
    val counting: Boolean get() = availability == StepsAvailability.Ready && ownerName == null
}

@HiltViewModel
class StepsViewModel @Inject constructor(
    private val repository: WiggleRepository,
    private val healthSteps: HealthSteps,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** The permissions the connect button asks for, worked out once Health Connect answers. */
    val permissions = MutableStateFlow(setOf(healthSteps.readSteps))

    init {
        viewModelScope.launch { permissions.value = healthSteps.permissionsToRequest() }
    }

    val state: StateFlow<StepsUiState> = combine(
        healthSteps.state,
        repository.activeProfile,
        repository.profiles,
        repository.settings,
        combine(repository.latestWeight, todayFlow(zone)) { weight, today -> weight to today },
    ) { steps, profile, profiles, settings, (latest, today) ->
        val owner = profiles.firstOrNull { it.id == settings.stepsProfileId }
        val mine = profile != null && settings.stepsProfileId == profile.id
        val count = if (mine) steps.today else 0L
        val height = profile?.heightCm ?: 170.0
        val sex = profile?.sex ?: io.wiggle.data.db.Sex.Unspecified
        val goal = profile?.dailyStepGoal ?: 10_000
        StepsUiState(
            availability = steps.availability,
            profile = profile,
            ownerName = if (mine || owner == null) null else owner.name,
            today = count,
            goal = goal,
            hourly = if (mine) steps.hourly else List(24) { 0L },
            week = (6 downTo 0).map { offset ->
                val date = today.minusDays(offset.toLong())
                date to if (mine) steps.daily[date] else null
            },
            streak = if (mine) StepStats.streak(steps.daily, goal, today) else 0,
            distanceKm = Nutrition.distanceKm(count, height, sex),
            burnedKcal = Nutrition.stepKcal(count, height, sex, latest?.weightKg),
            activeMinutes = Nutrition.activeMinutes(count),
            lengthUnit = settings.lengthUnit,
            syncedAt = steps.syncedAt,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StepsUiState())

    fun refresh() = viewModelScope.launch { healthSteps.refresh() }

    /**
     * After the permission screen: the phone's steps become this person's, which is who was on
     * screen when they connected.
     */
    fun onPermissionResult(granted: Set<String>) = viewModelScope.launch {
        if (healthSteps.readSteps in granted) claimSteps()
        healthSteps.refresh()
    }

    /** Hands the phone's step count to the person on screen. */
    fun claimSteps() = viewModelScope.launch {
        val id = repository.settings.first().activeProfileId
        if (id != 0L) repository.setStepsProfile(id)
    }

    fun installIntent() = healthSteps.installIntent()
}
