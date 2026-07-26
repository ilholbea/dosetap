package com.dosetap.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.dosetap.data.db.AlarmDao
import com.dosetap.data.db.MedicationDao
import com.dosetap.data.prefs.SettingsRepository
import com.dosetap.domain.model.Medication
import com.dosetap.domain.model.ScheduledAlarm
import com.dosetap.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

            setExact(
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
     * Re-arms a single snooze re-ping for [tapId] at [triggerAtMillis], reusing the original tap's
     * ID at the reserved [Constants.SNOOZE_FOLLOWUP_INDEX] slot.
     *
     * Unlike [scheduleFollowUps] this does **not** cancel the follow-up chain — the still-future
     * rows (indices 0 until [Constants.MAX_FOLLOWUP_COUNT]) are left untouched (E-H1). Only a prior
     * snooze re-ping for this tap is replaced. Because the snooze keeps the original [tapId], the
     * event it later produces stays grouped under the original tap, and [cancelFollowUps] (called
     * when the dose is finally logged) cancels the snooze too.
     */
    suspend fun scheduleSnooze(tapId: Long, medication: Medication, triggerAtMillis: Long) {
        // Replace only a previous snooze for this tap, never the real follow-up rows.
        cancelFollowUpAt(tapId, Constants.SNOOZE_FOLLOWUP_INDEX)

        val requestCode = buildRequestCode(tapId, Constants.SNOOZE_FOLLOWUP_INDEX)
        val pendingIntent = buildAlarmPendingIntent(
            tapId = tapId,
            medicationId = medication.id,
            followUpIndex = Constants.SNOOZE_FOLLOWUP_INDEX,
            requestCode = requestCode,
        )
        setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        alarmDao.insertAll(
            listOf(
                ScheduledAlarm(
                    tapId = tapId,
                    medicationId = medication.id,
                    followUpIndex = Constants.SNOOZE_FOLLOWUP_INDEX,
                    triggerAtMillis = triggerAtMillis,
                )
            )
        )
        settingsRepository.setAlarmsActive(true)
        Log.d(TAG, "Snooze re-ping scheduled tapId=$tapId at $triggerAtMillis")
    }

    /** Cancels only the alarm at [index] for [tapId], leaving the rest of the chain intact. */
    private suspend fun cancelFollowUpAt(tapId: Long, index: Int) {
        alarmDao.getActiveByTapId(tapId)
            .filter { it.followUpIndex == index }
            .forEach { alarm ->
                val requestCode = buildRequestCode(tapId, index)
                alarmManager.cancel(
                    buildAlarmPendingIntent(tapId, alarm.medicationId, index, requestCode)
                )
                alarmDao.cancelById(alarm.id)
            }
    }

    /**
     * Re-arms the still-future alarms of [tapId] (skipping [firedIndex], which just fired).
     *
     * A defensive safety net: when an earlier follow-up fires we're guaranteed the process is
     * alive, so we re-assert the later alarms in AlarmManager. This recovers the reported case
     * where a second reminder silently fails to appear after the first was ignored — if the OS
     * dropped or coalesced the later alarm, this puts it back. Idempotent (FLAG_UPDATE_CURRENT,
     * same trigger time), so it can never cause a double-fire.
     */
    suspend fun reassertChain(tapId: Long, firedIndex: Int) {
        val now = System.currentTimeMillis()
        alarmDao.getActiveByTapId(tapId)
            .filter { it.followUpIndex != firedIndex && it.triggerAtMillis > now }
            .forEach { alarm ->
                val requestCode = buildRequestCode(tapId, alarm.followUpIndex)
                val pendingIntent = buildAlarmPendingIntent(
                    tapId = tapId,
                    medicationId = alarm.medicationId,
                    followUpIndex = alarm.followUpIndex,
                    requestCode = requestCode,
                )
                setExact(AlarmManager.RTC_WAKEUP, alarm.triggerAtMillis, pendingIntent)
                Log.d(TAG, "Re-asserted chain alarm tapId=$tapId index=${alarm.followUpIndex}")
            }
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
        withContext(Dispatchers.IO) {
        val nowMillis = System.currentTimeMillis()
        val activeAlarms = alarmDao.getAllActive(nowMillis)

        // E-H2: alarms that came due while the device was off are re-armed to fire now (staggered),
        // so a reminder missed during downtime still nudges instead of silently vanishing. Alarms
        // older than the catch-up window are cancelled rather than firing a stale burst.
        val overdue = alarmDao.getOverdue(nowMillis)
        val catchUpWindowMillis = 12 * 60 * 60_000L
        var stagger = 0L
        overdue.forEach { alarm ->
            if (nowMillis - alarm.triggerAtMillis <= catchUpWindowMillis &&
                medicationDao.getById(alarm.medicationId) != null
            ) {
                val requestCode = buildRequestCode(alarm.tapId, alarm.followUpIndex)
                val pendingIntent = buildAlarmPendingIntent(
                    tapId = alarm.tapId,
                    medicationId = alarm.medicationId,
                    followUpIndex = alarm.followUpIndex,
                    requestCode = requestCode,
                )
                setExact(AlarmManager.RTC_WAKEUP, nowMillis + 5_000L + stagger, pendingIntent)
                stagger += 3_000L
                Log.d(TAG, "Catch-up: re-armed overdue alarm ${alarm.id} (was due ${alarm.triggerAtMillis})")
            } else {
                alarmDao.cancelById(alarm.id)
                Log.d(TAG, "Catch-up: dropped stale overdue alarm ${alarm.id}")
            }
        }

        if (activeAlarms.isEmpty()) {
            Log.d(TAG, "rescheduleAll: no future active alarms to restore")
            settingsRepository.setAlarmsActive(overdue.isNotEmpty())
            return@withContext
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

            setExact(
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

        } // end withContext
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
        setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
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
        setExact(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, pendingIntent)
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
        setExact(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, pendingIntent)
        Log.d(TAG, "Weekly summary scheduled at ${trigger.timeInMillis}")
    }

    /**
     * Cancels only today's remaining alarms for [medicationId].
     * Used by "Stop today" notification action — leaves tomorrow's daily reminder intact.
     */
    suspend fun cancelTodayRemainingForMedication(medicationId: Long) {
        val tomorrowMidnight = com.dosetap.util.TimeFormatter.startOfDay(
            System.currentTimeMillis() + 24 * 60 * 60 * 1000L
        )
        val activeAlarms = alarmDao.getActiveByMedicationId(medicationId)
        activeAlarms
            .filter { it.triggerAtMillis < tomorrowMidnight }
            .forEach { alarm ->
                val requestCode = buildRequestCode(alarm.tapId, alarm.followUpIndex)
                val pendingIntent = buildAlarmPendingIntent(
                    tapId = alarm.tapId,
                    medicationId = medicationId,
                    followUpIndex = alarm.followUpIndex,
                    requestCode = requestCode,
                )
                alarmManager.cancel(pendingIntent)
                alarmDao.cancelById(alarm.id)
                Log.d(TAG, "Stop-today: cancelled alarm tapId=${alarm.tapId} index=${alarm.followUpIndex}")
            }
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
        setExact(AlarmManager.RTC_WAKEUP, warningAt, pendingIntent)
        Log.d(TAG, "Wear-off warning scheduled for medication ${medication.id} at $warningAt")
    }

    /** Cancels the wear-off warning for [tapId]. Safe to call even if none was scheduled. */
    fun cancelWearOffWarning(tapId: Long) {
        val requestCode = (Constants.WEAROFF_WARNING_REQUEST_CODE_BASE + tapId.toInt()).and(0x7FFFFFFF)
        val intent = Intent(context, WearOffWarningReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Wear-off warning cancelled for tapId=$tapId")
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

    /**
     * Schedules the smart-escalation re-nudge chain for a reminder that just fired (Spec #2).
     *
     * Two nudges — [Constants.ESCALATION_FIRST_DELAY_MILLIS] and
     * [Constants.ESCALATION_SECOND_DELAY_MILLIS] after [originalFiredAt] — each re-post the
     * reminder with fresh sound/vibration via [EscalationReceiver]. Cancelled by
     * [cancelEscalation] the instant any dose is logged or actioned. Pro-gated by the caller.
     */
    fun scheduleEscalation(
        medicationId: Long,
        tapId: Long,
        followUpIndex: Int,
        doseEventId: Long,
        originalFiredAt: Long,
    ) {
        val notificationId = AlarmReceiver.buildNotificationId(medicationId, followUpIndex)
        for (nudge in 0 until Constants.ESCALATION_MAX_NUDGES) {
            val delay = if (nudge == 0) Constants.ESCALATION_FIRST_DELAY_MILLIS
                        else Constants.ESCALATION_SECOND_DELAY_MILLIS
            val triggerAt = originalFiredAt + delay
            if (triggerAt <= System.currentTimeMillis()) continue

            val intent = Intent(context, EscalationReceiver::class.java).apply {
                action = Constants.ACTION_ESCALATION
                putExtra(Constants.EXTRA_MEDICATION_ID, medicationId)
                putExtra(Constants.EXTRA_TAP_ID, tapId)
                putExtra(Constants.EXTRA_FOLLOWUP_INDEX, followUpIndex)
                putExtra(Constants.EXTRA_DOSE_EVENT_ID, doseEventId)
                putExtra(Constants.EXTRA_ESCALATION_NUDGE, nudge)
                putExtra(Constants.EXTRA_ESCALATION_ORIGINAL_FIRED, originalFiredAt)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, escalationRequestCode(notificationId, nudge), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            Log.d(TAG, "Escalation nudge $nudge scheduled for medication $medicationId at $triggerAt")
        }
    }

    /**
     * Cancels every pending escalation nudge for [medicationId] across all follow-up slots.
     *
     * Called from every dose-log / dose-action surface. Reconstructs each possible
     * PendingIntent with [PendingIntent.FLAG_NO_CREATE] so non-existent ones are skipped.
     */
    fun cancelEscalation(medicationId: Long) {
        if (medicationId == -1L) return
        // followUpIndex occupies 4 bits (0–15) in the notification ID; cover the full range.
        for (followUpIndex in 0..15) {
            val notificationId = AlarmReceiver.buildNotificationId(medicationId, followUpIndex)
            for (nudge in 0 until Constants.ESCALATION_MAX_NUDGES) {
                val intent = Intent(context, EscalationReceiver::class.java).apply {
                    action = Constants.ACTION_ESCALATION
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, escalationRequestCode(notificationId, nudge), intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
                ) ?: continue
                alarmManager.cancel(pendingIntent)
            }
        }
        Log.d(TAG, "Escalation cancelled for medicationId=$medicationId")
    }

    private fun escalationRequestCode(notificationId: Int, nudgeIndex: Int): Int =
        (Constants.ESCALATION_REQUEST_CODE_BASE +
            notificationId * Constants.ESCALATION_MAX_NUDGES + nudgeIndex) and 0x7FFFFFFF

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

    /**
     * Sets an exact alarm, guarding the Android 12+ [AlarmManager.canScheduleExactAlarms] permission
     * (E-H3). The app declares `USE_EXACT_ALARM` (auto-granted to alarm apps), but if that grant is
     * ever unavailable — e.g. forced onto `SCHEDULE_EXACT_ALARM`, which the user can revoke — this
     * degrades to an inexact alarm instead of crashing or silently no-op'ing. A missed exact window
     * is far better than a lost reminder.
     */
    private fun setExact(type: Int, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(type, triggerAtMillis, pendingIntent)
        } else {
            Log.w(TAG, "Exact alarms not permitted — inexact fallback at $triggerAtMillis")
            alarmManager.setAndAllowWhileIdle(type, triggerAtMillis, pendingIntent)
        }
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
