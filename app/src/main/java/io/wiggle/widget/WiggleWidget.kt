package io.wiggle.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
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
import io.wiggle.data.db.ReminderKind
import io.wiggle.domain.LogStatus
import io.wiggle.domain.TodayOverview
import io.wiggle.domain.format
import io.wiggle.domain.formatVolume
import io.wiggle.domain.volumeUnitLabel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
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

/** Long enough for a cold database, short enough that the widget never looks stuck. */
private const val LOAD_TIMEOUT_MILLIS = 6_000L

/** Which person a widget button is for, since one widget now shows several. */
private val ProfileIdKey = ActionParameters.Key<Long>("profileId")

private val Ink = ColorProvider(Color(0xFFF4F4F5))
private val Muted = ColorProvider(Color(0xFF9BA1AE))
private val Surface = ColorProvider(Color(0xFF12151C))
private val Raised = ColorProvider(Color(0xFF1D2430))
private val Done = ColorProvider(Color(0xFF5BE3B5))
private val Waiting = ColorProvider(Color(0xFFFFB27A))
private val Water = ColorProvider(Color(0xFF4FA8FF))
private val Track = ColorProvider(Color(0xFF2A3140))

/** One line of the overview: what it is, whether it is done, and the number behind it. */
private data class Task(
    val label: String,
    val status: LogStatus,
    val detail: String,
)

/** One person's day, as the widget draws it. */
private data class ProfileCard(
    val profileId: Long,
    val name: String,
    val tasks: List<Task>,
    val waterLine: String,
    val waterRemaining: String,
    val waterProgress: Float,
    val waterMet: Boolean,
)

/**
 * A home-screen readout of today: what has been logged, what is still waiting, and how much water
 * is left to drink.
 *
 * Every person is drawn, one card each, in a list the widget scrolls: a home-screen widget cannot
 * page sideways, and a scroll is the one gesture the launcher will hand to it.
 *
 * Deliberately a readout with one button rather than a second app. Anything that needs a number
 * typed in opens Wiggle, which is both less code here and a better place to type.
 */
class WiggleWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // A widget that never reaches provideContent sits on its loading layout for good, which is
        // exactly what a read that throws or never returns looks like on the home screen. Whatever
        // happens in load, something gets drawn.
        val cards = runCatching { withTimeout(LOAD_TIMEOUT_MILLIS) { load(context) } }.getOrNull()
        provideContent { Content(cards) }
    }

    private suspend fun load(context: Context): List<ProfileCard> {
        val repository = repositoryOf(context)
        val settings = repository.settings.first()
        val profiles = repository.profiles.first()
        if (profiles.isEmpty()) return emptyList()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // The active person first, so the one card a small widget shows is the one in use.
        val ordered = profiles.sortedByDescending { it.id == settings.activeProfileId }

        return ordered.map { profile ->
            val reminders = repository.remindersFor(profile.id).associateBy { it.kind }

            // Named for the log sheet it was written for, but it is simply "this person's most
            // recent weigh-in", which is what a card needs too.
            val latestWeight = repository.getLatestForPrefill(profile.id)
            val weighedToday = latestWeight?.measuredAt?.toLocalDate(zone) == today
            val weighIn = Task(
                label = "Weigh-in",
                status = TodayOverview.statusFor(
                    loggedToday = weighedToday,
                    dueToday = TodayOverview.dueToday(reminders[ReminderKind.WeighIn], today),
                ),
                detail = if (weighedToday && latestWeight != null) {
                    settings.weightUnit.fromKg(latestWeight.weightKg).format(1) +
                        " ${settings.weightUnit.label}"
                } else {
                    "not logged"
                },
            )

            val latestBody = repository.latestBody(profile.id)
            val measuredToday = latestBody?.measuredAt?.toLocalDate(zone) == today
            val measurements = Task(
                label = "Measurements",
                status = TodayOverview.statusFor(
                    loggedToday = measuredToday,
                    dueToday = TodayOverview.dueToday(reminders[ReminderKind.Measurements], today),
                ),
                detail = when {
                    measuredToday -> "done"
                    latestBody == null -> "never taken"
                    // A tape session is weeks apart, so when it last happened is the useful number.
                    else -> "${daysSince(latestBody.measuredAt, zone, today)}d ago"
                },
            )

            val drunk = repository.waterTotalToday(profile.id)
            val goal = profile.dailyWaterGoalMl
            val left = TodayOverview.remainingMl(drunk, goal)
            val unit = settings.volumeUnit

            ProfileCard(
                profileId = profile.id,
                name = profile.name,
                tasks = listOf(weighIn, measurements),
                waterLine = "${formatVolume(drunk, unit)} of " +
                    "${formatVolume(goal, unit)} ${volumeUnitLabel(goal, unit)}",
                waterRemaining = if (left == 0) "Goal met"
                else "${formatVolume(left, unit)} ${volumeUnitLabel(left, unit)} to go",
                waterProgress = if (goal <= 0) 0f else (drunk.toFloat() / goal).coerceIn(0f, 1f),
                waterMet = left == 0,
            )
        }
    }

    @Composable
    private fun Content(cards: List<ProfileCard>?) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Surface)
                .cornerRadius(24.dp)
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (cards.isNullOrEmpty()) {
                Text(
                    if (cards == null) "Could not read today. Tap to refresh."
                    else "Open Wiggle to get started",
                    style = TextStyle(color = Muted, fontSize = 13.sp),
                    modifier = GlanceModifier.clickable(
                        if (cards == null) actionRunCallback<RefreshAction>()
                        else actionStartActivity<MainActivity>()
                    ),
                )
                return@Column
            }

            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(cards, itemId = { it.profileId }) { card ->
                    ProfileBlock(card, showHint = cards.size > 1 && card == cards.first())
                }
            }
        }
    }

    /**
     * One person's card.
     *
     * Kept to three children, and spaced with padding rather than Spacers, because Glance turns a
     * container into a RemoteViews layout that holds at most ten children: go over and the whole
     * translation throws, which on the home screen looks like a widget that never finishes loading.
     */
    @Composable
    private fun ProfileBlock(card: ProfileCard, showHint: Boolean) {
        Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    "${card.name} · today",
                    style = TextStyle(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
                )
                if (showHint) {
                    Spacer(GlanceModifier.defaultWeight())
                    // Nothing else says the list goes on past the bottom of the widget.
                    Text("scroll ⌄", style = TextStyle(color = Muted, fontSize = 11.sp))
                }
            }

            Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp)) {
                card.tasks.forEach { TaskRow(it) }
            }

            Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.Bottom, modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        "Water",
                        style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Text(card.waterLine, style = TextStyle(color = Muted, fontSize = 12.sp))
                }
                LinearProgressIndicator(
                    progress = card.waterProgress,
                    color = if (card.waterMet) Done else Water,
                    backgroundColor = Track,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .padding(top = 0.dp)
                        .cornerRadius(3.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp),
                ) {
                    Text(
                        card.waterRemaining,
                        style = TextStyle(
                            color = if (card.waterMet) Done else Waiting,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        "+$QUICK_ADD_ML ml",
                        style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        modifier = GlanceModifier
                            .background(Raised)
                            .cornerRadius(16.dp)
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                            .clickable(
                                actionRunCallback<AddWaterAction>(
                                    actionParametersOf(ProfileIdKey to card.profileId)
                                )
                            ),
                    )
                }
            }
        }
    }

    @Composable
    private fun TaskRow(task: Task) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp),
        ) {
            // Glance has no icon of its own to hand, and a character costs no drawable.
            Text(
                when (task.status) {
                    LogStatus.Done -> "✓"
                    LogStatus.Due -> "!"
                    LogStatus.Idle -> "·"
                },
                style = TextStyle(
                    color = when (task.status) {
                        LogStatus.Done -> Done
                        LogStatus.Due -> Waiting
                        LogStatus.Idle -> Muted
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(task.label, style = TextStyle(color = Ink, fontSize = 13.sp))
            Spacer(GlanceModifier.defaultWeight())
            Text(
                if (task.status == LogStatus.Due) "due today" else task.detail,
                style = TextStyle(
                    color = if (task.status == LogStatus.Due) Waiting else Muted,
                    fontSize = 12.sp,
                ),
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
        // The card names the person, so a drink lands on the right one even when the widget is
        // showing someone who is not the selected profile.
        val profileId = parameters[ProfileIdKey]
            ?: repository.settings.first().activeProfileId
        if (profileId == 0L) return
        repository.addWater(profileId, QUICK_ADD_ML)
        WiggleWidget().updateAll(context)
    }
}

/** Tries the read again, for the case where the first one timed out or the database was busy. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        WiggleWidget().updateAll(context)
    }
}

class WiggleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WiggleWidget()
}

private fun Long.toLocalDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

private fun daysSince(millis: Long, zone: ZoneId, today: LocalDate): Long =
    today.toEpochDay() - millis.toLocalDate(zone).toEpochDay()
