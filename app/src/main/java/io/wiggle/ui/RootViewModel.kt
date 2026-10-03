package io.wiggle.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.wiggle.data.HealthSteps
import io.wiggle.data.StepsAvailability
import io.wiggle.data.WiggleRepository
import io.wiggle.data.db.ProfileEntity
import io.wiggle.data.prefs.Settings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RootUiState(
    val settings: Settings = Settings(),
    val profiles: List<ProfileEntity> = emptyList(),
    val activeProfile: ProfileEntity? = null,
    val ready: Boolean = false,
)

@HiltViewModel
class RootViewModel @Inject constructor(
    private val repository: WiggleRepository,
    private val healthSteps: HealthSteps,
) : ViewModel() {

    val state: StateFlow<RootUiState> = combine(
        repository.settings,
        repository.profiles,
        repository.activeProfile,
    ) { settings, profiles, active ->
        RootUiState(settings = settings, profiles = profiles, activeProfile = active, ready = true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootUiState())

    fun selectProfile(id: Long) = viewModelScope.launch { repository.selectProfile(id) }

    /**
     * Re-reads steps. When reading works but nobody owns the phone's steps yet (permission given in
     * Health Connect itself, or an update from 1.x), they go to whoever is on screen.
     */
    fun refreshSteps() = viewModelScope.launch {
        healthSteps.refresh()
        val settings = repository.settings.first()
        if (healthSteps.state.value.availability == StepsAvailability.Ready &&
            settings.stepsProfileId == 0L && settings.activeProfileId != 0L
        ) {
            repository.setStepsProfile(settings.activeProfileId)
        }
    }
}
