package com.dosetap.domain.usecase

import android.util.Log
import com.dosetap.alarm.AlarmScheduler
import com.dosetap.data.db.DoseEventDao
import com.dosetap.data.db.MedicationDao
import com.dosetap.domain.model.DoseAction
import com.dosetap.domain.model.DoseEvent

private const val TAG = "LogDoseUseCase"

/**
 * Records an initial dose tap and schedules the follow-up alarms.
 *
 * This is the primary action triggered when a user taps the widget or the
 * "Log Dose Now" button on the home screen.
 *
 * Steps:
 * 1. Insert an INITIAL_TAP [DoseEvent] to get its DB-assigned ID.
 * 2. Update the event so [DoseEvent.sourceTapId] = its own ID (groups follow-ups).
 * 3. Schedule follow-up alarms via [AlarmScheduler].
 *
 * @return The [DoseEvent.id] of the initial tap, or -1 on failure.
 */
class LogDoseUseCase(
    private val doseEventDao: DoseEventDao,
    private val alarmScheduler: AlarmScheduler,
    private val medicationDao: MedicationDao,
) {

    /**
     * Logs a dose for [medicationId] at [tapTimeMillis] and schedules follow-up alarms.
     *
     * @param medicationId ID of the medication being taken.
     * @param tapTimeMillis UTC millis of the dose (defaults to now).
     * @return The [DoseEvent.id] of the tap event, or -1 if the medication wasn't found.
     * @throws Exception if the DB write fails (callers should surface this to the user).
     */
    suspend fun execute(
        medicationId: Long,
        tapTimeMillis: Long = System.currentTimeMillis(),
    ): Long {
        val medication = medicationDao.getById(medicationId)
        if (medication == null) {
            Log.e(TAG, "Medication $medicationId not found — cannot log dose")
            return -1L
        }

        // Insert initial tap event with sourceTapId = 0 (placeholder, updated next)
        val tapEventId = doseEventDao.insert(
            DoseEvent(
                medicationId = medicationId,
                scheduledAt = null, // initial taps are not scheduled
                firedAt = null,
                actionedAt = tapTimeMillis,
                action = DoseAction.INITIAL_TAP,
                sourceTapId = 0L, // will be updated to its own ID
            )
        )

        // Update sourceTapId to point to itself (groups follow-ups under this tap)
        val event = doseEventDao.getById(tapEventId)
        if (event != null) {
            doseEventDao.update(event.copy(sourceTapId = tapEventId))
        }

        // Schedule follow-up alarms based on medication intervals
        alarmScheduler.scheduleFollowUps(
            tapId = tapEventId,
            medication = medication,
            baseTimeMillis = tapTimeMillis,
        )

        // Decrement pill count if tracking is enabled
        if (medication.pillCount != null && medication.pillCount > 0) {
            medicationDao.update(medication.copy(pillCount = medication.pillCount - 1))
        }

        // Schedule wear-off warning (free, safety-adjacent)
        alarmScheduler.scheduleWearOffWarning(tapEventId, medication, tapTimeMillis)

        // A dose from any surface cancels any in-flight escalation re-nudges (Spec #2).
        alarmScheduler.cancelEscalation(medicationId)

        Log.i(TAG, "Dose logged: tapId=$tapEventId medicationId=$medicationId at $tapTimeMillis")
        return tapEventId
    }

    /**
     * Undoes the most recent dose tap by cancelling its follow-up alarms
     * and deleting the INITIAL_TAP event.
     *
     * Only valid within the undo window ([com.dosetap.util.Constants.WIDGET_UNDO_WINDOW_MILLIS]).
     *
     * @param tapId The [DoseEvent.id] to undo.
     */
    suspend fun undo(tapId: Long) {
        alarmScheduler.cancelFollowUps(tapId)
        alarmScheduler.cancelWearOffWarning(tapId)
        doseEventDao.deleteById(tapId)
        Log.i(TAG, "Dose undone: tapId=$tapId")
    }
}
