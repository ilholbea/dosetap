package com.dosetap.alarm

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dosetap.DoseTapApp
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

                val notification = Notification.Builder(context, Constants.NOTIFICATION_CHANNEL_WARNINGS)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(medication.name)
                    .setContentText("Wearing off around $timeStr — plan accordingly 🌙")
                    .setAutoCancel(true)
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
