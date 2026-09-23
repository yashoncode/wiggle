package io.wiggle.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.action.ActionParameters
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.wiggle.MainActivity
import io.wiggle.data.WiggleRepository
import io.wiggle.domain.DayWeight
import io.wiggle.domain.Stats
import io.wiggle.domain.format
import io.wiggle.domain.formatSigned
import io.wiggle.domain.formatVolume
import io.wiggle.domain.volumeUnitLabel
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Glance runs outside the Hilt graph, so the widget reaches into it through an entry point. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun repository(): WiggleRepository
}

private fun repositoryOf(context: Context): WiggleRepository =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        .repository()

private const val QUICK_ADD_ML = 250

private val Ink = ColorProvider(Color(0xFFF4F4F5))
private val Muted = ColorProvider(Color(0xFF9BA1AE))
private val Surface = ColorProvider(Color(0xFF12151C))
private val Raised = ColorProvider(Color(0xFF1D2430))

/** Everything the widget draws, read once per update. */
private data class WidgetData(
    val name: String,
    val weight: String,
    val weightUnit: String,
    val change: String?,
    val water: String,
)

/**
 * A home-screen readout: the latest weight, the week's rate, and how the water is going.
 *
 * Deliberately a readout with one button rather than a second app. Anything that needs a number
 * typed in opens Wiggle, which is both less code here and a better place to type.
 */
class WiggleWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { Content(data) }
    }

    private suspend fun load(context: Context): WidgetData? {
        val repository = repositoryOf(context)
        val settings = repository.settings.first()
        val profile = repository.getProfile(settings.activeProfileId) ?: return null
        val entries = repository.weightEntries.first()
        val zone = ZoneId.systemDefault()
        val daily = Stats.toDaily(
            entries.map { DayWeight(it.measuredAt.toLocalDate(zone), it.weightKg) }
        )

        return WidgetData(
            name = profile.name,
            weight = entries.firstOrNull()
                ?.let { settings.weightUnit.fromKg(it.weightKg).format(1) }
                ?: "—",
            weightUnit = settings.weightUnit.label,
            change = Stats.weeklyRateKg(daily)?.let { kg ->
                "${settings.weightUnit.fromKg(kg).formatSigned(1)} ${settings.weightUnit.label} a week"
            },
            water = run {
                val drunk = repository.waterTotalToday(profile.id)
                val goal = profile.dailyWaterGoalMl
                "${formatVolume(drunk, settings.volumeUnit)} of " +
                    "${formatVolume(goal, settings.volumeUnit)} ${volumeUnitLabel(goal, settings.volumeUnit)}"
            },
        )
    }

    @Composable
    private fun Content(data: WidgetData?) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Surface)
                .cornerRadius(24.dp)
                .padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Top,
        ) {
            if (data == null) {
                Text("Open Wiggle to get started", style = TextStyle(color = Muted, fontSize = 13.sp))
                return@Column
            }

            Text(
                data.name,
                style = TextStyle(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(GlanceModifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    data.weight,
                    style = TextStyle(color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.width(4.dp))
                Text(data.weightUnit, style = TextStyle(color = Muted, fontSize = 14.sp))
            }
            if (data.change != null) {
                Text(data.change, style = TextStyle(color = Muted, fontSize = 12.sp))
            }

            Spacer(GlanceModifier.height(10.dp))
            Text("Water ${data.water}", style = TextStyle(color = Muted, fontSize = 12.sp))
            Spacer(GlanceModifier.height(8.dp))
            Text(
                "+$QUICK_ADD_ML ml",
                style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier
                    .background(Raised)
                    .cornerRadius(16.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .clickable(actionRunCallback<AddWaterAction>()),
            )
        }
    }
}

/** The one action worth having on a home screen: a drink, logged without opening anything. */
class AddWaterAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val repository = repositoryOf(context)
        val profileId = repository.settings.first().activeProfileId
        if (profileId == 0L) return
        repository.addWater(profileId, QUICK_ADD_ML)
        WiggleWidget().updateAll(context)
    }
}

class WiggleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WiggleWidget()
}

private fun Long.toLocalDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
