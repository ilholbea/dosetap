package com.dosetap.domain

import com.dosetap.domain.model.Medication
import com.dosetap.util.TimeFormatter

/**
 * Shift-aware dose timing rules shared by the Home screen, widget, ambient notification and
 * daily reminder.
 *
 * "Taken today" can't simply mean "since midnight": a night-shift user who doses at 20:00 is
 * still inside that dose's cycle at 03:00, and treating them as "not logged" invites a double
 * dose. A dose is therefore *current* while its cycle is running, or on the same calendar day
 * as long as it isn't nearly a full day old.
 */
object DoseTiming {

    /** Shortest cycle — covers a night shift even for meds with no duration/follow-ups set. */
    private const val MIN_CYCLE_MINUTES = 12 * 60

    /** A same-calendar-day dose older than this is treated as yesterday's cycle (dose at 00:30, next at 23:30). */
    private const val SAME_DAY_MAX_AGE_MILLIS = 18 * 60 * 60_000L

    /** Fallback double-dose window when the medication has no known duration. */
    private const val DEFAULT_DOUBLE_DOSE_WINDOW_MILLIS = 3 * 60 * 60_000L

    /** How long a dose stays "current": the longer of its effect window, its last follow-up, or 12h. */
    fun cycleMillis(medication: Medication): Long {
        val minutes = maxOf(
            medication.durationMinutes ?: 0,
            medication.followUps.maxOrNull() ?: 0,
            MIN_CYCLE_MINUTES,
        )
        return minutes * 60_000L
    }

    /** True when a dose taken at [doseMillis] still counts as the current dose at [nowMillis]. */
    fun isCurrent(doseMillis: Long, medication: Medication, nowMillis: Long): Boolean {
        val age = nowMillis - doseMillis
        if (age < 0) return true
        if (age < cycleMillis(medication)) return true
        return doseMillis >= TimeFormatter.startOfDay(nowMillis) && age < SAME_DAY_MAX_AGE_MILLIS
    }

    /**
     * True when logging a dose at [tapTimeMillis] could double up on a confirmed dose at
     * [lastDoseMillis] — i.e. it falls inside that dose's effect window (or 3h when unknown).
     * Symmetric, so backdating a dose to just before an existing one is caught too.
     */
    fun isPossibleDoubleDose(medication: Medication, lastDoseMillis: Long?, tapTimeMillis: Long): Boolean {
        if (lastDoseMillis == null) return false
        val window = medication.durationMinutes?.let { it * 60_000L } ?: DEFAULT_DOUBLE_DOSE_WINDOW_MILLIS
        return kotlin.math.abs(tapTimeMillis - lastDoseMillis) < window
    }
}
