# DopaTap — ADHD Product/UX Review + Ship Plan (dual-model)

> **Naming note for implementers:** the user-facing app name is **DopaTap**
> (`res/values/strings.xml` → `app_name`); the codebase, package (`com.dosetap`), and
> class names all say **DoseTap**. This document uses "DoseTap" when referring to code and
> "DopaTap" for anything user- or store-facing. Do not rename code symbols.

Two independent reviews of the decompiled app (`com.dosetap`), evaluated purely as a tool
for ADHD users — not as a medical app. **Reviewer 1: Claude Opus. Reviewer 2: Claude Fable.**
Both read the same decompiled sources (`ui/onboarding`, `ui/home`, `ui/focus`, `ui/stats`,
`ui/history`, `notification/`, `alarm/`, `widget/`, `domain/`, `util/Constants`).

**Document map:** Part 1–2 = the two ADHD reviews · Part 3 = consolidated priority list
**+ per-item interaction specs (3.1)** · Part 4 = engineering/reliability findings ·
Part 5 = monetization spec · Part 6 = unified build roadmap · **Part 7 = Google Play
deployment & getting paid** · **Part 8 = paid user-acquisition plan** · Part 9 = bottom line.

**Joint verdict:** this is a genuinely ADHD-native product, not a generic pill reminder with
ADHD branding. The core loops (anchor habit-stacking, ambient object permanence, one-tap
logging, forgiveness affordances, time-blindness scaffolding) are correctly designed. It
needs **refinement, not a rebuild**. Both reviewers independently identified the same top
retention risk (streak semantics); they diverge on the #1 daily-usefulness fix — see the
consolidated list.

---

## Part 1 — Opus review

**Bottom line:** This is not a generic pill reminder wearing an ADHD costume. The feature
set, copy, and flows are built around real ADHD failure modes (prospective-memory gaps,
object permanence, time blindness, decision paralysis, RSD). The ADHD side is already
strong. What it needs is refinement, not a rebuild — and the single most important fix is
how it frames streaks.

### (a) What it already gets RIGHT

1. **Habit-stacking "anchor" as the core mental model** (`OnboardingScreenKt`
   `ANCHOR_OPTIONS`: coffee / phone-unlock / brushing teeth / alarm-off). It doesn't just
   ask "what time?" — it ties the dose to an existing automatic behavior, which is the
   correct way to build a habit around a brain that can't rely on prospective memory.
   Crucially, the anchor is then **resurfaced at the moment of action**: the ambient
   notification renders "No dose logged yet (right after coffee)" and "Taken at 08:14 -
   2h ago - right after coffee" (`AmbientNotificationManager.build`). Cue → action →
   confirmation loop closed.

2. **Object permanence done properly.** A persistent `setOngoing(true)` ambient
   notification (`AmbientNotificationManager`) plus a home-screen widget (`DoseWidget`,
   "tap to log" / "logged at ... tap again to undo"). Both keep the dose in-sight so it
   doesn't fall out of mind. The widget and notification both log in **one tap** and undo
   in one tap — "out of sight, out of mind" is directly targeted.

3. **Time-blindness scaffolding.** Full-screen alarm intent (`AlarmReceiver`,
   `setFullScreenIntent`), a live "Xh ago" elapsed ticker (`HomeViewModel.tickerFlow`),
   an effect / "Active until HH:MM" window (`computeEffectWindow`), and a **wear-off
   warning fired 1h before the med's duration ends** (`AlarmScheduler.scheduleWearOffWarning`).
   It continuously answers "how long ago / how long left," which time-blind users cannot
   self-generate.

4. **RSD-safe, non-shaming language throughout.** Safety nudges are framed as questions,
   not scolding: "Log anyway?", "Are you sure?" (`DoseWarningType` DOUBLE_DOSE /
   LATE_DOSE / OFF_DAY, built in `HomeViewModel.buildWarning`). Missed doses are recorded
   neutrally (`DoseAction.MISSED`, history filter chip "Missed") with zero guilt copy.
   No "you failed" messaging anywhere in the strings.

5. **Forgiveness is designed in.** A 10.5s Undo banner after every log (`executeLogDose`
   → `delay(10500)`), plus `pauseReminders(hours)`, `skipTomorrow`, off-days, and a
   per-notification "Stop today" action. Users can gracefully opt out of a day without
   "breaking" anything or fighting the app.

6. **Dopamine/reward loop.** `ConfettiBurst` (6-color), `CelebrationKind.FIRST_OF_DAY`,
   `FirstDoseOverlay`, and milestone celebrations at 7/30/90/180/365 days plus "Personal
   best" — and celebrations are toggleable (`celebrationsEnabled`) for users who find
   them aversive.

7. **A genuine executive-function toolbelt** (`ToolsRow` on Home): Focus timer with a
   **Transition mode** specifically for task-switching (`FocusMode` FOCUS 25m / TASK
   count-up / TRANSITION 10m / BREAK 5m), **Brain Dump** ("capture a fleeting thought
   before it vanishes"), **Task Roulette** (picks a task for you to beat decision
   paralysis), **Breathe** (box-breathing 4-4-4-4, `HomeScreenKt` line 838), and a
   **Quicklist** of check-off micro-tasks. These map directly to ADHD executive-function
   deficits, not to medication tracking.

8. **Reliability treated as a first-class ADHD concern.** OEM-specific battery-optimization
   guidance (Xiaomi/Samsung/Huawei/OnePlus/OPPO in `util/OemDetector`), an "alarms may
   have been stopped" banner, timezone-change banner, and a reliability check. A silently
   dead reminder is catastrophic for someone who has outsourced their memory to the app;
   taking this seriously is a real differentiator.

### (b) Gaps, ranked by impact

1. **The streak is all-or-nothing and RSD-hostile — highest impact.** `StatsViewModel`
   computes the current streak by counting consecutive days from today backward
   (`compute$dayHasDose` loop), with a strict break on any missed day. Consequences:
   (a) every morning **before** you log, the headline streak reads **0** — demotivating
   at exactly the moment the streak should pull you; (b) a single missed day hard-resets
   to 0 with no grace. For ADHD users this triggers the "I broke the chain, so it's
   ruined, why bother" abandonment spiral — the opposite of what the otherwise-forgiving
   app intends.
   *Fix:* streak freeze/grace tokens (miss one day without losing the chain), treat today
   as intact until day-end, and lead the Stats screen with the forgiving 7/30-day
   adherence percentage (`compute$adherence`, already implemented) rather than the
   brittle consecutive-day count.

2. **The primary Home logging action is a press-and-hold, not a tap.** The hero uses a
   hold gesture (`HomeScreenKt`: `holdProgress`, `isHolding`, "Hold…"). The app's promise
   is one-tap logging, and the widget + notification honor it — but the most prominent
   surface makes you hold, which adds friction and is undiscoverable. It is also
   inconsistent with every other log path.
   *Fix:* make the hero a single tap (keep hold only as an optional accidental-tap
   guard); the double-dose confirmation already handles "did I mean it?" separately.

3. **Reward novelty is thin — dopamine habituates fast.** Only two celebration kinds
   (`FIRST_OF_DAY` / `REGULAR`) and the same confetti every time. ADHD brains habituate
   to fixed rewards quickly.
   *Fix:* rotate encouragement copy and celebration visuals; add occasional
   variable/surprise reinforcement (variable-ratio reward is markedly stickier). Cheap to
   add, high retention payoff.

4. **Task Roulette requires you to have already done the planning it's meant to replace.**
   `TaskRouletteSheet` spins only the user's Quicklist items. A new or overwhelmed user
   has an empty quicklist, so the anti-paralysis tool has nothing to offer precisely when
   paralysis is worst.
   *Fix:* seed Roulette with sensible default micro-tasks ("drink water," "stand up and
   stretch," "reply to one message") so it works on first open.

5. **Brain Dump is a dead-end capture.** It's capture + "Clear all" only
   (`HomeViewModel.addBrainDumpItem` / `clearBrainDump`). The executive-function value of
   a brain dump is converting the fleeting thought into an action; here thoughts land in
   "Recent" and are later wiped.
   *Fix:* one-tap promotion of a brain-dump item to Quicklist task / Roulette entry /
   reminder. Closes the capture→do loop.

6. **Ambient notification is single-med and doesn't show "next."**
   `AmbientNotificationManager.update` reflects only the currently selected medication
   and shows elapsed-since-last, not an at-a-glance next-dose/countdown. ADHD users with
   comorbid prescriptions (common) lose persistence on all but one med.
   *Fix:* support multiple meds in the persistent notification; add a "next dose at
   HH:MM" line so the always-on surface answers "what's next," not just "what happened."

7. **Reminders are purely time-absolute; onboarding is ~6 steps.** Follow-ups are fixed
   offsets from the dose (default 4h/8h) with limited escalation (Snooze 15 / Stop
   today). Time-based cues are the weakest signal for a time-blind user mid-task. And
   onboarding runs Welcome → PickMed → UsualTime → Anchor → Permissions → PinWidget —
   well-designed and mostly skippable, but 6 steps risks first-run drop-off. Lower
   priority: defer widget/permission steps to first contextual need; richer re-engagement
   after a "Stop today."

**Net:** the ADHD thinking here is above-average and in places genuinely excellent
(anchors, ambient persistence, wear-off timing, forgiveness, reliability). The daily
experience would improve most from fixing streak framing (#1) and hold-to-log friction
(#2); the rest is polish that compounds retention.

---

## Part 2 — Fable review

**Bottom line:** I agree with the overall verdict — the behavioral skeleton is right and
unusually complete. My read of the code surfaces one gap Opus under-weighted because it is
invisible in the UI: **the reminder-escalation system was designed (constants, request-code
ranges) and never wired up.** For an ADHD user, the single most common failure is not "no
reminder" — it's "the reminder fired once and my brain didn't engage with it." That path
currently dead-ends. I rank that #1, streak semantics #2, and I'd demote hold-to-log to a
milder fix than Opus suggests.

### (a) What it gets right (confirmed independently, with additional detail)

1. **The anchor concept is carried end-to-end, not just asked once.** Onboarding's
   `AnchorStep` offers concrete morning anchors (coffee, brushing teeth, phone unlock,
   turning the alarm off, or free text), stores it on `Medication.morningAnchor`, and the
   ambient notification replays it at decision time: "No dose logged yet (right after
   coffee)". This is textbook implementation-intention design ("after I X, I will Y"),
   which is the intervention with the best evidence for prospective-memory failure. Most
   apps ask for a time; this asks for a cue. That is the app's best single idea.

2. **Three redundant object-permanence surfaces, all one-tap:** ongoing ambient
   notification with an inline "Log dose" action (shown only when today is unlogged —
   correctly removed after logging, so no stale affordance), a Glance home-screen widget
   with tap-to-log, 1.5s debounce, and a 10s tap-again-to-undo window
   (`Constants.WIDGET_DEBOUNCE_MILLIS`, `WIDGET_UNDO_WINDOW_MILLIS`), and a full-screen
   alarm (`AlarmActivity`) with Taken / Snooze 15 / Stop-today. Logging never requires
   opening the app, which is the correct bar for ADHD.

3. **Presets remove the setup executive-function tax.** `MedicationPreset.ALL` pre-fills
   duration and follow-up offsets for 9 common ADHD meds (Ritalin IR/LA, Concerta,
   Adderall IR/XR, Vyvanse, Strattera, Wellbutrin, Guanfacine) including
   `hasEffectWindow=false` for non-stimulants ("Non-stimulant — no acute window").
   The user never has to know or research pharmacokinetics to get correct time-blindness
   scaffolding; `OnboardingDraft.isReady` requires only a non-blank name.

4. **The app doesn't nag redundantly.** `AlarmReceiver` skips the daily reminder when a
   dose is already logged ("Daily reminder skipped — dose already logged today") and
   honors skip-tomorrow by advancing a day. Redundant nagging is how ADHD users learn to
   ignore an app; this prevents alarm fatigue at the source.

5. **Warnings are consent gates, not lectures.** `buildWarning` produces exactly three
   question-framed confirms (double dose within 3h or active window, post-cutoff "may
   affect sleep. Log anyway?", off-day). They gate with a confirm rather than blocking,
   preserving user agency — the right RSD posture.

6. **Forgiveness affordances are unusually complete:** undo everywhere (10.5s in-app, 10s
   widget), pause-for-N-hours with an honest banner ("Alarms won't fire during the pause.
   Your dose history is unaffected."), skip-tomorrow, per-med off-days, backdating via
   `BackdateDialog` ("What time did you take it?") for the classic "took it, forgot to
   log it" case, and neutral Missed/Skipped records.

7. **Empty states coach instead of shaming:** "Log 3 doses to unlock your first adherence
   stats" (StatsScreen) turns cold-start into a tiny goal; the stats copy is "Keep going —
   your brain is building a real habit."

8. **The EF toolbelt is real, and Transition mode is a standout.** FOCUS 25m / TASK
   count-up / TRANSITION 10m / BREAK 5m (`FocusMode`) — a dedicated task-switching timer
   is rare and addresses a core ADHD deficit. Brain dump is capped at 20 items
   (`BRAIN_DUMP_MAX_ITEMS`), a good anti-hoarding constraint.

9. **Reliability paranoia is correct and thorough:** `BootReceiver` handles boot,
   package-replaced, time-set, and timezone-changed; `alarmMissingFlow` drives an "Alarms
   may have been stopped" banner with a one-tap "Fix" (`rescheduleAlarms`); OEM-specific
   battery guidance. For a user who has outsourced memory to the app, silent death is the
   worst failure and the app treats it that way.

### (b) Gaps, ranked by impact (Fable ranking)

1. **Escalation was designed but never implemented — the ignored-reminder path dead-ends.**
   `util/Constants.java` defines `ESCALATION_FIRST_DELAY_MILLIS = 600000` (10 min),
   `ESCALATION_SECOND_DELAY_MILLIS = 1500000` (25 min), and
   `ESCALATION_REQUEST_CODE_BASE = 4194304` — and **nothing in the app references them**
   (verified: zero call sites in `alarm/`, `domain/usecase/`, anywhere). So when a
   follow-up or daily reminder fires and the user is mid-hyperfocus, dismisses it
   reflexively, or simply doesn't process it, there is no automatic re-nudge — only manual
   Snooze 15, which requires the very engagement that just failed. This is the most common
   real-world failure moment for an ADHD user and it is currently unhandled.
   *Fix:* implement the escalation as evidently planned: if a reminder is neither actioned
   nor snoozed within 10 min, re-notify (gentle, re-worded — "Still there? Coffee's
   getting cold"); second re-nudge at 25 min; auto-cancel the chain the instant any dose
   log arrives from any surface. Keep it capped at two so it never becomes nagging.

2. **Streak mechanics punish users for using the app's own forgiveness features.**
   Same finding as Opus (streak = consecutive days counted back from today, so it reads 0
   every morning pre-log and hard-resets on one miss), plus one aggravator Opus didn't
   flag: `compute$dayHasDose` counts only days with a TAKEN/INITIAL_TAP event, so
   **app-sanctioned off-days, "skip tomorrow," and paused days also break the streak.**
   The app says "it's fine to skip tomorrow," then its stats page punishes you for it.
   That inconsistency teaches users the forgiveness features are a trap.
   *Fix:* treat off-days/skip-days/paused days as streak-neutral (bridge, don't break);
   show "today pending" (e.g., yesterday's streak + a hollow flame) instead of 0 before
   the first log; add one grace day per week.

3. **Hero button: keep the hold, fix the failure feedback.** I measured the hold at
   **250ms** (`DoseHeroSection$2$2$1$1`, `j = 250`) — that is not meaningful friction, and
   it is a reasonable pocket-tap/accidental-log guard for the largest tap target in the
   app. The real problem is the failure mode: a quick tap does nothing except flash
   "Hold…", with no explanation. Ranked below escalation and streaks because it costs
   milliseconds, not adherence — but it violates "just one tap" as perceived.
   *Fix (cheaper than Opus's):* on a short tap, log anyway OR show a one-time tooltip
   ("Press and hold half a second — prevents pocket logs") plus haptic on hold-start.
   Either restores trust; silent rejection is the only unacceptable option.

4. **Wear-off warning is informational, not actionable.** `scheduleWearOffWarning` fires
   once at duration minus 60 min ("Wearing off around HH:MM — plan accordingly"). For a
   time-blind user, "plan accordingly" is exactly the instruction they can't execute. It
   also has no action buttons.
   *Fix:* add actions to the notification — "Log next dose" (when a follow-up dose is
   plausible) and "Start Transition timer" (the app already has the perfect tool for the
   wear-off crash; connect them).

5. **Roulette cold-start and Brain Dump dead-end** — concur with Opus (his #4 and #5),
   same fixes: seed Roulette with defaults; one-tap promote brain-dump items to
   Quicklist/Roulette. I'd add: after a Roulette pick, offer "Start 10-min timer" so the
   pick flows straight into the existing TASK/FOCUS timer instead of ending at a card.

6. **Weekly summary can land as a Monday-night report card.** `WeeklySummaryReceiver`
   always sends "This week: X/7 days on time. Best streak: N days. Consistency is key for
   ADHD medication — great work staying on track." On a 1/7 week, "great work staying on
   track" reads as sarcasm and the fraction reads as a grade — a small but real RSD trap,
   delivered at Sunday/Monday 20:00 when mood is lowest.
   *Fix:* branch the copy: low weeks get "Rough week — it happens. Tomorrow is a clean
   slate. Want to check your anchor?" and never lead with the fraction.

7. **Ambient surface answers "what happened," not "what's next"** — concur with Opus's #6
   (single-med, no next-dose line). Additional note: after the last follow-up of the day
   there is no forward-looking cue at all until tomorrow's daily reminder; a "next dose
   tomorrow, right after coffee" line would keep the anchor loop warm overnight.

8. **Celebration variety** — concur with Opus's #3 (two kinds, fixed confetti; habituation
   risk). Variable reward + rotating copy is a cheap, high-leverage retention fix.

Not gaps, in my view: onboarding length (6 steps but each is a single pre-filled decision
and only the name is mandatory — this is fine; at most defer the widget step), and the Pro
gating (heatmap/paywall never blocks the core log-remind-undo loop, which is the correct
line to hold).

---

## Part 3 — Consolidated priority list (for implementation)

Where the reviewers disagree on rank, both positions are noted; the order below is the
merged recommendation.

| # | Item | Files/areas | Opus rank | Fable rank |
|---|------|-------------|-----------|------------|
| 1 | Streak forgiveness: today-pending display, grace day, off/skip/paused days streak-neutral; lead Stats with 7/30-day adherence % | `ui/stats/StatsViewModel` (`compute`), `StatsScreenKt` (StreakCard) | 1 | 2 |
| 2 | Implement reminder escalation (10/25-min re-nudge, max 2, reworded copy, cancel-on-log) — constants already exist unused | `alarm/AlarmScheduler`, `alarm/AlarmReceiver`, `util/Constants` (ESCALATION_*) | (noted, unranked) | 1 |
| 3 | Hero log button: single tap should log (or at minimum explain the 250ms hold on failed tap + haptic on hold-start) | `ui/home/HomeScreenKt` (DoseHeroSection) | 2 | 3 |
| 4 | Celebration variety: rotate copy/visuals, occasional variable reward | `ui/home/CelebrationKind`, `ConfettiBurst`, `FirstDoseOverlay` | 3 | 8 |
| 5 | Seed Task Roulette with default micro-tasks; add "start timer" after a pick | `ui/home` TaskRouletteSheet, `ui/focus` | 4 | 5 |
| 6 | Brain Dump: one-tap promote item → Quicklist/Roulette/reminder | `ui/home` BrainDumpSheet, `HomeViewModel` | 5 | 5 |
| 7 | Ambient notification: add "next dose at HH:MM / tomorrow after [anchor]" line; multi-med support | `notification/AmbientNotificationManager` | 6 | 7 |
| 8 | Wear-off warning: add action buttons (log next dose / start Transition timer) | `alarm/WearOffWarningReceiver`, `AlarmScheduler.scheduleWearOffWarning` | (part of 7) | 4 |
| 9 | Weekly summary: adaptive copy for low-adherence weeks, don't lead with X/7 | `alarm/WeeklySummaryReceiver` | – | 6 |
| 10 | Onboarding: optionally defer PinWidget/permissions to first contextual need | `ui/onboarding/OnboardingScreenKt` | 7 | (non-issue) |
| 11 | Accessibility/sensory pass: reduced-motion + TalkBack for confetti/hold gesture, content descriptions on the four log surfaces | `ui/home` (hero, ConfettiBurst), `widget/DoseWidget` | (new) | (new) |

Row 11 is a joint addition (not a re-rank): the 250ms hold gesture is invisible to
TalkBack/switch access, and full-screen confetti with no reduced-motion path is a real
sensory-overload risk — ADHD comorbidity with motor/sensory differences is common, and
the Play pre-launch report (Part 7.8) will flag missing content descriptions anyway.
All other ranks are unchanged from the original consolidation.

**Shared bottom line:** ship #1–#3 first. #1 protects long-term retention (the
broken-chain abandonment spiral), #2 fixes the single most common daily failure moment
(the ignored reminder), #3 repairs trust in the app's core promise ("just one tap").
Everything else is compounding polish on an already well-designed ADHD product.

### 3.1 — Interaction specs for implementation (what the user sees, exact states & copy)

These specs turn rows #1–#9 into build-ready behavior. Copy strings are suggestions in
the app's existing voice — questions not commands, never "missed/failed/broke," numbers
only when they orient ("2h ago" yes, "1/7 days" no). All new user-facing strings must go
through string resources, not literals (the current code hardcodes English — Part 4
MEDIUM).

**Global copy rules (apply to every item below):**
- Never address the user's character; address the situation ("The reminder didn't get
  logged" — not "You didn't log").
- Every warning is a question with a Yes-path ("Log anyway?"), never a block.
- Anything time-related shows both relative and absolute ("2h ago · 08:14") — time-blind
  users can't convert one into the other.

---

**Spec #1 — Streak forgiveness** (`StatsViewModel.compute`, `StatsScreenKt` StreakCard)

States of the streak card:
1. *Today logged:* filled flame, "🔥 12 days".
2. *Today not yet logged, yesterday intact:* show **yesterday's count**, hollow/50%-alpha
   flame, subtext "Day 12 — today still open". Never render 0 here. (Currently
   `compute$dayHasDose` counts back from *today*, so every pre-log morning reads 0.)
3. *Gap of exactly 1 day, grace available:* streak continues; one-time inline note
   "Streak bridged — one off day doesn't undo 12." Grace budget: 1 per rolling 7 days,
   auto-applied, no user action (a token UI adds an executive-function tax; see Part 5
   "Streak Insurance" for the Pro variant that adds *extra* tokens).
4. *Gap > grace:* streak resets, but the card leads with the surviving metric:
   "30-day adherence: 87%" big, "Streak restarting — day 1 is today" small. No red, no
   broken-chain iconography.
5. *App-sanctioned absences* — off-days (`Medication.offDays`), skip-tomorrow, paused
   days — are **bridges**: they neither increment nor break. Subtext on such a day:
   "Off day — streak safe."

Screen order change: AdherenceCard (7/30-day %) moves above StreakCard. Edge cases: DST
day-bucketing must be fixed first or grace logic misfires (E-H4); a backdated log
(`BackdateDialog`) that fills yesterday retroactively repairs the streak and should
recompute immediately; widget/summary surfaces that show the streak must use the same
"today still open" rule.

---

**Spec #2 — Reminder escalation** (`AlarmScheduler`, `AlarmReceiver`,
`Constants.ESCALATION_*`, request codes from `ESCALATION_REQUEST_CODE_BASE`)

Trigger: a daily reminder or follow-up notification is posted and, within
`ESCALATION_FIRST_DELAY_MILLIS` (10 min), none of these occur: dose logged (any surface),
Snooze tapped, Stop-today tapped, notification dismissed-with-action. Then:
- *Nudge 1 (+10 min):* re-post as a **new** notification (fresh sound/vibration — a
  silent update defeats the purpose). Title unchanged; body reworded:
  "Still there? Your dose is waiting (right after coffee)." Same three actions as the
  original (Log dose / Snooze 15 / Stop today).
- *Nudge 2 (+25 min from original):* "Last nudge for this one — tap to log, or snooze
  and we'll drop it." Same actions. **Hard cap: 2 nudges**, then silence until the next
  scheduled reminder. Nagging teaches dismissal.
- *Cancel rules:* any dose log from widget, ambient notification, alarm activity, or app
  cancels the whole chain instantly (hook the same path that already updates the ambient
  notification). Snooze cancels pending nudges and re-arms them relative to the snoozed
  fire time. Stop-today cancels everything for the day.
- *Never escalate:* while paused, on off-days, on skip-tomorrow days, or for the wear-off
  warning (informational only). Swipe-dismiss of the original **does** still escalate —
  reflexive dismissal is precisely the failure being covered; the cap keeps this safe.
- Pro-gated via existing `FeatureFlag.canUseSmartEscalation` (this is the feature the
  paywall already sells — E-C1).

---

**Spec #3 — Hero log button** (`HomeScreenKt` DoseHeroSection; hold ≈250ms with progress
arc, quick tap currently rejected silently)

Recommended (Opus): tap logs immediately — haptic tick + existing warning gates
(`buildWarning`) still apply, so accidental double-logs are already caught by the
DOUBLE_DOSE confirm. Keep the ring animation as a press effect only.
Acceptable fallback (Fable): keep the 250ms hold but kill the silent failure:
- On press-down: haptic `KEYBOARD_TAP`, arc starts.
- On release before threshold: arc snaps back + one-time-per-session tooltip below the
  button: "Hold for half a second — stops pocket-logs." Never just a flash.
- On threshold: haptic `CONFIRM`, log fires.
Either way, the button label when unlogged should say what the gesture is ("Hold to log"
or "Tap to log"), not just the med name. TalkBack: expose a plain `onClick` action that
logs regardless of gesture mode (row #11).

---

**Spec #4 — Celebration variety** (`CelebrationKind` — currently only FIRST_OF_DAY /
REGULAR — `ConfettiBurst`, `FirstDoseOverlay`)

- Copy pool: ≥10 rotating lines for first-of-day ("That's the hard part done.", "Logged.
  Brain: fed.", "Future-you says thanks.", "Anchor held. 🎯" …); no repeat within the
  last 5 shown (persist a small ring buffer in DataStore).
- Variable reward: on a random ~1-in-7 log, upgrade the burst (denser confetti, alternate
  palette, or a one-liner like "Bonus sparkles. No reason. Enjoy."). Variable-ratio
  reinforcement resists habituation; keep it rare enough to stay surprising.
- Milestones (7/30/90/180/365, personal best) get distinct visuals from daily bursts.
- Respect `celebrationsEnabled` and the system reduced-motion setting (static "✓ Logged"
  card instead of confetti).
- **Paywall never replaces or interrupts a celebration.** Per M3 the paywall may appear
  *after* the celebration overlay is dismissed, at most once per week, and never on a
  user's first-ever log.

---

**Spec #5 — Task Roulette cold start** (`TaskRouletteSheet`)

- If the quicklist is empty, spin over a built-in starter set (labeled "starter task"):
  drink a glass of water · stand up and stretch for 1 minute · reply to one message ·
  put one thing back where it belongs · 2-minute tidy of whatever is in front of you ·
  step outside for three breaths · refill your water bottle · write down the thing you
  keep re-remembering.
- Starter tasks disappear from the pool once the user has ≥3 of their own items.
- Result card gets three actions: **[Start 10-min timer]** (opens Focus in TASK mode,
  pre-armed at 10:00 — deep link, no extra taps), **[Spin again]** (max 2 respins, then
  "This one. Trust the wheel." — unlimited respins recreate the decision paralysis the
  tool exists to break), **[Done]**.

---

**Spec #6 — Brain Dump promotion** (`BrainDumpSheet`, `HomeViewModel`)

- Each item row gets one trailing icon-button: **"→ Quicklist"**. Tap: item moves to
  Quicklist (removed from dump), snackbar "Moved to Quicklist" with Undo. One tap, one
  destination — a promote-to-{task,reminder,roulette} chooser is scope creep and an EF
  tax; Quicklist items already feed Roulette, which closes the loop implicitly.
- "Clear all" keeps its confirm and now reads "Clear 7 thoughts? Anything worth keeping →
  Quicklist first."
- The 20-item cap stays; when full, oldest-first hint: "Dump's full — promote or clear a
  couple."

---

**Spec #7 — Ambient notification: answer "what's next"**
(`AmbientNotificationManager.build`)

Content states (single-med; multi-med wraps these in `InboxStyle`, one line per med):
1. *Unlogged, reminder pending:* "No dose logged yet (right after coffee) · reminder
   08:00".
2. *Logged, follow-up pending:* "Taken 08:14 · 2h ago · next reminder 12:14".
3. *Logged, day complete:* "Done for today ✓ · tomorrow right after coffee" — keeps the
   anchor loop warm overnight (Fable's note).
4. *Paused:* "Paused until 15:00 · history unaffected".
All times locale-formatted (fix the hardcoded `HH:mm` while here — Part 4 MEDIUM). The
inline "Log dose" action keeps its current shown-only-when-unlogged behavior.

---

**Spec #8 — Wear-off warning actions** (`WearOffWarningReceiver`,
`AlarmScheduler.scheduleWearOffWarning`)

- Body: "Wearing off around 17:30. The next hour can feel like wading — that's the meds,
  not you."
- Action 1: **[Start 10-min transition]** → Focus screen in TRANSITION mode, auto-start.
  Always shown.
- Action 2: **[Log next dose]** — only when plausible: another scheduled follow-up
  remains today *and* the double-dose gate wouldn't fire. Routes through the normal
  warning checks; this is a shortcut, not a bypass.
- Never escalates (see Spec #2), fires once, and is suppressed on off/skip/paused days.

---

**Spec #9 — Weekly summary adaptive copy** (`WeeklySummaryReceiver`; requires the E-C5
bootstrap fix or it never fires at all)

Branch on days-logged N of the week:
- N ≥ 6: "Strong week — N of 7 days. The habit is carrying you now."
- 3 ≤ N ≤ 5: "You showed up N days this week. Steady beats perfect." (fraction allowed
  here, never as the opener)
- N ≤ 2: "Rough week — it happens, and it doesn't erase anything. Tomorrow's a clean
  slate. Want to check your anchor still fits?" → tapping opens the anchor setting. **No
  numbers at all in this branch.**
- N = 0 with the app paused most of the week: skip the summary entirely; silence is
  kinder than a report card about a sanctioned break.
Drop "Consistency is key for ADHD medication" from every branch — it reads as a lecture
and edges toward medical-claim territory (see Part 7.6).

---

**Spec #10 — Onboarding deferral** (`OnboardingScreenKt`)

Keep Welcome → PickMed → UsualTime → Anchor. Move Permissions to a just-in-time prompt
the first time a reminder is armed ("To remind you, DopaTap needs notification
permission — that's its whole job"), and PinWidget to a Home-screen suggestion card
after the *second* successful log ("You've logged 2 days from inside the app — want a
one-tap widget so you don't even have to open it?"). Rationale: ask for commitment after
value is proven, and 4 steps beats 6 for first-run drop-off. Skippable as today.

---

## Part 4 — Engineering / reliability findings

Separate from UX: bugs and risks in the code that break the product's promise or leak
revenue. Reviewed against the decompiled sources; the shipped APK is a **debug build**
(`com.dosetap.debug`, `debuggable=true`, unobfuscated) — confirm the release variant
shares this code before acting.

**CRITICAL (revenue / reminders fail silently / data loss)**

- **E-C1. The paywall sells a feature that isn't built.** "Smart escalation reminders
  (+10/+25 min nudges)" is advertised in `PaywallBottomSheet` and gated by
  `FeatureFlag.canUseSmartEscalation`, but `Constants.ESCALATION_*` are referenced nowhere —
  no receiver, no scheduling. Paying users get nothing → refunds + Play policy risk. (Same
  root cause as ADHD gap #2 — building it fixes both.) *Fix:* implement escalation
  (see roadmap R3) or remove the paywall row.
- **E-C2. Purchasers lose Pro when Play Billing is unreachable.** `BillingManager._isPro`
  is in-memory, seeded `false`, only set true after a successful `queryPurchases`. Offline /
  slow / disconnected billing on cold start ⇒ a paying user sees the paywall and loses Pro
  for the session; `onBillingServiceDisconnected` doesn't reconnect. *Fix:* cache
  entitlement in DataStore, seed the flow from cache, only let a *successful* query
  downgrade, add reconnect-with-backoff.
- **E-C3. Free-Pro loopholes.** Trial state is device-local (reinstall / clear-data = fresh
  14 days, repeatable); gift + dev-grant codes are static SHA-256 hashes shipped in
  `res/raw/{gift_codes,dev_grant_codes}.txt`, never marked used, granting *permanent* Pro —
  one leaked code unlocks everyone forever, and the unobfuscated build makes them trivial to
  read. *Fix:* single-use/expiring codes + redeemed-set in DataStore, remove raw code files
  from the APK, bind trial to Play Integrity / durable id, enable R8. (Billing
  acknowledgement *is* handled correctly — no 3-day auto-refund leak.)
- **E-C4. Backup/restore silently loses data and isn't transactional.** Export omits dose
  `note`/`tags`; import drops `offDays`, `prescriptionDate`, `daysSupply`,
  `refillBufferDays`, `morningAnchor`, and does `deleteAll()` + row-by-row insert with no
  Room transaction, so a bad row can leave the DB wiped/partial. *Fix:* `withTransaction`,
  round-trip the full field set, validate the export `version`.
- **E-C5. Weekly summary never fires.** `scheduleWeeklySummary()` is only ever called by
  its own receiver; nothing bootstraps it and alarms don't survive reboot, so it's never
  armed. (Same as ADHD list #9's precondition.) *Fix:* arm it in `DoseTapApp.onCreate()`
  and in `BootReceiver`'s reschedule branch (idempotent via its fixed request code).

**HIGH (bugs users hit / weak monetization)**

- **E-H1. Snooze wipes the remaining follow-up chain.** `HandleDoseActionUseCase` on
  `SNOOZED` calls `scheduleFollowUps(... followUps=[0] ...)`, which begins with
  `cancelFollowUps(tapId)` — snoozing the +4h reminder cancels the +8h one and replaces the
  chain with a single +15 min ping. *Fix:* add a `cancelExisting=false` path or a
  `snoozeFollowUps` that shifts only the still-future rows.
- **E-H2. Missed-while-off / paused alarms are silently dropped.** `getAllActive` filters
  `triggerAtMillis > now`, so anything due while the phone was off is discarded on reboot —
  no late fire, no MISSED logged, stale row left active. Paused follow-ups are `cancelById`'d
  with no catch-up. *Fix:* re-arm at `max(triggerAt, pausedUntil)` or write a MISSED event.
- **E-H3. `USE_EXACT_ALARM` is a Play-review time bomb.** It's Play-restricted to
  alarm-clock/calendar apps; medication reminders should use `SCHEDULE_EXACT_ALARM` +
  `canScheduleExactAlarms()`. There's no guard around the four `setExactAndAllowWhileIdle`
  call sites → crash/no-op on Android 14+ if forced to switch. *Fix:* route all calls
  through a helper that checks `canScheduleExactAlarms()` and falls back to inexact, plus a
  just-in-time `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` prompt.
- **E-H5. `USE_FULL_SCREEN_INTENT` is not declared in the manifest.** The reviews describe
  a full-screen alarm path (`AlarmActivity`), but the manifest declares only 10 permissions
  and `USE_FULL_SCREEN_INTENT` is not among them. On targetSdk 34+ (this build targets 35),
  `setFullScreenIntent` without the declared permission silently degrades to a heads-up
  notification — the "wake the user up" surface never shows and nothing logs an error. If
  the full-screen alarm is intended, declare the permission **and** complete the Play
  Console full-screen-intent declaration (Part 7.6); note Play auto-grants it only to
  alarm/calling apps, others must send users to `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`.
  *Fix:* declare + runtime-check `canUseFullScreenIntent()`, fall back to high-priority
  notification.
- **E-H4. Stats break across DST.** `StatsViewModel.compute` buckets by local-midnight but
  iterates days with `+= 86400000`; on a 23h/25h DST day the iterator drifts off the bucket
  keys and every later day misreads as "no dose" → wrong adherence % and longest streak.
  (Also feeds ADHD list #1.) *Fix:* step by calendar day (`LocalDate.plusDays(1)`), same
  anywhere `86400000` is used for day math.

**MEDIUM / polish:** debug logging leaks health data — e.g. `WearOffWarningReceiver`
logs the medication name + wear-off time via `Log.d` (visible to any adb-connected host;
strip for release, see Part 7.2); current-streak reads 0 pre-log (UX #1); request-code scheme
`(tapId<<4)|index` collides after 2^27 events (safe today); hardcoded English + `HH:mm`
notification copy ignores locale; `DoseAction.valueOf` throws on forward-compat import files;
several time windows (`HistoryViewModel`, `alarmMissingFlow`) captured once at construction
drift past midnight in long sessions.

**Genuinely well-built (don't touch):** exact-alarm core with full-screen intents and
Taken/Snooze/Stop actions; reschedule on boot / package-replace / time-set / **timezone**;
OEM battery-killer guidance; billing acknowledgement hygiene; clean manual DI; additive Room
migrations 1→4; health DB excluded from cloud backup; dose-safety warning logic; widget
debounce + undo window.

---

## Part 5 — Monetization / profit spec

Entitlement spine: `AppContainer.isPro()` = `billingLive OR giftCodeUnlocked OR trialActive
OR devGrant`; gating via `FeatureFlag`. Everything below hangs off that, so it's cheap.

**Current state:** one product `dosetap_pro`, `inapp` (one-time), default **€2.99**; 14-day
device-local trial; static gift/dev codes (see E-C3). `FeatureFlag` currently gives away
almost everything and gates only multi-med, 4 follow-ups, adhdTools, custom tone, full stats
chart, doctor report, smart escalation, sponsor, refill prediction.

- **M1. Rebalance Free vs Pro.** *Free (keep — the habit loop must succeed before anyone
  pays):* single med, basic reminders, 2 follow-ups, ambient notification, one-tap log,
  current streak, brain dump, focus timer, off-days, effect window, comedown warning.
  *Move to Pro:* unlimited meds, 4+ follow-ups, **Smart Escalation** (build it), full stats
  history/charts, doctor/adherence PDF, custom alarm tones, refill prediction, and
  **cloud backup + export/import** (flip `canUseExportImport` to Pro).
- **M2. Reprice — €2.99 one-time badly under-monetizes.** Hero a **pay-once "lifetime"
  IAP at ~€8.99–11.99** (ADHD/RSD users prefer no recurring "subscription guilt"), with a
  **small secondary subscription (~€1.99/mo)** shown as the lesser option so lifetime feels
  like the deal. Adding `subs` requires querying `subs` in `queryProductDetails` (currently
  `inapp` only).
- **M3. Paywall timing — RSD-safe, never blocks core function.** Surface Pro at *earned*
  moments: right after a **streak celebration** (`_celebrationEvent`), when a free user hits
  a natural limit (2nd med, 3rd follow-up, stats history, doctor report, custom tone), and
  on the **weekly-summary** tap. No launch interstitial, no fake countdowns — RSD users
  screenshot and rage-uninstall dark patterns.
- **M4. Trial nudges.** Keep the silent 14-day trial; add a gentle "3 days of Pro left"
  and a one-time "trial expired → 40% off lifetime" on `trialExpiredDismissed == false`.
- **Three new Pro features that fit the code:**
  - **Smart Escalation** (finish what's already sold — highest EV; reuses `ESCALATION_*`).
  - **Streak Insurance / grace tokens** (monetizes the RSD pain point from UX #1; a
    `Preferences.Key<Int>` balance consumed before a streak breaks).
  - **Cloud backup + Doctor/Adherence PDF** (`BackupManager` and `DoctorReportScreen`
    already exist; Drive-restore doubles as the durable id that fixes trial abuse in E-C3).

---

## Part 6 — Unified build roadmap (do it in this order)

Merges UX, engineering, and monetization by impact × effort. "Fixes" columns cross-reference
the sections above.

| # | Do this | Why it's first | Effort | Refs |
|---|---------|----------------|--------|------|
| R1 | Cache Pro entitlement in DataStore + reconnect | Paying users currently lose Pro offline — pure revenue leak, de-risks all pricing work | Low | E-C2 |
| R2 | Close code/trial loopholes + enable R8 | Permanent free-Pro leak; do before repricing | Med | E-C3 |
| R3 | Build Smart Escalation (10/25-min re-nudge, cancel-on-log, max 2) | Ships a feature you already charge for AND fixes the #1 daily ADHD failure (ignored reminder) | Med | E-C1, ADHD #2, M-new-1 |
| R4 | Streak forgiveness (today-pending, grace day, off/skip/paused neutral; lead with adherence %) | Stops the broken-chain abandonment spiral — top retention risk both reviewers named | Med | ADHD #1, E-H4 |
| R5 | Fix snooze so it stops cancelling the chain | Core reliability bug; breaks the whole promise | Med | E-H1 |
| R6 | One-tap logging + always-visible countdown + hero-tap fix | Biggest retention lever, plumbing already exists | Low | ADHD #3, #7 |
| R7 | Fire streak celebrations + bootstrap weekly summary (adaptive copy) | Dopamine loop + recap hook currently dead | Low-Med | E-C5, ADHD #4, #9 |
| R8 | Rebalance gating + place paywall at celebration/limit moments + reprice to ~€9 lifetime | Fixes gross under-monetization, RSD-safe | Low-Med | M1, M2, M3 |
| R9 | Fix DST day bucketing; guard `canScheduleExactAlarms`; transactional backup | Correctness + Play-compliance + data safety | Low-Med | E-H4, E-H3, E-C4 |
| R10 | Polish: Roulette defaults, Brain Dump promote, wear-off actions, multi-med ambient, Streak Insurance / Cloud-backup Pro features | Compounding retention + new Pro value | Med | ADHD #5–8, M-new-2/3 |

---

## Part 7 — Google Play deployment & getting paid

An ordered, owner-executable checklist from the current state (a **debug** APK:
`com.dosetap.debug`, `versionCode=1`, `versionName="1.0.0"`, `debuggable=true`,
unobfuscated, targetSdk 35) to money in a bank account. Items marked ⚠ are things that
can block review or get the app pulled. Where a Play policy detail may have shifted,
it says "confirm in Play Console" — verify there rather than trusting this doc blindly.

### 7.1 Play Console developer account (start this first — it has the longest lead time)

1. Create the developer account at play.google.com/console — **$25 one-time fee**.
2. Choose account type. **Personal** is fine for a solo owner; note trade-offs:
   - Personal: government-ID identity verification (usually hours–days); your legal name
     shows on the listing; and ⚠ **new personal accounts must run a closed test with at
     least 12 testers opted-in continuously for 14 days before production access is
     granted** (currently 12 — confirm the current number in Console, Google has changed
     it before). Plan the calendar around this.
   - Organization: requires a **D-U-N-S number** (free from Dun & Bradstreet, but can take
     **up to ~30 days** to issue/verify) plus org verification; no 12-tester requirement;
     shows company name. If you have or plan an SRL/PFA entity anyway, this is cleaner
     for taxes, but it delays start.
3. Complete identity verification immediately — you cannot publish, or even create the
   merchant profile, until it clears. Also verify a developer email and phone.
4. Timeline: personal account ready in ~1–3 days; org account budget 2–5 weeks if you
   need a fresh D-U-N-S.

### 7.2 Release build (the current APK is not shippable)

1. **applicationId:** release must be `com.dosetap` (drop the `.debug` suffix that comes
   from the debug variant's `applicationIdSuffix`). ⚠ The applicationId is permanent once
   the first release is uploaded — decide now (recommendation: keep `com.dosetap`; the
   user never sees it, and the billing/product IDs already use the `dosetap_` prefix).
2. **Debuggable off, R8 on:** release build type with `minifyEnabled true` +
   `shrinkResources true`. Expect one round of keep-rule debugging: Room entities, Glance
   widget receivers, Play Billing, and Kotlin serialization (backup JSON) are the usual
   R8 victims — test *every* flow on the minified build (log, undo, widget, backup
   export/import, purchase). R8 is also the mitigation for E-C3's readable gift codes.
3. **Strip health-data logging:** ⚠ `WearOffWarningReceiver` logs medication name +
   schedule via `Log.d`; `AlarmActivity`/`AlarmReceiver` log dose-event ids. Route all
   logging through a wrapper that no-ops in release (or R8
   `-assumenosideeffects class android.util.Log`). A health-adjacent app logging med
   names to logcat is a needless liability and contradicts the Data Safety form (7.6).
4. **Remove `res/raw/gift_codes.txt` and `res/raw/dev_grant_codes.txt`** from the release
   APK (E-C3) — anyone with an unzip tool gets permanent Pro.
5. **Signing:** generate an **upload keystore** (`keytool -genkeypair`, RSA 2048+, 25+yr
   validity), enroll in **Play App Signing** (mandatory for new apps — Google holds the
   app signing key, you hold the upload key). ⚠ Back up the upload keystore + passwords
   in at least two places (password manager + offline copy; per the owner's existing
   practice, alongside `SERVER_HANDOVER.md`-style docs is the right instinct). A lost
   upload key is recoverable via a Google reset request; a leaked one is worse.
6. **Artifact:** build an **App Bundle (`.aab`)** — required for new apps; APKs are only
   for local testing.
7. **Versioning:** `versionCode` must increase monotonically every upload. Recommended
   scheme: `versionCode = 10000 * major + 100 * minor + patch` (1.0.0 → 10000), so hotfix
   numbering never paints you into a corner. `versionName` is display-only; keep semver.
8. **targetSdk:** already 35 — meets Play's current target-API requirement for new apps.
   Recurring obligation: Play requires the target API to stay within one year of the
   latest Android release (deadline each **Aug 31** — diarize it).
9. Before uploading, fix the two Play-facing engineering items: ⚠ **E-H3
   (`USE_EXACT_ALARM` → `SCHEDULE_EXACT_ALARM`)** and E-H5 (full-screen intent) — details
   in 7.6.

### 7.3 Play Billing go-live

Order matters: merchant profile (7.4) must exist before in-app products can be created.

1. In Play Console → Monetize → In-app products, create:
   - `dosetap_pro` — **keep this exact ID**: `BillingManager` queries it by string
     (`PRODUCT_ID_PRO = "dosetap_pro"`). Per M2, price it as the hero lifetime unlock
     (~€8.99–11.99), not €2.99. The in-app fallback price string "€2.99" in
     `BillingManager` is only a display fallback — update it to match, but the Console
     price is what charges.
   - Optional per M2: a subscription (e.g. `dosetap_pro_monthly`, ~€1.99/mo) — ⚠ this
     needs a code change first: `queryProductDetails` currently queries `inapp` only, and
     entitlement/`FeatureFlag` treat Pro as binary. Ship lifetime-only at launch if you
     want speed; add the sub with roadmap R8.
2. Set per-country prices (Play auto-converts; round to charm prices manually for your
   top 5 markets).
3. **License testing:** Play Console → Settings → License testing → add your own + 2–3
   friendly Gmail accounts. License testers can buy without being charged and exercise
   test cards ("always approves/declines/slow").
4. **Real-device test on the internal testing track** (7.8): install from the Play link
   (not adb — billing behaves differently sideloaded), buy `dosetap_pro`, verify: price
   renders from Play (not the €2.99 fallback), Pro unlocks, purchase survives app
   restart **and airplane mode** (this is E-C2 — fix it first or your first paying user's
   first offline morning shows them a paywall), and refund-revoke works (refund yourself
   in Order Management, confirm Pro revokes on next query).
5. Acknowledgement is already handled correctly in code (Part 4) — just confirm the
   purchase doesn't auto-refund after 3 days on the test track.

### 7.4 Getting paid (merchant profile, bank, taxes)

1. **Payments/merchant profile:** Play Console → Setup → Payments profile → create a
   merchant account. Romania is a supported seller country (confirm your country in the
   current "supported locations for merchant registration" list during setup). Name/
   address must match your ID or company registration.
2. **Bank account:** add an account (RON or EUR — a EUR account avoids one conversion),
   then complete the micro-deposit/verification challenge. Takes a few days.
3. **US tax interview:** in the payments profile, complete the tax questionnaire — as a
   non-US person/entity you'll file a **W-8BEN** (or W-8BEN-E for an SRL) electronically.
   This prevents default 24–30% US withholding on US-sourced sales; the US–Romania tax
   treaty rate is applied from what you enter. Not optional — payouts can be held until
   it's done.
4. **VAT:** Google is **merchant of record** on Play — it collects and remits EU VAT
   (including Romanian VAT) on your sales; you do not register for OSS/VAT for Play
   sales. What lands in your account is net of VAT and Google's fee.
5. **Income tax is yours:** Play income is ordinary income in Romania (PFA vs SRL vs
   personal — talk to an accountant; this document is not tax advice).
6. **Google's cut:** 15% on the first **$1M USD/year** of earnings, 30% above that
   (subscriptions are 15% throughout). ⚠ The 15% tier historically required opting in /
   confirming account-group info in Play Console → Financial reports — check that it
   shows as active rather than assuming.
7. **Payouts:** monthly, around the 15th for the prior month's earnings, automatically,
   once the (small) minimum threshold is met. Expect your first payout ~30–45 days after
   your first sale.

### 7.5 Store listing assets

- **App name (30 chars max):** `DopaTap: ADHD Routine Log` (25) or
  `DopaTap — ADHD Day Anchor` — ⚠ use **DopaTap**, the actual `app_name`, everywhere;
  never "DoseTap".
- **Short description (80 chars):** draft:
  `One-tap logging for meds & routines, built for ADHD brains. No shame, ever.` (76)
- **Full description (4000 max), benefit-led draft** (edit freely, keep the claims
  behavioral, never clinical):

  > **"Did I take it already?" — never wonder again.**
  > DopaTap is a one-tap routine and medication logger designed around how ADHD brains
  > actually work — not how planners wish they worked.
  >
  > 🧷 **Anchored to your real morning.** Tie your routine to something you already do —
  > right after coffee, right after brushing your teeth — and DopaTap reminds you *at
  > that moment*, not at a random clock time.
  > 👁 **Out of sight ≠ out of mind.** A quiet always-there notification and a
  > home-screen widget keep today's status visible. Log with one tap. Undo with one tap.
  > ⏳ **Time blindness, handled.** See "2h ago" at a glance, know when your meds wear
  > off, and get a heads-up before the afternoon dip.
  > 🧰 **An ADHD toolbelt, included.** Focus & transition timers, brain dump, task
  > roulette for decision paralysis, box breathing, and a quicklist.
  > 💚 **Zero guilt by design.** Missed a day? Nothing yells at you. Off days, skip
  > days, pauses, and undo are all first-class. Your streak bends instead of breaking.
  > 🔒 **Private by default.** Your data stays on your device. No account, no cloud, no
  > selling your information.
  >
  > DopaTap is an organizational tool for reminders and routine-building. It does not
  > provide medical advice, diagnosis, or treatment — always follow your prescriber's
  > instructions.

- **Graphics:** app icon 512×512 PNG; **feature graphic 1024×500** (required); ≥2 phone
  screenshots (do 6–8, 1080×1920+): hero = widget + one-tap log, then anchor onboarding,
  ambient notification, effect window/"2h ago", toolbelt, stats, dark mode. Put a short
  benefit caption on each — screenshots are read, not admired.
- **Category:** Health & Fitness fits the routine/wellness framing better than Medical
  (Medical implies clinical claims and invites stricter scrutiny). Tags: habit tracker,
  reminders, ADHD.
- Contact email (public), website optional but recommended — the privacy policy (7.6)
  needs a URL anyway.

### 7.6 Compliance — the items that actually block health-adjacent apps ⚠

1. **Privacy policy URL (required for all apps).** Host a short honest policy (GitHub
   Pages / your own domain — the owner already runs web infra): what's stored (meds,
   dose times — locally, on device), what leaves the device (nothing except Play Billing
   handled by Google; plus analytics **if** Part 8's Firebase is added), retention
   (until uninstall/clear-data; backup export is user-initiated), contact. Link it in
   Play Console *and* inside the app (Settings).
2. **Data Safety form.** As shipped, the truthful answers are unusually good: data is
   stored locally, never transmitted → "**No data collected**" ("collected" means sent
   off-device), "No data shared". The health DB is already excluded from cloud backup
   (Part 4), which keeps this clean. ⚠ Two traps: (a) if you add Firebase/GA4 for Part 8,
   this form changes materially (device IDs, app interactions collected — declare it or
   face removal); (b) if you ship "Cloud backup" as a Pro feature (M-new-3), health data
   leaves the device and must be declared as Health info, encrypted in transit, with
   deletion mechanism. File the form to match what the binary does *on the day you
   submit*, and re-file when either lands.
3. **Health apps declaration.** Play Console → App content includes a health-apps
   questionnaire for apps with health features. Declare truthfully as a
   medication/routine *reminder & adherence-logging* tool (roughly the "manage/track
   wellness" category — pick the closest truthful option in the current form). Do not
   claim diagnosis/treatment anywhere: not in the form, listing, or app copy. This is
   also why Spec #9 removes "Consistency is key for ADHD medication" — keep app copy
   describing *behavior* ("log", "remind"), not *outcomes* ("improves adherence/symptoms").
4. **Exact alarms (ties to E-H3).** ⚠ The manifest ships `USE_EXACT_ALARM`, which Play
   policy reserves for apps whose *core function* is an alarm clock or calendar — a
   medication reminder is explicitly the example Google routes to **`SCHEDULE_EXACT_ALARM`**
   instead. Expect rejection or a policy strike if submitted as-is. Do E-H3 first: swap
   the permission, gate all four `setExactAndAllowWhileIdle` sites behind
   `canScheduleExactAlarms()`, add the in-app `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`
   prompt (frame it in ADHD terms: "Exact timing is the whole point — allow it?"), and
   answer the Console's exact-alarm declaration accordingly.
5. **Full-screen intent (E-H5).** If keeping the full-screen alarm, declare
   `USE_FULL_SCREEN_INTENT` and complete the corresponding Console declaration; be ready
   to justify it (time-critical reminder). Simpler launch path: ship without it (
   high-priority notification + sound) and add it later with justification.
6. **Content rating:** IARC questionnaire — answer the health/medical-references question
   honestly; expect an Everyone/PEGI-3-ish rating. Takes minutes; required before release.
7. **Target audience:** 18+ (or 16+). ⚠ Do **not** include under-13 — that drags in
   Families policy (a real burden) and an ADHD med logger is plausibly used by teens,
   which reviewers will probe if you include them.
8. **"Medication" framing and review friction.** Truthful positioning that reduces
   friction: DopaTap is a *routine/reminder/logging tool for people who take ADHD
   medication* — it never doses, recommends, or adjusts anything; the in-app "warnings"
   are consent questions, not medical guidance. Say exactly that in the review notes
   field if asked. What you must NOT do: claim it improves symptoms, adherence outcomes,
   or health; call it a medical device; or hide that it handles medication schedules
   (misrepresentation is the faster ban).

### 7.7 Pre-submission gate (do these before the first upload)

Cross-reference of must-fix engineering items already specced in Parts 4/6:
E-H3 exact alarms (policy) · E-C1 escalation-or-remove-paywall-row (paid feature that
doesn't exist = refund/policy magnet) · E-C2 entitlement cache (first offline paying
user) · E-C3 code loopholes + R8 · E-C4 transactional backup (data loss) · E-C5 weekly
summary bootstrap. R1–R5 of Part 6 ≈ this gate.

### 7.8 Testing → launch flow

1. **Internal testing** (up to 100 testers, live in minutes, no review): your devices +
   license testers. Run the full billing pass (7.3.4) and the reliability pass here
   (reboot, Doze overnight, timezone change — the app claims to handle these; verify on
   a real Xiaomi/Samsung, the OEMs `OemDetector` worries about).
2. **Closed testing**: promote the same build. ⚠ Personal accounts (new rules): 12+
   testers opted in for 14 consecutive days, then apply for production access and answer
   a short questionnaire about your testing. Recruit testers from ADHD communities you're
   already in (this doubles as Part 8's organic groundwork) — a Google Group makes
   opt-in management easy.
3. **Pre-launch report** runs automatically on test tracks: fix flagged crashes,
   accessibility issues (row #11 of Part 3 covers the likely flags), and screenshots of
   broken screens.
4. **Production** with **staged rollout**: 10% → 25% → 50% → 100%, watching ANRs/crashes
   (Android vitals) and one-star reviews between stages. First-app review can take up to
   ~7 days; don't push updates mid-review.
5. **If rejected:** the email names the policy — fix, re-submit with a note in the
   review notes field, or appeal via the Policy Status page if you believe it's wrong.
   Most first rejections for this app would be exact-alarms (7.6.4) or Data-Safety
   mismatches (7.6.2). Never re-submit unchanged.

### 7.9 Realistic timeline & post-launch money notes

| Week | What happens |
|------|--------------|
| 1 | Console account + $25 + ID verification; merchant profile + bank + W-8BEN; keystore; start release-build hardening (7.2) |
| 2 | Pre-submission gate fixes (7.7 / R1–R5); listing assets + privacy policy; internal testing + billing pass |
| 3–4 | Closed testing (the 14-day clock, personal accounts); fix pre-launch-report findings; recruit the 12 testers day one of week 3 |
| 5 | Apply for production; review wait; staged rollout begins |
| 6+ | 100% rollout; start Part 8 Phase 0 only after the funnel instrumentation (8.1) is live |

**How the money actually arrives after launch:** the paywall converts at *earned
moments* (M3 — after celebrations, at natural limits; never as a launch interstitial);
ask for a Play rating with the in-app review API right after a milestone celebration
(the one moment users feel good about the app — cap at once per version); iterate ASO
(title/short-description keywords, screenshot captions) off the Console's search-terms
data; handle refunds generously and fast in Order Management — for a €9 health-adjacent
app, a €9 refund is always cheaper than a one-star "it charged me and I hate it" review.
Expect the pattern of one-time-purchase utilities: a sales trickle proportional to
installs, so growth = Part 8.

---

## Part 8 — Paid user-acquisition & ad investment plan

For an owner with real ad capital. The honest headline first: **a ~€9 one-time purchase
gives paid UA very little room** — this plan is deliberately gated so money only scales
into a funnel that's proven to convert. All € figures below are **illustrative planning
numbers, not benchmarks** — replace them with your own measured data as it arrives.

### 8.0 Prerequisite gate — do not spend until you can see the funnel

No ad spend until installs → activation → purchase is instrumented end-to-end and you
know your baseline paywall conversion from *organic* users. Otherwise you're buying
installs with no idea what they're worth.

- **Tooling:** Firebase Analytics (GA4) + the Play Install Referrer library (attributes
  which campaign an install came from). Both are free and the app currently has **no**
  analytics at all.
- **Events to implement** (names suggested; keep params generic):
  `onboarding_complete` · `first_dose_logged` (**the activation event** — a user whose
  first dose is logged within 48h of install is the one who retains) · `widget_added` ·
  `dose_logged_d3` (any log on day 3+, cheap retention proxy) · `paywall_shown` (param:
  `trigger` = celebration / limit / weekly_summary) · `purchase_initiated` ·
  purchases themselves are auto-logged by Firebase as `in_app_purchase` via Play Billing ·
  `trial_expired_offer_shown` (M4).
- ⚠ **Privacy (this app specifically):** never put medication names, dose times, or
  anything health-descriptive in event parameters — events describe app usage, not the
  user's health. Adding Firebase changes the Data Safety form (Part 7.6.2) and, for EU
  users, needs a consent story (a simple consent dialog + Google consent mode; you're
  not serving ads *in* the app, which keeps this light — confirm current EU-consent
  requirements when you wire it).
- **Know before spending:** organic install→activation %, activation→purchase %, and
  paywall conversion by trigger. Two weeks of post-launch organic data is enough.

### 8.1 Unit economics — what a €9 one-time price means

Worked example (illustrative):

```
Price (lifetime IAP):            €9.99
Google fee (15% tier):          −€1.50  → net LTV ≈ €8.49 per buyer
Install→purchase conversion:     3%     (utility apps: ~1–5%)

Revenue per INSTALL  = €8.49 × 3%      ≈ €0.25
→ break-even CPI     ≈ €0.25 (!)
→ CAC per BUYER      = CPI ÷ 3%; at €0.50 CPI that's €16.67 — a 2× loss
```

Targets that make paid UA rational here:
- **CAC per buyer ≤ €2–4** (≈ 25–50% of net LTV, leaving margin for misattribution and
  refunds). At 3% conversion that requires **CPI ≤ €0.06–0.12**, which almost no paid
  channel delivers in the EU/US — which is the honest constraint shaping everything
  below.
- Three levers widen the ceiling: (a) raise conversion (the paywall-at-celebration work,
  M3 — going 3%→6% doubles the CPI you can afford), (b) raise price (€11.99 lifetime is
  inside M2's recommended band), (c) **ship the subscription** (M2): a €1.99/mo sub at
  15% fee retaining a median ~5 months adds ≈ €8.5 net LTV per subscriber and is what
  actually makes paid channels sustainable. If serious capital is going into ads, ship
  the sub first.
- Payback for one-time purchases is immediate (no LTV curve to wait on) — which also
  means a campaign that isn't profitable *this week* never will be. That makes kill
  rules (8.4) unusually clean.

### 8.2 Channels, ranked for this app (Android-only, ADHD audience)

Apple Search Ads and anything iOS: **N/A** — Android only.

1. **Google App Campaigns (UAC)** — first paid €. Native Android install intent,
   cheapest installs, no creative burden (it assembles ads from your listing + supplied
   text/images/video). ⚠ Optimize for **in-app actions (the purchase event)**, not
   installs — install-optimized UAC famously buys low-intent volume. Caveat: action
   optimization needs conversion volume to learn (tens of purchases/month), so start
   "install volume with action focus" and graduate. Start: **€10–15/day**.
2. **Meta (IG Reels/Stories) + TikTok** — the ADHD community is enormous, engaged, and
   *actively shares* coping tools on these platforms; this is where creative-led growth
   and organic crossover (saves/shares/duets) live. Costlier installs but higher intent
   when the hook is right, and winning creatives seed organic. Note: purchase-optimized
   campaigns want the Meta/TikTok SDK or an MMP in the app — as a solo dev you can start
   install-optimized and judge by downstream Firebase numbers per campaign link instead
   of embedding more SDKs into a privacy-positioned app. Start: **€15–25/day across 4–6
   creatives, one platform at a time** (TikTok first: cheaper tests, ADHD-tok is bigger).
3. **ASO — the always-on free channel paid traffic compounds.** Every paid install's
   engagement improves ranking; conversely a weak listing wastes ad clicks (users bounce
   at the store page). Iterate title/short-description keywords ("ADHD reminder",
   "medication log", "routine", "habit"), screenshot captions, and watch Console
   store-listing conversion per acquisition channel. Free, and at this price point
   likely your best long-term ROI (see 8.6).
4. **Reddit + ADHD creators — highest audience fit, most rules-sensitive.**
   - Organic Reddit: ⚠ r/ADHD and similar subs strictly limit/ban self-promotion and
     medication talk is moderated hard — read each sub's current rules; the viable play
     is genuine participation + "I built this, mods approved this post" transparency in
     app-friendlier subs (r/ADHD_Programmers, r/adhdwomen app threads, r/androidapps),
     never link-drops. One good "I built a tool for my own ADHD" post outperforms weeks
     of ads when it's honest.
   - Reddit Ads: allowed and cheap-ish; health-related targeting is restricted — keep
     copy to the organization-tool framing (8.5).
   - **Creators/micro-influencers (10k–100k ADHD niche):** €100–500/video or
     affiliate-style promo codes. High trust transfer. ⚠ Two blockers first: the gift-code
     system currently grants *permanent* Pro from a static list shipped in the APK —
     fix E-C3 before handing codes to anyone with an audience; and Play's own promo-code
     system (Console → Promo codes) is the safer distribution channel anyway. Require
     the creator to disclose the sponsorship and to avoid medical claims (their claims
     are your policy problem on ad platforms).

### 8.3 Creative strategy — ADHD-specific hooks that convert

The moment to dramatize is universal for the audience and needs zero medical claims:

- **The panic moment:** POV, staring at a pill organizer — "Did I take it… or did I just
  *think about* taking it?" → cut to widget: "Logged, 8:14, 2h ago." One tap on-camera.
- **Time blindness:** "It's been 20 minutes" / clock shows 3 hours — then the app's
  "2h ago" ticker doing the remembering.
- **Object permanence:** "If I can't see it, it doesn't exist. So it lives on my lock
  screen now." (ambient notification demo).
- **Anchor framing:** "I don't take it at 8am. I take it *right after coffee*. Finally
  an app that gets that."
- **Non-shaming tone as the differentiator:** "I missed a day and the app… didn't yell
  at me? It said today's still open." (RSD-safe tone is rare and screenshots well.)
- Format: UGC-style selfie video > polish; show the *real UI* within 3 seconds; captions
  always on. Make **many cheap variants** (8–12 per test wave, one hook each) — creative
  is the targeting on Meta/TikTok; the algorithm finds the audience for the hook.
- ⚠ **Never claim:** treats/manages ADHD, improves symptoms/adherence/memory, replaces
  anything medical, "doctor recommended". Also avoid "you have ADHD"-style direct
  address in *paid* placements — Meta's personal-attributes policy prohibits ads that
  assert the viewer's health condition ("Built for ADHD brains" as product description
  passes where "Struggling with your ADHD meds?" gets rejected; exact enforcement
  shifts — pre-check current Meta/TikTok/Google health-ad policies rather than trusting
  this doc). Some regions restrict health-interest targeting entirely; rely on creative
  self-selection instead of targeting settings.

### 8.4 Phased budget ramp (illustrative figures — set your own, keep the ratios)

**Phase 0 — find a converting creative/channel. Budget ~€300–500, 2–3 weeks.**
TikTok or Meta: 6–10 creatives at €10–15/day total; one Google App Campaign at €10/day.
Measure per creative: CPI, install→activation (first_dose_logged ≤48h), any purchases.
Kill rules: creative dead after **€50 spend with zero activations** or CTR <0.5%;
channel dead after full phase with CAC-per-buyer >3× the €4 ceiling. Success = one
creative+channel with CPI and activation% that *project* to CAC ≤ €6.

**Phase 1 — validate CAC < LTV. Budget ~€500–1,500 over ~30 days.**
Concentrate on Phase 0 winners. Need **≥50 tracked purchases** before trusting any
CAC number. Pass gate: blended CAC per buyer ≤ ~70% of net LTV (≈ €6 at €8.49) held for
two consecutive weeks, refund rate <5%. Fail → stop paid, return to ASO/community/price
levers (8.1), re-test after the paywall/pricing changes land.

**Phase 2 — scale winners.** Increase winning campaign budgets **+30–50% per week**
(faster resets algorithmic learning), watching for CAC creep — CPI always rises with
scale; your Phase 1 margin is the runway. Refresh creatives every 2–3 weeks
(frequency >3 on Meta = fatigued). Stop-loss: any week CAC > net LTV → cut that
campaign's budget 50%; two consecutive weeks → pause channel entirely, no exceptions
(one-time purchases have no back-loaded LTV that later redeems an expensive cohort).

**Weekly dashboard (one spreadsheet row per week, per channel):**
spend · installs · CPI · activation % (first dose ≤48h) · paywall-view rate ·
install→purchase % · purchases · CAC/buyer · net revenue · **ROAS** (net rev ÷ spend) ·
refunds. Decide from the two-week trend, not single-day noise.

### 8.5 Compliance overlap with Part 7

Same rule that gets you through Play review keeps you inside ad policy: DopaTap is a
**routine/reminder/organization tool** — no diagnosis, treatment, efficacy, or outcome
claims, in ads, landing copy, creator briefs, or ASO keywords. Ad platforms
(Google/Meta/TikTok/Reddit) all restrict health/medical claims and condition-based
targeting in at least some regions, with shifting specifics — re-read each platform's
current health policy at campaign setup instead of relying on this document. If an ad is
rejected for health policy, the fix is almost always removing second-person condition
language, not appealing.

### 8.6 Honest caveats (read before spending)

- **The math in 8.1 is the message:** at €9 one-time, most paid channels lose money at
  realistic CPI/conversion. Paid UA becomes rational *after* the subscription (M2) or a
  higher lifetime price widens LTV, and after M3's paywall placement lifts conversion —
  which is why Part 6's R8 precedes serious ad spend.
- **Organic will likely out-ROI paid at this price point:** ASO + honest community
  presence + creator gifting compounds and costs mostly time. The right way to think
  about the ad budget: Phase 0/1 is a **measurement instrument** (what's a user worth,
  which hook converts) — knowledge that then supercharges the free channels — not a
  growth engine yet.
- The ADHD audience is unusually word-of-mouth-driven and unusually allergic to feeling
  marketed at (RSD again). The product decisions in Parts 3/5 (forgiveness, no dark
  patterns, one-tap value before any paywall) *are* the growth strategy; ads only
  amplify what retention has already proven.

---

## Part 9 — Bottom line

The app is ADHD-native and well-engineered — it needs **refinement, not a rebuild**.
Order of operations: **(1) Build** — ship R1–R5 of Part 6 first: they simultaneously stop
revenue leaks, fix the two worst daily-usefulness failures (ignored reminder,
broken-streak spiral), and deliver the escalation feature the paywall already charges
for. **(2) Ship** — run the Part 7 checklist; start the Play account + merchant/tax setup
on day one (longest lead time), fix the exact-alarm permission before first upload, and
budget ~5–6 weeks to a staged production rollout. **(3) Monetize & grow** — reprice per
M2, place the paywall per M3, then and only then put ad money through Part 8's gate:
instrument the funnel, validate CAC against a €9 LTV honestly, and let ASO + the ADHD
community's word-of-mouth do the heavy lifting while paid spend stays a measurement tool
until the subscription widens the ceiling.
