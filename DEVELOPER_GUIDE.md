# MeetMate — Developer Guide

MeetMate is a small Android app for scheduling meetings and getting **alarm-style reminders** before they start. You can type or speak a sentence like *"Standup with Priya every weekday at 10am"*. The app parses it into a title, person, date, time and repeat rule, saves it locally, and schedules an exact alarm. When the alarm goes off, a full-screen alarm screen rings, with Snooze, Dismiss and (when the meeting has a link) Join.

This guide covers setting up, building and running the app, how the code is organized, and how data moves through it.

---

## 1. Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.2.21 (JVM target 17) |
| UI | Jetpack Compose (BOM 2025.12.01), Material 3, Compose Navigation 2.9 |
| State | `AndroidViewModel` + Kotlin `Flow` / `StateFlow` |
| Persistence | Room 2.8.4 (KSP), schema exported to `app/schemas/` |
| Reminders | `AlarmManager.setAlarmClock` → `AlarmReceiver` → foreground `AlarmService` (sound + vibration) → full-screen `AlarmActivity` |
| Voice input | System speech recognizer (`RecognizerIntent`) |
| Extras | Home-screen widget (`RemoteViews`), Quick Settings tile, static app shortcuts |
| Build | Gradle 8.14.4 (wrapper), AGP 8.13.2, version catalog in `gradle/libs.versions.toml` |
| SDK levels | `minSdk 26`, `compileSdk 36`, `targetSdk 36` |

The app has no backend, no network calls and no DI framework. Everything is stored on the device.

---

## 2. Prerequisites

1. **JDK 17 or newer.** JDK 17 or 21 both work, and Android Studio's bundled JBR is fine.
   ```bash
   java -version
   ```
2. **Android SDK** with:
   - SDK Platform **36**
   - Android SDK Build-Tools 35 (AGP installs it automatically if your SDK licences are accepted)
   - Platform-tools (`adb`)
3. **Android Studio** (recommended), or just the command-line tools.
4. A device or emulator running **Android 8.0 (API 26)+**. To test voice input, use a device or an emulator image that includes Google Play services, since the speech recognizer comes from there.

---

## 3. First-time setup

```bash
git clone <repo-url> MeetMate
```

```bash
cd MeetMate
```

Create `local.properties` in the project root and point it at your Android SDK. Android Studio writes this file for you when you open the project. It's git-ignored, because the path is machine-specific.

```properties
sdk.dir=/home/<you>/Android/Sdk
```

Then either:

- **Android Studio:** *File → Open →* select the `MeetMate` folder and let Gradle sync, or
- **CLI:** run a build (next section). The Gradle wrapper downloads Gradle 8.14.4 on the first run.

---

## 4. Build, install, run

All commands run from the project root.

| Task | Command |
|---|---|
| Build debug APK | `./gradlew assembleDebug` |
| Install on connected device/emulator | `./gradlew installDebug` |
| Unit tests (JVM) | `./gradlew testDebugUnitTest` |
| Build release APK (R8-minified) | `./gradlew assembleRelease` |
| Clean | `./gradlew clean` |
| Lint | `./gradlew lint` |

Debug output: `app/build/outputs/apk/debug/app-debug.apk`

Launch the app after installing:

```bash
adb shell am start -n com.meetmate.app/.MainActivity
```

Follow the reminder logs (every log line uses the tag `MeetMateAlarm`):

```bash
adb logcat -s MeetMateAlarm
```

### Release builds

- Release builds use **R8** (`isMinifyEnabled` and `isShrinkResources`). Room ships its own keep rules; `app/proguard-rules.pro` adds a couple more.
- **Signing** reads `keystore.properties` in the project root (git-ignored). Without that file, `assembleRelease` produces an unsigned APK. To sign, create a keystore once:
  ```bash
  keytool -genkeypair -v -keystore meetmate-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias meetmate
  ```
  Then create `keystore.properties`:
  ```properties
  storeFile=meetmate-release.jks
  storePassword=…
  keyAlias=meetmate
  keyPassword=…
  ```
  Keep the keystore and passwords somewhere safe and **never commit them**. Losing the key means you can't ship updates to the same app listing.

---

## 5. Project layout

```
MeetMate/
├── build.gradle.kts            # root: declares plugins (apply false)
├── settings.gradle.kts         # repositories, includes :app
├── gradle/libs.versions.toml   # ← all dependency & plugin versions live here
└── app/
    ├── build.gradle.kts        # android {} config, signing, R8, Room schema export, dependencies
    ├── schemas/                # exported Room schemas (1.json, 2.json); also migration-test fixtures
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/            # theme, icons, widget layout, shortcuts.xml, widget info
        │   └── java/com/meetmate/app/
        │       ├── MeetMateApp.kt           # Application: creates the notification channel
        │       ├── MainActivity.kt          # Compose host, NavHost, theme, permission checks, intent routing
        │       ├── AlarmActivity.kt         # full-screen ringing screen over the lock screen
        │       ├── data/
        │       │   ├── Meeting.kt           # Room @Entity + Repeat enum + categories
        │       │   ├── MeetingDao.kt
        │       │   ├── MeetingDatabase.kt   # version 2, AutoMigration 1→2
        │       │   ├── MeetingDraft.kt      # what the add/edit screen returns
        │       │   └── AppSettings.kt       # theme preference (SharedPreferences)
        │       ├── viewmodel/MeetingViewModel.kt  # CRUD, undo, re-schedules all alarms on start
        │       ├── parser/NaturalLanguageParser.kt
        │       ├── util/
        │       │   ├── Recurrence.kt        # next occurrence + ReminderSchedule.plan()
        │       │   ├── TimeLabels.kt        # countdowns, day labels, greetings, initials
        │       │   └── Links.kt             # find / open meeting links
        │       ├── notification/
        │       │   ├── NotificationHelper.kt   # channel, permission checks, schedule/cancel/snooze alarms
        │       │   ├── AlarmReceiver.kt        # alarm fired → mark reminded, queue next, start AlarmService
        │       │   ├── AlarmService.kt         # foreground service: sound, vibration, notification actions
        │       │   └── RescheduleReceiver.kt   # boot / app update / time or time-zone change
        │       ├── widget/NextMeetingWidget.kt
        │       ├── tile/QuickAddTileService.kt
        │       └── ui/
        │           ├── theme/
        │           ├── screens/  HomeScreen.kt, AddMeetingScreen.kt
        │           └── components/  MeetingCard, NextUpCard, Avatar, AnimatedFab, EmptyState,
        │                            VoiceInputButton, ReminderIssues, SettingsSheet
        ├── test/          # JVM unit tests (parser, recurrence, reminder scheduling, labels)
        └── androidTest/   # on-device tests (DAO, database migration)
```

To add or upgrade a dependency, edit `gradle/libs.versions.toml` first, then reference it in `app/build.gradle.kts` as `libs.<alias>`.

---

## 6. Architecture

It's a single-module MVVM app:

```
 Compose UI ──events──► MeetingViewModel ──► MeetingDao (Room) ──► SQLite "meetmate_database"
                               │
                               └──► NotificationHelper.scheduleReminder()
                                        │  uses ReminderSchedule.plan()
                                        ▼
                                  AlarmManager.setAlarmClock
                                        │  (at trigger time)
                                        ▼
                                  AlarmReceiver ──► marks occurrence reminded, queues the next one
                                        │
                                        ▼
                                  AlarmService (foreground)
                                   ├─ sound + vibration (stops after 60 s)
                                   ├─ notification: Snooze 5 min · Dismiss · Join
                                   └─ full-screen intent ──► AlarmActivity
```

- **Source of truth:** Room. `MeetingViewModel.allMeetings` is the DAO's `Flow` turned into a `StateFlow`. The UI collects it with `collectAsStateWithLifecycle()`, so any write refreshes the screen and the widget.
- **Navigation** (`MainActivity.kt`): `home`, `add?voice={bool}` and `edit/{meetingId}`.
- **Outside entry points:** app shortcuts, the Quick Settings tile and the widget open `MainActivity` with an action (`ACTION_NEW_MEETING`, `ACTION_NEW_MEETING_VOICE`). Tapping a reminder notification sends `ACTION_STOP_ALARM`.

---

## 7. Key flows

### 7.1 App start
1. `MeetMateApp.onCreate()` creates the `meetmate_alarms_v5` channel (high importance, silent: the service plays sound and vibration itself, bypasses DND) and deletes older channel IDs.
2. `MainActivity` applies the saved theme (System / Light / Dark).
3. On Android 13+ it asks for `POST_NOTIFICATIONS` once.
4. On every resume it re-checks notifications, exact alarms and full-screen alerts. Anything that's off shows as a **warning card** on the home screen with an *Allow* button that opens the right system setting.
5. `MeetingViewModel` init calls `NotificationHelper.rescheduleAll()`. A force-stop wipes an app's alarms, so opening the app restores them.

### 7.2 Creating a meeting
1. The user types into the **Just say it** box, taps an example chip, or taps the mic. A voice result or example chip is parsed straight away; typed text is parsed with → or the keyboard's Done key.
2. `NaturalLanguageParser.parse()` fills in title, person, date, time, **repeat** and **meeting link**. The user can adjust everything, add notes, pick a category, a reminder offset and a repeat rule.
3. On save, a missing date or time opens the matching picker. Otherwise the screen returns a `MeetingDraft`.
4. `MeetingViewModel.addMeeting()` inserts the row and calls `scheduleReminder()`.
5. With a date and time set, the calendar icon in the top bar opens the system calendar's "new event" screen (`CalendarContract` insert intent, including an RRULE for repeats). No calendar permission is needed.

### 7.3 Scheduling (`ReminderSchedule.plan` in `util/Recurrence.kt`)
- The **occurrence** is `dateTimeMillis` for one-off meetings, or the next occurrence at or after now for repeating ones (`Recurrence.nextOccurrence`, computed in local time so it survives DST).
- If that occurrence's reminder already rang (`lastRemindedOccurrence`), a one-off meeting gets nothing and a repeating one moves to the following occurrence. That's why re-scheduling on app start, boot or update never rings the same meeting twice.
- Trigger = occurrence − reminder minutes. If that's already past but the meeting hasn't started, it rings in 5 seconds.
- Reminder and snooze alarms both use the meeting id as request code. Different intent actions (`ACTION_REMIND`, `ACTION_SNOOZE_FIRE`) keep their `PendingIntent`s apart.
- With exact-alarm access, alarms use `setAlarmClock` (shows the alarm icon in the status bar). Without it, they fall back to inexact `setAndAllowWhileIdle`.

### 7.4 Reminder fires
1. `AlarmReceiver` loads the meeting, records the occurrence as reminded, queues the next occurrence for repeating meetings, refreshes the widget and starts `AlarmService`.
2. `AlarmService` runs in the foreground (`mediaPlayback` type). It loops the alarm sound on the alarm stream, vibrates, and posts an ongoing notification with **Snooze 5 min**, **Dismiss** and, when there's a link, **Join**.
3. On a locked or idle phone, the full-screen intent opens `AlarmActivity` with large Join / Snooze / Dismiss buttons.
4. **Tapping the notification** opens the app and stops the ringing. **Join** goes through `AlarmActivity`, because Android 12+ blocks opening another app from a service. It stops the ringing, then opens the link.
5. If nobody responds within 60 seconds, the sound stops and a normal (silent) notification stays behind.

### 7.5 Edit / complete / delete
- **Edit** (tap a card) updates the row. If the time, repeat or reminder changed, it clears `lastRemindedOccurrence` and re-schedules.
- **Complete** (swipe right) marks the meeting done and cancels its alarm. For a repeating meeting, this **ends the series**.
- **Delete** (swipe left, or the trash icon on the edit screen with a confirmation) removes the row and cancels the alarm.
- Swipes show a snackbar with **Undo**, which upserts the original row and re-schedules it.
- TalkBack users get the same actions as custom accessibility actions ("Mark done", "Delete"), since they can't swipe.

### 7.6 Home screen
`HomeScreen` maps every meeting to its current occurrence and re-reads the clock every 30 s:
- **Upcoming:** not completed and still in the future. The first one is the gradient **Next up** card, which has a Join button when there's a link.
- **Today:** everything happening today.
- **Past:** completed, or one-off meetings whose time has passed.
- **Search** matches title, person and notes.
- The **settings** icon opens a sheet with the theme picker and the status of each reminder permission.

### 7.7 Re-scheduling
`RescheduleReceiver` handles `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIMEZONE_CHANGED` and `TIME_SET` by re-scheduling every active meeting and refreshing the widget.

### 7.8 Widget, tile, shortcuts
- **Widget** (`NextMeetingWidget`): next meeting title, day, time and countdown. Tapping it opens the app; the mic button starts "new meeting by voice". It refreshes when meetings change, when alarms fire and every 30 minutes.
- **Quick Settings tile** (`QuickAddTileService`): opens "new meeting by voice".
- **App shortcuts** (`res/xml/shortcuts.xml`): long-press the launcher icon for "Say a meeting" and "New meeting".

---

## 8. Natural-language parser cheat-sheet

`parser/NaturalLanguageParser.kt` is plain Kotlin, so it's covered by JVM unit tests. `parse()` takes optional `today` / `now` arguments so tests can pin the clock.

| Input fragment | Interpreted as |
|---|---|
| `with rahul`, `with rahul sharma` | person (stop-words such as "me", "the", weekdays and months are ignored) |
| `at 8`, `at 6:15`, `at 12.30`, `as 6:15` | time (minutes after `:` or `.`). With no am/pm: "morning"/"breakfast" keep the hour as AM; "evening", "night", "tonight", "dinner", "afternoon" make 1–11 PM; otherwise **hour 1–7 → PM** (e.g. "at 5" → 17:00) and 8–12 stay as written |
| `from 3 to 4pm`, `between 11 and 1pm`, `2-3pm` | the start time; it takes the end's am/pm unless that would put it after the end ("from 11 to 1pm" → 11:00). Needs "from"/"between" or an am/pm at the end, so "1 to 1" stays a title |
| `8pm`, `3:30 am` | time |
| `noon`, `midday`, `midnight` | 12:00, 12:00, 00:00 |
| `in 30 minutes`, `in an hour`, `in half an hour`, `in 2 hrs` | date and time relative to now |
| `today`, `tonight`, `tomorrow`, `day after tomorrow` | date |
| `5 nov`, `5th of november`, `november 5` | that date this year, or next year if it has passed |
| `31/12`, `5/11/2026`, `5/11/26` | day first; read month first only when that is the only valid date (`12/25`). Without a year, next year if it has passed |
| `monday`, `this monday` | next occurrence of that weekday (today counts) |
| `next monday` | strictly after today |
| `next week` | today + 7 days |
| `on 15`, `on the 15th` | that day this month, or next month if it has passed |
| `daily`, `every day` · `every weekday`, `weekdays` · `weekly`, `every monday` | repeat: Daily · Weekdays · Weekly. A Weekdays meeting whose date lands on a weekend moves to the Monday (also enforced in `Recurrence.nextOccurrence`) |
| `https://…`, `meet.google.com/…`, `zoom.us/…`, `teams.microsoft.com/…` | meeting link (case kept, removed from the title) |
| time only, no date | today, or tomorrow if that time has already passed today |
| leftover words | title. If fewer than 3 characters are left, the title becomes `Meeting with <Person>` |

---

## 9. Data model

`meetings` table (`data/Meeting.kt`), **database version 2**:

| Column | Type | Notes |
|---|---|---|
| `id` | Long PK, auto | also the alarm request code and (offset by 1000) the notification ID |
| `title` | String | |
| `personName` | String | may be empty |
| `dateTimeMillis` | Long | epoch millis. For repeating meetings this is the **first** occurrence |
| `reminderMinutesBefore` | Int | default 30 |
| `notes` | String | default `""` |
| `category` | String | `General`, `Work`, `Personal` or `Call` |
| `isCompleted` | Boolean | for repeating meetings, "series ended" |
| `createdAt` | Long | |
| `repeat` | `Repeat` enum (TEXT) | `NONE`, `DAILY`, `WEEKDAYS`, `WEEKLY` — added in v2 |
| `meetingLink` | String | added in v2 |
| `lastRemindedOccurrence` | Long | occurrence whose reminder already rang — added in v2 |

**Changing the schema:**
1. Change the entity, bump `version` in `MeetingDatabase`, and add a migration. For added columns with defaults, an `AutoMigration(from, to)` is enough; renames and deletes need an `AutoMigrationSpec` or a manual `Migration`.
2. Build once. KSP writes the new `app/schemas/.../<version>.json`. **Commit it**: the migration tests and future auto-migrations need every old schema.
3. Add a case to `MigrationTest` and run the device tests (section 11).

---

## 10. Permissions (and why)

| Permission | Used for |
|---|---|
| `POST_NOTIFICATIONS` | showing reminders (runtime prompt on Android 13+) |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | exact reminder timing (Android 12+). Google Play allows `USE_EXACT_ALARM` only for alarm/calendar-type apps, so you'll need to justify it |
| `USE_FULL_SCREEN_INTENT` | alarm screen over the lock screen (Android 14+). Play grants it by default only to alarm and calling apps; users can turn it off, which the home screen detects |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | `AlarmService` playing the alarm sound |
| `WAKE_LOCK` | keeping the CPU awake while the sound plays |
| `VIBRATE` | alarm vibration |
| `RECEIVE_BOOT_COMPLETED` | `RescheduleReceiver` after reboot |

No `RECORD_AUDIO`: voice input hands off to the system speech recognizer (`RecognizerIntent`), which has its own microphone access, so MeetMate never asks for the microphone. No `INTERNET` either.

---

## 11. Testing & debugging

- **Unit tests** (`app/src/test/`, JUnit 4, no device needed): parser, recurrence maths, reminder scheduling and labels.
  ```bash
  ./gradlew testDebugUnitTest
  ```
- **Device tests** (`app/src/androidTest/`): DAO queries on an in-memory database, and the 1→2 migration on a separate `migration-test` database.
  ```bash
  ./gradlew connectedDebugAndroidTest
  ```
  Note: `connectedDebugAndroidTest` **uninstalls the app afterwards, which deletes its data**. On a phone with real meetings, install and run the test APK by hand instead:
  ```bash
  ./gradlew installDebug assembleDebugAndroidTest
  ```
  ```bash
  adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  ```
  ```bash
  adb shell am instrument -w com.meetmate.app.test/androidx.test.runner.AndroidJUnitRunner
  ```
- **Quick reminder test:** create a meeting a few minutes ahead with a reminder of 5 or more minutes. The trigger time is already past, so it rings about **5 seconds** later.
- **Inspect scheduled alarms:**
  ```bash
  adb shell dumpsys alarm | grep -A3 com.meetmate.app
  ```
- **Simulate a reboot:**
  ```bash
  adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.meetmate.app
  ```
- **Inspect the database:** Android Studio → *App Inspection → Database Inspector* → `meetmate_database`.
- **Notification looks wrong after you change the channel?** Android caches channel settings. Bump `CHANNEL_ID` in `NotificationHelper` and add the old ID to `OLD_CHANNEL_IDS`.
- **Alarm not firing?** Check `logcat -s MeetMateAlarm`, then the settings sheet in the app (all three rows should be green), then battery optimisation (OEM skins such as Xiaomi or Samsung are aggressive).

---

## 12. Design system

| Token | Light | Dark | Use |
|---|---|---|---|
| Iris (primary) | `#4338CA` | `#A5B4FC` | buttons, links, accents, logo |
| Coral (secondary / accent) | `#FF6B57` | `#FFB4A8` | "starts soon", recording mic, logo badge, warning cards |
| Background | `#F7F7FB` | `#0F0F17` | screens |
| Surface | `#FFFFFF` + 1 dp hairline border | `#1A1A26` | cards, fields |

- Colours are defined in `ui/theme/Color.kt` and `Theme.kt`. Success/danger/accent colours that Material has no slot for come from `MeetMateTheme.extraColors`.
- **Dynamic (wallpaper) colour is off**, so the brand palette always shows. The user picks System / Light / Dark in the settings sheet (`AppSettings`).
- Shapes: 12 dp (chips), 18 dp (cards, fields), 24 dp (hero card), 32 dp (dialogs).
- **Logo:** a speech bubble holding a clock, plus a coral reminder dot, on an iris gradient (`res/drawable/ic_launcher_*.xml`), with a `monochrome` layer for themed icons. `ic_notification.xml` is the matching status-bar glyph. Android 12+ uses the foreground for the splash screen.
- **Motion:** add/edit screens rise 1/12 of the screen and fade in over 300 ms with no overshoot (`sheetEnter`/`sheetExit` in `MainActivity`).
- **Accessibility:** meeting cards read as one sentence, swipe actions are exposed as custom actions, tabs use the Tab role, section labels are headings, and the time column grows with large text.

---

## 13. Conventions

- Kotlin official code style (`kotlin.code.style=official`).
- Compose for all app UI. XML layouts only where Android requires them (the widget).
- Put new dependencies in the version catalog. Don't hard-code versions in `app/build.gradle.kts`.
- Keep Android-free logic (parser, recurrence, labels) in plain Kotlin objects so it stays unit-testable.
- Go through `NotificationHelper.scheduleReminder()` whenever a meeting is created or changed. It cancels the alarm by itself when there's nothing left to remind. Use `cancelReminder()` on delete/complete.
- Commit with git. Never commit `local.properties`, `keystore.properties` or keystores (all in `.gitignore`).

---

## 14. New developer checklist

- [ ] JDK 17+, Android SDK 36 installed
- [ ] `local.properties` points at your SDK
- [ ] `./gradlew assembleDebug` succeeds
- [ ] `./gradlew testDebugUnitTest` passes
- [ ] App installed and launched; the settings sheet shows all three reminder rows green
- [ ] Created a meeting by voice and by text, and saw the alarm screen ring
- [ ] Read `MeetingViewModel`, `NotificationHelper`, `AlarmService`, `Recurrence.kt` and `NaturalLanguageParser`, which hold most of the logic
