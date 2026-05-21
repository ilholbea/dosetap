package com.dosetap.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Records a single dose-related event (tap, alarm action, snooze, etc.).
 *
 * Events are grouped by [sourceTapId]: every initial widget tap creates a new group,
 * and all follow-up alarms spawned from that tap share the same [sourceTapId].
 */
@Entity(
    tableName = "dose_events",
    indices = [
        Index(value = ["medicationId"]),
        Index(value = ["actionedAt"]),
        Index(value = ["sourceTapId"]),
    ]
)
data class DoseEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** FK to [Medication.id]. */
    val medicationId: Long,
    /** When this alarm was originally scheduled (UTC millis). Null for INITIAL_TAP events. */
    val scheduledAt: Long? = null,
    /** When the alarm actually fired (UTC millis). Null for INITIAL_TAP and user-initiated events. */
    val firedAt: Long? = null,
    /** When the user performed an action (or when the event was created). UTC millis. */
    val actionedAt: Long,
    /** The action taken by the user or system. */
    val action: DoseAction,
    /**
     * Groups the initial tap and all its follow-ups together.
     * Set to the [DoseEvent.id] of the INITIAL_TAP event.
     * For INITIAL_TAP itself this is set after insert (updated to its own id).
     */
    val sourceTapId: Long,
)

/** The possible outcomes for a dose event. */
enum class DoseAction {
    /** User tapped the widget to log the first dose. */
    INITIAL_TAP,
    /** User confirmed they took the medication. */
    TAKEN,
    /** User snoozed the alarm for 15 minutes. */
    SNOOZED,
    /** User explicitly skipped this dose. */
    SKIPPED,
    /** Alarm fired but user never interacted within the next window. */
    MISSED,
}
