# Wiggle — build state

Android body-weight tracker. iOS-style frosted glass UI. Package `io.wiggle` (`io.wiggle.debug` for debug).

## Toolchain (matched to the working `C:\Users\Yashwanth\YT` project)

Gradle 9.6.1 · AGP 9.3.1 · Kotlin 2.4.10 (AGP built-in — **do not** add `org.jetbrains.kotlin.android`)
· KSP 2.3.11 · `compose-bom-alpha` 2026.08.00 · compileSdk 37 / targetSdk 36 / minSdk 26
· Room 2.8.4 · Hilt 2.60.1 · Haze 1.7.3 · Glance 1.1.1 · DataStore **1.1.1** (1.2.1 breaks on Windows).

Root `build.gradle.kts` needs `classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:…")` for the
`kotlin { compilerOptions { } }` DSL to resolve under AGP 9.

## Commands

```
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease          # signed, minified, ~2.6 MB
./gradlew :app:testDebugUnitTest        # 38 tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.wiggle.debug/io.wiggle.MainActivity
```

Emulator AVD `wiggle_pixel` (android-35, x86_64). In Git Bash, `export MSYS_NO_PATHCONV=1`
before any `adb shell` command with an absolute device path, or `/sdcard/x` becomes
`C:/Program Files/Git/sdcard/x`. Never poll with `grep -qc` (always exits non-zero → infinite loop).
`adb shell input keyevent KEYCODE_BACK` on the first screen exits the app, which silently brings
the *other* build to the foreground — check which package is resumed before trusting a screenshot.

Debug builds seed sample data on first launch (two profiles, 120 days of weights) and mark
onboarding complete, so first-run setup only appears in release or after `pm clear`.

## Release signing

`wiggle-release.jks` + `keystore.properties` sit at the project root and are read by
`app/build.gradle.kts`. **Back both up.** Losing the keystore means this app can never be updated
under the same identity. Without `keystore.properties`, `assembleRelease` still builds, unsigned.

Current release: `Wiggle 1.1` (versionCode 2). 1.0 (versionCode 1) is still at
`C:\Users\Yashwanth\Downloads\Wiggle-1.0.apk`.

## Done — each compiles, installs and runs

0. **Icon** — the ring-and-bars mark, drawn as vectors for the adaptive foreground, the themed
   monochrome layer and the notification glyph, so one shape covers every size.
1. **Theme + glass** — aurora background, Haze glass recipe, `#1A2140` @85% fallback below API 31,
   Sora/Manrope variable fonts, self-drawn Lucide icons, liquid-stretch tab bar, dark and light
   themes, theme mode follows the system by default (Settings → Appearance).
2. **Data layer** — Room v2, every table carries `profileId` (multi-account). `Stats`, `BulkParse`,
   `WaterCoach`, `Greeting` and `Csv` are pure Kotlin: **38 unit tests, all passing**.
3. **Today + log sheet** — greeting header ("Good evening, Yash", auto-shrinking to one line),
   hero card, rolling digits, water ring, BMI, "Up next"; the plus opens a quick-add sheet
   (weight / body / water); ruler wheel with haptics; confetti.
4. **Weight** (was "Trends") — 1W…All, line chart with draw-in, scrub and range morph, projection,
   weekly rate, weekday bars, streak, swipe-to-delete history + undo, "Log weight" in the header.
5. **Body** — US Navy body fat, waist-to-hip, per-part cards, step-by-step editor with an animated
   tape band on a drawn figure, custom measurement types, backdatable sessions.
6. **Water** — springy bottle (double sine surface, bubbles, accelerometer tilt), +150/250/500 and
   a custom-amount sheet, today's log with swipe-to-delete and undo, weekly bars, and `WaterCoach`:
   encouragement when behind, a cheer at the goal, and a hyponatremia warning past 3.5 L / 5 L.
7. **Settings** (replaced the Alerts tab) — person card, switch/edit/add person, the three alerts
   with editors (time, days, cadence, water window and interval), appearance, haptics, units,
   goals, bulk add, delete-all-data, version and byline footer.
8. **Onboarding** — welcome, name and units, height and sex, goal, first weigh-in with live BMI.
   Every step skippable; nothing is written until the last step.
9. **Alarms and notifications** (1.1) — `ReminderScheduler` mirrors the `reminders` table into
   AlarmManager: one alarm per reminder, re-armed by `ReminderReceiver` after each firing, because
   a repeating alarm cannot express "every other Sunday" or "skip when the goal is already met".
   `WiggleApp` collects `repository.allReminders` and calls `sync`, so a toggle, an edited time, a
   new person and a deleted one are all covered without any call site remembering to reschedule.
   Exact where the system allows it, a ten-minute window where it does not. One channel per kind.
   Actions: Log now (deep-links into the log sheet), Add 250 ml, Snooze 30m. A water nudge is
   dropped when the goal is met or a drink was logged in the last 30 minutes. Boot, time change,
   timezone change and package replacement all re-sync.
10. **CSV export** (1.1) — Settings → Data → Export CSV writes weight, body and water files into
   `cacheDir/export` and hands them to the share sheet through a FileProvider. `Csv` is pure and
   tested, and the weight file is written in the shape `BulkParse` already reads, so an export
   imports straight back.
11. **Glance widget** (1.1) — 3x2 home-screen readout: name, latest weight, weekly rate, water
   against the goal, and a +250 ml button that logs without opening anything. Tapping the body
   opens the app. Refreshed by `WiggleApp` when the data changes, with the provider's own
   30-minute timer as a backstop.
12. **Accessibility** (1.1) — rolling digits read as one number instead of digit by digit, screen
   titles are headings, both charts carry a spoken summary, the undo snackbar is a polite live
   region, and the light theme's faintest ink went from 2.9:1 to 4.5:1 against its canvas.
   Material's ripple is switched off app-wide (`LocalIndication`, provided *inside* `MaterialTheme`,
   which re-provides its own): it drew a grey rectangle around pill toggles and glass rows.

## Remaining

- **Health Connect** — read and write weight. Never wired up.
- **Shared-element morph** from the log sheet into the hero card.
- **Reduce-motion** is honoured throughout but only follows the system setting; there is no
  in-app override.

## Known gaps

- Not a git repository yet.
- Health Connect dependency is not in the Gradle cache; it will download on first use.
