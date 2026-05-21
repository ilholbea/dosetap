package com.dosetap.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a medication with scheduled follow-up reminders.
 *
 * The [followUps] list contains offset minutes from the initial tap.
 * For example, [240, 480] means +4h and +8h follow-ups.
 *
 * MVP supports a single medication. Data model is ready for multiple medications (Pro).
 */
@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** Human-readable name, e.g. "Adderall XR 20mg" */
    val name: String,
    /**
     * Minutes from initial tap for each follow-up alarm.
     * Range per item: 30–720 minutes (30 min to 12h), in 15-minute steps.
     * Max 4 follow-ups.
     */
    val followUps: List<Int>,
    /** URI string for a custom alarm sound, null = default alarm sound. */
    val soundUri: String? = null,
    /** Minutes from midnight for the proactive daily reminder, null = disabled. e.g. 540 = 09:00. */
    val dailyReminderMinutes: Int? = null,
    /** Minutes from midnight after which it's too late to take this medication (insomnia risk). null = no cutoff. */
    val lateCutoffMinutes: Int? = null,
    /** Current pill count for refill tracking, null = not tracking. */
    val pillCount: Int? = null,
    /** Show refill warning when pillCount drops to this number or below. */
    val refillAlertAt: Int = 7,
    /**
     * Calendar.DAY_OF_WEEK values (1=Sunday … 7=Saturday) on which reminders are suppressed.
     * Daily reminders and the dose button off-day warning use this list.
     */
    val offDays: List<Int> = emptyList(),
    /** How long this medication stays active in minutes, e.g. 240 for a 4-hour IR tablet. null = unknown. */
    val durationMinutes: Int? = null,
    /** Unix epoch millis when this medication was created. */
    val createdAt: Long = System.currentTimeMillis(),
)
