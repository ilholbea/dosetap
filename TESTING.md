# DoseTap — Manual Testing Guide

## Device Matrix

Test on at least these devices before each release:

| Device           | Android | OEM Layer       | Priority |
|------------------|---------|-----------------|----------|
| Pixel 6+         | 13–15   | Stock Android   | Required |
| Samsung S series | 13–14   | One UI          | Required |
| Xiaomi (MIUI 13+)| 12–13   | MIUI            | Required |
| OnePlus          | 12–13   | OxygenOS        | Nice to have |

---

## Core Reliability Tests

These must pass on every device before shipping.

### Test 1: Full cycle (most important)
1. Install app, complete onboarding, add medication with +4h and +8h follow-ups.
2. Tap widget to log a dose.
3. Lock the phone. Do not touch it.
4. After 4 hours: verify alarm fires (sound + full-screen activity appears).
5. Tap "Taken". Verify notification dismisses.
6. After 8 hours: verify second alarm fires.

**ADB helper:**
```bash
# Inspect scheduled alarms
adb shell dumpsys alarm | grep dosetap

# Simulate alarm firing immediately (replace <requestCode> from dumpsys output)
adb shell am broadcast -a com.dosetap.ALARM_TEST
```

### Test 2: Reboot mid-cycle
1. Log a dose.
2. Wait 30 seconds (alarms should be scheduled).
3. Reboot the device.
4. After the expected alarm time: verify alarm still fires.

### Test 3: App update mid-cycle
1. Log a dose.
2. Install a new build via `adb install -r app-debug.apk`.
3. Verify alarms still fire after the update.

### Test 4: Force-stop recovery
1. Log a dose.
2. Go to Settings → Apps → DoseTap → Force Stop.
3. Reopen the app.
4. Verify the banner "Alarms may have been stopped" appears.
5. Tap "Reschedule" and verify alarms are re-queued.

### Test 5: Notification permission denied
1. Deny notification permission in onboarding (or revoke via Settings).
2. Verify home screen shows the notification denied banner.
3. Re-grant permission. Verify banner disappears on next app open.

### Test 6: Widget debounce
1. Tap the widget twice quickly (within 1.5 seconds).
2. Verify only ONE dose is logged, not two.

### Test 7: Widget undo
1. Tap the widget.
2. Tap the widget again within 10 seconds.
3. Verify the dose is undone (no alarms scheduled, event removed).

### Test 8: Timezone change
1. Log a dose.
2. Change device timezone in Settings.
3. Open DoseTap. Verify timezone-change banner appears.
4. Verify alarms still exist in `adb shell dumpsys alarm | grep dosetap`.

---

## OEM-Specific Tests

### Samsung (One UI)
- Go to Settings → Battery → Background usage limits → Never sleeping apps.
- Add DoseTap. Verify alarms fire after extended screen-off.

### Xiaomi (MIUI)
- Go to Settings → Apps → DoseTap → Battery saver → No restrictions.
- Go to Security → Permissions → Autostart → Enable DoseTap.
- Verify alarms fire after extended screen-off.

---

## Functional Tests

### Onboarding
- [ ] Completes in < 60 seconds for a new user
- [ ] Medication saved correctly (verify in Settings)
- [ ] Widget pin prompt appears (or fallback instructions shown)
- [ ] Disclaimer visible on welcome screen

### History Screen
- [ ] Shows last 30 days of events
- [ ] Filter chips work (Taken / Snoozed / Skipped / Missed)
- [ ] Day grouping correct

### Settings
- [ ] Edit medication saves and reschedules pending alarms
- [ ] Test Reminder fires in ~1 minute
- [ ] Export Data produces valid JSON
- [ ] Delete All Data clears everything and returns to onboarding

### Alarm Activity
- [ ] Shows over lock screen
- [ ] "Taken" dismisses notification and closes activity
- [ ] "Snooze 15 min" schedules a new alarm 15 minutes out
- [ ] "Skip" dismisses without scheduling more alarms

---

## Accessibility Checks

- Font scaling: Set font size to 200% — verify no text is clipped.
- TalkBack: Navigate all screens via TalkBack — verify all interactive elements are reachable and
  have meaningful content descriptions.
- Contrast: Verify text contrast passes WCAG AA (4.5:1) in both light and dark themes.
  Use Android Accessibility Scanner.

---

## Build Verification

```bash
# Verify no INTERNET permission in release manifest
./gradlew assembleRelease
aapt dump permissions app/build/outputs/apk/release/app-release-unsigned.apk | grep INTERNET
# Must produce no output.

# Run unit tests
./gradlew test

# Run lint
./gradlew lint

# Check APK size (must be < 10 MB)
ls -lh app/build/outputs/apk/release/app-release-unsigned.apk
```
