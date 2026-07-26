package com.dosetap.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dosetap.DoseTapApp
import com.dosetap.MainActivity
import com.dosetap.domain.model.DoseAction
import com.dosetap.util.Constants
import com.dosetap.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private const val TAG = "WeeklySummaryReceiver"

class WeeklySummaryReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                val container = (context.applicationContext as DoseTapApp).container
                val sevenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
                val allEvents = container.doseEventDao.getAll()

                val takenActions = setOf(DoseAction.INITIAL_TAP, DoseAction.TAKEN)

                val daysTaken = allEvents
                    .filter { it.actionedAt >= sevenDaysAgo && it.action in takenActions }
                    .map { TimeFormatter.startOfDay(it.actionedAt) }
                    .distinct()
                    .size

                // Re-arm next week's summary regardless of whether we post one now.
                container.alarmScheduler.scheduleWeeklySummary()

                // A zero-dose week during a sanctioned break is silence, not a report card.
                val pausedUntil = container.settingsRepository.pausedUntilMillis.first()
                if (daysTaken == 0 && System.currentTimeMillis() < pausedUntil) {
                    Log.i(TAG, "Weekly summary suppressed — paused break with no doses")
                    return@launch
                }

                // Adaptive copy (Spec #9): never lead with a fraction, never lecture.
                val emoji: String
                val body: String
                when {
                    daysTaken >= 6 -> {
                        emoji = "🔥"
                        body = context.getString(com.dosetap.R.string.weekly_summary_strong, daysTaken)
                    }
                    daysTaken >= 3 -> {
                        emoji = "✅"
                        body = context.getString(com.dosetap.R.string.weekly_summary_steady, daysTaken)
                    }
                    else -> {
                        emoji = "🌱"
                        body = context.getString(com.dosetap.R.string.weekly_summary_rough)
                    }
                }

                val openPi = PendingIntent.getActivity(
                    context,
                    Constants.WEEKLY_SUMMARY_REQUEST_CODE.and(0x7FFFFFFF),
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )

                val notification = Notification.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(com.dosetap.R.drawable.ic_notification_pill)
                    .setContentTitle("${context.getString(com.dosetap.R.string.weekly_summary_title)} $emoji")
                    .setContentText(body)
                    .setStyle(Notification.BigTextStyle().bigText(body))
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setAutoCancel(true)
                    .setContentIntent(openPi)
                    .build()

                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(Constants.WEEKLY_SUMMARY_NOTIFICATION_ID, notification)
                Log.i(TAG, "Weekly summary posted: $daysTaken/7 days")
            } catch (e: Exception) {
                Log.e(TAG, "Error posting weekly summary", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
