# DoseTap — Revised Plan

> Reviewed and restructured with Opus. Cuts marked ~~strikethrough~~. New items marked *.

---

## CRITICAL: Fix Freemium Gating First

The current Pro gates are wrong in several places. These need fixing before launch or the app will get 1-star reviews and uninstalls.

**Move to FREE (currently wrongly gated as Pro):**

- `canUsePillCount` → **FREE.** Forgetting to refill stimulants is the #1 failure mode for ADHD. Locking this behind a paywall is hostile to the user. Free gets pill count + refill warning. Pro gets prescription-date-based refill prediction.
- `canUseOffDays` → **FREE.** Mon–Fri only prescriptions are the default for many ADHD patients. A free user getting false missed-dose warnings every Saturday will uninstall. Free gets recurring off-days. Pro can have the multi-day "drug holiday" variant.
- `canUseExportImport` → **FREE.** GDPR data portability is a user right in the EU. Locking export behind a paywall will trigger policy complaints. Export and import stay free.
- `canUseLateCutoff` → **FREE.** This is a safety feature ("don't take stimulants past 4 PM or you won't sleep"). The codebase already says "double-dose protection — never lock this." Apply the same logic here.

**Stays Pro (correctly gated):**
- Multiple medications
- Custom alarm tone
- Full stats (calendar heatmap + trends)
- Doctor visit PDF report ← *replaces weekly summary as the Pro stats feature*
- Smart escalation reminders (5 / 15 min ladder)
- Daily proactive reminder ("you haven't logged yet")
- Effect window tracker (active-until chip)

**Cut from Pro list:**
- Weekly summary notification — low engagement, ADHD users dismiss it. Replace with Doctor Report.

---

## Week 1 — Do Before Anything Else

### 1. Fix freemium gates (above) — 1h
Update `FeatureFlag.kt`. Every gate listed above flips to `= true` for free users.

### 2. Persistent dose-state notification — 1–2 days *(NEW, highest leverage)*
A sticky `Notification.Builder` with `setOngoing(true)` and an inline "Log dose" action button.

- Shows on lock screen and notification shade at all times
- Content: "✅ Ritalin — taken at 9:02" or "💊 Ritalin — not logged yet"
- Action button: "Log dose" (fires `TapAction` directly — no app open required)
- Updates automatically when dose is logged, undone, or day rolls over
- **Free tier.** This is the core ambient UI. The widget is secondary.
- Use a separate low-priority notification channel (silent, no vibration) so it doesn't ping on update
- Android 14 note: `setOngoing(true)` notifications can now be swiped away by users — handle re-creation in `onNotificationRemoved` or re-post on next alarm

### 3. Time-since-last-dose + next-dose countdown — 2h *(NEW)*
The hero section currently shows "Last dose: 9:02" but not "2h 14m ago."
For IR meds, the gap is what drives the "can I take another?" decision.

- Below the status chip: show elapsed time ("3h 20m ago") in large text, wall-clock time in small
- If effect window is configured: show "Next dose available at 12:00 — in 40 min" instead of just "Active until 12:00"
- This is the #1 piece of information IR users look at

### 4. Multi-medication home switcher — 1 day
Currently `HomeViewModel` uses `medicationDao.observeFirst()` everywhere. 
The whole state (effect window, off-day, pill count, warnings) is single-medication.

Proper fix:
- Store `selectedMedicationId: Long` in DataStore
- `HomeViewModel` observes `selectedMedicationId` → loads that medication's full state
- Home screen top bar: medication name is tappable → shows a bottom sheet list of all medications to switch between
- Each medication shows its own status chip, effect window, pill count
- Keep `observeFirst()` as fallback if nothing is selected yet

### 5. Remaining bugs — 1h
- Follow-up count Pro gate: hide "+ Add follow-up" after 2 for free users
- `SettingsViewModel.saveMedication`: check `FeatureFlag.maxMedications(isPro)` before insert
- `AlarmScheduler`: check `FeatureFlag` before scheduling off-days / daily reminder logic on downgrade

---

## Week 2 — Core Experience

### 6. Onboarding + medication preset library — 1 day
Currently the app drops a new user into an empty home screen with no explanation.
Onboarding is 3 steps, one action each:

**Step 1 — What's your medication?**
Preset picker with common ADHD meds and pre-filled effect duration:
- Ritalin IR / Generic methylphenidate IR → 240 min (4h)
- Ritalin LA / Medikinet CR → 480 min (8h)
- Concerta / Equasym XL → 720 min (12h)
- Adderall IR → 300 min (5h)
- Adderall XR → 480 min (8h)
- Vyvanse / Elvanse → 720–840 min (12–14h)
- Strattera → no effect window (non-stimulant, mark as such)
- "Other / manual" → free text + manual duration

**Step 2 — What time do you usually take it?**
Single time picker → sets the first alarm immediately.

**Step 3 — "Here's how DoseTap works"**
One screen: tap the big button every time you take your meds. That's it. Shows the button, shows the undo banner, done.

No instructions wall. No permissions screen (request notification permission after step 3 is dismissed).

### 7. Backup and restore — 1 day
JSON export/import is already built but gated to Pro (see fix above — move to free).

Additional work:
- Enable Android Auto Backup for Room via `backup_rules.xml` (include `dosetap.db`, exclude nothing)
- Test full reinstall cycle: install → use → uninstall → reinstall → import JSON → verify history intact
- Show "Import backup" prominently in onboarding if a backup file is detected on the device

### 8. Calendar heatmap chart (Pro) — 1 day *(replaces current bar chart as the Pro stats feature)*
Free tier: current streak + adherence %. That's all they need.
Pro tier: a GitHub-style 30-day or 90-day grid.

- Each day is a small square: green (taken), amber (taken late), red (missed), grey (no data / off-day)
- Tap a day → shows the exact dose events for that day
- This is the picture users show their psychiatrist
- Much more informative than the 14-bar chart currently gated as Pro

### 9. App icon + Play Console + Privacy policy — 1–2 days
Blocking for launch regardless of features.

- Design a real icon. Suggestion: a stylised pill with a small clock or checkmark. Not a generic capsule.
- Play Console: create `dosetap_pro` one-time in-app purchase at €2.99, publish to internal testing, add test account emails
- Privacy policy: short, honest, host on GitHub Pages if needed. Update URL in `strings.xml`.
- Screenshots: minimum 2 phone screenshots for Play listing
- `SCHEDULE_EXACT_ALARM` vs `USE_EXACT_ALARM`: declare `USE_EXACT_ALARM` in manifest (medical use case qualifies under Google policy — requires no runtime prompt and cannot be revoked by user)

---

## Week 3 — Pro Features Worth Building

### 10. Doctor visit report — PDF / share (Pro)
One-tap summary for psychiatrist appointments. ADHD users cannot recall how last month went.

Contents:
- Medication name + dose (if stored)
- Date range (last 30 / 60 / 90 days — picker)
- Adherence % overall + by day-of-week
- Average time of first dose
- Missed doses count + which days
- Trend: improving / stable / worsening

Implementation: generate HTML → use `PrintManager` to "print to PDF" or share sheet. Avoid `android.graphics.pdf.PdfDocument` directly — produces ugly output.

### 11. Comedown / wear-off warning — 2h *(NEW)*
An hour before the effect window ends, post a soft notification:
"Your Ritalin will wear off around 5:00 PM — plan accordingly 🌙"

This is uniquely valuable for stimulants. No other reminder app does this.
- Scheduled when dose is logged, based on `durationMinutes`
- Separate low-priority notification channel, no vibration
- Can be disabled in settings
- **Free tier** (safety-adjacent)

### 12. "Skip tomorrow" one-tap — 2h *(NEW)*
Off-days handle recurring skip patterns. But the real ADHD use case is "I'm not working Friday, skip it."

- Long-press the dose button (or overflow menu) → "Skip tomorrow"
- Stores a one-off skip date in DataStore alongside `offDays`
- `AlarmScheduler` checks this date before scheduling the next day's alarm
- Shows a small badge on tomorrow's status: "📅 Skipped"

### 13. Smart escalation reminders (Pro)
If the dose time alarm fires and no dose is logged within 10 minutes:
- +10 min: "Still haven't logged yet 👀"
- +25 min: "Last reminder — did you take your meds?"
- Stop. No more. Two follow-ups maximum or the user mutes the app.
- Don't count as "missed" until the end-of-day boundary

### 14. Dose notes with preset tags — 3h
After logging, a small dismissible card appears beneath the undo banner:
"Add a note?" → tag chips: `with food` · `late` · `forgot earlier` · `half dose` · `brand change` · custom text

Notes appear in history timeline. Useful for correlating side effects with timing.
Free tier.

### 15. Controlled-substance refill reminder — 3h *(expanded from plan)*
Beyond pill count, add prescription-aware tracking (Pro):
- Store prescription date + days supply + "days needed to get new script" buffer
- Auto-calculate "contact psychiatrist by" date (= runout date minus buffer)
- Reminder fires on that date: "Time to book your prescription appointment — you have ~10 days left"
- Respects the reality that controlled stimulants can't be auto-refilled

---

## Technical Debt to Fix

### Undo timer timing bug
`executeLogDose` uses `delay(WIDGET_UNDO_WINDOW_MILLIS)` inside `viewModelScope`. If `WhileSubscribed(5000)` tears down the flow while the user is backgrounded, the delay coroutine cancels and undo state may not clear. Fix: derive undo visibility from `System.currentTimeMillis() < lastTapTimeMillis + UNDO_WINDOW` rather than transient ViewModel state.

### `USE_EXACT_ALARM` vs `SCHEDULE_EXACT_ALARM`
Replace `SCHEDULE_EXACT_ALARM` with `USE_EXACT_ALARM` in the manifest. The former requires user permission that can be revoked from Settings; the latter is granted by the system for apps in the medical/alarm category and cannot be revoked. Verify with `alarmManager.canScheduleExactAlarms()` and handle the rare false case gracefully.

### Alarm missing banner race condition
`alarmMissingFlow` fires when `activeCount == 0` after alarms were active. There's a window during reschedule where count transiently hits 0 and the banner flickers. Add a 500ms debounce on the flow or only check after a settled state.

### Gift codes in APK
SHA-256 hashes ship in `res/raw/gift_codes.txt` — anyone with `apktool` can extract them. The hashes are one-way so the codes themselves aren't exposed, but an attacker can try common patterns. Mitigated by the device-local DataStore check (a code that's been redeemed on one device can't be redeemed again on that device). Acceptable for now; document the trade-off.

### ProGuard keep rules
Add to `proguard-rules.pro`:
```
-keep class com.android.billingclient.** { *; }
-keepclassmembers class com.dosetap.billing.** { *; }
```
Verify `gift_codes.txt` is not stripped as dead resource (mark it used in `GiftCodeManager`).

### Phantom tap protection *(NEW)*
The 160dp dose button can be pocket-tapped. Options:
- Require 200ms press-and-hold before registering (use `pointerInput` with `detectTapGestures` and a delay)
- Or: double-tap within 1.5 seconds to confirm (first tap shows "Tap again to confirm", second tap logs)
The undo window helps but only if the user notices. Add one of these before launch.

### `AlarmScheduler.rescheduleAll` dispatch
Verify it's running on `Dispatchers.IO`. It's called from `viewModelScope` which defaults to `Dispatchers.Main`. If there are many alarms, this blocks the UI thread.

---

## Cut Entirely

These were in the original plan. Removing them:

- ~~Wear OS companion~~ — tiny market, multi-week build, no retention impact
- ~~SMS caregiver mode~~ — `SEND_SMS` is a restricted permission, Play will reject it. Replace with a "Share status" intent using the OS share sheet if requested.
- ~~Referral gift codes / family pack~~ — needs a backend
- ~~Habit stacking custom notification copy~~ — users skim notifications, low ROI
- ~~"Lower weekend dose" / every-other-day patterns~~ — edge case, schema change; off-days already covers Mon–Fri
- ~~Weekly summary notification~~ — ADHD users dismiss it; replaced by Doctor Report
- ~~WorkManager fallback for weekly summary~~ — don't add infrastructure for a cut feature
- ~~Symptom 1–5 emoji rating with note field~~ — used twice then abandoned; build only if Pro users ask. Keep dose notes (section 14) instead, which is simpler and more durable.

---

## Copy / Tone Notes

- Replace "Drug holiday" with "Days off"
- Replace "🚀" as effectiveness rating — that's stimulant drug slang
- Keep clinical copy where it matters (refill, dose, warnings)
- "💊" emoji is fine for the button — universally understood
- Avoid gamification language around taking controlled medication

---

## Final Priority Order

| # | Task | Effort | Tier |
|---|------|--------|------|
| 1 | Fix freemium gates (pill count, off-days, export, late cutoff → free) | 1h | - |
| 2 | Persistent dose-state notification with Log action | 1–2d | Free |
| 3 | Time-since-dose + next-dose-available countdown | 2h | Free |
| 4 | Multi-medication home switcher (selectedMedicationId in DataStore) | 1d | Pro |
| 5 | Remaining bugs (follow-up gate, medication cap, scheduler gating) | 1h | - |
| 6 | Onboarding + medication preset library | 1d | Free |
| 7 | Backup/restore tested end-to-end + Auto Backup for Room | 1d | Free |
| 8 | Calendar heatmap (replaces 14-bar chart as Pro stats) | 1d | Pro |
| 9 | App icon + Play Console + privacy policy + screenshots | 1–2d | - |
| 10 | Comedown wear-off warning | 2h | Free |
| 11 | "Skip tomorrow" one-tap | 2h | Free |
| 12 | Doctor visit PDF report | 1d | Pro |
| 13 | Smart escalation reminders | 1d | Pro |
| 14 | Dose notes with preset tags | 3h | Free |
| 15 | Controlled-substance refill reminder | 3h | Pro |
| 16 | Phantom tap protection | 2h | Free |
| 17 | Fix undo timer + alarm race condition + USE_EXACT_ALARM | 3h | - |
