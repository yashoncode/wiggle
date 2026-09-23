package io.wiggle.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.wiggle.data.UpdateChecker
import io.wiggle.domain.AppRelease
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateUiState(
    val release: AppRelease? = null,
    val checking: Boolean = false,
    /** Set only by a check the person asked for, so a silent one stays silent. */
    val upToDate: Boolean = false,
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checker: UpdateChecker,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    init {
        check(announce = false)
    }

    /**
     * [announce] is what separates the check on launch from the one in Settings: both offer an
     * update, only the asked-for one reports that there is nothing to install.
     */
    fun check(announce: Boolean = true) {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true, upToDate = false) }
        viewModelScope.launch {
            val release = checker.newerRelease()
            _state.value = UpdateUiState(
                release = release,
                checking = false,
                upToDate = announce && release == null,
            )
        }
    }

    fun dismiss() {
        _state.value = UpdateUiState()
    }
}
