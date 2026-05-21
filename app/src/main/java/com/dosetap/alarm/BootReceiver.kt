package com.dosetap.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dosetap.DoseTapApp
import com.dosetap.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "BootReceiver"

/**
 * Handles system broadcasts that require alarm rescheduling.
 *
 * Listens for all 4 events (§5.1 requirement 6):
 * - `BOOT_COMPLETED` — device rebooted, all AlarmManager alarms lost
 * - `MY_PACKAGE_REPLACED` — app was updated, alarms may be cleared
 * - `TIMEZONE_CHANGED` — user travelled/changed timezone
 * - `TIME_SET` — user manually changed the system clock
 *
 * For TIMEZONE_CHANGED and TIME_SET: alarms are already stored as absolute UTC millis,
 * so they will still fire at the correct absolute time. However, the displayed local time
 * will differ, so we record the change and show a banner to the user.
 */
class BootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "BootReceiver received: $action")

        val pendingResult = goAsync()
        scope.launch {
            try {
                handleBroadcast(context, action)
            } catch (e: Exception) {
                Log.e(TAG, "Error in BootReceiver for action=$action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleBroadcast(context: Context, action: String?) {
        val container = (context.applicationContext as DoseTapApp).container

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i(TAG, "Rescheduling all alarms after: $action")
                container.alarmScheduler.rescheduleAll()
            }

            Intent.ACTION_TIMEZONE_CHANGED -> {
                Log.i(TAG, "Timezone changed — rescheduling alarms and recording event")
                // Alarms stored as UTC millis are still correct in absolute time.
                // Reschedule to ensure AlarmManager has the correct entries.
                container.alarmScheduler.rescheduleAll()
                // Record TZ change timestamp so home screen can show informational banner.
                // resetAlarmBanner() is NOT called here — we want the banner to appear on next open.
                container.settingsRepository.recordTimezoneChange(System.currentTimeMillis())
            }

            Intent.ACTION_TIME_CHANGED -> {
                Log.i(TAG, "System time changed — rescheduling alarms")
                // Absolute millis are still valid, but the AlarmManager uses wall clock internally.
                // Reschedule to ensure consistency.
                container.alarmScheduler.rescheduleAll()
            }

            else -> Log.w(TAG, "Unexpected action in BootReceiver: $action")
        }
    }
}
