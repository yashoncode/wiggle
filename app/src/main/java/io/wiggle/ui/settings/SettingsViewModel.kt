package io.wiggle.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.wiggle.data.CsvExporter
import io.wiggle.data.WiggleRepository
import io.wiggle.data.db.ProfileEntity
import io.wiggle.data.db.ReminderEntity
import io.wiggle.data.db.ReminderKind
import io.wiggle.data.db.Sex
import io.wiggle.data.prefs.Settings
import io.wiggle.domain.LengthUnit
import io.wiggle.domain.VolumeUnit
import io.wiggle.domain.WeightUnit
import io.wiggle.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val loading: Boolean = true,
    val settings: Settings = Settings(),
    val profile: ProfileEntity? = null,
    val profiles: List<ProfileEntity> = emptyList(),
    /** Always all three kinds, in a fixed order, even if the database is missing a row. */
    val reminders: List<ReminderEntity> = emptyList(),
)

/** Which sheet the settings screen has open. Only one can be open at a time. */
sealed interface SettingsSheet {
    data object None : SettingsSheet
    data class Reminder(val kind: ReminderKind) : SettingsSheet
    data object AddPerson : SettingsSheet
    data object EditPerson : SettingsSheet
    data object WaterGoal : SettingsSheet
    data object GoalWeight : SettingsSheet
    data object ConfirmWipe : SettingsSheet
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: WiggleRepository,
    private val csvExporter: CsvExporter,
) : ViewModel() {

    /** Files waiting to be handed to the share sheet. The screen clears this once it has. */
    private val _exportFiles = MutableStateFlow<List<Uri>>(emptyList())
    val exportFiles: StateFlow<List<Uri>> = _exportFiles.asStateFlow()

    private val _sheet = MutableStateFlow<SettingsSheet>(SettingsSheet.None)
    val sheet: StateFlow<SettingsSheet> = _sheet.asStateFlow()

    val state: StateFlow<SettingsUiState> = combine(
        repository.settings,
        repository.profiles,
        repository.activeProfile,
        repository.reminders,
    ) { settings, profiles, profile, reminders ->
        SettingsUiState(
            loading = false,
            settings = settings,
            profile = profile,
            profiles = profiles,
            reminders = ReminderKind.entries.map { kind ->
                reminders.firstOrNull { it.kind == kind }
                    ?: defaultReminder(settings.activeProfileId, kind)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun openSheet(sheet: SettingsSheet) = _sheet.update { sheet }

    fun closeSheet() = _sheet.update { SettingsSheet.None }

    // --- appearance and units ------------------------------------------------------------------

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repository.setThemeMode(mode) }

    fun setWeightUnit(unit: WeightUnit) = viewModelScope.launch { repository.setWeightUnit(unit) }

    fun setLengthUnit(unit: LengthUnit) = viewModelScope.launch { repository.setLengthUnit(unit) }

    fun setVolumeUnit(unit: VolumeUnit) = viewModelScope.launch { repository.setVolumeUnit(unit) }

    fun setHaptics(enabled: Boolean) = viewModelScope.launch { repository.setHapticsEnabled(enabled) }

    // --- alerts --------------------------------------------------------------------------------

    /** Saves a reminder, inserting it first if this profile never had that row. */
    fun saveReminder(reminder: ReminderEntity) = viewModelScope.launch {
        val profileId = repository.settings.first().activeProfileId
        if (profileId == 0L) return@launch
        repository.saveReminder(reminder.copy(profileId = profileId))
    }

    fun setReminderEnabled(kind: ReminderKind, enabled: Boolean) = viewModelScope.launch {
        val profileId = repository.settings.first().activeProfileId
        if (profileId == 0L) return@launch
        val existing = repository.reminder(profileId, kind) ?: defaultReminder(profileId, kind)
        repository.saveReminder(existing.copy(enabled = enabled))
    }

    fun exportCsv() = viewModelScope.launch {
        val profileId = repository.settings.first().activeProfileId
        if (profileId == 0L) return@launch
        _exportFiles.value = csvExporter.export(profileId)
    }

    fun exportHandled() = _exportFiles.update { emptyList() }

    // --- person --------------------------------------------------------------------------------

    fun updateProfile(profile: ProfileEntity) = viewModelScope.launch {
        repository.updateProfile(profile)
    }

    fun setWaterGoal(ml: Int) = viewModelScope.launch {
        state.value.profile?.let { repository.updateProfile(it.copy(dailyWaterGoalMl = ml)) }
    }

    fun setGoalWeight(kg: Double?) = viewModelScope.launch {
        state.value.profile?.let { repository.updateProfile(it.copy(goalWeightKg = kg)) }
    }

    /** Creates a person and switches to them, because that is always why you added one. */
    fun addPerson(name: String, heightCm: Double, sex: Sex, goalWeightKg: Double?) =
        viewModelScope.launch {
            val id = repository.createProfile(
                name = name.trim().ifBlank { "Someone" },
                heightCm = heightCm,
                sex = sex,
                goalWeightKg = goalWeightKg,
            )
            repository.selectProfile(id)
            closeSheet()
        }

    fun selectProfile(id: Long) = viewModelScope.launch { repository.selectProfile(id) }

    fun deletePerson(profile: ProfileEntity) = viewModelScope.launch {
        repository.deleteProfile(profile)
        closeSheet()
    }

    fun wipeData() = viewModelScope.launch {
        val profileId = repository.settings.first().activeProfileId
        if (profileId != 0L) repository.deleteAllDataFor(profileId)
        closeSheet()
    }

    private fun defaultReminder(profileId: Long, kind: ReminderKind) = when (kind) {
        ReminderKind.WeighIn -> ReminderEntity(
            profileId = profileId,
            kind = kind,
            enabled = false,
            timeMinutes = 7 * 60,
        )

        ReminderKind.Measurements -> ReminderEntity(
            profileId = profileId,
            kind = kind,
            enabled = false,
            timeMinutes = 8 * 60,
            daysMask = 1 shl 6,
        )

        ReminderKind.Water -> ReminderEntity(
            profileId = profileId,
            kind = kind,
            enabled = false,
            timeMinutes = 8 * 60,
            intervalMinutes = 120,
            untilMinutes = 22 * 60,
        )
    }
}
