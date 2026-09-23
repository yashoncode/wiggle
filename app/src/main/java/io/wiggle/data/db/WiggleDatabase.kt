package io.wiggle.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Converters {
    @TypeConverter fun sexToString(value: Sex): String = value.name

    @TypeConverter fun stringToSex(value: String): Sex =
        runCatching { Sex.valueOf(value) }.getOrDefault(Sex.Unspecified)

    @TypeConverter fun kindToString(value: ReminderKind): String = value.name

    @TypeConverter fun stringToKind(value: String): ReminderKind = ReminderKind.valueOf(value)
}

@Database(
    entities = [
        ProfileEntity::class,
        WeightEntryEntity::class,
        BodyMeasurementEntity::class,
        WaterEntryEntity::class,
        ReminderEntity::class,
        CustomMeasureTypeEntity::class,
        CustomMeasurementValueEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class WiggleDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun weightDao(): WeightDao
    abstract fun bodyDao(): BodyDao
    abstract fun waterDao(): WaterDao
    abstract fun reminderDao(): ReminderDao
    abstract fun customMeasureDao(): CustomMeasureDao

    companion object {
        const val NAME = "wiggle.db"

        /** Adds user-defined measurement types and their readings. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_measure_types` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`profileId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`anchor` TEXT NOT NULL, " +
                        "`defaultCm` REAL NOT NULL, " +
                        "`minCm` REAL NOT NULL, " +
                        "`maxCm` REAL NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_custom_measure_types_profileId` " +
                        "ON `custom_measure_types` (`profileId`)"
                )
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_measurement_values` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, " +
                        "`typeId` INTEGER NOT NULL, " +
                        "`valueCm` REAL NOT NULL, " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `body_measurements`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`typeId`) REFERENCES `custom_measure_types`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_custom_measurement_values_sessionId` " +
                        "ON `custom_measurement_values` (`sessionId`)"
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_custom_measurement_values_typeId` " +
                        "ON `custom_measurement_values` (`typeId`)"
                )
            }
        }
    }
}
