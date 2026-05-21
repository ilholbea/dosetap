package com.dosetap.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dosetap_settings")

/**
 * Manages app settings and onboarding state using DataStore Preferences.
 *
 * All preferences are typed and use explicit keys. No raw string keys.
 */
class SettingsRepository(private val context: Context) {

    private val dataStore = context.dataStore

    // ===== Onboarding =====

    val onboardingComplete: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETE] ?: false
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    // ===== Widget state =====

    /** Maps a widget ID to a medication ID (serialized as "widgetId:medicationId,..." pairs). */
    val widgetMedicationMap: Flow<Map<Int, Long>> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.WIDGET_MEDICATION_MAP] ?: return@map emptyMap()
        parseWidgetMap(raw)
    }

    suspend fun setWidgetMedication(widgetId: Int, medicationId: Long) {
        dataStore.edit { prefs ->
            val current = parseWidgetMap(prefs[Keys.WIDGET_MEDICATION_MAP] ?: "")
            val updated = current.toMutableMap().also { it[widgetId] = medicationId }
            prefs[Keys.WIDGET_MEDICATION_MAP] = serializeWidgetMap(updated)
        }
    }

    suspend fun removeWidget(widgetId: Int) {
        dataStore.edit { prefs ->
            val current = parseWidgetMap(prefs[Keys.WIDGET_MEDICATION_MAP] ?: "")
            val updated = current.toMutableMap().also { it.remove(widgetId) }
            prefs[Keys.WIDGET_MEDICATION_MAP] = serializeWidgetMap(updated)
        }
    }

    // ===== Alarm reconciliation =====

    /** Timestamp of the last known timezone change. Used to show TZ-change notification. */
    val lastTimezoneChangeMillis: Flow<Long> = dataStore.data.map { prefs ->
        prefs[Keys.LAST_TIMEZONE_CHANGE_MILLIS] ?: 0L
    }

    suspend fun recordTimezoneChange(millis: Long) {
        dataStore.edit { it[Keys.LAST_TIMEZONE_CHANGE_MILLIS] = millis }
    }

    val timezoneChangeDismissed: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.TIMEZONE_CHANGE_DISMISSED] ?: false
    }

    suspend fun dismissTimezoneChange() {
        dataStore.edit {
            it[Keys.TIMEZONE_CHANGE_DISMISSED] = true
            it[Keys.LAST_TIMEZONE_CHANGE_MILLIS] = 0L
        }
    }

    // ===== Force-stop detection =====

    /**
     * Written to DataStore just before each alarm is scheduled.
     * If on next app open this is set but the alarm is not in AlarmManager,
     * we know a force-stop happened.
     */
    val alarmsWereActive: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ALARMS_WERE_ACTIVE] ?: false
    }

    suspend fun setAlarmsActive(active: Boolean) {
        dataStore.edit { it[Keys.ALARMS_WERE_ACTIVE] = active }
    }

    val alarmBannerDismissed: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ALARM_BANNER_DISMISSED] ?: false
    }

    suspend fun dismissAlarmBanner() {
        dataStore.edit { it[Keys.ALARM_BANNER_DISMISSED] = true }
    }

    suspend fun resetAlarmBanner() {
        dataStore.edit { it[Keys.ALARM_BANNER_DISMISSED] = false }
    }

    // ===== Clear all =====

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    // ===== Internal helpers =====

    private fun parseWidgetMap(raw: String): Map<Int, Long> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(",")
            .mapNotNull { pair ->
                val parts = pair.split(":")
                if (parts.size == 2) {
                    val widgetId = parts[0].toIntOrNull() ?: return@mapNotNull null
                    val medId = parts[1].toLongOrNull() ?: return@mapNotNull null
                    widgetId to medId
                } else null
            }
            .toMap()
    }

    private fun serializeWidgetMap(map: Map<Int, Long>): String {
        return map.entries.joinToString(",") { (widgetId, medId) -> "$widgetId:$medId" }
    }

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val WIDGET_MEDICATION_MAP = stringPreferencesKey("widget_medication_map")
        val LAST_TIMEZONE_CHANGE_MILLIS = longPreferencesKey("last_timezone_change_millis")
        val TIMEZONE_CHANGE_DISMISSED = booleanPreferencesKey("timezone_change_dismissed")
        val ALARMS_WERE_ACTIVE = booleanPreferencesKey("alarms_were_active")
        val ALARM_BANNER_DISMISSED = booleanPreferencesKey("alarm_banner_dismissed")
    }
}
