package com.dosetap.domain.usecase

import android.util.Log
import com.dosetap.alarm.AlarmScheduler
import com.dosetap.data.db.DoseEventDao
import com.dosetap.data.db.MedicationDao
import com.dosetap.domain.model.DoseAction
import com.dosetap.util.Constants

private const val TAG = "HandleDoseActionUseCase"

/**
 * Handles user responses to medication alarm notifications (Taken / Snooze / Skip).
 *
 * Updates the [com.dosetap.domain.model.DoseEvent] originally written as MISSED by [com.dosetap.alarm.AlarmReceiver],
 * and for snooze: reschedules a new alarm.
 */
class HandleDoseActionUseCase(
    private val doseEventDao: DoseEventDao,
    private val alarmScheduler: AlarmScheduler,
    private val medicationDao: MedicationDao,
) {

    /**
     * Marks a dose event as [action] (TAKEN, SNOOZED, or SKIPPED).
     *
     * For SNOOZED: a new alarm is scheduled [Constants.SNOOZE_DURATION_MILLIS] from now.
     *
     * @param doseEventId ID of the [com.dosetap.domain.model.DoseEvent] to update.
     * @param action The action the user took.
     * @param nowMillis Current time (injectable for testing).
     * @return true if the event was found and updated, false otherwise.
     */
    suspend fun execute(
        doseEventId: Long,
        action: DoseAction,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val event = doseEventDao.getById(doseEventId)
        if (event == null) {
            Log.e(TAG, "DoseEvent $doseEventId not found — cannot handle action $action")
            return false
        }

        doseEventDao.update(event.copy(action = action, actionedAt = nowMillis))
        Log.i(TAG, "DoseEvent $doseEventId updated to $action")

        if (action == DoseAction.SNOOZED) {
            val medication = medicationDao.getById(event.medicationId)
            if (medication != null) {
                val snoozeTriggerAt = nowMillis + Constants.SNOOZE_DURATION_MILLIS
                // Unique tapId for snooze to avoid colliding with original tap's request codes
                val snoozeTapId = event.sourceTapId * 1000L + (nowMillis % 1000L)
                alarmScheduler.scheduleFollowUps(
                    tapId = snoozeTapId,
                    medication = medication.copy(followUps = listOf(0)),
                    baseTimeMillis = snoozeTriggerAt,
                )
                Log.d(TAG, "Snooze alarm scheduled at $snoozeTriggerAt")
            } else {
                Log.w(TAG, "Medication ${event.medicationId} not found for snooze scheduling")
            }
        }

        return true
    }
}
