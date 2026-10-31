package com.dosetap.domain.usecase

import android.util.Log
import com.dosetap.alarm.AlarmScheduler
import com.dosetap.data.db.DoseEventDao
import com.dosetap.data.db.MedicationDao
import com.dosetap.domain.DoseTiming
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

        // Only take a pill off when tracking is on and there's one to take — recorded on the event
        // so undo restores exactly what was removed.
        val decrementPill = medication.pillCount != null && medication.pillCount > 0

        // Insert initial tap event with sourceTapId = 0 (placeholder, updated next)
        val tapEventId = doseEventDao.insert(
            DoseEvent(
                medicationId = medicationId,
                scheduledAt = null, // initial taps are not scheduled
                firedAt = null,
                actionedAt = tapTimeMillis,
                action = DoseAction.INITIAL_TAP,
                sourceTapId = 0L, // will be updated to its own ID
                pillDecremented = decrementPill,
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

        if (decrementPill) {
            medication.pillCount?.let { medicationDao.update(medication.copy(pillCount = it - 1)) }
        }

        // Schedule wear-off warning (free, safety-adjacent)
        alarmScheduler.scheduleWearOffWarning(tapEventId, medication, tapTimeMillis)

        // A dose from any surface cancels any in-flight escalation re-nudges (Spec #2).
        alarmScheduler.cancelEscalation(medicationId)

        Log.i(TAG, "Dose logged: tapId=$tapEventId medicationId=$medicationId at $tapTimeMillis")
        return tapEventId
    }

    /**
     * Returns the confirmed dose that logging [medicationId] at [tapTimeMillis] would double up on,
     * or null when it's safe. One-tap surfaces (widget, lock-screen notification) can't show the
     * Home screen's confirm dialog, so they call this and refuse instead of silently double-logging.
     */
    suspend fun findDoubleDoseConflict(medicationId: Long, tapTimeMillis: Long): DoseEvent? {
        val medication = medicationDao.getById(medicationId) ?: return null
        val last = doseEventDao.getLastConfirmedDose(medicationId) ?: return null
        return last.takeIf { DoseTiming.isPossibleDoubleDose(medication, it.actionedAt, tapTimeMillis) }
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
        val event = doseEventDao.getById(tapId)
        alarmScheduler.cancelFollowUps(tapId)
        alarmScheduler.cancelWearOffWarning(tapId)
        doseEventDao.deleteById(tapId)

        // Give back the pill only if [execute] actually took one off (not when logged at a count of 0).
        if (event != null && event.pillDecremented) {
            val medication = medicationDao.getById(event.medicationId)
            if (medication?.pillCount != null) {
                medicationDao.update(medication.copy(pillCount = medication.pillCount + 1))
            }
        }
        Log.i(TAG, "Dose undone: tapId=$tapId")
    }
}
