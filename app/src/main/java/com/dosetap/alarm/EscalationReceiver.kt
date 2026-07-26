package com.dosetap.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dosetap.DoseTapApp
import com.dosetap.domain.model.DoseAction
import com.dosetap.domain.model.FeatureFlag
import com.dosetap.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "EscalationReceiver"

/**
 * Re-posts a dose reminder as a gentle re-nudge when the user hasn't engaged (Spec #2 / E-C1).
 *
 * Scheduled by [AlarmScheduler.scheduleEscalation] at +10 and +25 min after the original
 * reminder fires. The chain is normally cancelled the instant any dose is logged or actioned
 * (see [AlarmScheduler.cancelEscalation]); the guards here are a belt-and-suspenders backstop
 * for the case where a cancel was missed or state changed after scheduling.
 */
class EscalationReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra(Constants.EXTRA_MEDICATION_ID, -1L)
        val tapId = intent.getLongExtra(Constants.EXTRA_TAP_ID, -1L)
        val followUpIndex = intent.getIntExtra(Constants.EXTRA_FOLLOWUP_INDEX, 0)
        val doseEventId = intent.getLongExtra(Constants.EXTRA_DOSE_EVENT_ID, -1L)
        val nudgeIndex = intent.getIntExtra(Constants.EXTRA_ESCALATION_NUDGE, 0)
        val originalFiredAt = intent.getLongExtra(Constants.EXTRA_ESCALATION_ORIGINAL_FIRED, 0L)

        if (medicationId == -1L) return

        Log.d(TAG, "Escalation fired: medicationId=$medicationId nudge=$nudgeIndex")

        val pendingResult = goAsync()
        scope.launch {
            try {
                val container = (context.applicationContext as DoseTapApp).container

                // Guard: reminders paused → never nudge.
                val pausedUntil = container.settingsRepository.pausedUntilMillis.first()
                if (System.currentTimeMillis() < pausedUntil) {
                    Log.d(TAG, "Paused — suppressing escalation")
                    return@launch
                }

                // Guard: Pro entitlement may have lapsed since scheduling.
                if (!FeatureFlag.canUseSmartEscalation(container.isPro.first())) {
                    Log.d(TAG, "Not Pro — suppressing escalation")
                    return@launch
                }

                val medication = container.medicationDao.getById(medicationId) ?: return@launch

                // Backstop 1: a dose was confirmed after the original reminder fired.
                val lastConfirmed = container.doseEventDao.getLastConfirmedDose(medicationId)
                if (lastConfirmed != null && lastConfirmed.actionedAt >= originalFiredAt) {
                    Log.d(TAG, "Dose already logged since reminder — suppressing escalation")
                    return@launch
                }

                // Backstop 2: the original reminder event was actioned via a notification button.
                if (doseEventId != -1L) {
                    val event = container.doseEventDao.getById(doseEventId)
                    if (event != null && event.action != DoseAction.MISSED) {
                        Log.d(TAG, "Reminder already actioned (${event.action}) — suppressing escalation")
                        return@launch
                    }
                }

                val bodyRes = if (nudgeIndex == 0) {
                    com.dosetap.R.string.escalation_nudge_1
                } else {
                    com.dosetap.R.string.escalation_nudge_2
                }
                val notificationId = AlarmReceiver.buildNotificationId(medicationId, followUpIndex)
                val notification = DoseReminderNotification.build(
                    context = context,
                    medication = medication,
                    doseEventId = doseEventId,
                    tapId = tapId,
                    notificationId = notificationId,
                    bodyText = context.getString(bodyRes, medication.name),
                )
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(notificationId, notification)
                Log.d(TAG, "Re-nudge $nudgeIndex posted for ${medication.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling escalation", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
