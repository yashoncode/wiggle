# Steps: counting, daily goal, tracking and analytics

**Status: superseded by Wiggle 2.0 (2026-10-03), which took the Health Connect route below.** Kept for the reasoning; the sensor route was not built.
this until the open questions below are settled and the design is signed off. The data-and-counting
section was presented but not approved: the user then asked how much work it would be to read steps
from the phone's own health app instead, which reopens the step source (see "Alternative: Health
Connect").

## What was asked

Count steps, set a daily step goal, track steps over time, and show analytics for them.

## Decisions so far

| Question | Answer |
|---|---|
| Where steps come from | The phone's step sensor, counted by Wiggle itself. **Reopened**, see below. |
| Whose steps | Whoever is the active person when the sensor reports. Switching person mid-day splits that day's steps between them; accepted. |
| Where it lives | The Water tab becomes **Daily** and holds both water and steps. |
| Analytics | All four: range bars with a summary, goal streak and hit rate, today hour by hour, distance and calorie estimates. |
| Extras | Goal-reached alert, evening nudge, steps on the widget. |
| Left out | Steps in the CSV export, manual step entry or editing, writing to Health Connect. |

## Open questions

1. **Step source.** Sensor (current choice), Health Connect, or both. Health Connect is less code
   and has far fewer failure modes, but it only works if the phone's health app writes steps into
   it. Check that first.
2. **Goal default.** 8,000 proposed; 10,000 is the classic figure.
3. **Daily tab layout.** A Water | Steps switch at the top (proposed), or one long scroll holding
   both, which is what "merge into Water" literally described. Five steps cards under four water
   cards buries the steps analytics.
4. **Steps accent colour.** Violet proposed, taken from the existing background glow. The light
   theme needs a darker shade that clears 4.5:1, like the other accents.

## What the design builds on

- There is no navigation graph: `WiggleRoot` switches on `tabIndex`. Daily keeps Water's slot
  (index 3), so the hardcoded tab indices do not move.
- Water is the pattern for a per-person daily goal: `ProfileEntity.dailyWaterGoalMl`,
  `WaterGoalSheet`, the weekly `BarChart`, confetti on crossing the goal.
- Weight is the pattern for analytics: the range picker, `Stats` (streak, weekday averages), stat
  tiles.
- Every table carries `profileId` and cascades on delete.
- `ReminderScheduler` and `ReminderReceiver` already handle exact alarms, boot, time changes and app
  updates.
- Pure logic lives in `domain/` with plain JUnit tests (53 today).

## Design: sensor route

### Data (database version 3, migration 2 → 3)

- `step_hours(profileId, hourStart, steps)`: primary key `(profileId, hourStart)`, foreign key to
  `profiles` with cascade. `hourStart` is the epoch millis of the start of the local hour the steps
  were taken in. Local hour rather than UTC hour because India is UTC+5:30, so a UTC hour straddles
  two local hours. Reads group by local date and hour in the current zone, as water already does.
- `step_sensor_state`: one row holding `bootCount`, `counter` and `readAt`. It is per phone, not per
  person, so switching person can never count the same steps twice.
- `profiles.dailyStepGoal INTEGER NOT NULL DEFAULT 8000`.
- `ReminderKind.Steps`, appended **last**: the ordinal is part of alarm request codes and
  notification ids, so inserting it anywhere else would orphan alarms that are already set.
- DataStore: `stepCountingEnabled`, per phone.
- Hourly rather than daily rows, because "today hour by hour" needs them. A year is at most 8,760
  rows per person.

### Turning sensor readings into steps

`TYPE_STEP_COUNTER` reports the steps taken since boot, and the Android docs say it only counts
while some app keeps it registered. So a foreground service keeps a listener registered, and each
report becomes a delta. The rules live in a pure `Steps` object:

- The first reading after counting is switched on sets the baseline and adds nothing. Switching
  counting on clears the state row.
- Boot count changed (`Settings.Global.BOOT_COUNT`): steps = counter, because the counter restarted
  at zero.
- Same boot: steps = counter − last counter. A counter that goes down is treated as a reset.
- A jump faster than 250 steps a minute since the last reading is dropped rather than counted, so
  one sensor glitch cannot add 12,000 steps. The baseline still moves.
- The steps go to the hour of the last step in the report (the event timestamp converted to wall
  time), clamped between the last reading and now in case a phone reports a nonsense timestamp.
- No active person yet (during onboarding): the baseline moves and the steps are dropped.
- The state row and the hour row are written in one Room transaction, so a process killed mid-write
  cannot double count. Update-then-insert, not SQL `UPSERT`: the SQLite on API 26–29 predates it.
- Readings pass through a `MutableStateFlow` with one collector. The counter is cumulative, so only
  the latest reading matters, and two readings can never be processed out of order.

### Service and permissions

- `steps/StepCounterService`: a foreground service of type `health`, `START_STICKY`, with sensor
  reports batched about once a minute.
- The sensor route needs a permanent notification, so it should earn its place: "Yash · 6,432
  steps · 1,568 to go". Low importance, silent, updated on each report and at midnight. Tapping it
  opens Daily on Steps.
- Started from `MainActivity.onStart`, from `ReminderReceiver` on boot and on app update, and when
  the toggle is switched on. Android allows a foreground service to start from each of those.
  Stopped when the toggle goes off or the permission is revoked.
- Manifest: `ACTIVITY_RECOGNITION` (runtime, API 29+), `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_HEALTH`, the `<service>` with `foregroundServiceType="health"`, and
  `<uses-feature android:name="android.hardware.sensor.stepcounter" android:required="false" />`.
- A phone with no step counter: the Steps view says so and the toggle is disabled.
- "Delete all data" clears the person's steps too.

### UI (proposed, not yet reviewed)

**Daily tab.** Label "Daily" with a new Lucide `Activity` icon. The header moves out of
`WaterScreen` into a small `DailyScreen`: the date eyebrow, the title "Daily", the selected goal as a
pill, then the Water | Steps switch. The selected segment is held in `WiggleRoot` next to
`tabIndex`, so the Today cards and notification taps can open the right one. The Water segment is
today's Water screen, unchanged.

**Steps segment**, top to bottom:

1. Today: a ring against the goal (`ProgressRing` already draws overflow past 100%), today's steps,
   "1,568 to go" or "Goal met", and the distance and calorie estimates.
2. A status card, shown only when not counting: off ("Count steps with this phone", which asks for
   the activity permission), permission denied (explains and opens app settings), or no sensor. It
   also carries one line about allowing background activity on phones that kill apps.
3. Today by hour: 24 bars labelled 12a / 6a / 12p / 6p, and "Most active: 6–7 pm".
4. Range: 1W / 1M / 3M / 1Y. 1W and 1M draw one bar per day. 3M draws 13 weekly averages and 1Y
   draws 12 monthly averages, because 90 or 365 bars do not fit a phone. A dashed goal line runs
   across all of them. Tiles: average per day, total, best day, goal met X of N days, distance.
5. Streaks and pattern: the current and longest run of goal days, and the average for each weekday.

Confetti when today's total crosses the goal while the Steps segment is on screen.

**Today.** A full-width Steps card under the Water/BMI row: ring, steps, how many to go. Tapping it
opens Daily on Steps. Nothing else on Today moves.

**Settings.** A "Daily steps" row in Goals opens a `StepGoalSheet` (±500, 1,000 to 40,000). A new
Steps card holds the counting toggle, a status line ("Counting for whoever is active", "Off", "Needs
activity permission", "No step counter on this phone") and a row that opens the app's battery
settings. The evening nudge shows up in Alerts by itself, because that list maps
`ReminderKind.entries`.

**Charts.** `BarChart` gains an optional dashed reference line for the goal, included in its maximum
so the line always fits. The hour chart passes blank labels for most of its 24 bars.

### Rules for the numbers

- A day with no rows is "no data", not zero. Averages and hit rates cover days with data and say so:
  "goal met 18 of 20 days with data".
- Streak: consecutive goal days ending today, or ending yesterday while today is not yet met. This is
  the same rule as `Stats.currentStreak`.
- Past days are judged against today's goal, because no goal history is kept. Add one if that turns
  out to mislead.
- Distance: steps × stride, where stride = height × 0.415 (male), 0.413 (female) or 0.414
  (unspecified). Kilometres with the centimetre unit, miles with inches.
- Calories: 0.5 kcal per kg per km walked (the net cost of walking), using the latest weigh-in, and
  hidden when there is none. Shown as an estimate, like body fat.

### Extras (proposed, not yet reviewed)

- **Goal-reached alert.** The recording transaction returns the active person's day total before
  and after. Crossing the goal posts one notification on a "Step goal" channel; tapping it opens
  Daily on Steps.
- **Evening nudge.** `ReminderKind.Steps`, off by default, 19:00 every day, scheduled like the
  weigh-in (`nextDaily`). Skipped when the goal is met, reusing `pauseWhenGoalMet`. Body: "3,120
  steps so far, 4,880 to go." Every exhaustive `when` over `ReminderKind` needs a Steps branch:
  `Alarms.openTargetFor`, `ReminderSchedule.nextOccurrence` and `title`, `ReminderReceiver`, Today's
  "Up next", and the Settings alert row and reminder sheet. Add `Alarms.OPEN_STEPS`.
- **Widget.** A steps line and bar under water on each person's card, as one nested block, so the
  card stays well under Glance's ten-children limit. The service writes about once a minute while
  walking, so steps refresh the widget at most once every five minutes. Check that the card still
  fits the 3x3 size.

### Debug sample data

`SampleData` seeds hourly steps for both sample people (120 and 60 days): commute peaks, quieter
weekends, a few gaps, and today part-way to the goal, so every chart has something to draw.

### Testing

- `StepsTest` covers every counting rule (first reading, reboot, counter going down, implausible
  jump, no active person, timestamp clamp) and every analytics rule (local day and hour grouping,
  including the +5:30 offset and a DST change, streaks, hit rate, weekly and monthly buckets, best
  day, weekday averages, distance and calories).
- The existing 53 tests still pass.
- On a real phone: walk and watch the count and the notification; reboot and check that the day
  carries on; switch person mid-walk; turn counting off and on again.
- Migration: install the 1.5 release with data, install the new build over it, and check that
  weights, water and body data survive and steps start empty.
- Test the release build, not only debug: NOTES records R8 breaking the widget in release alone.
- Ship as 1.6 (versionCode 7), because the in-app update check compares version names.

### Known limits

- Steps taken while the service was dead land in the hour they are first seen.
- Roughly the last minute of steps before a reboot is lost.
- Phones that kill background apps (ColorOS, Realme UI) can stop the service. The fix is the battery
  setting NOTES already gives for the widget.
- Steps belong to whoever was active, so a switch mid-walk splits them.

## Alternative: read steps from the phone's health app (Health Connect)

The user asked how much work it would be to take steps from the phone's own health app instead of
counting them in Wiggle.

**Health Connect is the only realistic bridge.** Samsung Health's own SDK needs Samsung partner
approval, and phone makers' health apps such as OPPO's and Realme's do not offer a public one.
Health Connect is Android's shared health store: health apps write steps into it, and any app with
permission can read them. Samsung Health, Google Fit and Fitbit write steps there. Whether the
phone's own health app does is the thing to check.

**Check the phone first.** Open Health Connect (search Settings for "Health Connect"; on Android 13
and older it is a separate app). Under app permissions, see whether the phone's health app may write
Steps; under the data view, see whether today has step entries. If neither, this route has nothing
to read.

**Work involved**

1. Add `androidx.health.connect:connect-client`. NOTES already expects this dependency to download
   on first use.
2. Manifest: `android.permission.health.READ_STEPS`, and a small screen explaining why Wiggle reads
   steps. It is reached through `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` on Android 13
   and older, and through an activity-alias for `android.intent.action.VIEW_PERMISSION_USAGE` with
   category `android.intent.category.HEALTH_PERMISSIONS` on Android 14+. Android 13 and older also
   need a `<queries>` entry for the Health Connect package.
3. `HealthConnectClient.getSdkStatus`: available; needs installing or updating (Android 9–13); or
   unavailable (Android 8, since minSdk is 26), in which case the Steps segment explains.
4. The permission request, through `PermissionController.createRequestPermissionResultContract()`.
5. Sync: `aggregateGroupByDuration` in hourly buckets for recent days, replacing rows in the same
   `step_hours` table. Health Connect merges phone and watch by the user's own priority settings,
   so nothing is counted twice. Everything downstream (goal, analytics, Today card, widget, alerts)
   is the same code as the sensor route.
6. History: 30 days before the first grant, or older with `READ_HEALTH_DATA_HISTORY`.
7. Background: the widget, goal alert and evening nudge run with the app closed. Reading then needs
   `READ_HEALTH_DATA_IN_BACKGROUND`, which only newer Health Connect versions offer. Without it they
   use the last synced numbers.

**What goes away:** the foreground service, the permanent notification, `ACTIVITY_RECOGNITION` and
both foreground-service permissions, the `step_sensor_state` table, every counting rule above, and
the battery-killer problem, since the phone maker's own app does the counting.

**What changes:** the owner rule. Health Connect holds the phone owner's steps and a sync re-reads
whole days, so "whoever is active" would hand past days to whoever happens to be selected at sync
time. This route needs a fixed "Count steps for" person.

**The trade:** less code and far fewer failure modes than the sensor route, plus history from day
one and watch steps. In exchange there is no live count (numbers update whenever the health app
writes, which varies by app), and everything depends on that app writing steps.

**Both routes:** Health Connect when it has data, the sensor otherwise. That is the union of both
work lists.

## Files (sensor route)

Paths are under `app/src/main/java/io/wiggle/` unless given in full.

New:
- `steps/StepCounterService.kt`
- `domain/Steps.kt`
- `app/src/test/java/io/wiggle/domain/StepsTest.kt`
- `ui/steps/StepsScreen.kt`, `ui/steps/StepsViewModel.kt`
- `ui/daily/DailyScreen.kt`
- `app/schemas/io.wiggle.data.db.WiggleDatabase/3.json` (generated by Room)

Changed:
- `app/src/main/AndroidManifest.xml`
- `data/db/Entities.kt`, `data/db/Daos.kt`, `data/db/WiggleDatabase.kt`, `di/AppModule.kt`
- `data/WiggleRepository.kt`, `data/prefs/SettingsStore.kt`, `data/SampleData.kt`
- `WiggleApp.kt`, `MainActivity.kt`
- `alarm/ReminderAlarms.kt`, `alarm/ReminderReceiver.kt`, `domain/ReminderSchedule.kt`
- `ui/Nav.kt`, `ui/WiggleRoot.kt`, `ui/water/WaterScreen.kt`
- `ui/today/TodayScreen.kt`, `ui/today/TodayViewModel.kt`
- `ui/settings/SettingsScreen.kt`, `ui/settings/SettingsSheets.kt`, `ui/settings/SettingsViewModel.kt`
- `ui/charts/WeekdayChart.kt` (`BarChart`), `ui/icons/Lucide.kt`, `ui/theme/Color.kt`
- `widget/WiggleWidget.kt`
- `app/build.gradle.kts` (version 1.6), and `NOTES.md` once shipped

## Next step when resumed

Settle the open questions, review the UI and extras sections, then turn this into a step-by-step
implementation plan.
