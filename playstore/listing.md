# DoseTap — Play Store Listing Copy

Drafted per PLAN.md v3 §11. Source of truth — paste into Play Console at submission time.

## App details

- **App name** (max 30 chars): `DoseTap — ADHD Med Reminder`
- **Package** / app ID: `com.dosetap`
- **Category**: Medical (not Health & Fitness — Medical signals seriousness and qualifies the app for the USE_EXACT_ALARM exception)
- **Default language**: English (United States) — `en-US`
- **Content rating**: Everyone
- **Tags / keywords** (for search): ADHD, stimulant, Ritalin, Concerta, Adderall, Vyvanse, medication reminder, dose tracker, pill tracker, ADHD reminder

## Short description (max 80 chars)

```
One-tap meds for ADHD. No account, no ads, your data stays on your phone.
```

## Long description (Google Play long copy, ~4000 chars budget)

```
Made by an ADHD developer for ADHD users. DoseTap is a reminder, not a tracker.
Its single job: be the first thing you see in the morning, with your dose one tap away.

No accounts. No ads. No analytics. Your medication history never leaves your phone.

━━━━━━━━━━━━━━━━━━━━━━
DAILY
━━━━━━━━━━━━━━━━━━━━━━

• Persistent lock-screen notification — tap once to log, no unlock needed
• Home-screen widget with the same one-tap action
• Habit anchor — tie your dose to coffee, brushing teeth, or whatever you do every morning ("right after ☕ coffee")
• Time since your last dose, plus when your next one is available
• Phantom-tap protection (press-and-hold 250ms) + 8-second undo
• Skip tomorrow with one long-press

━━━━━━━━━━━━━━━━━━━━━━
SAFETY
━━━━━━━━━━━━━━━━━━━━━━

• Pill-count tracking with refill warning (always free — running out of a controlled stimulant is genuinely harmful)
• Late-cutoff warning ("taking this after 4pm often disrupts sleep")
• Comedown / wear-off warning so you can plan
• Off-days for Mon–Fri prescriptions
• Double-dose protection — warns, never blocks (you decide)

━━━━━━━━━━━━━━━━━━━━━━
INSIGHTS (PRO)
━━━━━━━━━━━━━━━━━━━━━━

One-time €2.99. No subscription, ever.

• Doctor visit report — print-ready PDF with adherence, weekday/weekend split, and time-of-first-dose patterns to bring to your next appointment
• 90-day adherence heatmap — the picture you show your psychiatrist
• Sponsor a friend — Pro you buy includes a Pro you give. One free unlock to share, lifetime
• Smart escalation reminders (+10 / +25 min nudges)
• Prescription refill prediction — know when to book your psych appointment
• Up to 4 follow-up reminders (free includes 2)
• Custom alarm tone

━━━━━━━━━━━━━━━━━━━━━━
PRIVACY
━━━━━━━━━━━━━━━━━━━━━━

DoseTap collects nothing. No analytics SDK, no crash reporting, no advertising IDs. Your dose history is stored locally on your device, period.

Data portability via in-app JSON export (GDPR Article 20).

━━━━━━━━━━━━━━━━━━━━━━
NOT A MEDICAL DEVICE
━━━━━━━━━━━━━━━━━━━━━━

DoseTap is a reminder tool. It does not provide medical advice and is not a substitute for consulting your doctor. The app never suggests doses you weren't prescribed and never frames a missed dose as harm.
```

## Data Safety form answers

- **Does your app collect or share any of the required user data types?** No.
- **Is all of the user data collected by your app encrypted in transit?** N/A — no data is collected.
- **Do you provide a way for users to request that their data be deleted?** Yes — all data is local; uninstalling the app deletes everything. Settings → Delete all data also wipes within the app.
- **Has your app been independently validated against a global security standard?** No.

## In-app product

- **SKU**: `dosetap_pro`
- **Type**: Managed product (one-time)
- **Price**: €2.99 (Play auto-handles regional pricing; do not enable subscription tier)
- **Title**: DoseTap Pro
- **Description**: One-time unlock for Pro features.

## Privacy policy URL

To be hosted at `https://<github-username>.github.io/dosetap-privacy` (or equivalent GH Pages). Source: `PRIVACY_POLICY.md` in this repo. Confirm hosting before submission and paste the final URL into Play Console + `strings.xml` (`privacy_url`).

## Contact

- Developer name: (to set in Play Console)
- Email: (must be a working address — Play sends developer notifications here)
- Website: (optional, GH Pages docs site works)

## Submission checklist

- [ ] `versionCode` bumped from 1 if shipping any subsequent build
- [ ] `versionName` matches the user-visible "1.0.0"
- [ ] Release signing key generated and stored OUTSIDE the repo (do NOT commit keystore)
- [ ] `./gradlew assembleRelease` succeeds, APK < 10MB
- [ ] Internal test track verifies upgrade path from older build (if any)
- [ ] Privacy policy URL live and reachable
- [ ] Data Safety form submitted
- [ ] Content rating questionnaire completed
- [ ] 5 phone screenshots uploaded (see `screenshots.md`)
- [ ] 1024×500 feature graphic uploaded
- [ ] 512×512 app icon uploaded
- [ ] `dosetap_pro` SKU created and active
- [ ] At least one internal tester account added
