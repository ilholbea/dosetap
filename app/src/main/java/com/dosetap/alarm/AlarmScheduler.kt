package com.dosetap.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dosetap.data.db.AlarmDao
import com.dosetap.data.db.MedicationDao
import com.dosetap.data.prefs.SettingsRepository
import com.dosetap.domain.model.Medication
import com.dosetap.domain.model.ScheduledAlarm
import com.dosetap.util.Constants

private const val TAG = "AlarmScheduler"

/**
 * Schedules, cancels, and reschedules medication follow-up alarms.
 *
 * Uses [AlarmManager.setExactAndAllowWhileIdle] to ensure alarms fire even in Doze mode.
 * This is the most reliable scheduling method for user-facing exact alarms (§5.1).
 *
 * **Request code formula:** `(tapId.toInt() shl 4) or followUpIndex`
 * This guarantees unique codes per alarm while allowing cancellation by reconstruction.
 *
 * **Persistence:** Every scheduled alarm is written to [AlarmDao] before being set in the system.
 * On reboot/force-stop, [rescheduleAll] reads from the DB and re-sets all pending alarms.
 */
class AlarmScheduler(
    private val context: Context,
    private val alarmDao: AlarmDao,
    private val medicationDao: MedicationDao,
    private val settingsRepository: SettingsRepository,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedules follow-up alarms for a new dose tap.
     *
     * Cancels any existing alarms for [tapId] first to avoid duplicates
     * (e.g. if user taps widget twice quickly — debounce should prevent this but
     * we defend here too).
     *
     * @param tapId Unique ID of the initial tap (DoseEvent.sourceTapId).
     * @param medication The medication whose follow-up intervals to use.
     * @param baseTimeMillis The UTC millis of the initial tap (alarms are baseTime + interval).
     */
    suspend fun scheduleFollowUps(
        tapId: Long,
        medication: Medication,
        baseTimeMillis: Long,
    ) {
        // Cancel any existing alarms for this tapId first
        cancelFollowUps(tapId)

        val alarmsToInsert = mutableListOf<ScheduledAlarm>()

        medication.followUps.forEachIndexed { index, intervalMinutes ->
            val triggerAtMillis = baseTimeMillis + intervalMinutes * 60_000L
            val requestCode = buildRequestCode(tapId, index)

            val pendingIntent = buildAlarmPendingIntent(
                tapId = tapId,
                medicationId = medication.id,
                followUpIndex = index,
                requestCode = requestCode,
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )

            Log.d(TAG, "Scheduled alarm tapId=$tapId index=$index at $triggerAtMillis (requestCode=$requestCode)")

            alarmsToInsert.add(
                ScheduledAlarm(
                    tapId = tapId,
                    medicationId = medication.id,
                    followUpIndex = index,
                    triggerAtMillis = triggerAtMillis,
                )
            )
        }

        alarmDao.insertAll(alarmsToInsert)
        settingsRepository.setAlarmsActive(true)
        settingsRepository.resetAlarmBanner()
    }

    /**
     * Cancels all pending alarms for the given [tapId].
     *
     * Reconstructs PendingIntents using the same formula as scheduling,
     * so the system can match and cancel them.
     */
    suspend fun cancelFollowUps(tapId: Long) {
        val activeAlarms = alarmDao.getActiveByTapId(tapId)
        activeAlarms.forEach { alarm ->
            val requestCode = buildRequestCode(tapId, alarm.followUpIndex)
            val pendingIntent = buildAlarmPendingIntent(
                tapId = tapId,
                medicationId = alarm.medicationId,
                followUpIndex = alarm.followUpIndex,
                requestCode = requestCode,
            )
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Cancelled alarm tapId=$tapId index=${alarm.followUpIndex}")
        }
        alarmDao.cancelByTapId(tapId)
    }

    /**
     * Reschedules all active alarms from the database.
     *
     * Called by [BootReceiver] after device reboot, app update, timezone change, or time set.
     * The DB is the source of truth; AlarmManager is just a cache.
     *
     * Alarms whose trigger time has already passed are skipped (they will appear as MISSED
     * in the dose history since [AlarmReceiver] wrote MISSED at schedule time).
     */
    suspend fun rescheduleAll() {
        val nowMillis = System.currentTimeMillis()
        val activeAlarms = alarmDao.getAllActive(nowMillis)

        if (activeAlarms.isEmpty()) {
            Log.d(TAG, "rescheduleAll: no active alarms to restore")
            settingsRepository.setAlarmsActive(false)
            return
        }

        activeAlarms.forEach { alarm ->
            val medication = medicationDao.getById(alarm.medicationId)
            if (medication == null) {
                Log.w(TAG, "rescheduleAll: medication ${alarm.medicationId} not found, skipping alarm ${alarm.id}")
                alarmDao.cancelById(alarm.id)
                return@forEach
            }

            val requestCode = buildRequestCode(alarm.tapId, alarm.followUpIndex)
            val pendingIntent = buildAlarmPendingIntent(
                tapId = alarm.tapId,
                medicationId = alarm.medicationId,
                followUpIndex = alarm.followUpIndex,
                requestCode = requestCode,
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarm.triggerAtMillis,
                pendingIntent,
            )

            Log.d(TAG, "Rescheduled alarm tapId=${alarm.tapId} index=${alarm.followUpIndex} at ${alarm.triggerAtMillis}")
        }

        settingsRepository.setAlarmsActive(true)
        Log.i(TAG, "rescheduleAll: restored ${activeAlarms.size} alarms")

        // Re-schedule daily reminders for medications that have them configured
        val allMedications = medicationDao.getAll()
        allMedications.filter { it.dailyReminderMinutes != null }.forEach { scheduleDailyReminder(it) }

        // Re-schedule weekly summary
        scheduleWeeklySummary()
    }

    /**
     * Schedules a single test alarm [Constants.TEST_REMINDER_DELAY_MILLIS] from now.
     * Uses a fixed request code so it can be easily identified and cancelled.
     *
     * @param medicationId The medication ID to use in the test alarm notification.
     */
    fun scheduleTestAlarm(medicationId: Long) {
        val triggerAt = System.currentTimeMillis() + Constants.TEST_REMINDER_DELAY_MILLIS
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(Constants.EXTRA_TAP_ID, -1L) // Special sentinel for test alarms
            putExtra(Constants.EXTRA_MEDICATION_ID, medicationId)
            putExtra(Constants.EXTRA_FOLLOWUP_INDEX, 0)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            Constants.TEST_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        Log.d(TAG, "Test alarm scheduled at $triggerAt")
    }

    /**
     * Schedules a proactive daily reminder for [medication] at [Medication.dailyReminderMinutes]
     * minutes from midnight. If that time has already passed today, schedules for tomorrow.
     * Respects off-days and the skip-tomorrow setting.
     * Does nothing if [Medication.dailyReminderMinutes] is null.
     */
    suspend fun scheduleDailyReminder(medication: Medication) {
        val dailyMinutes = medication.dailyReminderMinutes ?: return

        val trigger = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, dailyMinutes / 60)
            set(java.util.Calendar.MINUTE, dailyMinutes % 60)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        if (trigger.timeInMillis <= System.currentTimeMillis()) {
            trigger.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }

        // Skip if tomorrow is marked as a skip day
        val skipDate = settingsRepository.getSkipTomorrowDate()
        if (skipDate > 0L) {
            val triggerDayStart = java.util.Calendar.getInstance().apply {
                timeInMillis = trigger.timeInMillis
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            if (triggerDayStart == skipDate) {
                trigger.add(java.util.Calendar.DAY_OF_YEAR, 1)
                Log.d(TAG, "Daily reminder skipped tomorrow (skip day); advanced to day after")
            }
        }

        // Skip off-days: advance to the next non-off day
        if (medication.offDays.isNotEmpty()) {
            var attempts = 0
            while (trigger.get(java.util.Calendar.DAY_OF_WEEK) in medication.offDays && attempts < 7) {
                trigger.add(java.util.Calendar.DAY_OF_YEAR, 1)
                attempts++
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(Constants.EXTRA_MEDICATION_ID, medication.id)
            putExtra(Constants.EXTRA_IS_DAILY_REMINDER, true)
            putExtra(Constants.EXTRA_TAP_ID, -1L)
            putExtra(Constants.EXTRA_FOLLOWUP_INDEX, 0)
        }
        val requestCode = Constants.DAILY_REMINDER_REQUEST_CODE_BASE + medication.id.toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, pendingIntent)
        Log.d(TAG, "Daily reminder scheduled for medication ${medication.id} at ${trigger.timeInMillis}")
    }

    /**
     * Schedules the weekly adherence summary notification for Sunday at [Constants.WEEKLY_SUMMARY_HOUR].
     * If that time has already passed this week, schedules for next Sunday.
     */
    fun scheduleWeeklySummary() {
        val trigger = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_WEEK, Constants.WEEKLY_SUMMARY_DAY)
            set(java.util.Calendar.HOUR_OF_DAY, Constants.WEEKLY_SUMMARY_HOUR)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        if (trigger.timeInMillis <= System.currentTimeMillis()) {
            trigger.add(java.util.Calendar.WEEK_OF_YEAR, 1)
        }
        val intent = Intent(context, WeeklySummaryReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            Constants.WEEKLY_SUMMARY_REQUEST_CODE.and(0x7FFFFFFF),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, pendingIntent)
        Log.d(TAG, "Weekly summary scheduled at ${trigger.timeInMillis}")
    }

    /** Cancels all follow-up alarms for [medicationId] — used when deleting a medication. */
    suspend fun cancelAllForMedication(medicationId: Long) {
        val activeAlarms = alarmDao.getActiveByMedicationId(medicationId)
        activeAlarms.forEach { alarm ->
            val requestCode = buildRequestCode(alarm.tapId, alarm.followUpIndex)
            val pendingIntent = buildAlarmPendingIntent(
                tapId = alarm.tapId,
                medicationId = medicationId,
                followUpIndex = alarm.followUpIndex,
                requestCode = requestCode,
            )
            alarmManager.cancel(pendingIntent)
        }
        alarmDao.cancelByMedicationId(medicationId)
        Log.d(TAG, "Cancelled all alarms for medicationId=$medicationId")
    }

    /**
     * Schedules a comedown / wear-off warning for [tapId] on [medication].
     * Fires 60 minutes before the effect window ends (if durationMinutes is set).
     * Free tier feature — always schedules.
     */
    fun scheduleWearOffWarning(tapId: Long, medication: Medication, doseTimeMillis: Long) {
        val duration = medication.durationMinutes ?: return
        val wearOffAt = doseTimeMillis + duration * 60_000L
        val warningAt = wearOffAt - 60 * 60_000L // 60 min before

        if (warningAt <= System.currentTimeMillis()) return // already in the past

        val intent = Intent(context, WearOffWarningReceiver::class.java).apply {
            putExtra(Constants.EXTRA_MEDICATION_ID, medication.id)
            putExtra("wear_off_at_millis", wearOffAt)
        }
        val requestCode = (Constants.WEAROFF_WARNING_REQUEST_CODE_BASE + tapId.toInt()).and(0x7FFFFFFF)
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, warningAt, pendingIntent)
        Log.d(TAG, "Wear-off warning scheduled for medication ${medication.id} at $warningAt")
    }

    /** Cancels the daily reminder for [medication]. Safe to call even if none was scheduled. */
    fun cancelDailyReminder(medication: Medication) {
        val requestCode = Constants.DAILY_REMINDER_REQUEST_CODE_BASE + medication.id.toInt()
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Daily reminder cancelled for medication ${medication.id}")
    }

    // ===== Private helpers =====

    /**
     * Builds a unique PendingIntent request code for a given tap ID and follow-up index.
     *
     * Formula: `(tapId.toInt() shl 4) or followUpIndex`
     * - 28 bits for tapId (truncated), 4 bits for followUpIndex (0–15)
     * - This guarantees unique codes for up to 4 follow-ups per tap
     */
    private fun buildRequestCode(tapId: Long, followUpIndex: Int): Int {
        return (tapId.toInt() shl Constants.REQUEST_CODE_SHIFT) or followUpIndex
    }

    private fun buildAlarmPendingIntent(
        tapId: Long,
        medicationId: Long,
        followUpIndex: Int,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(Constants.EXTRA_TAP_ID, tapId)
            putExtra(Constants.EXTRA_MEDICATION_ID, medicationId)
            putExtra(Constants.EXTRA_FOLLOWUP_INDEX, followUpIndex)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            // FLAG_IMMUTABLE is required on Android 12+ (API 31+). FLAG_UPDATE_CURRENT ensures
            // rescheduling replaces the previous intent rather than creating a duplicate.
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
