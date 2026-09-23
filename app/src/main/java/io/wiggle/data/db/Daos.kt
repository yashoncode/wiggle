package io.wiggle.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observe(id: Long): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    @Query("SELECT * FROM profiles ORDER BY createdAt ASC LIMIT 1")
    suspend fun first(): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Delete
    suspend fun delete(profile: ProfileEntity)
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_entries WHERE profileId = :profileId ORDER BY measuredAt DESC")
    fun observeAll(profileId: Long): Flow<List<WeightEntryEntity>>

    @Query(
        "SELECT * FROM weight_entries WHERE profileId = :profileId AND measuredAt >= :sinceMillis " +
            "ORDER BY measuredAt ASC"
    )
    fun observeSince(profileId: Long, sinceMillis: Long): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entries WHERE profileId = :profileId ORDER BY measuredAt DESC LIMIT 1")
    fun observeLatest(profileId: Long): Flow<WeightEntryEntity?>

    @Query("SELECT * FROM weight_entries WHERE profileId = :profileId ORDER BY measuredAt DESC LIMIT 1")
    suspend fun latest(profileId: Long): WeightEntryEntity?

    @Query("SELECT * FROM weight_entries WHERE profileId = :profileId ORDER BY measuredAt ASC")
    suspend fun allFor(profileId: Long): List<WeightEntryEntity>

    @Query("SELECT MIN(weightKg) FROM weight_entries WHERE profileId = :profileId")
    suspend fun lowest(profileId: Long): Double?

    @Query("SELECT * FROM weight_entries WHERE id = :id")
    suspend fun get(id: Long): WeightEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WeightEntryEntity): Long

    @Update
    suspend fun update(entry: WeightEntryEntity)

    @Query("DELETE FROM weight_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM weight_entries WHERE profileId = :profileId")
    suspend fun deleteAllFor(profileId: Long)
}

@Dao
interface BodyDao {
    @Query("SELECT * FROM body_measurements WHERE profileId = :profileId ORDER BY measuredAt DESC")
    fun observeAll(profileId: Long): Flow<List<BodyMeasurementEntity>>

    @Query("SELECT * FROM body_measurements WHERE profileId = :profileId ORDER BY measuredAt DESC LIMIT 1")
    fun observeLatest(profileId: Long): Flow<BodyMeasurementEntity?>

    @Query("SELECT * FROM body_measurements WHERE profileId = :profileId ORDER BY measuredAt DESC LIMIT 1")
    suspend fun latest(profileId: Long): BodyMeasurementEntity?

    @Query("SELECT * FROM body_measurements WHERE profileId = :profileId ORDER BY measuredAt ASC")
    suspend fun allFor(profileId: Long): List<BodyMeasurementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BodyMeasurementEntity): Long

    @Query("DELETE FROM body_measurements WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM body_measurements WHERE profileId = :profileId")
    suspend fun deleteAllFor(profileId: Long)
}

@Dao
interface WaterDao {
    @Query(
        "SELECT * FROM water_entries WHERE profileId = :profileId AND loggedAt >= :fromMillis " +
            "AND loggedAt < :toMillis ORDER BY loggedAt DESC"
    )
    fun observeBetween(profileId: Long, fromMillis: Long, toMillis: Long): Flow<List<WaterEntryEntity>>

    @Query(
        "SELECT COALESCE(SUM(amountMl), 0) FROM water_entries WHERE profileId = :profileId " +
            "AND loggedAt >= :fromMillis AND loggedAt < :toMillis"
    )
    suspend fun totalBetween(profileId: Long, fromMillis: Long, toMillis: Long): Int

    @Query("SELECT * FROM water_entries WHERE profileId = :profileId ORDER BY loggedAt ASC")
    suspend fun allFor(profileId: Long): List<WaterEntryEntity>

    @Query("SELECT MAX(loggedAt) FROM water_entries WHERE profileId = :profileId")
    suspend fun lastLoggedAt(profileId: Long): Long?

    @Insert
    suspend fun insert(entry: WaterEntryEntity): Long

    @Query("DELETE FROM water_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM water_entries WHERE profileId = :profileId")
    suspend fun deleteAllFor(profileId: Long)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE profileId = :profileId")
    fun observeAll(profileId: Long): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE profileId = :profileId")
    suspend fun allFor(profileId: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun allEnabled(): List<ReminderEntity>

    /** Every reminder for every profile: what the alarm scheduler watches. */
    @Query("SELECT * FROM reminders")
    fun observeEvery(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE profileId = :profileId AND kind = :kind")
    suspend fun get(profileId: Long, kind: ReminderKind): ReminderEntity?

    @Upsert
    suspend fun upsert(reminder: ReminderEntity)
}

@Dao
interface CustomMeasureDao {
    @Query("SELECT * FROM custom_measure_types WHERE profileId = :profileId ORDER BY sortOrder ASC, createdAt ASC")
    fun observeTypes(profileId: Long): Flow<List<CustomMeasureTypeEntity>>

    @Query("SELECT * FROM custom_measure_types WHERE profileId = :profileId ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun typesFor(profileId: Long): List<CustomMeasureTypeEntity>

    @Query("SELECT COUNT(*) FROM custom_measure_types WHERE profileId = :profileId")
    suspend fun typeCount(profileId: Long): Int

    @Insert
    suspend fun insertType(type: CustomMeasureTypeEntity): Long

    @Update
    suspend fun updateType(type: CustomMeasureTypeEntity)

    @Query("DELETE FROM custom_measure_types WHERE id = :id")
    suspend fun deleteType(id: Long)

    /** Every custom reading for a profile, joined to the session date it belongs to. */
    @Query(
        "SELECT v.* FROM custom_measurement_values v " +
            "INNER JOIN body_measurements s ON s.id = v.sessionId " +
            "WHERE s.profileId = :profileId ORDER BY s.measuredAt ASC"
    )
    fun observeValues(profileId: Long): Flow<List<CustomMeasurementValueEntity>>

    @Query(
        "SELECT v.* FROM custom_measurement_values v " +
            "INNER JOIN body_measurements s ON s.id = v.sessionId " +
            "WHERE s.profileId = :profileId"
    )
    suspend fun allValuesFor(profileId: Long): List<CustomMeasurementValueEntity>

    @Query("SELECT * FROM custom_measurement_values WHERE sessionId = :sessionId")
    suspend fun valuesForSession(sessionId: Long): List<CustomMeasurementValueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValues(values: List<CustomMeasurementValueEntity>)
}
