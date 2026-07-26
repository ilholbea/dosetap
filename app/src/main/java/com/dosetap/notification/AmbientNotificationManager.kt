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
        updateAll(listOf(medication to lastDose))
    }

    /**
     * Post or update the ambient notification for one or more medications. A single med gets the
     * detailed one-line card; multiple meds wrap one line each in an [Notification.InboxStyle]
     * (Spec #7).
     */
    fun updateAll(items: List<Pair<Medication, DoseEvent?>>) {
        if (items.isEmpty()) {
            nm.cancel(Constants.AMBIENT_NOTIFICATION_ID)
            return
        }
        val notification =
            if (items.size == 1) buildSingle(items[0].first, items[0].second)
            else buildInbox(items)
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

    /** Returns (emoji, medName, stateBody) describing a single medication's dose state today. */
    private fun describe(medication: Medication, lastDose: DoseEvent?): Triple<String, String, String> {
        val nowMillis = System.currentTimeMillis()
        val anchor = medication.morningAnchor
        return when {
            lastDose != null && isToday(lastDose.actionedAt) -> {
                val timeStr = TimeFormatter.formatTime(context, lastDose.actionedAt)
                val elapsedStr = TimeFormatter.formatElapsedShort(nowMillis - lastDose.actionedAt)
                // Next follow-up reminder = dose time + the soonest still-future follow-up offset.
                val nextReminder = medication.followUps
                    .map { lastDose.actionedAt + it * 60_000L }
                    .filter { it > nowMillis }
                    .minOrNull()
                if (nextReminder != null) {
                    // State 2 — logged, follow-up pending.
                    val nextStr = TimeFormatter.formatTime(context, nextReminder)
                    Triple("✅", medication.name, "Taken $timeStr · $elapsedStr ago · next reminder $nextStr")
                } else {
                    // State 3 — day complete; keep the anchor loop warm overnight.
                    val tomorrow = if (anchor != null) " · tomorrow right after $anchor" else ""
                    Triple("✅", medication.name, "Done for today ✓$tomorrow")
                }
            }
            else -> {
                // State 1 — unlogged; surface the proactive reminder time if one is set.
                val anchorNote = if (anchor != null) " (right after $anchor)" else ""
                val reminderNote = medication.dailyReminderMinutes?.let { mins ->
                    val cal = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, mins / 60)
                        set(java.util.Calendar.MINUTE, mins % 60)
                        set(java.util.Calendar.SECOND, 0)
                    }
                    " · reminder ${TimeFormatter.formatTime(context, cal.timeInMillis)}"
                } ?: ""
                Triple("💊", medication.name, "No dose logged yet$anchorNote$reminderNote")
            }
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        Constants.AMBIENT_OPEN_APP_REQUEST_CODE,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Detailed single-medication card, with an inline "Log dose" action while unlogged. */
    private fun buildSingle(medication: Medication, lastDose: DoseEvent?): Notification {
        val (emoji, title, body) = describe(medication, lastDose)

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
            .setSmallIcon(com.dosetap.R.drawable.ic_notification_pill)
            .setContentTitle("$emoji $title")
            .setContentText(body)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent())
            .apply {
                if (!hasTakenToday) {
                    addAction(
                        Notification.Action.Builder(null, "Log dose", logDoseIntent).build()
                    )
                }
            }
            .build()
    }

    /** Multi-medication summary: one line per med (Spec #7). Tapping opens the app. */
    private fun buildInbox(items: List<Pair<Medication, DoseEvent?>>): Notification {
        val lines = items.map { (med, dose) ->
            val (emoji, name, body) = describe(med, dose)
            "$emoji $name — $body"
        }
        val loggedCount = items.count { (_, dose) -> dose != null && isToday(dose.actionedAt) }
        val summary = "$loggedCount of ${items.size} logged today"

        val inbox = Notification.InboxStyle().also { style ->
            lines.forEach { style.addLine(it) }
            style.setSummaryText(summary)
        }

        return Notification.Builder(context, Constants.NOTIFICATION_CHANNEL_AMBIENT)
            .setSmallIcon(com.dosetap.R.drawable.ic_notification_pill)
            .setContentTitle("Today's doses")
            .setContentText(summary)
            .setStyle(inbox)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent())
            .build()
    }

    private fun isToday(millis: Long): Boolean {
        val startOfDay = TimeFormatter.startOfDay(System.currentTimeMillis())
        return millis >= startOfDay
    }
}
