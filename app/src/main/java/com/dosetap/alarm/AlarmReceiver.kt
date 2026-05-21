package com.dosetap.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import com.dosetap.AlarmActivity
import com.dosetap.AppContainer
import com.dosetap.DoseTapApp
import com.dosetap.MainActivity
import com.dosetap.domain.model.DoseAction
import com.dosetap.domain.model.DoseEvent
import com.dosetap.util.Constants
import com.dosetap.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

        val takenIntent = DoseActionReceiver.buildIntent(
            context, Constants.ACTION_DOSE_TAKEN, doseEventId, notificationId, tapId
        )
        val snoozeIntent = DoseActionReceiver.buildIntent(
            context, Constants.ACTION_DOSE_SNOOZED, doseEventId, notificationId, tapId
        ).also {
            it.putExtra(Constants.EXTRA_MEDICATION_ID, medicationId)
        }
        val skipIntent = DoseActionReceiver.buildIntent(
            context, Constants.ACTION_DOSE_SKIPPED, doseEventId, notificationId, tapId
        )

        val takenPi = PendingIntent.getBroadcast(
            context, notificationId * 10 + 1, takenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val snoozePi = PendingIntent.getBroadcast(
            context, notificationId * 10 + 2,
            snoozeIntent.also { it.putExtra(Constants.EXTRA_MEDICATION_ID, medicationId) },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val skipPi = PendingIntent.getBroadcast(
            context, notificationId * 10 + 3, skipIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Full-screen intent → AlarmActivity (shown over lock screen)
        val alarmActivityIntent = Intent(context, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(Constants.EXTRA_DOSE_EVENT_ID, doseEventId)
            putExtra(Constants.EXTRA_MEDICATION_ID, medicationId)
            putExtra(Constants.EXTRA_TAP_ID, tapId)
        }
        val fullScreenPi = PendingIntent.getActivity(
            context, notificationId,
            alarmActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val soundUri = medication.soundUri?.let { Uri.parse(it) }
            ?: android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI

        val notification = Notification.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(com.dosetap.R.string.alarm_title))
            .setContentText(medication.name)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)
            .addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(com.dosetap.R.string.alarm_taken),
                    takenPi
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(com.dosetap.R.string.alarm_snooze),
                    snoozePi
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(com.dosetap.R.string.alarm_skip),
                    skipPi
                ).build()
            )
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)

        container.refreshWidgets()
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
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
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
