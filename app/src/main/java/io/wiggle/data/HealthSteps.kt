package io.wiggle.data

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

enum class StepsAvailability {
    /** Not asked yet. */
    Checking,
    Ready,

    /** Health Connect is there, Wiggle has not been allowed to read steps. */
    NeedsPermission,

    /** Android 13 and older without Health Connect installed, or with an old copy. */
    NeedsInstall,

    /** Below what Health Connect supports. */
    Unsupported,
}

data class StepsState(
    val availability: StepsAvailability = StepsAvailability.Checking,
    /** Today, one bucket per local hour. */
    val hourly: List<Long> = List(24) { 0L },
    /** Recent days, today included. A day with nothing recorded is missing, not zero. */
    val daily: Map<LocalDate, Long> = emptyMap(),
    val syncedAt: Instant? = null,
) {
    val today: Long get() = daily[LocalDate.now()] ?: hourly.sum()
}

/**
 * Steps come from Health Connect, Android's shared health store, rather than from counting them
 * here. The phone's own health app writes into it (Realme and OPPO's OHealth under Data sharing,
 * Samsung Health, Google Fit, Fitbit), and on Android 14 and later Health Connect counts the
 * phone's steps itself once any app may read them. That leaves Wiggle with no background service,
 * no permanent notification and nothing for a battery saver to kill.
 *
 * Health Connect also settles which source wins when a phone and a watch both count, so nothing is
 * added twice.
 */
@Singleton
class HealthSteps @Inject constructor(@ApplicationContext private val context: Context) {

    private val _state = MutableStateFlow(StepsState())
    val state: StateFlow<StepsState> = _state.asStateFlow()

    private val mutex = Mutex()

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    val readSteps: String = HealthPermission.getReadPermission(StepsRecord::class)

    /** What to ask for: reading steps, and reading them in the background where that exists. */
    suspend fun permissionsToRequest(): Set<String> {
        if (sdkStatus() != HealthConnectClient.SDK_AVAILABLE) return setOf(readSteps)
        return if (backgroundReadSupported()) {
            setOf(readSteps, HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
        } else {
            setOf(readSteps)
        }
    }

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    /** The Play Store page for Health Connect, for phones that need it installed or updated. */
    fun installIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
        data = "market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding".toUri()
        setPackage("com.android.vending")
        putExtra("overlay", true)
        putExtra("callerId", context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Re-reads today's hours and the last five weeks of days. Failures leave the last good read. */
    suspend fun refresh(zone: ZoneId = ZoneId.systemDefault()) = mutex.withLock {
        val availability = availability()
        if (availability != StepsAvailability.Ready) {
            _state.value = _state.value.copy(availability = availability)
            return@withLock
        }
        runCatching {
            val today = LocalDate.now(zone)
            StepsState(
                availability = StepsAvailability.Ready,
                hourly = hourly(today, zone),
                daily = daily(today.minusDays(34), today),
                syncedAt = Instant.now(),
            )
        }.onSuccess { _state.value = it }
            .onFailure { Log.w(TAG, "Could not read steps", it) }
    }

    /**
     * Steps between two moments, for the move reminder. Null when Health Connect cannot be read,
     * which in the background is any phone without background reading allowed.
     */
    suspend fun stepsBetween(start: Instant, end: Instant): Long? = runCatching {
        if (sdkStatus() != HealthConnectClient.SDK_AVAILABLE) return null
        client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
            )
        )[StepsRecord.COUNT_TOTAL] ?: 0L
    }.onFailure { Log.w(TAG, "Could not read recent steps", it) }.getOrNull()

    private fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    private suspend fun availability(): StepsAvailability = when (sdkStatus()) {
        HealthConnectClient.SDK_UNAVAILABLE -> StepsAvailability.Unsupported
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> StepsAvailability.NeedsInstall
        else -> {
            val granted = runCatching { client.permissionController.getGrantedPermissions() }
                .getOrDefault(emptySet())
            if (readSteps in granted) StepsAvailability.Ready else StepsAvailability.NeedsPermission
        }
    }

    private suspend fun backgroundReadSupported(): Boolean = runCatching {
        client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }.getOrDefault(false)

    private suspend fun hourly(date: LocalDate, zone: ZoneId): List<Long> {
        val buckets = LongArray(24)
        val groups = client.aggregateGroupByDuration(
            AggregateGroupByDurationRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(
                    date.atStartOfDay(zone).toInstant(),
                    date.plusDays(1).atStartOfDay(zone).toInstant(),
                ),
                timeRangeSlicer = Duration.ofHours(1),
            )
        )
        // Placed by the local hour each bucket starts in, which also folds the repeated hour of a
        // clocks-back night into one bar instead of overflowing the day.
        groups.forEach { group ->
            val hour = group.startTime.atZone(zone).hour
            buckets[hour] += group.result[StepsRecord.COUNT_TOTAL] ?: 0L
        }
        return buckets.toList()
    }

    private suspend fun daily(from: LocalDate, toInclusive: LocalDate): Map<LocalDate, Long> =
        client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(
                    from.atStartOfDay(),
                    toInclusive.plusDays(1).atStartOfDay(),
                ),
                timeRangeSlicer = Period.ofDays(1),
            )
        ).mapNotNull { group ->
            group.result[StepsRecord.COUNT_TOTAL]?.let { group.startTime.toLocalDate() to it }
        }.toMap()

    private companion object {
        const val TAG = "HealthSteps"
        const val PROVIDER = "com.google.android.apps.healthdata"
    }
}
