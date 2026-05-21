package com.dosetap.domain.usecase

import android.util.Log
import com.dosetap.alarm.AlarmScheduler
import com.dosetap.data.db.MedicationDao

private const val TAG = "ScheduleFollowUpsUseCase"

/**
 * Re-schedules follow-up alarms when a user changes medication intervals
 * while alarms are already pending (§9 edge case 12).
 *
 * Cancels pending alarms for the current cycle and reschedules with new intervals
 * from the original tap time.
 */
class ScheduleFollowUpsUseCase(
    private val alarmScheduler: AlarmScheduler,
    private val medicationDao: MedicationDao,
) {

    /**
     * Reschedules follow-up alarms for [tapId] using the current medication intervals.
     *
     * Should be called after medication intervals are changed in Settings while
     * a tap cycle is in progress.
     *
     * @param tapId The source tap ID whose follow-ups need rescheduling.
     * @param medicationId The medication whose updated intervals to use.
     * @param baseTimeMillis The original tap time (alarms = baseTime + interval).
     */
    suspend fun execute(
        tapId: Long,
        medicationId: Long,
        baseTimeMillis: Long,
    ) {
        val medication = medicationDao.getById(medicationId)
        if (medication == null) {
            Log.e(TAG, "Medication $medicationId not found — cannot reschedule follow-ups")
            return
        }

        // cancelFollowUps is called inside scheduleFollowUps before scheduling
        alarmScheduler.scheduleFollowUps(
            tapId = tapId,
            medication = medication,
            baseTimeMillis = baseTimeMillis,
        )

        Log.i(TAG, "Follow-ups rescheduled: tapId=$tapId medicationId=$medicationId")
    }
}
