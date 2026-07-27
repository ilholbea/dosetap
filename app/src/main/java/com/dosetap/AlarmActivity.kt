package com.dosetap

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.widget.Toast
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.dosetap.widget.DoseWidget
import com.dosetap.widget.LastDoseTimeKey
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dosetap.alarm.AlarmReceiver
import com.dosetap.domain.model.DoseAction
import com.dosetap.domain.model.DoseEvent
import com.dosetap.domain.model.Medication
import com.dosetap.ui.theme.AppTheme
import com.dosetap.util.Constants
import com.dosetap.util.TimeFormatter
import kotlinx.coroutines.launch

private const val TAG = "AlarmActivity"

/**
 * Full-screen alarm activity shown when a medication reminder fires.
 *
 * Shows over the lock screen using [setShowWhenLocked] and [setTurnScreenOn].
 * Three large action buttons: Taken / Snooze 15 min / Skip.
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val doseEventId = intent.getLongExtra(Constants.EXTRA_DOSE_EVENT_ID, -1L)
        val medicationId = intent.getLongExtra(Constants.EXTRA_MEDICATION_ID, -1L)

        val notificationId = AlarmReceiver.buildNotificationId(
            medicationId = medicationId,
            followUpIndex = intent.getIntExtra(Constants.EXTRA_FOLLOWUP_INDEX, 0),
        )

        Log.d(TAG, "AlarmActivity started: doseEventId=$doseEventId medicationId=$medicationId")

        val container = (applicationContext as DoseTapApp).container

        fun handleAction(action: DoseAction) {
            // Show confirmation immediately — before the coroutine — so it appears even as the screen closes
            val toastMsg = when (action) {
                DoseAction.TAKEN -> "✓ Dose recorded"
                DoseAction.SNOOZED -> "⏰ Snoozed 15 min"
                DoseAction.SKIPPED -> "Dose skipped"
                else -> null
            }
            toastMsg?.let { Toast.makeText(applicationContext, it, Toast.LENGTH_SHORT).show() }

            lifecycleScope.launch {
                try {
                    if (doseEventId != -1L) {
                        container.handleDoseActionUseCase.execute(doseEventId, action)
                    }
                    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(notificationId)
                    // Inline awaited widget update — must complete before finish() is called.
                    // Write dose time to DataStore first: Glance reuses a running session on
                    // update() and only re-reads DataStore, not the DB.
                    val nowMillis = System.currentTimeMillis()
                    val glanceManager = GlanceAppWidgetManager(applicationContext)
                    val glanceIds = glanceManager.getGlanceIds(DoseWidget::class.java)
                    Log.d(TAG, "AlarmActivity widget refresh: ${glanceIds.size} widget(s)")
                    if (glanceIds.isNotEmpty()) {
                        val doseTimeForWidget = if (action == DoseAction.TAKEN) nowMillis else {
                            val med = container.medicationDao.getFirst()
                            med?.let { container.doseEventDao.getLastConfirmedDose(it.id) }?.actionedAt ?: 0L
                        }
                        glanceIds.forEach { glanceId ->
                            updateAppWidgetState(applicationContext, glanceId) { prefs ->
                                prefs[LastDoseTimeKey] = doseTimeForWidget
                            }
                            DoseWidget().update(applicationContext, glanceId)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling alarm action: $action", e)
                } finally {
                    finish()
                }
            }
        }

        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AlarmContent(
                        doseEventId = doseEventId,
                        medicationId = medicationId,
                        onAction = ::handleAction,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
private fun AlarmContent(
    doseEventId: Long,
    medicationId: Long,
    onAction: (DoseAction) -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as DoseTapApp).container

    var medication by remember { mutableStateOf<Medication?>(null) }
    var doseEvent by remember { mutableStateOf<DoseEvent?>(null) }
    var isActioning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        medication = if (medicationId != -1L) container.medicationDao.getById(medicationId) else null
        doseEvent = if (doseEventId != -1L) container.doseEventDao.getById(doseEventId) else null
    }

    fun handleAction(action: DoseAction) {
        if (isActioning) return
        isActioning = true
        onAction(action)
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top accent band
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(primaryColor)
        )

        Spacer(Modifier.weight(1f))

        // Alarm icon in circle
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(primaryColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Alarm,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = primaryColor,
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.alarm_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )

        if (medication != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = medication!!.name,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = primaryColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }

        if (doseEvent?.scheduledAt != null) {
            Spacer(Modifier.height(16.dp))
            val scheduledTime = TimeFormatter.formatTime(context, doseEvent!!.scheduledAt!!)
            val lateness = System.currentTimeMillis() - (doseEvent!!.scheduledAt ?: System.currentTimeMillis())

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${stringResource(R.string.alarm_scheduled_label)} $scheduledTime",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (lateness > 60_000L) {
                        Text(
                            text = stringResource(R.string.alarm_late_label, TimeFormatter.formatDuration(lateness)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Action buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            // Primary action — TAKEN
            Button(
                onClick = { handleAction(DoseAction.TAKEN) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                enabled = !isActioning,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                ),
            ) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.alarm_taken),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { handleAction(DoseAction.SNOOZED) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    enabled = !isActioning,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.alarm_snooze))
                }
                OutlinedButton(
                    onClick = { handleAction(DoseAction.SKIPPED) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    enabled = !isActioning,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.alarm_skip))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
