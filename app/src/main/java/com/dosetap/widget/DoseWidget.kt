package com.dosetap.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dosetap.DoseTapApp
import com.dosetap.MainActivity
import com.dosetap.R
import com.dosetap.domain.model.ScheduledAlarm
import com.dosetap.ui.theme.DarkColorScheme
import com.dosetap.ui.theme.LightColorScheme
import com.dosetap.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DoseWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as DoseTapApp).container
        val medication = withContext(Dispatchers.IO) { container.medicationDao.getFirst() }
        // Use getLastConfirmedDose (INITIAL_TAP or TAKEN) so that doses confirmed
        // via the AlarmActivity "Taken" button — which updates an existing MISSED
        // event in place rather than inserting an INITIAL_TAP — still update the
        // widget's "last dose" display. The previous getLastInitialTap query
        // ignored TAKEN updates, which is why the widget went stale after alarm
        // confirmations even though update() was correctly invoked.
        val lastDoseEvent = medication?.let {
            withContext(Dispatchers.IO) { container.doseEventDao.getLastConfirmedDose(it.id) }
        }
        val pendingAlarms: List<ScheduledAlarm> = medication?.let { med ->
            lastDoseEvent?.let { tap ->
                withContext(Dispatchers.IO) {
                    container.alarmDao.getActiveByTapId(tap.sourceTapId)
                        .filter { it.triggerAtMillis > System.currentTimeMillis() }
                        .sortedBy { it.triggerAtMillis }
                }
            }
        } ?: emptyList()

        provideContent {
            val prefs = currentState<Preferences>()
            val undoVisibleUntil = prefs[UndoVisibleUntilKey] ?: 0L
            val showUndo = System.currentTimeMillis() < undoVisibleUntil
            // Always read from DataStore: update() reuses a running session and only re-reads
            // DataStore (not DB), so DataStore is the source of truth for reactive dose time.
            val lastDoseTimeMillis = prefs[LastDoseTimeKey] ?: lastDoseEvent?.actionedAt ?: 0L

            GlanceTheme(
                colors = ColorProviders(
                    light = LightColorScheme,
                    dark = DarkColorScheme,
                )
            ) {
                WidgetContent(
                    medicationName = medication?.name,
                    lastDoseTimeMillis = lastDoseTimeMillis,
                    pendingAlarms = pendingAlarms,
                    showUndo = showUndo,
                    context = context,
                )
            }
        }
    }
}

@Composable
private fun WidgetContent(
    medicationName: String?,
    lastDoseTimeMillis: Long,
    pendingAlarms: List<ScheduledAlarm>,
    showUndo: Boolean,
    context: Context,
) {
    val openAppAction = actionStartActivity(
        android.content.Intent(context, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    )

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .clickable(actionRunCallback<TapAction>()),
        contentAlignment = Alignment.Center,
    ) {
        // Top-right open-app button
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(8.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            Text(
                text = "↗",
                modifier = GlanceModifier.clickable(openAppAction).padding(4.dp),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 14.sp,
                ),
            )
        }

        when {
            medicationName == null -> NoMedicationContent(context)
            showUndo -> UndoContent(context, lastDoseTimeMillis)
            else -> NormalContent(context, medicationName, lastDoseTimeMillis, pendingAlarms)
        }
    }
}

@Composable
private fun NoMedicationContent(context: Context) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "💊",
            style = TextStyle(fontSize = 28.sp),
        )
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = context.getString(R.string.widget_no_medication),
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun UndoContent(context: Context, lastDoseTimeMillis: Long) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "✓ Logged",
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = TimeFormatter.formatTime(context, lastDoseTimeMillis),
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = "Tap to undo",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
            ),
        )
    }
}

@Composable
private fun NormalContent(
    context: Context,
    medicationName: String,
    lastDoseTimeMillis: Long,
    pendingAlarms: List<ScheduledAlarm>,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Medication name
        Text(
            text = medicationName,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )

        Spacer(GlanceModifier.height(10.dp))

        // Last dose
        if (lastDoseTimeMillis > 0L) {
            val elapsed = System.currentTimeMillis() - lastDoseTimeMillis
            Text(
                text = context.getString(R.string.home_last_dose, TimeFormatter.formatTime(context, lastDoseTimeMillis)),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                ),
                maxLines = 1,
            )
            Text(
                text = formatElapsed(elapsed),
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        } else {
            Text(
                text = context.getString(R.string.widget_tap_to_log),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 13.sp,
                ),
                maxLines = 1,
            )
        }

        // Next alarm
        if (pendingAlarms.isNotEmpty()) {
            Spacer(GlanceModifier.height(8.dp))
            val nextTime = TimeFormatter.formatTime(context, pendingAlarms[0].triggerAtMillis)
            Text(
                text = context.getString(R.string.widget_next_doses, nextTime),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

private fun formatElapsed(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ${minutes % 60}m ago"
        else -> "${minutes / (60 * 24)}d ago"
    }
}
