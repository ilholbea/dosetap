package com.dosetap.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Mirrors an alarm that has been scheduled in the Android AlarmManager.
 *
 * The system drops alarms on reboot, app force-stop, and app update.
 * This entity is the source of truth used by [com.dosetap.alarm.BootReceiver]
 * to reschedule all pending alarms after such events.
 */
@Entity(
    tableName = "scheduled_alarms",
    indices = [
        Index(value = ["tapId"]),
        Index(value = ["triggerAtMillis"]),
    ]
)
data class ScheduledAlarm(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** The [DoseEvent.sourceTapId] this alarm belongs to. */
    val tapId: Long,
    /** The [Medication.id] this alarm is for. */
    val medicationId: Long,
    /** Index within the follow-up list (0-based). Used to reconstruct unique PendingIntent request codes. */
    val followUpIndex: Int,
    /** Absolute UTC millis when this alarm should fire. */
    val triggerAtMillis: Long,
    /** Whether this alarm has already fired or been cancelled. */
    val isCancelled: Boolean = false,
)
