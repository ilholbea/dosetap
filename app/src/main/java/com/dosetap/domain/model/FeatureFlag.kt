package com.dosetap.domain.model

object FeatureFlag {
    /** Max follow-ups per medication (Pro: 4, Free: 2). */
    fun maxFollowUps(isPro: Boolean) = if (isPro) 4 else 2

    /** Max medications (Pro: unlimited, Free: 1). */
    fun maxMedications(isPro: Boolean) = if (isPro) Int.MAX_VALUE else 1

    // ===== Always FREE (safety / data-rights / core function) =====
    // Off-days: Mon–Fri prescriptions are the ADHD default; false missed warnings → uninstall.
    fun canUseOffDays(@Suppress("UNUSED_PARAMETER") isPro: Boolean) = true
    // Daily proactive reminder: core function of a reminder app, never lock it.
    fun canUseDailyReminder(@Suppress("UNUSED_PARAMETER") isPro: Boolean) = true
    // Effect window: "when does my med wear off?" is a safety question, never paywalled.
    fun canUseEffectWindow(@Suppress("UNUSED_PARAMETER") isPro: Boolean) = true
    // Pill count: running out of a controlled stimulant is harmful. Free.
    fun canUsePillCount(@Suppress("UNUSED_PARAMETER") isPro: Boolean) = true
    // Export/import: GDPR Article 20 data portability. Free.
    fun canUseExportImport(@Suppress("UNUSED_PARAMETER") isPro: Boolean) = true
    // Late cutoff warning: sleep safety feature, same logic as double-dose protection.
    fun canUseLateCutoff(@Suppress("UNUSED_PARAMETER") isPro: Boolean) = true
    // Comedown warning: safety-adjacent, helps users plan for wear-off.
    fun canUseComedownWarning(@Suppress("UNUSED_PARAMETER") isPro: Boolean) = true

    // ===== Pro only =====
    fun canUseCustomAlarmTone(isPro: Boolean) = isPro
    fun canUseFullStatsChart(isPro: Boolean) = isPro
    fun canUseDoctorReport(isPro: Boolean) = isPro
    fun canUseSmartEscalation(isPro: Boolean) = isPro
    fun canUseSponsorFriend(isPro: Boolean) = isPro
    fun canUsePrescriptionRefillPrediction(isPro: Boolean) = isPro

    // ===== Removed (was Pro, now cut) =====
    // canUseWeeklySummary — replaced by Doctor Report

    // ===== Always free =====
    // - Core dose logging
    // - Up to 2 follow-up reminders
    // - Full 30-day history
    // - Widget
    // - Undo + backdate (forgetfulness is core ADHD symptom — never lock this)
    // - Basic streak + adherence %
    // - Double-dose protection (safety — never lock this)
    // - Skip tomorrow, dose notes, comedown warning
}
