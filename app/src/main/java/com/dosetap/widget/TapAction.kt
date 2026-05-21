package com.dosetap.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.dosetap.DoseTapApp
import com.dosetap.util.Constants

private const val TAG = "TapAction"

val LastTapTimeKey = longPreferencesKey("last_tap_time")
val LastTapIdKey = longPreferencesKey("last_tap_id")
val UndoVisibleUntilKey = longPreferencesKey("undo_visible_until")

/** Stores the actual dose time for display in the undo banner (reactive via DataStore). */
val LastDoseTimeKey = longPreferencesKey("last_dose_time")

class TapAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val nowMillis = System.currentTimeMillis()
        val container = (context.applicationContext as DoseTapApp).container

        // Read current DataStore state before deciding what to do
        val currentPrefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val lastTapTime = currentPrefs[LastTapTimeKey] ?: 0L
        val undoVisibleUntil = currentPrefs[UndoVisibleUntilKey] ?: 0L
        val lastTapId = currentPrefs[LastTapIdKey] ?: -1L

        if (nowMillis < undoVisibleUntil && lastTapId != -1L) {
            // Undo: DB work happens here, outside the DataStore transform
            Log.d(TAG, "Undo tapped for tapId=$lastTapId")
            try {
                container.logDoseUseCase.undo(lastTapId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to undo dose tapId=$lastTapId", e)
            }
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[LastTapIdKey] = -1L
                prefs[UndoVisibleUntilKey] = 0L
                prefs[LastTapTimeKey] = nowMillis
                prefs[LastDoseTimeKey] = 0L
            }
            container.refreshWidgets()
            return
        }

        if (nowMillis - lastTapTime < Constants.WIDGET_DEBOUNCE_MILLIS) {
            Log.d(TAG, "Widget tap debounced (${nowMillis - lastTapTime}ms since last tap)")
            return
        }

        val medication = container.medicationDao.getFirst()
        if (medication == null) {
            Log.w(TAG, "Widget tapped but no medication found")
            return
        }

        // DB work happens here, outside the DataStore transform
        try {
            val tapId = container.logDoseUseCase.execute(medication.id, nowMillis)
            if (tapId != -1L) {
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[LastTapIdKey] = tapId
                    prefs[UndoVisibleUntilKey] = nowMillis + Constants.WIDGET_UNDO_WINDOW_MILLIS
                    prefs[LastTapTimeKey] = nowMillis
                    prefs[LastDoseTimeKey] = nowMillis
                }
                Log.d(TAG, "Widget dose logged: tapId=$tapId medication=${medication.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log dose from widget", e)
        }

        container.refreshWidgets()
    }
}
