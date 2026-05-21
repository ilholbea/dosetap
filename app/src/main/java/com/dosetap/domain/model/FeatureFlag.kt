package com.dosetap.domain.model

/**
 * Feature flags for Pro unlock gating.
 *
 * In MVP all features are unlocked (proUnlocked = true).
 * When billing is added, set proUnlocked based on purchase state.
 * All Pro-gated features should check this object before enabling.
 */
object FeatureFlag {
    /**
     * Whether the user has unlocked Pro features.
     * MVP: always true (all features unlocked, billing not yet implemented).
     */
    val proUnlocked: Boolean = true

    /** Max number of medications (Pro: unlimited, Free: 1). */
    val maxMedications: Int get() = if (proUnlocked) Int.MAX_VALUE else 1

    /** Max follow-ups per medication (Pro: 4, Free: 2). */
    val maxFollowUps: Int get() = if (proUnlocked) 4 else 2

    /** Whether unlimited widgets are allowed. */
    val unlimitedWidgets: Boolean get() = proUnlocked
}
