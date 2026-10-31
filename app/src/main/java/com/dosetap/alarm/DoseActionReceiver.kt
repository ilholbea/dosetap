package com.dosetap.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.dosetap.DoseTapApp
import com.dosetap.domain.model.DoseAction
import com.dosetap.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "DoseActionReceiver"

/**
 * Handles user actions from notification buttons (Taken / Snooze / Skip / Stop today).
 *
 * Taken / Snooze / Skip go through [com.dosetap.domain.usecase.HandleDoseActionUseCase] — the same
 * path as the full-screen [com.dosetap.AlarmActivity] — so both surfaces behave identically.
 */
class DoseActionReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val doseEventId = intent.getLongExtra(Constants.EXTRA_DOSE_EVENT_ID, -1L)
        val notificationId = intent.getIntExtra("notification_id", -1)
        val tapId = intent.getLongExtra(Constants.EXTRA_TAP_ID, -1L)
        val medicationId = intent.getLongExtra(Constants.EXTRA_MEDICATION_ID, -1L)

        Log.d(TAG, "DoseActionReceiver: action=$action doseEventId=$doseEventId notificationId=$notificationId")
        val toastMsg = when (action) {
            Constants.ACTION_DOSE_TAKEN -> "✓ Dose recorded"
            Constants.ACTION_DOSE_SNOOZED -> "⏰ Snoozed 15 min"
            Constants.ACTION_DOSE_SKIPPED -> "Dose skipped"
            Constants.ACTION_DOSE_STOP_TODAY -> context.getString(com.dosetap.R.string.toast_stop_today)
            else -> null
        }
        if (toastMsg != null) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
            }
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                handleAction(context, action, doseEventId, notificationId, tapId, medicationId)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling dose action: $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAction(
        context: Context,
        action: String,
        doseEventId: Long,
        notificationId: Int,
        tapId: Long,
        medicationId: Long,
    ) {
        val container = (context.applicationContext as DoseTapApp).container

        // Resolve medicationId — TAKEN/SKIPPED intents don't carry it, so fall back to the event.
        val resolvedMedicationId = when {
            medicationId != -1L -> medicationId
            doseEventId != -1L -> container.doseEventDao.getById(doseEventId)?.medicationId ?: -1L
            else -> -1L
        }

        val doseAction = when (action) {
            Constants.ACTION_DOSE_TAKEN -> DoseAction.TAKEN
            Constants.ACTION_DOSE_SNOOZED -> DoseAction.SNOOZED
            Constants.ACTION_DOSE_SKIPPED -> DoseAction.SKIPPED
            else -> null
        }

        when {
            doseAction != null -> {
                if (doseEventId != -1L) {
                    container.handleDoseActionUseCase.execute(doseEventId, doseAction)
                }
                Log.d(TAG, "Dose event $doseEventId marked $doseAction")
            }

            action == Constants.ACTION_DOSE_STOP_TODAY -> {
                // End *this dose's* reminder chain. Scoped to the tap rather than the calendar day:
                // for a night shift "today" flips at midnight mid-cycle, so a day-based cancel either
                // missed the post-midnight reminders or wiped the next shift's.
                if (tapId != -1L) {
                    container.alarmScheduler.cancelFollowUps(tapId)
                } else if (medicationId != -1L) {
                    container.alarmScheduler.cancelTodayRemainingForMedication(medicationId)
                }
                container.alarmScheduler.cancelEscalation(resolvedMedicationId)
                Log.d(TAG, "Stop: cancelled remaining reminders for tapId=$tapId medicationId=$medicationId")
            }

            else -> Log.w(TAG, "Unknown action: $action")
        }

        // Dismiss the notification regardless of action
        if (notificationId != -1) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notificationId)
        }

        try {
            container.refreshAmbient()
        } catch (e: Exception) {
            Log.e(TAG, "Ambient notification update failed", e)
        }
        // Awaited before pendingResult.finish(). Writes each widget's own medication's last dose
        // (the old inline version stamped the first medication's time onto every widget).
        container.updateWidgetsNow()
    }

    companion object {
        /**
         * Builds a [DoseActionReceiver] intent for use as a notification action PendingIntent.
         */
        fun buildIntent(
            context: Context,
            action: String,
            doseEventId: Long,
            notificationId: Int,
            tapId: Long,
        ): Intent {
            return Intent(context, DoseActionReceiver::class.java).apply {
                this.action = action
                putExtra(Constants.EXTRA_DOSE_EVENT_ID, doseEventId)
                putExtra("notification_id", notificationId)
                putExtra(Constants.EXTRA_TAP_ID, tapId)
            }
        }
    }
}
