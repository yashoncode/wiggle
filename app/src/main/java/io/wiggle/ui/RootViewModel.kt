package io.wiggle.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.wiggle.data.WiggleRepository
import io.wiggle.data.db.ProfileEntity
import io.wiggle.data.prefs.Settings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
) : ViewModel() {

    val state: StateFlow<RootUiState> = combine(
        repository.settings,
        repository.profiles,
        repository.activeProfile,
    ) { settings, profiles, active ->
        RootUiState(settings = settings, profiles = profiles, activeProfile = active, ready = true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootUiState())

    fun selectProfile(id: Long) = viewModelScope.launch { repository.selectProfile(id) }
}
