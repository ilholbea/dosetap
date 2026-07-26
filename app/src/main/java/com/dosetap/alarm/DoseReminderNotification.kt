package com.dosetap.alarm

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dosetap.AlarmActivity
import com.dosetap.domain.model.Medication
import com.dosetap.util.Constants

/**
 * Builds the dose-reminder notification shown by [AlarmReceiver] and re-posted by
 * [EscalationReceiver].
 *
 * Extracted so the original reminder and its escalation re-nudges share one definition:
 * identical Taken / Snooze / Stop-today actions, full-screen intent, and notification ID.
 * Only the body text differs (the nudges reword it — see [Constants.ACTION_ESCALATION]).
 */
object DoseReminderNotification {

    /**
     * @param doseEventId The MISSED [com.dosetap.domain.model.DoseEvent] the actions update.
     * @param tapId Source tap ID (used to cancel the follow-up chain on action).
     * @param notificationId Stable ID from [AlarmReceiver.buildNotificationId]; re-posting with
     *   the same ID replaces the tray entry and re-alerts (fresh sound/vibration).
     * @param bodyText Content text — medication name for the original, reworded for nudges.
     */
    fun build(
        context: Context,
        medication: Medication,
        doseEventId: Long,
        tapId: Long,
        notificationId: Int,
        bodyText: String,
    ): Notification {
        val medicationId = medication.id

        val takenIntent = DoseActionReceiver.buildIntent(
            context, Constants.ACTION_DOSE_TAKEN, doseEventId, notificationId, tapId
        )
        val snoozeIntent = DoseActionReceiver.buildIntent(
            context, Constants.ACTION_DOSE_SNOOZED, doseEventId, notificationId, tapId
        ).also { it.putExtra(Constants.EXTRA_MEDICATION_ID, medicationId) }
        val stopTodayIntent = DoseActionReceiver.buildIntent(
            context, Constants.ACTION_DOSE_STOP_TODAY, doseEventId, notificationId, tapId
        ).also { it.putExtra(Constants.EXTRA_MEDICATION_ID, medicationId) }

        val takenPi = PendingIntent.getBroadcast(
            context, notificationId * 10 + 1, takenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val snoozePi = PendingIntent.getBroadcast(
            context, notificationId * 10 + 2, snoozeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopTodayPi = PendingIntent.getBroadcast(
            context, notificationId * 10 + 4, stopTodayIntent,
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
            context, notificationId, alarmActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(com.dosetap.R.drawable.ic_notification_pill)
            .setContentTitle(context.getString(com.dosetap.R.string.alarm_title))
            .setContentText(bodyText)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)
            .addAction(
                Notification.Action.Builder(
                    null, context.getString(com.dosetap.R.string.alarm_taken), takenPi
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    null, context.getString(com.dosetap.R.string.alarm_snooze), snoozePi
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    null, context.getString(com.dosetap.R.string.alarm_stop_today), stopTodayPi
                ).build()
            )
            .build()
    }
}
