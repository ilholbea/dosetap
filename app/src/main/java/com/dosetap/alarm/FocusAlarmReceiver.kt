package com.dosetap.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dosetap.MainActivity
import com.dosetap.R
import com.dosetap.util.Constants

class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Constants.ACTION_FOCUS_TIMER_DONE) return

        val mode = intent.getStringExtra(EXTRA_MODE) ?: "Focus"
        val openAppPi = PendingIntent.getActivity(
            context, Constants.FOCUS_TIMER_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val (title, body) = when (mode) {
            "BREAK" -> "Break time over ☕" to "Ready to focus again?"
            "TRANSITION" -> "Time's up 🔔" to "Transition complete."
            else -> "Focus session done ✨" to "Take a moment — you earned it."
        }

        val notification = Notification.Builder(context, Constants.NOTIFICATION_CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_notification_pill)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppPi)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(Constants.FOCUS_TIMER_NOTIFICATION_ID, notification)
    }

    companion object {
        const val EXTRA_MODE = "extra_focus_mode"
    }
}
