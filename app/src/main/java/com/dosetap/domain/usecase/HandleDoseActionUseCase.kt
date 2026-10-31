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
 *
 * Single source of truth for both surfaces — the notification buttons ([com.dosetap.alarm.DoseActionReceiver])
 * and the full-screen [com.dosetap.AlarmActivity]. They used to diverge: the notification path
 * cancelled every remaining follow-up on Taken/Skip, so later reminders silently never came when
 * the user answered from the notification but did when they answered from the alarm screen.
 *
 * Taken / Skip answer **this** reminder only. The rest of the follow-up chain stays armed (TESTING.md
 * Test 1: tap Taken on the +4h alarm, the +8h alarm still fires). Use "Stop today" to end a chain.
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

        // Any response to the reminder cancels the escalation re-nudge chain (Spec #2).
        // For SNOOZED the snoozed follow-up re-arms a fresh chain when it fires.
        alarmScheduler.cancelEscalation(event.medicationId)

        val tapId = event.sourceTapId
        val medication = medicationDao.getById(event.medicationId)
        when (action) {
            DoseAction.SNOOZED -> {
                // Test alarms and daily reminders carry tapId = -1 and have no chain to re-ping.
                if (medication != null && tapId != -1L) {
                    val snoozeTriggerAt = nowMillis + Constants.SNOOZE_DURATION_MILLIS
                    // Re-ping under the original tap so the remaining follow-up chain survives (E-H1).
                    alarmScheduler.scheduleSnooze(
                        tapId = tapId,
                        medication = medication,
                        triggerAtMillis = snoozeTriggerAt,
                    )
                    Log.d(TAG, "Snooze alarm scheduled at $snoozeTriggerAt")
                } else {
                    Log.w(TAG, "No snooze scheduled (medication=${medication?.id} tapId=$tapId)")
                }
            }
            DoseAction.TAKEN, DoseAction.SKIPPED -> {
                // Answered — a pending snooze re-ping for this tap is now redundant.
                if (tapId != -1L) alarmScheduler.cancelSnooze(tapId)
                // A confirmed dose restarts the effect window, so move the wear-off warning to it.
                if (action == DoseAction.TAKEN && medication != null && tapId != -1L) {
                    alarmScheduler.scheduleWearOffWarning(tapId, medication, nowMillis)
                }
            }
            else -> Unit
        }

        return true
    }
}
