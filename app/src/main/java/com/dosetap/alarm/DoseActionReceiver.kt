package com.dosetap.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.dosetap.DoseTapApp
import com.dosetap.domain.model.DoseAction
import com.dosetap.util.Constants
import com.dosetap.widget.DoseWidget
import com.dosetap.widget.LastDoseTimeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "DoseActionReceiver"

/**
 * Handles user actions from notification buttons (Taken / Snooze / Skip).
 *
 * Updates the [com.dosetap.domain.model.DoseEvent] that was written as MISSED by [AlarmReceiver],
 * dismisses the notification, and for snooze: reschedules a follow-up alarm.
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
        val nowMillis = System.currentTimeMillis()

        when (action) {
            Constants.ACTION_DOSE_TAKEN -> {
                if (doseEventId != -1L) {
                    val event = container.doseEventDao.getById(doseEventId)
                    if (event != null) {
                        container.doseEventDao.update(
                            event.copy(action = DoseAction.TAKEN, actionedAt = nowMillis)
                        )
                    }
                }
                Log.d(TAG, "Dose marked TAKEN for event $doseEventId")
            }

            Constants.ACTION_DOSE_SNOOZED -> {
                if (doseEventId != -1L) {
                    val event = container.doseEventDao.getById(doseEventId)
                    if (event != null) {
                        container.doseEventDao.update(
                            event.copy(action = DoseAction.SNOOZED, actionedAt = nowMillis)
                        )
                    }
                }

                // Schedule a follow-up alarm in SNOOZE_DURATION_MILLIS
                if (medicationId != -1L) {
                    val medication = container.medicationDao.getById(medicationId)
                    if (medication != null) {
                        val snoozeTriggerAt = nowMillis + Constants.SNOOZE_DURATION_MILLIS
                        // Use a unique snooze tapId derived from original tapId
                        val snoozeTapId = tapId * 1000 + (nowMillis % 1000)
                        container.alarmScheduler.scheduleFollowUps(
                            tapId = snoozeTapId,
                            medication = medication.copy(followUps = listOf(0)), // immediate follow-up
                            baseTimeMillis = snoozeTriggerAt,
                        )
                    }
                }
                Log.d(TAG, "Dose SNOOZED, snooze alarm scheduled")
            }

            Constants.ACTION_DOSE_SKIPPED -> {
                if (doseEventId != -1L) {
                    val event = container.doseEventDao.getById(doseEventId)
                    if (event != null) {
                        container.doseEventDao.update(
                            event.copy(action = DoseAction.SKIPPED, actionedAt = nowMillis)
                        )
                    }
                }
                Log.d(TAG, "Dose SKIPPED for event $doseEventId")
            }

            else -> Log.w(TAG, "Unknown action: $action")
        }

        // Dismiss the notification regardless of action
        if (notificationId != -1) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notificationId)
        }

        // Update ambient notification after action
        try {
            val med = container.medicationDao.getFirst()
            val lastDose = med?.let { container.doseEventDao.getLastConfirmedDose(it.id) }
            container.ambientNotificationManager.update(med, lastDose)
        } catch (e: Exception) {
            Log.e(TAG, "Ambient notification update failed", e)
        }

        // Inline widget refresh — awaited before pendingResult.finish()
        // Glance reuses a running session on update() and only re-reads DataStore, so we must
        // write the current dose time to DataStore first for the composable to pick it up.
        try {
            val appCtx = context.applicationContext
            val glanceManager = GlanceAppWidgetManager(appCtx)
            val glanceIds = glanceManager.getGlanceIds(DoseWidget::class.java)
            Log.d(TAG, "Widget refresh: found ${glanceIds.size} Glance widget(s)")
            if (glanceIds.isNotEmpty()) {
                val doseTimeForWidget = if (action == Constants.ACTION_DOSE_TAKEN) nowMillis else {
                    val med = container.medicationDao.getFirst()
                    med?.let { container.doseEventDao.getLastConfirmedDose(it.id) }?.actionedAt ?: 0L
                }
                glanceIds.forEach { glanceId ->
                    updateAppWidgetState(appCtx, glanceId) { prefs ->
                        prefs[LastDoseTimeKey] = doseTimeForWidget
                    }
                    DoseWidget().update(appCtx, glanceId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Widget refresh failed", e)
        }
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
