package com.dosetap.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dosetap.MainActivity
import com.dosetap.domain.model.DoseAction
import com.dosetap.domain.model.DoseEvent
import com.dosetap.domain.model.Medication
import com.dosetap.util.Constants
import com.dosetap.util.TimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "AmbientNotification"

/**
 * Manages the persistent lock-screen dose-state notification (the "Morning Anchor").
 *
 * This is a silent, always-visible notification that tells the user at a glance:
 *   - Pending:  "💊 Ritalin — not logged yet"   (grey)
 *   - Taken:    "✅ Ritalin — taken at 9:02 · 2h 14m ago"  (green)
 *   - Late:     "🟡 Ritalin — no dose yet"  (amber, shown after daily reminder time)
 *
 * One "Log dose" action button fires [Constants.ACTION_AMBIENT_LOG_DOSE] without opening the app.
 *
 * Channel: [Constants.NOTIFICATION_CHANNEL_AMBIENT] — low importance, no sound, no vibration.
 * This means the notification silently updates without making noise.
 *
 * Android 14+: setOngoing(true) can be swiped away. Re-post on every alarm fire and on reboot.
 */
class AmbientNotificationManager(private val context: Context) {

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Post or update the ambient notification to reflect [lastDose] state for [medication]. */
    fun update(medication: Medication?, lastDose: DoseEvent?) {
        if (medication == null) {
            nm.cancel(Constants.AMBIENT_NOTIFICATION_ID)
            return
        }
        val notification = build(medication, lastDose)
        try {
            nm.notify(Constants.AMBIENT_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post ambient notification", e)
        }
    }

    /** Cancel the ambient notification (e.g. on data wipe). */
    fun cancel() {
        nm.cancel(Constants.AMBIENT_NOTIFICATION_ID)
    }

    private fun build(medication: Medication, lastDose: DoseEvent?): Notification {
        val nowMillis = System.currentTimeMillis()

        val (emoji, title, body) = when {
            lastDose != null && isToday(lastDose.actionedAt) -> {
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(Date(lastDose.actionedAt))
                val elapsedStr = TimeFormatter.formatElapsedShort(nowMillis - lastDose.actionedAt)
                val anchor = medication.morningAnchor
                val anchorNote = if (anchor != null) " · right after $anchor" else ""
                Triple("✅", medication.name, "Taken at $timeStr · $elapsedStr ago$anchorNote")
            }
            else -> {
                val anchor = medication.morningAnchor
                val anchorNote = if (anchor != null) " (right after $anchor)" else ""
                Triple("💊", medication.name, "No dose logged yet$anchorNote")
            }
        }

        val openAppIntent = PendingIntent.getActivity(
            context,
            Constants.AMBIENT_OPEN_APP_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val logDoseIntent = PendingIntent.getBroadcast(
            context,
            Constants.AMBIENT_LOG_DOSE_REQUEST_CODE,
            Intent(Constants.ACTION_AMBIENT_LOG_DOSE).apply {
                setPackage(context.packageName)
                putExtra(Constants.EXTRA_MEDICATION_ID, medication.id)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val hasTakenToday = lastDose != null && isToday(lastDose.actionedAt)

        return Notification.Builder(context, Constants.NOTIFICATION_CHANNEL_AMBIENT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$emoji $title")
            .setContentText(body)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent)
            .apply {
                if (!hasTakenToday) {
                    addAction(
                        Notification.Action.Builder(
                            null,
                            "Log dose",
                            logDoseIntent
                        ).build()
                    )
                }
            }
            .build()
    }

    private fun isToday(millis: Long): Boolean {
        val startOfDay = TimeFormatter.startOfDay(System.currentTimeMillis())
        return millis >= startOfDay
    }
}
