package com.dosetap.domain.model

/**
 * Common ADHD medication presets used in onboarding to pre-fill name, duration,
 * and reasonable follow-up reminder offsets. Non-stimulant entries (atomoxetine,
 * bupropion, guanfacine) have no acute effect window — the effect-window UI
 * should be hidden for these.
 */
data class MedicationPreset(
    val label: String,
    /** Total active duration in minutes; null = no acute window (non-stimulant). */
    val durationMinutes: Int?,
    /** Default follow-up reminder offsets in minutes from the initial tap. */
    val followUps: List<Int>,
    /** Whether this medication has a meaningful effect/wear-off window. */
    val hasEffectWindow: Boolean,
) {
    companion object {
        val ALL: List<MedicationPreset> = listOf(
            MedicationPreset("Ritalin IR / methylphenidate IR", 240, listOf(180, 360), true),
            MedicationPreset("Ritalin LA / Medikinet CR / Equasym XL", 480, listOf(240, 420), true),
            MedicationPreset("Concerta", 720, listOf(360, 600), true),
            MedicationPreset("Adderall IR", 300, listOf(180, 360), true),
            MedicationPreset("Adderall XR", 480, listOf(240, 420), true),
            MedicationPreset("Vyvanse / Elvanse", 720, listOf(360, 600), true),
            MedicationPreset("Strattera / atomoxetine", null, listOf(720), false),
            MedicationPreset("Wellbutrin / bupropion", null, listOf(720), false),
            MedicationPreset("Guanfacine / Intuniv", null, listOf(720), false),
        )
    }
}
