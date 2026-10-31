# MeetMate — Developer Guide

MeetMate is a small Android app for scheduling meetings and getting **alarm-style reminders** before they start. You can type or speak a sentence like *"Lunch with Rahul tomorrow at 1pm"*. The app parses it into a title, person, date and time, saves it locally, and schedules an exact alarm that rings and shows a full-screen notification.

This guide covers setting up, building and running the app, how the code is organized, and how data moves through it.

---

## 1. Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 1.9.24 (JVM target 17) |
| UI | Jetpack Compose (BOM 2024.06.00), Material 3, Compose Navigation |
| State | `AndroidViewModel` + Kotlin `Flow` / `StateFlow` |
| Persistence | Room 2.6.1 (annotation processing through KSP) |
| Reminders | `AlarmManager` exact alarms + `BroadcastReceiver`s + `MediaPlayer` |
| Voice input | System speech recognizer (`RecognizerIntent`) |
| Permissions | Accompanist Permissions |
| Build | Gradle 8.14.4 (wrapper), AGP 8.7.3, version catalog in `gradle/libs.versions.toml` |
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
   - Android SDK Build-Tools (recent)
   - Platform-tools (`adb`)
3. **Android Studio** (recommended), or just the command-line tools.
4. A device or emulator running **Android 8.0 (API 26)+**. To test voice input, use a device or an emulator image that includes Google Play services, since the speech recognizer comes from there.

> AGP 8.7.3 doesn't officially support compileSdk 36. `gradle.properties` sets `android.suppressUnsupportedCompileSdk=36` to hide that warning, so leave the setting in place.

---

## 3. First-time setup

```bash
git clone <repo-url> MeetMate
```

```bash
cd MeetMate
```

Create `local.properties` in the project root and point it at your Android SDK. Android Studio writes this file for you when you open the project. **Don't commit it**, because the path is machine-specific.

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
| Build release APK (unsigned) | `./gradlew assembleRelease` |
| Clean | `./gradlew clean` |
| Lint | `./gradlew lint` |
| List all tasks | `./gradlew tasks` |

Output: `app/build/outputs/apk/debug/app-debug.apk`

Launch the app after installing:

```bash
adb shell am start -n com.meetmate.app/.MainActivity
```

Follow the reminder logs (every log line uses the tag `MeetMateAlarm`):

```bash
adb logcat -s MeetMateAlarm
```

### Release builds

- There is **no signing config** yet, so `assembleRelease` produces an unsigned APK. Add a `signingConfigs` block (keystore kept outside VCS) before distributing.
- `isMinifyEnabled = false`. If you turn on R8, the Room keep rules in `app/proguard-rules.pro` are already there.

---

## 5. Project layout

```
MeetMate/
├── build.gradle.kts            # root: declares plugins (apply false)
├── settings.gradle.kts         # repositories, includes :app
├── gradle.properties           # JVM args, AndroidX, compileSdk-36 suppression
├── gradle/libs.versions.toml   # ← all dependency & plugin versions live here
└── app/
    ├── build.gradle.kts        # android {} config + dependencies
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml # permissions, activity, 3 receivers
        ├── res/                # strings, colors, themes, launcher + notification icons
        └── java/com/meetmate/app/
            ├── MeetMateApp.kt              # Application: creates notification channel
            ├── MainActivity.kt             # Compose host, NavHost, permission prompts
            ├── data/
            │   ├── Meeting.kt              # Room @Entity  (table "meetings")
            │   ├── MeetingDao.kt           # queries (Flow + suspend)
            │   └── MeetingDatabase.kt      # Room DB singleton "meetmate_database"
            ├── viewmodel/
            │   └── MeetingViewModel.kt     # CRUD, undo (restore), schedules/cancels alarms
            ├── util/
            │   └── TimeLabels.kt           # countdowns, day labels, greetings, initials (plain Kotlin)
            ├── parser/
            │   └── NaturalLanguageParser.kt# free text → person/date/time/title
            ├── notification/
            │   ├── NotificationHelper.kt   # channel, schedule/cancel exact alarms
            │   ├── AlarmReceiver.kt        # fires at reminder time → notification + sound
            │   ├── AlarmSoundManager.kt    # looping alarm sound, auto-stops after 60s
            │   ├── DismissReceiver.kt      # "Dismiss" action / swipe → stop sound
            │   └── BootReceiver.kt         # re-schedules alarms after reboot
            └── ui/
                ├── theme/                  # brand palette, type scale, shapes, extra colours
                ├── screens/
                │   ├── HomeScreen.kt       # greeting header, search, tabs, Next-up hero, day sections, swipe + undo
                │   └── AddMeetingScreen.kt # "just say it" composer, details, quick date & reminder chips
                └── components/             # MeetingCard, NextUpCard, Avatar, AnimatedFab, EmptyState, VoiceInputButton
```

To add or upgrade a dependency, edit `gradle/libs.versions.toml` first, then reference it in `app/build.gradle.kts` as `libs.<alias>`.

---

## 6. Architecture

It's a single-module MVVM app:

```
 Compose UI (screens/components)
        │  events (onSave, onDelete, onComplete)
        ▼
 MeetingViewModel  ──────────────►  NotificationHelper ──► AlarmManager
        │  suspend calls / Flow                               │ (at trigger time)
        ▼                                                     ▼
 MeetingDao (Room)  ◄──── BootReceiver (re-schedule)     AlarmReceiver
        │                                                     │
        ▼                                                     ├─► Notification (full-screen, ongoing)
 SQLite "meetmate_database"                                   └─► AlarmSoundManager (loop ≤60s)
                                                                    ▲
                                                     DismissReceiver┘ (stop + cancel notification)
```

- **Source of truth:** Room. `MeetingViewModel.allMeetings` (every meeting, including completed ones) is the DAO's `Flow` turned into a `StateFlow` with `stateIn(WhileSubscribed(5000))`. The UI collects it with `collectAsStateWithLifecycle()`, so any DB write refreshes the list automatically.
- **Navigation** (`MainActivity.kt`): three routes.
  - `home` → `HomeScreen`
  - `add` → `AddMeetingScreen`
  - `edit/{meetingId}` (Long arg) → `AddMeetingScreen(editMeeting = …)`
- The ViewModel is created once in `MeetMateNavigation()` and shared by all routes.

---

## 7. Key flows

### 7.1 App start
1. `MeetMateApp.onCreate()` calls `NotificationHelper.createNotificationChannel()`. This deletes old channel IDs and creates `meetmate_alarms_v4` (high importance, vibration, **no channel sound**, bypasses DND).
2. `MainActivity` sets up Compose with `MeetMateTheme`.
3. On Android 13+ it asks for `POST_NOTIFICATIONS`.
4. If `canScheduleExactAlarms()` is false (Android 12+), it opens the system **"Alarms & reminders"** settings screen.

### 7.2 Creating a meeting
1. The user types into the **Just say it** box, taps an example chip, or taps the mic. The mic launches `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`; a voice result or an example chip is parsed straight away.
2. `parseAndApply()` calls `NaturalLanguageParser.parse(text)` and fills in **title, person, date, time**. Typed text is parsed by the → button or the keyboard's Done key. The user can then adjust the fields, use the Today/Tomorrow chips, and pick a reminder chip (5 / 15 / 30 / 60 min, default 30).
3. On save, a missing date or time opens the matching picker. Otherwise the screen calls `onSave(title, person, epochMillis, reminderMinutes)`.
4. `MeetingViewModel.addMeeting()` inserts into Room, takes the generated `id`, and calls `NotificationHelper.scheduleReminder()`.

### 7.3 Scheduling a reminder (`NotificationHelper.scheduleReminder`)
- Meetings that are already in the past get skipped.
- `triggerTime = meetingTime − reminderMinutes`. If that moment has already passed, the alarm fires **5 seconds from now** instead.
- Each alarm uses a `PendingIntent` (broadcast to `AlarmReceiver`) with **request code = meeting id**. Cancelling relies on the same request code.
- With exact-alarm permission, the alarm goes through `setAlarmClock(...)`. Without it, the fallback is `setAndAllowWhileIdle(RTC_WAKEUP, ...)`, which is inexact.

### 7.4 Reminder fires
1. `AlarmReceiver.onReceive` builds an **ongoing, max-priority, full-screen** notification with a "Dismiss" action. Tapping it opens `MainActivity`.
2. `AlarmSoundManager.play()` loops the default alarm sound (falling back to ringtone, then notification sound) on the `USAGE_ALARM` stream. It stops automatically after 60 seconds.
3. Pressing "Dismiss" or swiping the notification away (delete intent) triggers `DismissReceiver`. That stops the sound and cancels the notification. Its request code is `id + 10000`.

### 7.5 Edit / complete / delete
- **Edit** (tap a card) updates the row, cancels the old alarm and schedules a new one.
- **Complete** (swipe a card right) runs `markCompleted(id)` and cancels the alarm. The meeting moves to the **Past** tab.
- **Delete** (swipe left, or the trash icon on the edit screen) removes the row and cancels the alarm.
- Swiping shows a snackbar with **Undo**, which calls `restoreMeeting()`. That does a Room `@Upsert` of the original row and re-schedules its alarm.

### 7.5.1 Home screen tabs
`HomeScreen` filters `allMeetings` in memory and re-reads the clock every 30 s:
- **Upcoming:** not completed and still in the future. The first one is shown as the gradient **Next up** card.
- **Today:** everything dated today, done or not.
- **Past:** completed, or the start time has passed (newest first).
- **Search** (magnifier icon) matches title or person across all meetings.

### 7.6 Reboot
Alarms don't survive a reboot. `BootReceiver` listens for `BOOT_COMPLETED`, reads every upcoming meeting from Room, and schedules each one again.

---

## 8. Natural-language parser cheat-sheet

`parser/NaturalLanguageParser.kt` is plain Kotlin with no Android dependencies, so it's the easiest part of the code to unit test.

| Input fragment | Interpreted as |
|---|---|
| `with rahul`, `with rahul sharma` | person (stop-words such as "me", "the" and weekdays are ignored) |
| `at 8`, `at 6:15`, `as 6:15` | time. **No am/pm with hour 1–7 → PM** (e.g. "at 5" → 17:00) |
| `8pm`, `3:30 am` | time |
| `today`, `tomorrow` | date |
| `monday`…`sunday`, `mon`…`sun` | next occurrence of that weekday (today counts) |
| `on 15`, `on 15th` | that day this month, or next month if it has already passed |
| time only, no date | today, or tomorrow if that time has already passed today |
| leftover words | title. If fewer than 3 chars are left, the title becomes `Meeting with <Person>` |

| `day after tomorrow`, `tonight` | today + 2, today |
| `this friday`, `next friday` | stripped from the title. The date is still the next occurrence of that weekday |

`parse()` takes optional `today` / `now` arguments so tests can pin the clock.

---

## 9. Data model

`meetings` table (`data/Meeting.kt`):

| Column | Type | Notes |
|---|---|---|
| `id` | Long PK, auto | also used as the alarm/notification ID |
| `title` | String | |
| `personName` | String | may be empty |
| `dateTimeMillis` | Long | epoch millis, system time zone |
| `reminderMinutesBefore` | Int | default 30 |
| `notes` | String | default `""`, not set from the UI yet |
| `category` | String | default `"General"`, not set from the UI yet |
| `isCompleted` | Boolean | |
| `createdAt` | Long | |

**Changing the schema:** the DB is `version = 1` with `exportSchema = false` and **no migrations**. If you add or change a column, bump the version and add a `Migration` (or `fallbackToDestructiveMigration()` during development). If you skip this, existing installs will crash when the database opens.

The UI reads `getAllMeetings()` and filters in memory. `BootReceiver` uses `getUpcomingMeetings()`. `getPastMeetings`, `getTodayMeetings` and `search` are still unused.

---

## 10. Permissions (and why)

| Permission | Used for |
|---|---|
| `POST_NOTIFICATIONS` | showing reminders (runtime prompt on Android 13+) |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | exact reminder timing (Android 12+). Google Play needs a policy justification for `USE_EXACT_ALARM` |
| `VIBRATE` | vibration pattern on the channel |
| `RECEIVE_BOOT_COMPLETED` | `BootReceiver` re-scheduling |
| `RECORD_AUDIO` | voice input |

---

## 11. Testing & debugging tips

- **Unit tests** live in `app/src/test/java/` (JUnit 4) and cover `NaturalLanguageParser` and `TimeLabels`. Run them with:
  ```bash
  ./gradlew testDebugUnitTest
  ```
  There are no instrumented (`androidTest`) or UI tests yet.
- **Quick reminder test:** create a meeting a few minutes ahead with a reminder of 5 or more minutes. The trigger time is then already past, so the alarm fires about **5 seconds** later.
- **Inspect scheduled alarms:**
  ```bash
  adb shell dumpsys alarm | grep -A3 com.meetmate.app
  ```
- **Simulate a reboot to test `BootReceiver`:**
  ```bash
  adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.meetmate.app
  ```
- **Inspect the database:** Android Studio → *App Inspection → Database Inspector* → `meetmate_database`.
- **Notification looks wrong after you change the channel?** Android caches channel settings. Bump `CHANNEL_ID` in `NotificationHelper` (e.g. `_v5`) and add the old ID to `OLD_CHANNEL_IDS`, or uninstall the app.
- **Alarm not firing?** Check `logcat -s MeetMateAlarm`. Then confirm "Alarms & reminders" is allowed and battery optimization isn't killing the app (OEM skins such as Xiaomi or Samsung are aggressive about this).

---

## 12. Design system

| Token | Light | Dark | Use |
|---|---|---|---|
| Iris (primary) | `#4338CA` | `#A5B4FC` | buttons, links, accents, logo |
| Coral (secondary / accent) | `#FF6B57` | `#FFB4A8` | "starts soon", recording mic, logo badge |
| Background | `#F7F7FB` | `#0F0F17` | screens |
| Surface | `#FFFFFF` + 1 dp hairline border | `#1A1A26` | cards, fields |

- Colours are defined in `ui/theme/Color.kt` and `Theme.kt`. Success/danger/accent colours that Material has no slot for come from `MeetMateTheme.extraColors`.
- **Dynamic (wallpaper) colour is off by default**, so the brand palette always shows. Pass `dynamicColor = true` to `MeetMateTheme` to turn it back on.
- Shapes: 12 dp (chips), 18 dp (cards, fields), 24 dp (hero card), 32 dp (dialogs).
- **Logo:** a speech bubble holding a clock, plus a coral reminder dot, on an iris gradient. The vectors are in `res/drawable/ic_launcher_*.xml`. There's a `monochrome` layer for Android 13 themed icons, and `ic_notification.xml` is the matching status-bar glyph. Android 12+ uses the same foreground for the splash screen (`values-v31/themes.xml`).

## 13. Conventions

- Kotlin official code style (`kotlin.code.style=official`).
- Compose only, no XML layouts. Theme tokens live in `ui/theme`.
- Put new dependencies in the version catalog. Don't hard-code versions in `app/build.gradle.kts`.
- Keep Android-free logic (such as the parser) in plain Kotlin objects so it stays unit-testable.
- Every `scheduleReminder` needs a matching `cancelReminder` when a meeting changes or goes away, keyed by meeting `id`.

---

## 14. New developer checklist

- [ ] JDK 17+, Android SDK 36 installed
- [ ] `./gradlew testDebugUnitTest` passes
- [ ] `local.properties` points at your SDK
- [ ] `./gradlew assembleDebug` succeeds
- [ ] App installed and launched; notification + exact-alarm permissions granted
- [ ] Created a meeting by voice and by text, and saw the reminder fire
- [ ] Read `MeetingViewModel`, `NotificationHelper`, and `NaturalLanguageParser`, which hold most of the logic
