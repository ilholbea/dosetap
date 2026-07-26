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
import com.dosetap.ui.focus.FocusMode
import com.dosetap.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "WearOffWarningReceiver"

/**
 * Posts the comedown / wear-off warning notification.
 * Fired by AlarmScheduler ~60 minutes before the effect window ends.
 */
class WearOffWarningReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra(Constants.EXTRA_MEDICATION_ID, -1L)
        val wearOffAtMillis = intent.getLongExtra("wear_off_at_millis", -1L)
        if (medicationId == -1L) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                val container = (context.applicationContext as DoseTapApp).container
                val medication = container.medicationDao.getById(medicationId) ?: return@launch

                val timeStr = if (wearOffAtMillis != -1L)
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(wearOffAtMillis))
                else "soon"

                // [Start 10-min transition] → opens Focus in Transition mode, auto-started (Spec #8).
                // Note: [Log next dose] is intentionally omitted — the wear-off warning fires while
                // the medication is still within its effect window, so logging now would trip the
                // double-dose gate, which the spec says must suppress this action.
                val transitionPi = PendingIntent.getActivity(
                    context,
                    (Constants.WEAROFF_WARNING_REQUEST_CODE_BASE + medicationId.toInt() + 1).and(0x7FFFFFFF),
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(MainActivity.EXTRA_NAV_DEST, MainActivity.NAV_DEST_FOCUS)
                        putExtra(MainActivity.EXTRA_FOCUS_MODE, FocusMode.TRANSITION.name)
                        putExtra(MainActivity.EXTRA_FOCUS_AUTOSTART, true)
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )

                val notification = Notification.Builder(context, Constants.NOTIFICATION_CHANNEL_WARNINGS)
                    .setSmallIcon(com.dosetap.R.drawable.ic_notification_pill)
                    .setContentTitle(medication.name)
                    .setContentText("Wearing off around $timeStr. The next hour can feel like wading — that's the meds, not you.")
                    .setAutoCancel(true)
                    .addAction(
                        Notification.Action.Builder(null, "Start 10-min transition", transitionPi).build()
                    )
                    .build()

                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(
                    (Constants.WEAROFF_WARNING_NOTIFICATION_ID_BASE + medicationId.toInt()).and(0x7FFFFFFF),
                    notification
                )
                Log.d(TAG, "Wear-off warning posted for ${medication.name} wearing off at $timeStr")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post wear-off warning", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
