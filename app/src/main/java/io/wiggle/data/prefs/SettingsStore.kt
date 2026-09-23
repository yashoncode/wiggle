package io.wiggle.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.wiggle.domain.LengthUnit
import io.wiggle.domain.VolumeUnit
import io.wiggle.domain.WeightUnit
import io.wiggle.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("wiggle_settings")

data class Settings(
    /** Which person the app is currently showing. 0 means none chosen yet. */
    val activeProfileId: Long = 0,
    val weightUnit: WeightUnit = WeightUnit.Kg,
    val lengthUnit: LengthUnit = LengthUnit.Cm,
    val volumeUnit: VolumeUnit = VolumeUnit.Ml,
    val themeMode: ThemeMode = ThemeMode.System,
    val onboardingComplete: Boolean = false,
    val healthConnectEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
)

@Singleton
class SettingsStore @Inject constructor(private val context: Context) {

    private object Keys {
        val ActiveProfile = longPreferencesKey("active_profile_id")
        val WeightUnit = stringPreferencesKey("weight_unit")
        val LengthUnit = stringPreferencesKey("length_unit")
        val VolumeUnit = stringPreferencesKey("volume_unit")
        val Theme = stringPreferencesKey("theme_mode")
        val Onboarded = booleanPreferencesKey("onboarding_complete")
        val HealthConnect = booleanPreferencesKey("health_connect_enabled")
        val Haptics = booleanPreferencesKey("haptics_enabled")
    }

    val settings: Flow<Settings> = context.settingsDataStore.data.map { prefs ->
        Settings(
            activeProfileId = prefs[Keys.ActiveProfile] ?: 0L,
            weightUnit = prefs[Keys.WeightUnit].toEnum(WeightUnit.Kg),
            lengthUnit = prefs[Keys.LengthUnit].toEnum(LengthUnit.Cm),
            volumeUnit = prefs[Keys.VolumeUnit].toEnum(VolumeUnit.Ml),
            themeMode = prefs[Keys.Theme].toEnum(ThemeMode.System),
            onboardingComplete = prefs[Keys.Onboarded] ?: false,
            healthConnectEnabled = prefs[Keys.HealthConnect] ?: false,
            hapticsEnabled = prefs[Keys.Haptics] ?: true,
        )
    }

    suspend fun setActiveProfile(id: Long) = put(Keys.ActiveProfile, id)
    suspend fun setWeightUnit(unit: WeightUnit) = put(Keys.WeightUnit, unit.name)
    suspend fun setLengthUnit(unit: LengthUnit) = put(Keys.LengthUnit, unit.name)
    suspend fun setVolumeUnit(unit: VolumeUnit) = put(Keys.VolumeUnit, unit.name)
    suspend fun setThemeMode(mode: ThemeMode) = put(Keys.Theme, mode.name)
    suspend fun setOnboardingComplete(value: Boolean) = put(Keys.Onboarded, value)
    suspend fun setHealthConnectEnabled(value: Boolean) = put(Keys.HealthConnect, value)
    suspend fun setHapticsEnabled(value: Boolean) = put(Keys.Haptics, value)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.settingsDataStore.edit { it[key] = value }
    }
}

private inline fun <reified T : Enum<T>> String?.toEnum(fallback: T): T =
    this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
