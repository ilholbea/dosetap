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

                val bestStreak = computeBestStreak(
                    days = allEvents
                        .filter { it.action in takenActions }
                        .map { TimeFormatter.startOfDay(it.actionedAt) }
                        .distinct()
                )

                val streakEmoji = when {
                    daysTaken == 7 -> "🔥"
                    daysTaken >= 5 -> "✅"
                    daysTaken >= 3 -> "📊"
                    else -> "💊"
                }

                val openPi = PendingIntent.getActivity(
                    context,
                    Constants.WEEKLY_SUMMARY_REQUEST_CODE.and(0x7FFFFFFF),
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )

                val bodyText = "This week: $daysTaken/7 days on time.\n" +
                    "Best streak: $bestStreak days.\n" +
                    "Consistency is key for ADHD medication — great work staying on track."

                val notification = Notification.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("Weekly Dose Summary $streakEmoji")
                    .setContentText("This week: $daysTaken/7 days on time. Best streak: $bestStreak days.")
                    .setStyle(Notification.BigTextStyle().bigText(bodyText))
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setAutoCancel(true)
                    .setContentIntent(openPi)
                    .build()

                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(Constants.WEEKLY_SUMMARY_NOTIFICATION_ID, notification)
                Log.i(TAG, "Weekly summary: $daysTaken/7 days, best streak $bestStreak")

                container.alarmScheduler.scheduleWeeklySummary()
            } catch (e: Exception) {
                Log.e(TAG, "Error posting weekly summary", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun computeBestStreak(days: List<Long>): Int {
        if (days.isEmpty()) return 0
        val sorted = days.sorted()
        val dayMillis = TimeUnit.DAYS.toMillis(1)
        var best = 1
        var current = 1
        for (i in 1 until sorted.size) {
            current = if (sorted[i] - sorted[i - 1] == dayMillis) current + 1 else 1
            if (current > best) best = current
        }
        return best
    }
}
