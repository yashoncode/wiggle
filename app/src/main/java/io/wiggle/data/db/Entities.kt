package io.wiggle.data.db

import androidx.room.ColumnInfo
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
    /** Null means worked out from height, weight, age and goal; see `Nutrition.calorieGoal`. */
    val dailyCalorieGoal: Int? = null,
    @ColumnInfo(defaultValue = "10000") val dailyStepGoal: Int = 10000,
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

/**
 * Append only: the ordinal is part of every alarm request code and notification id, so inserting
 * a kind anywhere but the end would orphan alarms that are already set.
 */
enum class ReminderKind { WeighIn, Measurements, Water, Meals, Move, Tablets }

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
    /** Meals only: breakfast, lunch and dinner times, comma-separated minutes after midnight. */
    @ColumnInfo(defaultValue = "") val times: String = "",
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

enum class Meal { Breakfast, Lunch, Snacks, Dinner }

/**
 * One food eaten, stored with its nutrients per 100 g and the portion, so the log never changes when
 * the food database does, and a recent entry can be added again exactly as it was.
 */
@Entity(
    tableName = "food_entries",
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
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val loggedAt: Long,
    val meal: Meal,
    val name: String,
    /** Where the food came from: `IN`, `US`, `OFF` (Open Food Facts) or `MY` for your own. */
    val source: String,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    /** "1 chapati", "1 katori"; empty means plain grams. */
    val servingLabel: String,
    val servingG: Double,
    val servings: Double,
)

/** A food you made yourself, or one you starred. Either way it is kept with its nutrients. */
@Entity(
    tableName = "saved_foods",
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
data class SavedFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val name: String,
    val source: String,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    val servingLabel: String,
    val servingG: Double,
    val favorite: Boolean,
    /** Made in Wiggle rather than found in a database. Shown under "My foods". */
    val custom: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A tablet, capsule or supplement taken on a daily schedule. */
@Entity(
    tableName = "medications",
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
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val name: String,
    /** "after lunch", "with food". */
    val note: String = "",
    /** Dose times, comma-separated minutes after midnight. */
    val times: String,
    /** Tablets left, or null when not counted. Taking a dose takes [perDose] off. */
    val stock: Int? = null,
    val perDose: Int = 1,
    val colorIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A dose ticked off: one row per medication, day and time slot. */
@Entity(
    tableName = "dose_logs",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["medicationId", "day", "slotMinutes"], unique = true)],
)
data class DoseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    /** Local calendar day, as epoch days. */
    val day: Long,
    val slotMinutes: Int,
    val takenAt: Long,
)
