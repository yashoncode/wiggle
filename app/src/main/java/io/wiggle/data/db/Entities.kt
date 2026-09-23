package io.wiggle.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Every measurement in the database is stored in base units — kilograms, centimetres,
 * millilitres — and converted only at the edge of the UI. Nothing downstream has to ask which
 * unit a number is in.
 *
 * Every row also carries a [profileId]: one install can track several people.
 */

enum class Sex { Male, Female, Unspecified }

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Emoji-free single-letter avatar is derived from the name; this is the accent tint index. */
    val colorIndex: Int = 0,
    val heightCm: Double,
    val sex: Sex,
    val birthYear: Int?,
    val goalWeightKg: Double?,
    /** Weight of the first entry, kept so goal progress has a stable start even if that entry is deleted. */
    val startWeightKg: Double?,
    val dailyWaterGoalMl: Int = 2500,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "weight_entries",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["profileId", "measuredAt"])],
)
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    /** Epoch millis of the moment being logged, not of the insert. */
    val measuredAt: Long,
    val weightKg: Double,
    val note: String? = null,
)

@Entity(
    tableName = "body_measurements",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["profileId", "measuredAt"])],
)
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val measuredAt: Long,
    val neckCm: Double? = null,
    val chestCm: Double? = null,
    val waistCm: Double? = null,
    val hipsCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
    val calfCm: Double? = null,
    val forearmCm: Double? = null,
)

@Entity(
    tableName = "water_entries",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["profileId", "loggedAt"])],
)
data class WaterEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val loggedAt: Long,
    val amountMl: Int,
)

enum class ReminderKind { WeighIn, Measurements, Water }

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["profileId", "kind"], unique = true)],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val kind: ReminderKind,
    val enabled: Boolean,
    /** Minutes after midnight. For the water reminder this is the start of the active window. */
    val timeMinutes: Int,
    /** Bitmask, bit 0 = Monday. Used by the weigh-in reminder. */
    val daysMask: Int = 0b1111111,
    /** Measurements only: 1 = every week, 2 = every other week, and so on. */
    val everyNWeeks: Int = 1,
    /** Water only: minutes between nudges. */
    val intervalMinutes: Int = 120,
    /** Water only: end of the active window, minutes after midnight. */
    val untilMinutes: Int = 22 * 60,
    /** Water only: stop nudging once the daily goal is met. */
    val pauseWhenGoalMet: Boolean = true,
)

/**
 * A measurement the user invented: "Left calf", "Shoulders", "Wrist".
 *
 * Kept per profile, because what one person tracks is rarely what another does, and stored as a
 * type plus values rather than as extra columns so adding one never needs a schema change.
 */
@Entity(
    tableName = "custom_measure_types",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["profileId"])],
)
data class CustomMeasureTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val name: String,
    /** Which body region the tape band sits on in the diagram. */
    val anchor: String,
    val defaultCm: Double = 40.0,
    val minCm: Double = 5.0,
    val maxCm: Double = 250.0,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One reading of a [CustomMeasureTypeEntity], tied to the session it was taken in. */
@Entity(
    tableName = "custom_measurement_values",
    foreignKeys = [
        ForeignKey(
            entity = BodyMeasurementEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CustomMeasureTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["typeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId"]), Index(value = ["typeId"])],
)
data class CustomMeasurementValueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val typeId: Long,
    val valueCm: Double,
)
