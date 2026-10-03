package io.wiggle.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.wiggle.data.CsvExporter
import io.wiggle.data.HealthSteps
import io.wiggle.data.StepsAvailability
import io.wiggle.data.WiggleRepository
import io.wiggle.data.db.ProfileEntity
import io.wiggle.data.db.ReminderEntity
import io.wiggle.data.db.ReminderKind
import io.wiggle.data.db.Sex
import io.wiggle.data.defaultReminder
import io.wiggle.data.prefs.Settings
import io.wiggle.domain.LengthUnit
import io.wiggle.domain.Nutrition
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
import java.time.LocalDate
import javax.inject.Inject

data class SettingsUiState(
    val loading: Boolean = true,
    val settings: Settings = Settings(),
    val profile: ProfileEntity? = null,
    val profiles: List<ProfileEntity> = emptyList(),
    /** Always every kind, in a fixed order, even if the database is missing a row. */
    val reminders: List<ReminderEntity> = emptyList(),
    val latestKg: Double? = null,
    /** The calorie goal in force: the person's own, or the one worked out for them. */
    val calorieGoal: Int = 2000,
    /** What the goal would be if left to Wiggle. */
    val autoCalorieGoal: Int = 2000,
    val steps: StepsAvailability = StepsAvailability.Checking,
)

/** Which sheet the settings screen has open. Only one can be open at a time. */
sealed interface SettingsSheet {
    data object None : SettingsSheet
    data class Reminder(val kind: ReminderKind) : SettingsSheet
    data object AddPerson : SettingsSheet
    data object EditPerson : SettingsSheet
    data object WaterGoal : SettingsSheet
    data object GoalWeight : SettingsSheet
    data object CalorieGoal : SettingsSheet
    data object StepGoal : SettingsSheet
    data object ConfirmWipe : SettingsSheet
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: WiggleRepository,
    private val csvExporter: CsvExporter,
    healthSteps: HealthSteps,
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
        combine(repository.latestWeight, healthSteps.state) { weight, steps -> weight to steps },
    ) { settings, profiles, profile, reminders, (latest, steps) ->
        val today = LocalDate.now()
        val auto = profile?.let { Nutrition.goalFor(it.copy(dailyCalorieGoal = null), latest?.weightKg, today) } ?: 2000
        SettingsUiState(
            loading = false,
            settings = settings,
            profile = profile,
            profiles = profiles,
            reminders = ReminderKind.entries.map { kind ->
                reminders.firstOrNull { it.kind == kind } ?: defaultReminder(settings.activeProfileId, kind)
            },
            latestKg = latest?.weightKg,
            calorieGoal = profile?.dailyCalorieGoal ?: auto,
            autoCalorieGoal = auto,
            steps = steps.availability,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun openSheet(sheet: SettingsSheet) = _sheet.update { sheet }

    fun closeSheet() = _sheet.update { SettingsSheet.None }

    // --- display -------------------------------------------------------------------------------

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repository.setThemeMode(mode) }

    /** One switch for all three: metric is kg, cm and ml; imperial is lb, in and fl oz. */
    fun setMetric(metric: Boolean) = viewModelScope.launch {
        repository.setWeightUnit(if (metric) WeightUnit.Kg else WeightUnit.Lb)
        repository.setLengthUnit(if (metric) LengthUnit.Cm else LengthUnit.In)
        repository.setVolumeUnit(if (metric) VolumeUnit.Ml else VolumeUnit.FlOz)
    }

    fun setHaptics(enabled: Boolean) = viewModelScope.launch { repository.setHapticsEnabled(enabled) }

    fun setReduceMotion(enabled: Boolean) = viewModelScope.launch { repository.setReduceMotion(enabled) }

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

    // --- person and goals ----------------------------------------------------------------------

    fun updateProfile(profile: ProfileEntity) = viewModelScope.launch {
        repository.updateProfile(profile)
    }

    fun setWaterGoal(ml: Int) = viewModelScope.launch {
        state.value.profile?.let { repository.updateProfile(it.copy(dailyWaterGoalMl = ml)) }
    }

    fun setGoalWeight(kg: Double?) = viewModelScope.launch {
        state.value.profile?.let { repository.updateProfile(it.copy(goalWeightKg = kg)) }
    }

    /** Null hands the goal back to Wiggle to work out. */
    fun setCalorieGoal(kcal: Int?, birthYear: Int?) = viewModelScope.launch {
        state.value.profile?.let { repository.updateProfile(it.copy(dailyCalorieGoal = kcal, birthYear = birthYear)) }
    }

    fun setStepGoal(steps: Int) = viewModelScope.launch {
        state.value.profile?.let { repository.updateProfile(it.copy(dailyStepGoal = steps)) }
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
}
