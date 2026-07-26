# DoseTap — App Icon Brief

For a Fiverr or independent designer hand-off. Budget €30 per PLAN.md §10. The current `ic_launcher_*.xml` assets are placeholders — replace before Play submission.

## Concept

A **pill silhouette overlapping a clock face / checkmark hybrid**. The mark should read as "medication + time" at any size, including the launcher 48dp.

Avoid:
- Generic capsule-and-bell mashups (overused in the category).
- Brain motifs (cliché for ADHD apps, distracts from the actual product).
- 🚀 rocket or "boost" imagery — stimulant slang, breaks PLAN Hard Rules.
- Anything that looks like a controlled substance or scheduling-disclosure issue (no Rx-paper, no "Adderall" word marks).

## Format requirements

- **Adaptive icon**: foreground PNG/SVG at 108dp × 108dp (the safe zone is the center 66dp circle), background as a solid colour or simple gradient at 108dp × 108dp.
- **Legacy launcher icon**: 512 × 512 PNG (Play Store listing).
- **Density buckets**: mdpi (48), hdpi (72), xhdpi (96), xxhdpi (144), xxxhdpi (192) — designer can export from a single 512×512 master.
- **Maskable**: must look correct under both circular and squircle masks (test in the Android Studio asset preview).

## Palette

Pull from the app's Material 3 primary in `ui/theme/Color.kt`. Approximately:
- Primary: soft blue/teal — readable in both light and dark mode launchers
- Background: warm off-white OR the primary container colour at low saturation
- Accent (checkmark / clock hand): the primary at full saturation

A designer with Material 3 fluency will know to test both `Theme.Light` and `Theme.Dark` launcher previews.

## Acceptance criteria

- Recognisable at 48dp on a busy home screen (squint test).
- No text inside the icon (Play rejects icons containing the app name).
- Foreground respects the 66dp safe zone — no critical detail in the outer 21dp ring.
- Delivered as: SVG master + PNGs at all density buckets + 512×512 Play Store icon.

## Source files

If working with the existing placeholder XML drawables, the designer should replace:
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/drawable/ic_launcher_background.xml` (or the colour resource it points to)
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` (adaptive icon manifest — usually unchanged)
- All `mipmap-*/ic_launcher.png` and `ic_launcher_round.png` density buckets

Hand-off package should include the SVG master so future updates can re-export at new densities without losing fidelity.
