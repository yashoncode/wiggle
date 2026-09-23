package io.wiggle.data

import io.wiggle.data.db.BodyDao
import io.wiggle.data.db.BodyMeasurementEntity
import io.wiggle.data.db.CustomMeasureDao
import io.wiggle.data.db.CustomMeasureTypeEntity
import io.wiggle.data.db.CustomMeasurementValueEntity
import io.wiggle.data.db.ProfileDao
import io.wiggle.data.db.ProfileEntity
import io.wiggle.data.db.ReminderDao
import io.wiggle.data.db.ReminderEntity
import io.wiggle.data.db.ReminderKind
import io.wiggle.data.db.Sex
import io.wiggle.data.db.WaterDao
import io.wiggle.data.db.WaterEntryEntity
import io.wiggle.data.db.WeightDao
import io.wiggle.data.db.WeightEntryEntity
import io.wiggle.data.prefs.Settings
import io.wiggle.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Everything one person has recorded, as read for a CSV export. */
data class ExportSnapshot(
    val profile: ProfileEntity?,
    val weights: List<WeightEntryEntity>,
    val body: List<BodyMeasurementEntity>,
    val water: List<WaterEntryEntity>,
    val customTypes: List<CustomMeasureTypeEntity>,
    val customValues: List<CustomMeasurementValueEntity>,
)

/**
 * One way in and out of storage.
 *
 * Reads are scoped to the active profile: the screens never pass a profile id around, they
 * observe these flows and get whichever person is selected. Writes take the id explicitly so a
 * notification action can log against the right profile without touching the selection.
 */
@Singleton
class WiggleRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val weightDao: WeightDao,
    private val bodyDao: BodyDao,
    private val waterDao: WaterDao,
    private val reminderDao: ReminderDao,
    private val customMeasureDao: CustomMeasureDao,
    private val settingsStore: SettingsStore,
) {
    val settings: Flow<Settings> = settingsStore.settings

    val activeProfileId: Flow<Long> =
        settings.map { it.activeProfileId }.distinctUntilChanged()

    val profiles: Flow<List<ProfileEntity>> = profileDao.observeAll()

    val activeProfile: Flow<ProfileEntity?> =
        activeProfileId.flatMapLatest { id ->
            if (id == 0L) flowOf(null) else profileDao.observe(id)
        }

    val weightEntries: Flow<List<WeightEntryEntity>> =
        activeProfileId.flatMapLatest { id ->
            if (id == 0L) flowOf(emptyList()) else weightDao.observeAll(id)
        }

    val latestWeight: Flow<WeightEntryEntity?> =
        activeProfileId.flatMapLatest { id ->
            if (id == 0L) flowOf(null) else weightDao.observeLatest(id)
        }

    val bodyMeasurements: Flow<List<BodyMeasurementEntity>> =
        activeProfileId.flatMapLatest { id ->
            if (id == 0L) flowOf(emptyList()) else bodyDao.observeAll(id)
        }

    /** The measurement types this person added on top of the eight built in. */
    val customMeasureTypes: Flow<List<CustomMeasureTypeEntity>> =
        activeProfileId.flatMapLatest { id ->
            if (id == 0L) flowOf(emptyList()) else customMeasureDao.observeTypes(id)
        }

    /** Every custom reading for this person, oldest first. */
    val customMeasureValues: Flow<List<CustomMeasurementValueEntity>> =
        activeProfileId.flatMapLatest { id ->
            if (id == 0L) flowOf(emptyList()) else customMeasureDao.observeValues(id)
        }

    val reminders: Flow<List<ReminderEntity>> =
        activeProfileId.flatMapLatest { id ->
            if (id == 0L) flowOf(emptyList()) else reminderDao.observeAll(id)
        }

    /** Water logged inside [date]'s local calendar day. */
    fun waterOn(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<List<WaterEntryEntity>> {
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return activeProfileId.flatMapLatest { id ->
            if (id == 0L) flowOf(emptyList()) else waterDao.observeBetween(id, from, to)
        }
    }

    fun waterBetween(
        from: LocalDate,
        toExclusive: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Flow<List<WaterEntryEntity>> {
        val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = toExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        return activeProfileId.flatMapLatest { id ->
            if (id == 0L) flowOf(emptyList()) else waterDao.observeBetween(id, start, end)
        }
    }

    // --- Profiles ----------------------------------------------------------------------------

    suspend fun createProfile(
        name: String,
        heightCm: Double,
        sex: Sex,
        goalWeightKg: Double?,
        birthYear: Int? = null,
        dailyWaterGoalMl: Int = 2500,
    ): Long {
        val colorIndex = profileDao.count()
        val id = profileDao.insert(
            ProfileEntity(
                name = name,
                colorIndex = colorIndex,
                heightCm = heightCm,
                sex = sex,
                birthYear = birthYear,
                goalWeightKg = goalWeightKg,
                startWeightKg = null,
                dailyWaterGoalMl = dailyWaterGoalMl,
            )
        )
        seedDefaultReminders(id)
        return id
    }

    suspend fun updateProfile(profile: ProfileEntity) = profileDao.update(profile)

    suspend fun getProfile(id: Long): ProfileEntity? = profileDao.get(id)

    suspend fun selectProfile(id: Long) = settingsStore.setActiveProfile(id)

    /**
     * Deletes a person and everything measured for them. The caller is responsible for choosing
     * a new active profile; this returns the id of a remaining one, or 0 when none is left.
     */
    suspend fun deleteProfile(profile: ProfileEntity): Long {
        profileDao.delete(profile)
        val remaining = profileDao.first()
        val next = remaining?.id ?: 0L
        settingsStore.setActiveProfile(next)
        return next
    }

    private suspend fun seedDefaultReminders(profileId: Long) {
        reminderDao.upsert(
            ReminderEntity(
                profileId = profileId,
                kind = ReminderKind.WeighIn,
                enabled = false,
                timeMinutes = 7 * 60,
            )
        )
        reminderDao.upsert(
            ReminderEntity(
                profileId = profileId,
                kind = ReminderKind.Measurements,
                enabled = false,
                timeMinutes = 8 * 60,
                // Sunday only.
                daysMask = 1 shl 6,
                everyNWeeks = 1,
            )
        )
        reminderDao.upsert(
            ReminderEntity(
                profileId = profileId,
                kind = ReminderKind.Water,
                enabled = false,
                timeMinutes = 8 * 60,
                intervalMinutes = 120,
                untilMinutes = 22 * 60,
            )
        )
    }

    // --- Weight ------------------------------------------------------------------------------

    /**
     * Saves a weigh-in. Returns true when it is the lowest ever recorded for this profile, which
     * is what the confetti on the log sheet is waiting for.
     */
    suspend fun addWeight(
        profileId: Long,
        weightKg: Double,
        measuredAt: Long,
        note: String? = null,
    ): Boolean {
        val previousLowest = weightDao.lowest(profileId)
        weightDao.insert(
            WeightEntryEntity(
                profileId = profileId,
                measuredAt = measuredAt,
                weightKg = weightKg,
                note = note?.takeIf { it.isNotBlank() },
            )
        )
        // The first entry doubles as the starting point for goal progress.
        val profile = profileDao.get(profileId)
        if (profile?.startWeightKg == null && profile != null) {
            profileDao.update(profile.copy(startWeightKg = weightKg))
        }
        return previousLowest != null && weightKg < previousLowest
    }

    suspend fun updateWeight(entry: WeightEntryEntity) = weightDao.update(entry)

    suspend fun getWeight(id: Long): WeightEntryEntity? = weightDao.get(id)

    /** The most recent weigh-in, used to seed the log sheet and to show the change since. */
    suspend fun getLatestForPrefill(profileId: Long): WeightEntryEntity? = weightDao.latest(profileId)

    suspend fun setWeightUnit(unit: io.wiggle.domain.WeightUnit) = settingsStore.setWeightUnit(unit)

    suspend fun setLengthUnit(unit: io.wiggle.domain.LengthUnit) = settingsStore.setLengthUnit(unit)

    suspend fun setVolumeUnit(unit: io.wiggle.domain.VolumeUnit) = settingsStore.setVolumeUnit(unit)

    suspend fun setThemeMode(mode: io.wiggle.ui.theme.ThemeMode) = settingsStore.setThemeMode(mode)

    suspend fun setOnboardingComplete(value: Boolean) = settingsStore.setOnboardingComplete(value)

    suspend fun setHapticsEnabled(value: Boolean) = settingsStore.setHapticsEnabled(value)

    suspend fun setHealthConnectEnabled(value: Boolean) = settingsStore.setHealthConnectEnabled(value)

    suspend fun deleteWeight(id: Long) = weightDao.deleteById(id)

    /** Puts a swiped-away entry back, keeping its original id so nothing else has to change. */
    suspend fun restoreWeight(entry: WeightEntryEntity) {
        weightDao.insert(entry)
    }

    // --- Body --------------------------------------------------------------------------------

    suspend fun addBodyMeasurement(entry: BodyMeasurementEntity): Long = bodyDao.insert(entry)

    /**
     * Saves one tape session: the eight built-in columns plus any custom readings, which are
     * written against the session id the insert just produced.
     */
    suspend fun addBodySession(
        entry: BodyMeasurementEntity,
        customValues: Map<Long, Double>,
    ): Long {
        val sessionId = bodyDao.insert(entry)
        if (customValues.isNotEmpty()) {
            customMeasureDao.insertValues(
                customValues.map { (typeId, value) ->
                    CustomMeasurementValueEntity(sessionId = sessionId, typeId = typeId, valueCm = value)
                }
            )
        }
        return sessionId
    }

    suspend fun customTypes(profileId: Long): List<CustomMeasureTypeEntity> =
        customMeasureDao.typesFor(profileId)

    suspend fun customValuesForSession(sessionId: Long): List<CustomMeasurementValueEntity> =
        customMeasureDao.valuesForSession(sessionId)

    suspend fun addCustomMeasureType(
        profileId: Long,
        name: String,
        anchor: String,
        defaultCm: Double,
        minCm: Double,
        maxCm: Double,
    ): Long = customMeasureDao.insertType(
        CustomMeasureTypeEntity(
            profileId = profileId,
            name = name.trim(),
            anchor = anchor,
            defaultCm = defaultCm,
            minCm = minCm,
            maxCm = maxCm,
            sortOrder = customMeasureDao.typeCount(profileId),
        )
    )

    suspend fun deleteCustomMeasureType(id: Long) = customMeasureDao.deleteType(id)

    suspend fun latestBody(profileId: Long): BodyMeasurementEntity? = bodyDao.latest(profileId)

    suspend fun deleteBodyMeasurement(id: Long) = bodyDao.deleteById(id)

    suspend fun restoreBodyMeasurement(entry: BodyMeasurementEntity) {
        bodyDao.insert(entry)
    }

    // --- Water -------------------------------------------------------------------------------

    suspend fun addWater(profileId: Long, amountMl: Int, at: Long = System.currentTimeMillis()): Long =
        waterDao.insert(WaterEntryEntity(profileId = profileId, loggedAt = at, amountMl = amountMl))

    suspend fun deleteWater(id: Long) = waterDao.deleteById(id)

    suspend fun waterTotalToday(profileId: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val today = LocalDate.now(zone)
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return waterDao.totalBetween(profileId, from, to)
    }

    suspend fun lastWaterLoggedAt(profileId: Long): Long? = waterDao.lastLoggedAt(profileId)

    // --- Reminders ---------------------------------------------------------------------------

    /** Every reminder row in the database, which is what the alarm scheduler mirrors. */
    val allReminders: Flow<List<ReminderEntity>> = reminderDao.observeEvery()

    suspend fun remindersFor(profileId: Long): List<ReminderEntity> = reminderDao.allFor(profileId)

    suspend fun allEnabledReminders(): List<ReminderEntity> = reminderDao.allEnabled()

    suspend fun reminder(profileId: Long, kind: ReminderKind): ReminderEntity? =
        reminderDao.get(profileId, kind)

    suspend fun saveReminder(reminder: ReminderEntity) = reminderDao.upsert(reminder)

    // --- Export ------------------------------------------------------------------------------

    /** Everything stored for one person, read in one go so an export is a consistent snapshot. */
    suspend fun exportSnapshot(profileId: Long) = ExportSnapshot(
        profile = profileDao.get(profileId),
        weights = weightDao.allFor(profileId),
        body = bodyDao.allFor(profileId),
        water = waterDao.allFor(profileId),
        customTypes = customMeasureDao.typesFor(profileId),
        customValues = customMeasureDao.allValuesFor(profileId),
    )

    // --- Wipe --------------------------------------------------------------------------------

    /** Clears everything measured for a profile but keeps the person and their settings. */
    suspend fun deleteAllDataFor(profileId: Long) {
        weightDao.deleteAllFor(profileId)
        bodyDao.deleteAllFor(profileId)
        waterDao.deleteAllFor(profileId)
        profileDao.get(profileId)?.let { profileDao.update(it.copy(startWeightKg = null)) }
    }
}
