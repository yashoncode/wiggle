package io.wiggle.di

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.wiggle.data.db.BodyDao
import io.wiggle.data.db.ProfileDao
import io.wiggle.data.db.CustomMeasureDao
import io.wiggle.data.db.ReminderDao
import io.wiggle.data.db.WaterDao
import io.wiggle.data.db.WeightDao
import io.wiggle.data.db.WiggleDatabase
import io.wiggle.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): WiggleDatabase =
        Room.databaseBuilder(context, WiggleDatabase::class.java, WiggleDatabase.NAME)
            .addMigrations(WiggleDatabase.MIGRATION_1_2)
            .build()

    @Provides fun profileDao(db: WiggleDatabase): ProfileDao = db.profileDao()

    @Provides fun weightDao(db: WiggleDatabase): WeightDao = db.weightDao()

    @Provides fun bodyDao(db: WiggleDatabase): BodyDao = db.bodyDao()

    @Provides fun waterDao(db: WiggleDatabase): WaterDao = db.waterDao()

    @Provides fun reminderDao(db: WiggleDatabase): ReminderDao = db.reminderDao()

    @Provides fun customMeasureDao(db: WiggleDatabase): CustomMeasureDao = db.customMeasureDao()

    @Provides
    @Singleton
    fun settingsStore(@ApplicationContext context: Context): SettingsStore = SettingsStore(context)

    @Provides
    @Singleton
    fun alarmManager(@ApplicationContext context: Context): AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    @Provides
    @Singleton
    fun notificationManager(@ApplicationContext context: Context): NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    /** For work that must outlive whatever screen started it, such as a notification action. */
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
