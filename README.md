# DoseTap

A medication reminder app for Android. Tap a home-screen widget when you take your first dose of the day — the app schedules follow-up alarms automatically.

**Notification reliability is the entire product.** DoseTap is built around `AlarmManager.setExactAndAllowWhileIdle`, persisted alarm state, and boot-recovery — not fragile job schedulers.

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                    UI Layer                          │
│  Onboarding · Home · History · Settings · Alarm     │
│  Jetpack Compose + Material 3 + Navigation Compose  │
└─────────────────┬───────────────────────────────────┘
                  │ ViewModel + StateFlow
┌─────────────────▼───────────────────────────────────┐
│                 Domain Layer                         │
│  LogDoseUseCase · HandleDoseActionUseCase            │
│  ScheduleFollowUpsUseCase                            │
└──────────┬────────────────────┬───────────────────┘
           │                    │
┌──────────▼──────┐   ┌─────────▼──────────────────────┐
│   Data Layer    │   │       Alarm Layer               │
│  Room (3 tables)│   │  AlarmScheduler                 │
│  DataStore      │   │  AlarmReceiver                  │
│  Migrations     │   │  BootReceiver (4 broadcasts)    │
└─────────────────┘   │  DoseActionReceiver             │
                      └─────────────────────────────────┘
                                    │
                      ┌─────────────▼──────────┐
                      │    Widget Layer         │
                      │  Glance (Compose-based) │
                      │  TapAction + debounce   │
                      └────────────────────────┘
```

### Key Design Decisions

| Decision | Reasoning |
|----------|-----------|
| `AlarmManager.setExactAndAllowWhileIdle` | Fires in Doze mode. WorkManager is for deferrable work. |
| Persist alarms in Room | System drops AlarmManager state on reboot/force-stop/update. DB is source of truth. |
| `BootReceiver` listens for 4 broadcasts | `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIMEZONE_CHANGED`, `TIME_SET` |
| Alarms stored as absolute UTC millis | Correct across timezone changes for absolute scheduling |
| Manual DI via `AppContainer` | No Hilt overhead for MVP scope |
| No `INTERNET` permission | All data stays on-device. CI check enforces this. |

---

## Build Instructions

### Requirements

- Android Studio Ladybug (2024.2+) or newer
- JDK 17
- Android SDK API 35

### Build

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (requires signing config)
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumented tests (connected device required)
./gradlew connectedAndroidTest

# Lint
./gradlew lint

# Detekt
./gradlew detekt
```

### Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Project Structure

```
app/src/main/java/com/dosetap/
├── DoseTapApp.kt              # Application — creates AppContainer, notification channels
├── AppContainer.kt            # Manual DI, isPro / isProNow(), ambient + widget refresh
├── MainActivity.kt            # Single activity, hosts Compose nav
├── AlarmActivity.kt           # Full-screen alarm shown over lock screen
├── alarm/
│   ├── AlarmScheduler.kt      # Schedules/cancels/reschedules every alarm type
│   ├── AlarmReceiver.kt       # Follow-up + daily reminder fire → notification
│   ├── DoseReminderNotification.kt # Shared reminder notification (original + re-nudges)
│   ├── DoseActionReceiver.kt  # Taken/Snooze/Skip/Stop from notification buttons
│   ├── BootReceiver.kt        # Re-arms after reboot/update/TZ/time change/exact-alarm grant
│   ├── EscalationReceiver.kt  # Pro re-nudges at +10/+25 min
│   ├── WearOffWarningReceiver.kt
│   ├── WeeklySummaryReceiver.kt
│   ├── AmbientLogDoseReceiver.kt # "Log dose" on the lock-screen status notification
│   └── FocusAlarmReceiver.kt
├── billing/                   # Play Billing, gift codes, sponsor-a-friend codes
├── data/
│   ├── db/                    # Room: AppDatabase, 3 DAOs, Converters
│   └── prefs/                 # DataStore: SettingsRepository
├── domain/
│   ├── DoseTiming.kt          # Shift-aware "current dose" + double-dose window rules
│   ├── model/                 # Medication, DoseEvent, ScheduledAlarm, FeatureFlag, presets
│   └── usecase/               # LogDose, HandleDoseAction, ScheduleFollowUps
├── notification/              # Persistent lock-screen dose status ("Morning Anchor")
├── ui/
│   ├── theme/                 # "Calm Focus" color scheme, typography, shapes
│   ├── navigation/            # NavHost + Screen destinations
│   ├── onboarding/            # Onboarding flow (incl. permissions)
│   ├── home/                  # Home screen + ViewModel
│   ├── history/               # 30-day dose history + ViewModel
│   ├── stats/                 # Stats + heatmap (Pro)
│   ├── report/                # Doctor report (Pro)
│   ├── focus/                 # Focus timer (Pro)
│   ├── paywall/               # Paywall bottom sheet + code redeem
│   └── settings/              # Settings + ViewModel
├── widget/
│   ├── DoseWidget.kt          # Glance widget
│   ├── DoseWidgetReceiver.kt  # AppWidgetProvider
│   ├── TapAction.kt           # Widget click handler + debounce + double-dose guard
│   └── WidgetConfigActivity.kt
└── util/
    ├── BackupManager.kt       # JSON export/import
    ├── BatteryOptimizationHelper.kt
    ├── ExactAlarmHelper.kt
    ├── Constants.kt
    ├── OemDetector.kt
    └── TimeFormatter.kt
```

### Reminder semantics

- **Taken / Skip** answer *that* reminder only — later follow-ups in the chain still fire.
  **Stop today** ends the current dose's chain (scoped to the dose, not the calendar day).
- A dose is **current** while its cycle runs (longest of effect duration, last follow-up, or 12h),
  so night shifts don't flip to "not taken" at midnight. See `domain/DoseTiming.kt`.
- The widget and lock-screen "Log dose" refuse to log inside the last dose's effect window
  (3h if no duration is set); a deliberate second dose goes through the app's confirm dialog.

---

## Data Model

```
Medication          DoseEvent              ScheduledAlarm
──────────          ─────────              ──────────────
id (PK)             id (PK)                id (PK)
name                medicationId (idx)     tapId (idx)
followUps: List<Int> scheduledAt           medicationId
soundUri?           firedAt?               followUpIndex
createdAt           actionedAt (idx)       triggerAtMillis (idx)
                    action: DoseAction     isCancelled
                    sourceTapId (idx)
```

---

## Privacy

- **No network calls.** No INTERNET permission. CI enforces this.
- **All data local.** Room DB + DataStore, device-only.
- **No analytics, no crash SDKs, no Firebase.**
- Room DB excluded from Android backup (see `backup_rules.xml`).
- In-app: export data as JSON, delete all data.

---

## See Also

- [TESTING.md](TESTING.md) — Manual device test matrix
- [PLAN.md](PLAN.md) — Product plan and hard ethics rules
