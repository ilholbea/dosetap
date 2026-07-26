package com.dosetap.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.dosetap.AppContainer
import com.dosetap.DoseTapApp
import com.dosetap.MainActivity
import com.dosetap.domain.model.DoseAction
import com.dosetap.domain.model.DoseEvent
import com.dosetap.domain.model.FeatureFlag
import com.dosetap.util.Constants
import com.dosetap.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "AlarmReceiver"

/**
 * BroadcastReceiver that fires when a medication alarm triggers.
 *
 * Responsibilities (§5.2):
 * 1. Acquire a partial WakeLock for max [Constants.WAKELOCK_TIMEOUT_MILLIS].
 * 2. Write a [DoseEvent] with action=MISSED immediately (updated later if user responds).
 * 3. Build a high-priority notification with full-screen intent → [AlarmActivity].
 * 4. Attach Taken/Snooze/Skip action buttons pointing to [DoseActionReceiver].
 */
class AlarmReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val tapId = intent.getLongExtra(Constants.EXTRA_TAP_ID, -1L)
        val medicationId = intent.getLongExtra(Constants.EXTRA_MEDICATION_ID, -1L)
        val followUpIndex = intent.getIntExtra(Constants.EXTRA_FOLLOWUP_INDEX, 0)
        val isDailyReminder = intent.getBooleanExtra(Constants.EXTRA_IS_DAILY_REMINDER, false)

        if (medicationId == -1L) {
            Log.e(TAG, "AlarmReceiver received intent with missing medicationId")
            return
        }

        Log.d(TAG, "AlarmReceiver fired: tapId=$tapId medicationId=$medicationId followUpIndex=$followUpIndex isDailyReminder=$isDailyReminder")

        // Acquire WakeLock to prevent CPU from sleeping before we show the notification
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        // Justification for @SuppressLint: WakeLock is released in the finally block
        // or automatically after WAKELOCK_TIMEOUT_MILLIS to prevent battery drain.
        @Suppress("WakelockTimeout")
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DoseTap:AlarmWakeLock",
        ).also { it.acquire(Constants.WAKELOCK_TIMEOUT_MILLIS) }

        val pendingResult = goAsync()

        scope.launch {
            try {
                val container = (context.applicationContext as DoseTapApp).container
                if (isDailyReminder) {
                    handleDailyReminder(context, container, medicationId)
                } else {
                    handleAlarm(context, container, tapId, medicationId, followUpIndex)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling alarm for tapId=$tapId medicationId=$medicationId", e)
            } finally {
                wakeLock.release()
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAlarm(
        context: Context,
        container: AppContainer,
        tapId: Long,
        medicationId: Long,
        followUpIndex: Int,
    ) {
        val nowMillis = System.currentTimeMillis()

        // Respect pause — suppress notification silently, clean up DB alarm
        val pausedUntil = container.settingsRepository.pausedUntilMillis.first()
        if (nowMillis < pausedUntil) {
            Log.d(TAG, "Reminders paused until $pausedUntil — suppressing alarm tapId=$tapId")
            val pausedAlarm = if (tapId != -1L)
                container.alarmDao.getActiveByTapId(tapId).find { it.followUpIndex == followUpIndex }
            else null
            if (pausedAlarm != null) container.alarmDao.cancelById(pausedAlarm.id)
            return
        }

        val medication = container.medicationDao.getById(medicationId)

        if (medication == null) {
            Log.w(TAG, "Medication $medicationId not found — alarm for stale medication, ignoring")
            return
        }

        // Write MISSED event immediately (updated to TAKEN/SNOOZED/SKIPPED if user responds)
        val scheduledAlarms = if (tapId != -1L) container.alarmDao.getActiveByTapId(tapId) else emptyList()
        val thisAlarm = scheduledAlarms.find { it.followUpIndex == followUpIndex }

        val doseEventId = container.doseEventDao.insert(
            DoseEvent(
                medicationId = medicationId,
                scheduledAt = thisAlarm?.triggerAtMillis,
                firedAt = nowMillis,
                actionedAt = nowMillis,
                action = DoseAction.MISSED,
                sourceTapId = if (tapId == -1L) -1L else tapId,
            )
        )

        // Mark this alarm as cancelled in DB (it has now fired)
        if (thisAlarm != null) {
            container.alarmDao.cancelById(thisAlarm.id)
        }

        val notificationId = buildNotificationId(medicationId, followUpIndex)

        val notification = DoseReminderNotification.build(
            context = context,
            medication = medication,
            doseEventId = doseEventId,
            tapId = tapId,
            notificationId = notificationId,
            bodyText = medication.name,
        )

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)

        // Safety net: re-assert the remaining follow-ups so a later reminder in the chain still
        // fires even if the OS dropped it after this one (the "2nd alarm didn't show" report).
        if (tapId != -1L) container.alarmScheduler.reassertChain(tapId, followUpIndex)

        // Smart escalation (Pro): if the user doesn't engage, re-nudge at +10 and +25 min.
        // Real follow-ups only (test alarms use tapId = -1). Cancelled the instant any dose
        // log or action arrives; guarded again at fire time (paused / already-logged / Pro).
        if (tapId != -1L && FeatureFlag.canUseSmartEscalation(container.isPro.first())) {
            container.alarmScheduler.scheduleEscalation(
                medicationId = medicationId,
                tapId = tapId,
                followUpIndex = followUpIndex,
                doseEventId = doseEventId,
                originalFiredAt = nowMillis,
            )
        }

        container.refreshWidgets()
        // Update ambient notification to show no-dose-yet state after alarm fires
        container.refreshAmbient()
        Log.d(TAG, "Notification posted: id=$notificationId for medication=${medication.name}")
    }

    private suspend fun handleDailyReminder(context: Context, container: AppContainer, medicationId: Long) {
        val medication = container.medicationDao.getById(medicationId) ?: return

        val todayStart = TimeFormatter.startOfDay(System.currentTimeMillis())
        val todayEvents = container.doseEventDao.getForMedicationSince(medicationId, todayStart)
        val hasTakenToday = todayEvents.any {
            it.action == DoseAction.INITIAL_TAP || it.action == DoseAction.TAKEN
        }

        if (!hasTakenToday) {
            val openAppPi = PendingIntent.getActivity(
                context,
                (Constants.DAILY_REMINDER_REQUEST_CODE_BASE + medicationId.toInt()).and(0x7FFFFFFF),
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = Notification.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(com.dosetap.R.drawable.ic_notification_pill)
                .setContentTitle("Time for ${medication.name}")
                .setContentText("You haven't logged today's dose yet.")
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(openAppPi)
                .build()
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify((Constants.DAILY_REMINDER_REQUEST_CODE_BASE + medicationId.toInt()).and(0x7FFFFFFF), notification)
            Log.d(TAG, "Daily reminder notification posted for ${medication.name}")
        } else {
            Log.d(TAG, "Daily reminder skipped — dose already logged today for ${medication.name}")
        }

        // Reschedule for tomorrow
        container.alarmScheduler.scheduleDailyReminder(medication)
    }

    companion object {
        /** Builds a stable notification ID from medicationId and followUpIndex. */
        fun buildNotificationId(medicationId: Long, followUpIndex: Int): Int {
            return ((medicationId.toInt() shl 4) or followUpIndex).and(0x7FFFFFFF)
        }
    }
}
