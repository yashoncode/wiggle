package io.wiggle.ui.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.wiggle.data.WiggleRepository
import io.wiggle.data.db.BodyMeasurementEntity
import io.wiggle.data.db.CustomMeasureTypeEntity
import io.wiggle.data.db.CustomMeasurementValueEntity
import io.wiggle.data.db.ProfileEntity
import io.wiggle.data.db.Sex
import io.wiggle.data.prefs.Settings
import io.wiggle.domain.BodyPart
import io.wiggle.domain.MeasureSpec
import io.wiggle.domain.Stats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

data class PartSummary(
    val spec: MeasureSpec,
    val latestCm: Double?,
    /** Change against the previous session that recorded this measurement. */
    val changeCm: Double?,
    /** Oldest first, for the card sparkline. */
    val history: List<Double>,
)

data class BodyUiState(
    val loading: Boolean = true,
    val profile: ProfileEntity? = null,
    val settings: Settings = Settings(),
    val latest: BodyMeasurementEntity? = null,
    val lastMeasuredOn: LocalDate? = null,
    val bodyFatPercent: Double? = null,
    val bodyFatChange: Double? = null,
    val waistToHip: Double? = null,
    val waistToHipRisk: String? = null,
    val parts: List<PartSummary> = emptyList(),
    val customTypes: List<CustomMeasureTypeEntity> = emptyList(),
    val sessionCount: Int = 0,
)

/** The step-by-step editor's own state, separate because it is a transient flow. */
data class MeasureEditorState(
    val open: Boolean = false,
    val stepIndex: Int = 0,
    val specs: List<MeasureSpec> = emptyList(),
    /** Keyed by [MeasureSpec.key], always in centimetres. */
    val values: Map<String, Double> = emptyMap(),
    val skipped: Set<String> = emptySet(),
    /** When this session was taken. Editable, so a missed week can be filled in later. */
    val measuredAt: LocalDateTime = LocalDateTime.now(),
    val saving: Boolean = false,
    /** True while the "add your own measurement" form is showing. */
    val addingType: Boolean = false,
    val newTypeName: String = "",
    val newTypeAnchor: BodyPart = BodyPart.Waist,
) {
    val currentSpec: MeasureSpec?
        get() = specs.getOrNull(stepIndex.coerceIn(0, (specs.size - 1).coerceAtLeast(0)))

    val isLastStep: Boolean get() = stepIndex >= specs.lastIndex
}

@HiltViewModel
class BodyViewModel @Inject constructor(
    private val repository: WiggleRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val _editor = MutableStateFlow(MeasureEditorState())
    val editor: StateFlow<MeasureEditorState> = _editor.asStateFlow()

    private val _lastDeleted = MutableStateFlow<BodyMeasurementEntity?>(null)
    val lastDeleted: StateFlow<BodyMeasurementEntity?> = _lastDeleted.asStateFlow()

    val state: StateFlow<BodyUiState> = combine(
        repository.bodyMeasurements,
        repository.activeProfile,
        repository.settings,
        repository.customMeasureTypes,
        repository.customMeasureValues,
    ) { sessions, profile, settings, customTypes, customValues ->
        // The DAO hands back newest first; anything chronological needs the reverse.
        val chronological = sessions.sortedBy { it.measuredAt }
        val latest = sessions.firstOrNull()
        val isFemale = profile?.sex == Sex.Female

        fun bodyFatOf(entry: BodyMeasurementEntity?) = entry?.let {
            Stats.navyBodyFatPercent(
                isFemale = isFemale,
                heightCm = profile?.heightCm ?: 0.0,
                neckCm = it.neckCm,
                waistCm = it.waistCm,
                hipsCm = it.hipsCm,
            )
        }

        val bodyFat = bodyFatOf(latest)
        // Compare against the newest session at least 25 days back, so "in 30 days" is honest.
        val monthAgo = System.currentTimeMillis() - 25L * 24 * 60 * 60 * 1000
        val referenceBodyFat = bodyFatOf(chronological.lastOrNull { it.measuredAt <= monthAgo })
        val ratio = latest?.let { Stats.waistToHipRatio(it.waistCm, it.hipsCm) }

        val valuesBySession: Map<Long, List<CustomMeasurementValueEntity>> =
            customValues.groupBy { it.sessionId }

        val specs = MeasureSpec.all(customTypes)

        BodyUiState(
            loading = false,
            profile = profile,
            settings = settings,
            latest = latest,
            lastMeasuredOn = latest?.let {
                Instant.ofEpochMilli(it.measuredAt).atZone(zone).toLocalDate()
            },
            bodyFatPercent = bodyFat,
            bodyFatChange = if (bodyFat != null && referenceBodyFat != null) {
                bodyFat - referenceBodyFat
            } else {
                null
            },
            waistToHip = ratio,
            waistToHipRisk = ratio?.let { Stats.whrRisk(it, isFemale) },
            parts = specs.map { spec ->
                val series = chronological.mapNotNull { session ->
                    spec.builtIn?.valueOf(session)
                        ?: valuesBySession[session.id]
                            ?.firstOrNull { it.typeId == spec.customTypeId }
                            ?.valueCm
                }
                PartSummary(
                    spec = spec,
                    latestCm = series.lastOrNull(),
                    changeCm = if (series.size >= 2) series.last() - series[series.size - 2] else null,
                    history = series.takeLast(12),
                )
            },
            customTypes = customTypes,
            sessionCount = sessions.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyUiState())

    /** Opens the editor pre-filled from the last session, so only what changed needs adjusting. */
    fun openEditor() = viewModelScope.launch {
        val profileId = repository.settings.first().activeProfileId
        val previous = if (profileId != 0L) repository.latestBody(profileId) else null
        val customTypes = if (profileId != 0L) repository.customTypes(profileId) else emptyList()
        val previousCustom = previous
            ?.let { repository.customValuesForSession(it.id) }
            ?.associate { it.typeId to it.valueCm }
            .orEmpty()

        val specs = MeasureSpec.all(customTypes)
        val values = specs.associate { spec ->
            val last = spec.builtIn?.let { part -> previous?.let(part::valueOf) }
                ?: spec.customTypeId?.let(previousCustom::get)
            spec.key to (last ?: spec.defaultCm)
        }
        // A measurement missing from the last session stays off unless the user touches it.
        val skipped = specs.filter { spec ->
            previous != null &&
                spec.builtIn?.valueOf(previous) == null &&
                spec.customTypeId?.let(previousCustom::containsKey) != true
        }.map { it.key }.toSet()

        _editor.value = MeasureEditorState(
            open = true,
            stepIndex = 0,
            specs = specs,
            values = values,
            skipped = skipped,
            measuredAt = LocalDateTime.now(),
        )
    }

    fun closeEditor() = _editor.update { it.copy(open = false, addingType = false) }

    fun setValue(spec: MeasureSpec, cm: Double) = _editor.update {
        it.copy(
            values = it.values + (spec.key to cm.coerceIn(spec.range)),
            skipped = it.skipped - spec.key,
        )
    }

    fun skipCurrent() = _editor.update { current ->
        val spec = current.currentSpec ?: return@update current
        current.copy(
            skipped = current.skipped + spec.key,
            stepIndex = (current.stepIndex + 1).coerceAtMost(current.specs.lastIndex),
        )
    }

    fun goToStep(index: Int) = _editor.update {
        it.copy(stepIndex = index.coerceIn(0, it.specs.lastIndex.coerceAtLeast(0)))
    }

    fun nextStep() = _editor.update {
        it.copy(stepIndex = (it.stepIndex + 1).coerceAtMost(it.specs.lastIndex))
    }

    fun previousStep() = _editor.update { it.copy(stepIndex = (it.stepIndex - 1).coerceAtLeast(0)) }

    fun setMeasuredAt(at: LocalDateTime) = _editor.update { it.copy(measuredAt = at) }

    // --- user-defined measurement types ---------------------------------------------------

    fun startAddingType() = _editor.update {
        it.copy(addingType = true, newTypeName = "", newTypeAnchor = BodyPart.Waist)
    }

    fun cancelAddingType() = _editor.update { it.copy(addingType = false) }

    fun setNewTypeName(name: String) = _editor.update { it.copy(newTypeName = name) }

    fun setNewTypeAnchor(anchor: BodyPart) = _editor.update { it.copy(newTypeAnchor = anchor) }

    /**
     * Adds the type, then jumps the editor straight to its step so the first reading can be taken
     * without hunting for it.
     */
    fun confirmAddType() = viewModelScope.launch {
        val current = _editor.value
        val name = current.newTypeName.trim()
        if (name.isBlank()) return@launch
        val profileId = repository.settings.first().activeProfileId
        if (profileId == 0L) return@launch

        val anchor = current.newTypeAnchor
        val id = repository.addCustomMeasureType(
            profileId = profileId,
            name = name,
            anchor = anchor.name,
            // A new measurement inherits the plausible range of the region it sits on.
            defaultCm = anchor.defaultCm,
            minCm = anchor.range.start,
            maxCm = anchor.range.endInclusive,
        )
        val customTypes = repository.customTypes(profileId)
        val specs = MeasureSpec.all(customTypes)
        val newSpec = specs.firstOrNull { it.customTypeId == id }

        _editor.update { editorState ->
            editorState.copy(
                addingType = false,
                newTypeName = "",
                specs = specs,
                values = editorState.values + specs
                    .filter { it.key !in editorState.values }
                    .associate { it.key to it.defaultCm },
                stepIndex = specs.indexOf(newSpec).coerceAtLeast(0),
            )
        }
    }

    fun deleteCustomType(typeId: Long) = viewModelScope.launch {
        repository.deleteCustomMeasureType(typeId)
        val profileId = repository.settings.first().activeProfileId
        if (profileId == 0L) return@launch
        val specs = MeasureSpec.all(repository.customTypes(profileId))
        _editor.update {
            it.copy(specs = specs, stepIndex = it.stepIndex.coerceAtMost(specs.lastIndex.coerceAtLeast(0)))
        }
    }

    // --- saving ------------------------------------------------------------------------------

    fun saveSession(onSaved: () -> Unit) = viewModelScope.launch {
        val editorState = _editor.value
        val profileId = repository.settings.first().activeProfileId
        if (profileId == 0L) return@launch
        _editor.update { it.copy(saving = true) }

        var entry = BodyMeasurementEntity(
            profileId = profileId,
            measuredAt = editorState.measuredAt.atZone(zone).toInstant().toEpochMilli(),
        )
        val customValues = mutableMapOf<Long, Double>()

        editorState.specs.forEach { spec ->
            if (spec.key in editorState.skipped) {
                spec.builtIn?.let { entry = it.with(entry, null) }
                return@forEach
            }
            val value = editorState.values[spec.key] ?: return@forEach
            when {
                spec.builtIn != null -> entry = spec.builtIn.with(entry, value)
                spec.customTypeId != null -> customValues[spec.customTypeId] = value
            }
        }

        repository.addBodySession(entry, customValues)
        _editor.value = MeasureEditorState()
        onSaved()
    }

    fun delete(entry: BodyMeasurementEntity) = viewModelScope.launch {
        repository.deleteBodyMeasurement(entry.id)
        _lastDeleted.value = entry
    }

    fun undoDelete() = viewModelScope.launch {
        _lastDeleted.value?.let { repository.restoreBodyMeasurement(it) }
        _lastDeleted.value = null
    }

    fun clearUndo() {
        _lastDeleted.value = null
    }
}
