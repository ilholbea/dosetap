# DoseTap — Play Store Screenshot Specs

Play Store requires a minimum of 2 phone screenshots. We ship 5 — five converts better than two on medical apps. Render at 1080×1920 portrait minimum, JPEG or PNG, ≤8MB each.

## Scene order

Order matters — the first screenshot is what most users see in search results.

### 1 — Lock-screen Anchor (lead)
**Subject:** A real lock-screen showing the persistent DoseTap notification with the medication name, anchor cue ("right after ☕ coffee"), and a "Log dose" action button.
**Overlay copy (top):** "Your morning anchor — one tap from the lock screen."
**Why first:** This is the single highest-leverage feature in the app. It also signals "this isn't another tracker."

### 2 — Home screen with status chip
**Subject:** Home screen showing time-since-last-dose ("Taken 2h 14m ago"), next-dose-available chip, and the 💊 button.
**Overlay copy (top):** "Time-since-dose, next dose, all at a glance."
**Why:** Demonstrates the core daily-use surface. Reassures that the app answers "is it safe to take one now?".

### 3 — Dose-logged confirmation + undo banner
**Subject:** Post-tap state: confetti-free dose-logged toast/card with the 8-second undo banner visible.
**Overlay copy (top):** "Phantom-tap protection + 8-second undo."
**Why:** ADHD-specific feature, addresses the #1 fear ("did I tap by accident?").

### 4 — 90-day heatmap (Pro teaser)
**Subject:** Stats screen with the GitHub-style 13×7 heatmap, green/amber/red squares filling in.
**Overlay copy (top):** "The picture you show your psychiatrist."
**Footer chip:** "Pro" badge in corner.

### 5 — Doctor Report preview (Pro teaser)
**Subject:** Doctor Report screen showing the Adherence card with weekday/weekend split + time-of-first-dose card.
**Overlay copy (top):** "Print-ready PDF for your next appointment."
**Footer chip:** "Pro" badge in corner.

## Feature graphic (required)

- **Dimensions:** 1024 × 500 px, PNG or JPEG, ≤1MB
- **Layout:** App icon on the left (~30% width), tagline on the right
- **Tagline:** "ADHD meds — one tap, no cloud."
- **Sub-tagline (smaller):** "Made by an ADHD developer."
- **Palette:** Use the Material 3 primary from the app theme (see `ui/theme/Color.kt`) on a near-white background. No screenshots — feature graphic is brand, not product.
- **Typography:** Sans-serif, bold for the tagline, regular for the sub. Avoid gimmicky display fonts.

## Screenshots NOT to include

- No "subscribe" or "trial" callouts on screenshots — paywall framing in search converts poorly for medical utilities.
- No mock pill-shaped icons or stock images of stressed students. Show the actual product.
- No streak counters as the focal point of a screenshot (per Hard Rule 2 — streaks are quiet, never a trophy).
- No before/after "I missed 5 doses" framing — shame copy is a harm vector.

## Rendering workflow

1. Build a debug or staging APK with sample data populated (medication, 30 days of dose events with realistic times, a few notes/tags).
2. Use Android Studio's "Screen Capture" on a Pixel 6 emulator (1080×2400) — gives a clean Android 14 look.
3. Composite the overlay text in Figma / Affinity / GIMP at 1080×2280 (leaving ~120px top + 240px bottom for overlays).
4. Export as PNG (lossless preferred for crisp text).
