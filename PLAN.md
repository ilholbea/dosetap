# DoseTap — Product Plan v3

> Rewritten from three lenses simultaneously: ADHD psychiatrist (clinical value, harm avoidance), actual ADHD user (will they open it tomorrow?), indie dev (does it earn €2.99 and spread itself?). Where this plan disagrees with v2, the reasoning is stated inline. **Bold** = new or materially changed. ~~Strikethrough~~ = cut from prior plan.

---

## The Core Thesis (read before building anything)

**Tracking apps lose. Routine apps win.**

The #1 clinical reason ADHD patients miss stimulant doses is not memory — it's that the dose isn't yet welded to an existing morning anchor (coffee, alarm-off, phone-unlock, brushing teeth). A reminder app that pings and waits is a tracking app. A reminder app that *becomes the anchor* is an adherence app.

DoseTap's single job is: **be the first thing the user sees on their lock screen every morning, with the dose button one tap away, and confirm the action loudly and satisfyingly.** Everything else — stats, PDFs, heatmaps — is secondary. If the persistent lock-screen notification + widget pair isn't world-class, no Pro feature will save this.

This thesis reorders the plan. The persistent notification (formerly #2) becomes #1. Stats features get demoted. A new "Morning Anchor" concept is added.

---

## Hard Rules (clinical safety + ethics)

These are not features. They are constraints. Violating any of them ships harm.

1. **Never suggest the user take a dose they haven't been prescribed.** No "you missed your 9am, take it now" copy. Use "Log if taken" framing. The app does not advise — it records and reminds.
2. **Never gamify dose-taking.** No streak shame, no "you broke a 47-day streak!" red text, no leaderboards, ever. Streaks display as a quiet number, not a trophy. A missed dose because someone was sick or had a planned off-day must not feel like failure.
3. **Never frame missed doses as harm to the user.** A missed stimulant dose is an inconvenience, not a medical emergency. Copy must reflect that. Anxiety-inducing reminders are a harm vector for ADHD patients who already have RSD and perfectionism comorbidities.
4. **Never encourage self-adjustment of dose timing or amount.** No "you took it 2h late, try earlier tomorrow" nudges. That's a psychiatrist's job.
5. **The late-cutoff warning is the only feature that warns *against* an action.** Keep its copy gentle: "Heads up — taking this after 4pm often disrupts sleep." Not "DON'T TAKE IT."
6. **Pill count and refill reminders stay free.** Running out of a controlled stimulant is genuinely harmful (withdrawal, rebound, day lost). Paywalling refill warnings is unethical for this drug class.
7. **No cloud, no account, no analytics SDK.** The privacy story ("your data never leaves your phone") is both ethical and a marketing asset. Stick to it.

---

## CRITICAL: Fix Freemium Gates Before Anything Else

The current Pro gates are wrong. Ship them as-is and you get 1-star reviews citing "predatory paywall on a medical app."

**Move to FREE:**
- `canUsePillCount` — see Hard Rule 6.
- `canUseOffDays` — Mon–Fri only is the default ADHD pattern, not an edge case. A free user gets false missed-dose warnings every Saturday and uninstalls by week 2.
- `canUseExportImport` — GDPR Article 20 (data portability). Locking this risks Play policy strikes and is legally fragile in the EU.
- `canUseLateCutoff` — safety adjacent. The codebase already enforces "double-dose protection — never lock." Same logic applies.
- `canUseComedownWarning` — safety adjacent (planning for wear-off prevents users from driving / parenting while crashing).

**Stays Pro (genuinely justifies €2.99):**
- Multiple medications (>1)
- Calendar heatmap + trends
- Doctor visit PDF report
- Smart escalation reminders (the 10/25 min ladder)
- Custom alarm tone
- **Sponsor-a-friend (1 gift slot)** — see section 9
- Prescription-date-based refill prediction (pill count stays free; the "book your psych appointment by X" prediction is Pro)

**Cut from Pro list entirely:**
- Weekly summary notification — replaced by Doctor Report.
- Effect-window "active until" chip — this is a clinical safety/planning feature. **Move to free.** Locking the "when does my med wear off?" question behind a paywall is hostile.
- Daily proactive reminder ("you haven't logged yet") — this is the core reminder function. **Move to free.** If you paywall the *reminder* in a *reminder app*, you have no free product.

> Disagreement with v2: v2 kept "effect window tracker" and "daily proactive reminder" as Pro. That's wrong. Both are core to the free product's value. Pro should add *power*, not unlock *function*.

---

## The Killer Feature: Morning Anchor (NEW, replaces "persistent notification" as #1)

The persistent notification in v2 is correct but undersold. Rebuild it as the **Morning Anchor** — a deliberately designed first-touch surface that exists everywhere the user looks in the first 60 seconds of waking up:

1. **Persistent lock-screen notification** (free, default on)
   - `setOngoing(true)`, low-priority silent channel, single line + one big "Log dose" action.
   - Three states: pending (💊 grey), taken (✅ green with time + "2h 14m ago"), late (🟡 amber with elapsed time).
   - Re-post on Android 14 swipe-dismiss via `NotificationListenerService` is not allowed without sensitive permission — instead, re-post on next AlarmManager fire and on `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`.
   - Tapping the action button logs the dose with **no app launch, no unlock**. This is the single most important interaction in the app.

2. **Widget** (free, existing) — same three states, same one-tap action. Same visual language as the notification. They're the same UI, two surfaces.

3. **Always-on display tile** (Pro, stretch goal — defer to v1.1) — Quick Settings tile with dose state. Low effort once notification is solid.

**Why "Anchor":** the name matters internally. The team (you) should think of this as the user's morning anchor — the first deliberate action of the day. Every design decision routes through "does this make the anchor stronger or weaker?"

---

## Week 1 — Foundation (Must Ship)

### 1. Fix freemium gates — 1h
Per section above. Flip flags in `FeatureFlag.kt`. Also flip effect-window and daily-reminder to free.

### 2. Morning Anchor: persistent lock-screen notification — 2 days
Per spec above. This is the single highest-leverage task in the whole plan.

Acceptance criteria:
- Notification visible on lock screen at all times after first dose is configured.
- "Log dose" action works from lock screen on Android 11, 13, 14, 15 without unlock.
- Updates within 200ms of dose log/undo via `NotificationManager.notify()` with stable ID.
- Survives reboot (re-posted in `BootCompletedReceiver`).
- Battery profile: no wake locks, no foreground service. AlarmManager + notification update only.

### 3. Time-since-last-dose + next-available countdown — 3h
v2 was right. Below the status chip:
- If not taken: "💊 Not logged yet" + time of next scheduled dose.
- If taken: "Taken 2h 14m ago at 9:02" + (if effect window set) "Wearing off in 1h 46m" or "Next dose at 13:00 — in 40 min."

For IR users this is *the* answer to *the* question they open the app to ask.

### 4. Phantom tap protection — 2h (Hard requirement, not optional)
A 160dp button on a phone in a pocket *will* fire. Implement **press-and-hold 250ms** (not double-tap — double-tap is hostile to ADHD users who'll wonder if the first tap registered). Show a tiny radial progress fill during the hold so it's obvious feedback.

Pair with the existing 8-second undo window. Both are needed: hold prevents phantom, undo catches the "wait, I already took it" case.

### 5. Multi-medication home switcher — 1 day
Store `selectedMedicationId: Long` in DataStore. `HomeViewModel` observes it. Top bar med name → bottom sheet picker. Per-med state isolated.

Keep `observeFirst()` fallback for first-run.

### 6. Remaining gating bugs — 1h
- Follow-up cap enforcement
- `SettingsViewModel.saveMedication` checks `maxMedications(isPro)` before insert
- `AlarmScheduler` re-checks `FeatureFlag` on Pro downgrade (refund / chargeback)

---

## Week 2 — Onboarding, Trust, and Distribution Prep

### 7. Onboarding (3 screens, no walls) — 1 day

**Screen 1 — Pick your med.** Preset list with pre-filled durations:
- Ritalin IR / methylphenidate IR — 4h
- Ritalin LA / Medikinet CR / Equasym XL — 8h
- Concerta — 12h
- Adderall IR — 5h
- Adderall XR — 8h
- Vyvanse / Elvanse — 12h (mark 10–14h variable)
- Strattera / atomoxetine — no acute window (non-stimulant, hide effect-window UI for this med)
- Wellbutrin / bupropion — no acute window
- Guanfacine / Intuniv — no acute window
- "Other" — manual

**Screen 2 — When do you usually take it?** Single time picker. Sets first alarm.

**Screen 3 — Anchor it.** New screen, critical: *"What do you do every morning without fail?"* Options: ☕ Make coffee · 📱 Check phone · 🪥 Brush teeth · 🔔 Turn off alarm · Custom. Saved as a string shown in the notification: "💊 Take Ritalin — right after ☕ coffee."

This is the habit-stacking primitive. It's free, it's one extra screen, and it does more for adherence than any Pro feature.

> Disagreement with v2: v2 cut "habit stacking custom notification copy" as low-ROI. That's wrong if it's the default, not a power-user setting. The cue *is* the product. Make it the third onboarding screen.

Notification permission requested **after** screen 3, with context: "We'll remind you right after your coffee."

### 8. Backup, restore, and Auto Backup — 1 day
- Export/import JSON (already free per gate fix).
- Android Auto Backup via `backup_rules.xml` — include `dosetap.db`, exclude billing cache.
- Test: install → use 7 days → uninstall → reinstall → restore → history intact.
- Detect backup file in `Downloads/` on first run, surface "Restore from backup?" in onboarding.

### 9. Sponsor-a-Friend — 1 day (NEW, replaces gift code APK workflow)

**Problem with current gift codes:** SHA-256 hashes in APK = fixed pool at build time, can't gift after release without a new build, and the developer is rebuilding the APK every time he gifts to a new person. Doesn't scale, and the user side is a code-paste flow which is friction.

**Solution: in-app sponsor flow.**
- Pro users see a "Sponsor a friend" button in Settings (Pro tier benefit). One free Pro unlock to give away, per device, lifetime.
- Tapping it generates a code locally (deterministic from a device-bound secret + a slot number — no backend), surfaces it with a "Share" sheet → "I gave you DoseTap Pro: ABC-123-XYZ".
- Recipient pastes code in their Settings → unlocks Pro. Code is consumed in their DataStore; sponsor's slot is consumed in theirs.
- Validation: codes verify against a checksum + a baked-in HMAC key. No backend.

**For the developer's personal gifting (girlfriend, friends, beta testers):**
- Ship a separate "developer master code" derived from the HMAC key, valid for N redemptions per device install. Document it in a private note. Realistic ceiling: **plan for ~20 personal gifts before this becomes annoying.** Beyond that, get a backend or accept that some friends pay €2.99.
- Keep the existing `gift_codes.txt` SHA-256 system as a fallback for press / influencer gifts in batches.

**Why this matters as a Pro feature:** "buy Pro, give Pro to one person" is a known +30–60% conversion lift in medical/wellness apps (MyFitnessPal, Headspace family flows). It also creates organic word-of-mouth: the sponsored friend tells two other ADHD friends, who buy. This is the *only* zero-backend referral mechanic that works.

### 10. App icon, Play Console, privacy policy, screenshots — 1–2 days
- Icon: pill silhouette + clock hand or checkmark. Hire a designer on Fiverr for €30 — your time is worth more. Do not ship a generic capsule.
- Play Console: `dosetap_pro` SKU at €2.99, internal track, 5 test accounts.
- Privacy policy: short, "your data never leaves your phone" front and center. GitHub Pages.
- Screenshots: 5 phone screenshots (Play requires min 2, but 5 converts better). Required scenes: lock-screen notification, home with status chip, dose-just-logged confirmation, calendar heatmap (Pro teaser), doctor report (Pro teaser).
- **Feature graphic** (1024×500) — required for Play. Plan for it.
- Manifest: declare `USE_EXACT_ALARM` (medical/alarm category qualifies — no runtime prompt). Keep `SCHEDULE_EXACT_ALARM` as fallback on older targets.

### 11. ASO copy — 4h (NEW)
The Play listing is where 80% of conversion is won or lost. Write it deliberately:
- **Title:** "DoseTap — ADHD Med Reminder"
- **Short description (80 chars):** "One-tap med tracking for ADHD. No account, no ads, your data stays on your phone."
- **Long description:** lead with the persistent-notification screenshot, lead the copy with "made by an ADHD developer for ADHD users," list features under headers (Daily, Safety, Insights). Search keywords: "ADHD," "stimulant," "Ritalin reminder," "Concerta tracker," "medication reminder ADHD."

---

## Week 3 — Pro Features That Earn the Price

### 12. Calendar heatmap (Pro) — 1 day
Replace the 14-bar chart. GitHub-style grid, 30/60/90-day toggle. Squares: green (taken on time), amber (late), red (missed), grey (off-day / no data). Tap day → event details.

This is "the picture you show your psychiatrist." It must look that good.

### 13. Doctor Visit Report (Pro) — 1–2 days
**This is the single Pro feature that drives word-of-mouth from psychiatrists.** Build it carefully.

Contents:
- Patient name (optional, never required), date range, medication, dose strength (free text — never inferred).
- Adherence: % overall, % on weekdays, % on weekends.
- Time-of-first-dose distribution (chart): "usually 8:30am, range 7:50–10:15."
- Missed-dose count + dates (not reasons — reasons are private).
- Effect-window heatmap if logged.
- **What this report does NOT include:** mood ratings, symptom subjective scores, "DoseTap recommends X." Psychiatrists distrust apps that diagnose. The report is data, not interpretation.

Implementation: HTML → `PrintManager` → PDF. Share sheet from there.

**Distribution lever:** offer to email a PDF sample to ADHD psychiatrists you can find on LinkedIn / Twitter. "Built this for myself, would value 5 min of feedback." That's how you get the "psychiatrists recommend it" channel. Two warm intros are worth more than 1000 Play installs.

### 14. Smart escalation reminders (Pro) — 1 day
+10 min: "Still haven't logged 👀". +25 min: "Last reminder." Stop. Two follow-ups is the cap — past that you're a nag, not a tool.

Do not count as missed until end-of-day. Important: end-of-day must be **user-configurable** (default 3am next day for late-shift workers and night owls — extremely common in ADHD population). v2 didn't specify this; it matters.

### 15. Prescription-aware refill prediction (Pro) — 3h
Pill count is free. The Pro upgrade: store Rx date + days supply + buffer (default 7 days). Calculate "book psych by" date. Fires one notification on that date. Done.

### 16. Comedown / wear-off warning (Free, moved from Pro) — 2h
60 min before end of effect window: "Your Ritalin will wear off around 5:00 PM 🌙". Free tier. Safety-planning, not gamification.

User-configurable: off / 30min / 60min lead time.

### 17. "Skip tomorrow" one-tap (Free) — 2h
Long-press dose button → "Skip tomorrow." One-off skip stored in DataStore. Tomorrow's status: "📅 Skipped — tap to undo."

### 18. Dose notes with preset tags (Free) — 3h
Post-log card: "Add a note?" → chips: `with food` · `late` · `forgot earlier` · `half dose` · `brand change` · `crashed early` · `slept poorly`. Free text fallback.

Surface notes in Doctor Report (Pro) as a "patterns" section: "Notes most often tagged: 'crashed early' (8x), 'with food' (12x)." That's clinically useful.

---

## Cut Entirely (with reasoning)

- ~~Wear OS companion~~ — 2% of Android users, multi-week build.
- ~~SMS caregiver mode~~ — `SEND_SMS` will get the app rejected. Use OS share sheet "share status" if anyone asks.
- ~~Symptom 1–5 emoji rating~~ — abandoned after week 2 in every app that's tried it. Dose notes cover the use case.
- ~~Weekly summary notification~~ — dismissed by ADHD users. Doctor Report on demand replaces it.
- ~~"Lower weekend dose" patterns~~ — schema change, edge case. Off-days + skip-tomorrow handles 95%.
- ~~Referral gift codes requiring backend~~ — replaced by Sponsor-a-Friend (no backend).
- ~~Gamification of streaks~~ — Hard Rule 2.

---

## Technical Debt (must fix before launch)

### Undo timer race
`executeLogDose` uses `delay()` in `viewModelScope` with `WhileSubscribed(5000)` — undo state can desync if user backgrounds the app. Fix: derive undo from `System.currentTimeMillis() < lastTapTimeMillis + UNDO_WINDOW`, not transient state.

### `USE_EXACT_ALARM` migration
Replace `SCHEDULE_EXACT_ALARM`. Medical/alarm category qualifies. Verify with `alarmManager.canScheduleExactAlarms()`, fall back gracefully.

### Alarm-missing banner flicker
500ms debounce on `alarmMissingFlow`. Or check only after settled state.

### Gift codes in APK
Document the trade-off. Sponsor-a-Friend (section 9) replaces this for scale; SHA-256 list stays for press/batch gifts. Acceptable.

### ProGuard
```
-keep class com.android.billingclient.** { *; }
-keepclassmembers class com.dosetap.billing.** { *; }
-keepclassmembers class com.dosetap.giftcodes.** { *; }
```
Mark `gift_codes.txt` used so it isn't stripped.

### `AlarmScheduler.rescheduleAll` dispatcher
Force `Dispatchers.IO`. Currently inherits `Main` from `viewModelScope`. With many alarms, blocks UI.

### Notification channel hygiene
Three channels: `dose_alarms` (high), `dose_ambient` (low, silent — for the Anchor), `dose_warnings` (default, for comedown / late cutoff / refill). User-tunable individually.

---

## Distribution & Passive Income Strategy

### Realistic numbers
- Conversion: 3–5% is the medical-utility benchmark. At 1,000 installs and 4% = 40 conversions × €2.99 × 0.85 (Play cut) ≈ €100/mo at steady state.
- This is not life-changing money at 1k installs. **The plan must be: get to 10k+ installs.** That's where €1k+/mo passive income lives.

### The three zero-cost distribution channels (in priority order)

1. **r/ADHD and r/ADHDmemes posts** — a single well-written "I built this because I kept forgetting my meds" post can do 5k installs in 48h. Post when feature-complete, not before. Include the lock-screen notification screenshot first.
2. **Psychiatrist warm-intro** — email 10 ADHD psychiatrists with the Doctor Report PDF and a 1-month Pro code. 2 will respond. 1 might mention it to patients. Long tail, durable.
3. **ADHD TikTok / YouTube creators** — Hannah Choi, How To ADHD, etc. Send Pro codes + a 60-second demo video. Conversion rate of these creators' recommendations is brutal-high in this niche.

### Sponsor-a-Friend as growth loop
Built per section 9. Every Pro purchase = 1 free Pro to a friend = ~30% of those friends will tell another ADHD friend (who pays). This is the only growth mechanic in the plan that compounds.

### What NOT to do
- No paid ads. Margin doesn't support it.
- No subscriptions. One-time €2.99 is correct — matches user expectation for a utility and avoids the "another subscription?" rejection. Resist the urge to add a Pro+ tier in year 1.
- No social features, no sharing of adherence data, no leaderboards. Hard Rule 2.

---

## Final Priority Order

| # | Task | Effort | Tier | Why this slot |
|---|------|--------|------|---------------|
| 1 | Fix freemium gates (incl. effect window + daily reminder → free) | 1h | — | Blocks launch ethics |
| 2 | Morning Anchor: persistent lock-screen notification | 2d | Free | Single highest-leverage feature |
| 3 | Time-since-dose + next-available countdown | 3h | Free | Answers the #1 question users open the app for |
| 4 | Phantom tap protection (250ms hold) | 2h | Free | Ships harm if missing |
| 5 | Multi-medication home switcher | 1d | — | Existing bug |
| 6 | Remaining gating bugs | 1h | — | Existing bug |
| 7 | Onboarding 3 screens incl. habit anchor | 1d | Free | The anchor screen IS the product |
| 8 | Backup/restore + Auto Backup tested | 1d | Free | Trust + GDPR |
| 9 | Sponsor-a-Friend (in-app code gen) | 1d | Pro | Growth loop + dev gifting |
| 10 | App icon, Play Console, privacy policy, screenshots, feature graphic | 1–2d | — | Launch-blocking |
| 11 | ASO listing copy | 4h | — | 80% of install conversion |
| 12 | Calendar heatmap | 1d | Pro | The "show your psychiatrist" picture |
| 13 | Doctor Visit PDF Report | 1–2d | Pro | The psychiatrist-recommendation lever |
| 14 | Smart escalation reminders (configurable EoD) | 1d | Pro | Pro power feature |
| 15 | Prescription-aware refill prediction | 3h | Pro | Pro power feature |
| 16 | Comedown / wear-off warning | 2h | Free | Safety-adjacent |
| 17 | Skip-tomorrow long-press | 2h | Free | Real ADHD use case |
| 18 | Dose notes + preset tags | 3h | Free | Feeds Doctor Report |
| 19 | Tech debt: undo timer, USE_EXACT_ALARM, alarm flicker, dispatchers, channels | 4h | — | Stability before launch |

**Launch gate:** items 1–11 are v1.0. Items 12–19 are v1.1 (within 4 weeks of v1.0). Do not delay v1.0 launch to add v1.1 features — the Reddit post needs a stable app, not a complete one.

---

## Copy / Tone Final Rules

- "Days off" not "Drug holiday"
- No 🚀 emoji anywhere (stimulant slang)
- 💊 for the button is fine
- Streak number is small grey text, never red, never large
- Missed dose copy: "No dose logged" — never "You missed your dose"
- Late dose copy: "Logged late — that's fine" — never "You were late"
- Comedown copy: "Wearing off around X — plan accordingly 🌙" — never "Crash incoming"
- The word "fail," "broken," "lost" never appears in any user-facing string

---

## What Success Looks Like at 6 Months

- 5,000+ installs
- 4% Pro conversion (~200 conversions × €2.99 ≈ €500 lifetime, slow drip)
- One psychiatrist or ADHD creator has publicly recommended it
- The girlfriend and ~10 friends are using it daily on sponsored Pro
- 4.5+ Play rating, with multiple reviews mentioning "the lock-screen notification" or "I show this to my psychiatrist"
- Developer has shipped 2–3 small updates based on real feedback, nothing major

If you hit all six, this becomes a €100–300/mo passive-income asset that helps real people. That's the target. Anything more is upside.
