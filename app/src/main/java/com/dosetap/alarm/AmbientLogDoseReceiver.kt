package com.dosetap.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.dosetap.DoseTapApp
import com.dosetap.util.Constants
import com.dosetap.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "AmbientLogDoseReceiver"

/**
 * Handles the "Log dose" action button from the ambient lock-screen notification.
 *
 * Logs the dose directly without opening the app, then updates the ambient notification.
 */
class AmbientLogDoseReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Constants.ACTION_AMBIENT_LOG_DOSE) return

        val medicationId = intent.getLongExtra(Constants.EXTRA_MEDICATION_ID, -1L)
        if (medicationId == -1L) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                val container = (context.applicationContext as DoseTapApp).container
                val nowMillis = System.currentTimeMillis()
                // A stale notification can still carry the button after a dose was logged
                // elsewhere — never double-log from the lock screen; the app's confirm dialog is
                // the only place to deliberately log a second dose.
                val conflict = container.logDoseUseCase.findDoubleDoseConflict(medicationId, nowMillis)
                val message = if (conflict != null) {
                    Log.d(TAG, "Ambient log refused — dose already logged at ${conflict.actionedAt}")
                    "Already logged at ${TimeFormatter.formatTime(context, conflict.actionedAt)} — open DoseTap to log another"
                } else {
                    val tapId = container.logDoseUseCase.execute(medicationId, nowMillis)
                    Log.d(TAG, "Ambient log dose: tapId=$tapId medicationId=$medicationId")
                    "✅ Dose logged"
                }
                container.refreshAmbient()
                container.updateWidgetsNow()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log dose from ambient notification", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
