package com.dosetap.domain.model

/**
 * Runtime feature flags for Pro gating.
 *
 * Pro is unlocked via either:
 *  - Google Play purchase ([com.dosetap.billing.BillingManager])
 *  - A gift code redeemed through [com.dosetap.billing.GiftCodeManager]
 *
 * ViewModels and screens observe [com.dosetap.billing.BillingManager.isPro] directly.
 * This object provides computed caps used throughout the codebase.
 */
object FeatureFlag {
    /** Max follow-ups per medication (Pro: 4, Free: 2). */
    fun maxFollowUps(isPro: Boolean) = if (isPro) 4 else 2

    /** Max medications (Pro: unlimited, Free: 1). */
    fun maxMedications(isPro: Boolean) = if (isPro) Int.MAX_VALUE else 1

    // Features gated behind Pro
    fun canUseOffDays(isPro: Boolean) = isPro
    fun canUseDailyReminder(isPro: Boolean) = isPro
    fun canUseEffectWindow(isPro: Boolean) = isPro
    fun canUsePillCount(isPro: Boolean) = isPro
    fun canUseCustomAlarmTone(isPro: Boolean) = isPro
    fun canUseExportImport(isPro: Boolean) = isPro
    fun canUseWeeklySummary(isPro: Boolean) = isPro
    fun canUseFullStatsChart(isPro: Boolean) = isPro
    fun canUseLateCutoff(isPro: Boolean) = isPro

    // Always free
    // - Core dose logging
    // - Up to 2 follow-up reminders
    // - Full 30-day history
    // - Widget
    // - Undo + backdate (forgetfulness is core ADHD symptom — never lock this)
    // - Basic streak + adherence %
    // - Double-dose protection (safety — never lock this)
}
