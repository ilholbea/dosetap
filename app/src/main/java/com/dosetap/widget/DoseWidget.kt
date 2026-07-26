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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
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
import com.dosetap.domain.model.ScheduledAlarm
import com.dosetap.ui.theme.DarkColorScheme
import com.dosetap.ui.theme.LightColorScheme
import com.dosetap.util.TimeFormatter
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DoseWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as DoseTapApp).container

        // Resolve raw appWidgetId from GlanceId so we can look up the medication in SettingsRepository.
        // This zip is safe here: provideGlance is called only after the widget is fully registered,
        // so both lists are complete and in the same order.
        val rawIds = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, DoseWidgetReceiver::class.java))
            .toList()
        val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(DoseWidget::class.java)
        val rawId = rawIds.zip(glanceIds).find { (_, gId) -> gId == id }?.first ?: -1

        val configuredMedId = if (rawId != -1)
            container.settingsRepository.getWidgetMedication(rawId)
        else -1L

        val medication = withContext(Dispatchers.IO) {
            if (configuredMedId != -1L)
                container.medicationDao.getById(configuredMedId) ?: container.medicationDao.getFirst()
            else container.medicationDao.getFirst()
        }
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
            .cornerRadius(20.dp)
            .clickable(actionRunCallback<TapAction>()),
        contentAlignment = Alignment.Center,
    ) {
        when {
            medicationName == null -> NoMedicationContent(context, openAppAction)
            showUndo -> UndoContent(context, lastDoseTimeMillis, openAppAction)
            else -> NormalContent(context, medicationName, lastDoseTimeMillis, pendingAlarms, openAppAction)
        }
    }
}

@Composable
private fun NoMedicationContent(
    context: Context,
    openAppAction: androidx.glance.action.Action,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "💊", style = TextStyle(fontSize = 32.sp))
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = "Set up DoseTap",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = "Tap to add medication →",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 11.sp,
            ),
            modifier = GlanceModifier.clickable(openAppAction),
        )
    }
}

@Composable
private fun UndoContent(
    context: Context,
    lastDoseTimeMillis: Long,
    openAppAction: androidx.glance.action.Action,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "✅ Dose logged",
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = TimeFormatter.formatTime(context, lastDoseTimeMillis),
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = "Tap again to undo",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 11.sp,
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
    openAppAction: androidx.glance.action.Action,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Header: tapping the whole row opens the app (big, obvious target). The body below
        // stays a one-tap dose log.
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(openAppAction),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "💊", style = TextStyle(fontSize = 18.sp))
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = medicationName,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
            )
            Text(
                text = "Open ↗",
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        Spacer(GlanceModifier.height(8.dp))

        // Last dose block
        if (lastDoseTimeMillis > 0L) {
            val elapsed = System.currentTimeMillis() - lastDoseTimeMillis
            Text(
                text = formatElapsed(elapsed),
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = "since last dose · ${TimeFormatter.formatTime(context, lastDoseTimeMillis)}",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
                maxLines = 1,
            )
        } else {
            Text(
                text = "No dose today",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = "Tap to log →",
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 12.sp,
                ),
            )
        }

        // Next alarm
        if (pendingAlarms.isNotEmpty()) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = "⏰ Follow-up: ${TimeFormatter.formatTime(context, pendingAlarms[0].triggerAtMillis)}",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

private fun formatElapsed(millis: Long): String {
    val minutes = millis / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h ${minutes % 60}m"
        else -> "${days}d ${hours % 24}h"
    }
}
