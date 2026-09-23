# Wiggle — build state

Android body-weight tracker. iOS-style frosted glass UI. Package `io.wiggle` (`io.wiggle.debug` for debug).

## Toolchain (matched to the working `C:\Users\Yashwanth\YT` project)

Gradle 9.6.1 · AGP 9.3.1 · Kotlin 2.4.10 (AGP built-in — **do not** add `org.jetbrains.kotlin.android`)
· KSP 2.3.11 · `compose-bom-alpha` 2026.08.00 · compileSdk 37 / targetSdk 36 / minSdk 26
· Room 2.8.4 · Hilt 2.60.1 · Haze 1.7.3 · Glance 1.2.0 · DataStore **1.1.1** (1.2.1 breaks on Windows).

Root `build.gradle.kts` needs `classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:…")` for the
`kotlin { compilerOptions { } }` DSL to resolve under AGP 9.

## Commands

```
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease          # signed, minified, ~2.6 MB
./gradlew :app:testDebugUnitTest        # 53 tests
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

Current release: `Wiggle 1.3` (versionCode 4), published at
<https://github.com/yashoncode/wiggle/releases>. Earlier APKs are in
`C:\Users\Yashwanth\Downloads\` (`Wiggle-1.0.apk`, `Wiggle-1.1.apk`).

## Publishing a release

The app looks for new builds on the GitHub releases page, so a release is not finished until the
APK is attached to it.

```
./gradlew :app:assembleRelease
git push origin main
git tag -a v1.4 -m "Wiggle 1.4" && git push origin v1.4
```

Then create the release and attach `app/build/outputs/apk/release/app-release.apk` to it, named
`Wiggle-<version>.apk`. The GitHub CLI is not installed on this machine; the REST API works with
the credential Git already has:

```
GHTOK=$(printf "protocol=https\nhost=github.com\n\n" | git credential fill | sed -n 's/^password=//p')
```

`POST /repos/yashoncode/wiggle/releases` creates it, then POST the APK to the `upload_url` it
returns with `Content-Type: application/vnd.android.package-archive`. The
`GITHUB_PERSONAL_ACCESS_TOKEN` in the environment is read-only for contents and cannot do either.

**Never commit `keystore.properties` or `wiggle-release.jks`.** Both are in `.gitignore`; with
them, anyone can publish a build that Android installs straight over this app.

## Done — each compiles, installs and runs

0. **Icon** — the ring-and-bars mark, drawn as vectors for the adaptive foreground, the themed
   monochrome layer and the notification glyph, so one shape covers every size.
1. **Theme + glass** — aurora background, Haze glass recipe, `#1A2140` @85% fallback below API 31,
   Sora/Manrope variable fonts, self-drawn Lucide icons, liquid-stretch tab bar, dark and light
   themes, theme mode follows the system by default (Settings → Appearance).
2. **Data layer** — Room v2, every table carries `profileId` (multi-account). `Stats`, `BulkParse`,
   `WaterCoach`, `Greeting`, `Csv`, `Updates` and `TodayOverview` are pure Kotlin:
   **53 unit tests, all passing**.
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
11. **Glance widget** (1.1, rebuilt in 1.3) — a 3x3 readout of today, one card per person in a
   list the widget scrolls: weigh-in and measurements marked done, due or idle, then water against
   the goal with a bar, how much is left, and a +250 ml button that logs to *that* person without
   opening anything. `TodayOverview` decides done/due/idle and is tested. Refreshed by `WiggleApp`
   when weight, body, water or reminders change, with the provider's 30-minute timer as a backstop.
   A widget cannot page sideways, so the list scrolls instead.

   Two traps live here. **A Glance container holds at most ten children**; go over and the whole
   translation throws and the widget sits on its loading layout for good, which is why a card is
   three nested blocks spaced with padding rather than a flat run of Spacers. And `provideGlance`
   loads under `withTimeout` inside a `runCatching`, so a slow or failing read still reaches
   `provideContent` and draws something tappable instead of spinning.
12. **Accessibility** (1.1) — rolling digits read as one number instead of digit by digit, screen
   titles are headings, both charts carry a spoken summary, the undo snackbar is a polite live
   region, and the light theme's faintest ink went from 2.9:1 to 4.5:1 against its canvas.
   Material's ripple is switched off app-wide (`LocalIndication`, provided *inside* `MaterialTheme`,
   which re-provides its own): it drew a grey rectangle around pill toggles and glass rows.

13. **In-app updates** (1.2) — `UpdateChecker` reads the latest GitHub release on launch and the
   sheet offers it, with the release notes stripped of their Markdown and a button that hands the
   APK to the browser. Settings has a manual check that also answers "you are on the latest".
   `HttpURLConnection` and `org.json`, because one unauthenticated GET is not worth an HTTP stack
   in the APK. A failed check is silent: no network is not news.

14. **Feel** (1.2) — tapping a card gives it a short damped wobble (`Modifier.wiggleOnTap`), which
   is how a surface with nothing behind it still answers a finger; it adds no click semantics, so
   a screen reader is not told a static card is a button. The −/+ steppers were rebuilt around
   `rememberUpdatedState`: keying their `pointerInput` on the lambda tore the gesture down mid-press,
   stranding the press highlight and leaving the repeat loop counting from a stale value.

15. **Widget overview** (1.3) — see the widget entry above: the readout became today's checklist,
   several people at once, and each card logs its own water.

## Remaining

- **Health Connect** — read and write weight. Never wired up.
- **Shared-element morph** from the log sheet into the hero card.
- **Reduce-motion** is honoured throughout but only follows the system setting; there is no
  in-app override.

## Known gaps

- The app downloads an update through the browser rather than installing it itself. Doing that
  in-app needs `REQUEST_INSTALL_PACKAGES`, a `DownloadManager` job and an installer intent.
- A dismissed update is offered again on the next launch; there is no "skip this version".
- Tag `v1.2` sits one commit before `Render release notes as plain text`, which is in the released
  APK. Moving a published tag needed a force push, so the tag was left where it was.
- The widget shows the loading layout for as long as the launcher takes to start the app process
  after an install, which on a cold device is tens of seconds. Nothing in the app can shorten it.
- Health Connect dependency is not in the Gradle cache; it will download on first use.
