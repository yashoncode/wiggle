package io.wiggle

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import io.wiggle.alarm.ReminderScheduler
import io.wiggle.data.SampleData
import io.wiggle.data.WiggleRepository
import io.wiggle.data.db.ProfileDao
import io.wiggle.data.prefs.SettingsStore
import io.wiggle.di.ApplicationScope
import io.wiggle.widget.WiggleWidget
import kotlinx.coroutines.CoroutineScope
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltAndroidApp
class WiggleApp : Application() {

    @Inject lateinit var repository: WiggleRepository

    @Inject lateinit var profileDao: ProfileDao

    @Inject lateinit var settingsStore: SettingsStore

    @Inject lateinit var scheduler: ReminderScheduler

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) seedSampleDataIfEmpty()
        keepAlarmsInStepWithSettings()
        keepWidgetInStepWithData()
    }

    /**
     * The home-screen widget has no way to know a weigh-in happened, so the app tells it. The
     * provider also refreshes on its own timer, which covers the case where nothing here is running.
     */
    private fun keepWidgetInStepWithData() = appScope.launch {
        // Everything the widget draws, including who exists at all: finishing onboarding adds the
        // first person without touching a weight, a measurement, water or a reminder, and the
        // widget sat on "Open Wiggle to get started" until something else happened to change.
        val sources = listOf(
            repository.latestWeight,
            repository.bodyMeasurements,
            repository.waterOn(LocalDate.now()),
            repository.reminders,
            repository.profiles,
            repository.settings,
        ).map { source -> source.map { } }

        combine(sources) { }
            .collectLatest {
                runCatching { WiggleWidget().updateAll(this@WiggleApp) }
                    .onFailure { Log.w("WiggleWidget", "Could not redraw the widget", it) }
            }
    }

    /**
     * The alarm clock mirrors the reminders table. Watching the table instead of rescheduling at
     * each call site means a toggle, an edited time, a new person and a deleted one are all
     * already handled, and none of them can forget to.
     */
    private fun keepAlarmsInStepWithSettings() = appScope.launch {
        repository.allReminders.collectLatest { scheduler.sync(it) }
    }

    /**
     * Debug builds start with a populated database so every screen has something to draw.
     * Release builds go through onboarding instead.
     */
    private fun seedSampleDataIfEmpty() = appScope.launch {
        if (profileDao.count() > 0) return@launch
        SampleData.seed(repository)
        settingsStore.setOnboardingComplete(true)
    }
}
