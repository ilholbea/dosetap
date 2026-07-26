package com.dosetap.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    suspend fun getWidgetMedication(widgetId: Int): Long {
        val raw = dataStore.data.first()[Keys.WIDGET_MEDICATION_MAP] ?: return -1L
        return parseWidgetMap(raw)[widgetId] ?: -1L
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

    // ===== Pro unlock (gift code path) =====

    val giftCodeUnlocked: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.GIFT_CODE_UNLOCKED] ?: false
    }

    suspend fun setGiftCodeUnlocked(unlocked: Boolean) {
        dataStore.edit { it[Keys.GIFT_CODE_UNLOCKED] = unlocked }
    }

    // ===== Cached Pro entitlement (billing offline resilience) =====

    /**
     * Last known Pro entitlement from a *successful* Play Billing query, persisted so a
     * paying user isn't shown a paywall on a cold start where billing is offline/slow.
     * Only a successful query is allowed to write this (including writing `false`);
     * a failed/unreachable query must never downgrade it. See [BillingManager].
     */
    val proEntitlementCached: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.PRO_ENTITLEMENT_CACHED] ?: false
    }

    suspend fun setProEntitlementCached(isPro: Boolean) {
        dataStore.edit { it[Keys.PRO_ENTITLEMENT_CACHED] = isPro }
    }

    // ===== Developer grant (permanent Pro, issued by developer) =====

    val devGrantUnlocked: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.DEV_GRANT_UNLOCKED] ?: false
    }

    suspend fun setDevGrantUnlocked() {
        dataStore.edit { it[Keys.DEV_GRANT_UNLOCKED] = true }
    }

    // ===== Pause reminders =====

    val pausedUntilMillis: Flow<Long> = dataStore.data.map { prefs ->
        prefs[Keys.PAUSED_UNTIL_MILLIS] ?: 0L
    }

    suspend fun setPausedUntilMillis(millis: Long) {
        dataStore.edit { it[Keys.PAUSED_UNTIL_MILLIS] = millis }
    }

    // ===== Sponsor-a-friend =====

    /** Number of sponsor gift slots used on this device (Pro users get 1). */
    val sponsorSlotsUsed: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.SPONSOR_SLOTS_USED] ?: 0
    }

    suspend fun incrementSponsorSlots() {
        dataStore.edit { it[Keys.SPONSOR_SLOTS_USED] = (it[Keys.SPONSOR_SLOTS_USED] ?: 0) + 1 }
    }

    // ===== Multi-medication home switcher =====

    val selectedMedicationId: Flow<Long> = dataStore.data.map { prefs ->
        prefs[Keys.SELECTED_MEDICATION_ID] ?: -1L
    }

    suspend fun setSelectedMedicationId(id: Long) {
        dataStore.edit { it[Keys.SELECTED_MEDICATION_ID] = id }
    }

    // ===== Skip tomorrow =====

    /** UTC start-of-day millis for the day to skip. -1 means no skip pending. */
    val skipTomorrowDate: Flow<Long> = dataStore.data.map { prefs ->
        prefs[Keys.SKIP_TOMORROW_DATE] ?: -1L
    }

    suspend fun setSkipTomorrow(dayStartMillis: Long) {
        dataStore.edit { it[Keys.SKIP_TOMORROW_DATE] = dayStartMillis }
    }

    suspend fun clearSkipTomorrow() {
        dataStore.edit { it[Keys.SKIP_TOMORROW_DATE] = -1L }
    }

    suspend fun getSkipTomorrowDate(): Long {
        return dataStore.data.map { it[Keys.SKIP_TOMORROW_DATE] ?: -1L }.first()
    }

    // ===== 14-day trial (sponsor / gift code) =====

    val trialStartedAtMillis: Flow<Long> = dataStore.data.map { prefs ->
        prefs[Keys.TRIAL_STARTED_AT_MILLIS] ?: 0L
    }

    val trialSource: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.TRIAL_SOURCE] ?: ""
    }

    val trialExpiredDismissed: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.TRIAL_EXPIRED_DISMISSED] ?: false
    }

    /** Starts a 14-day trial. Returns false if a trial was already used on this device. */
    suspend fun startTrial(source: String): Boolean {
        val existing = dataStore.data.first()[Keys.TRIAL_STARTED_AT_MILLIS] ?: 0L
        if (existing > 0L) return false
        dataStore.edit {
            it[Keys.TRIAL_STARTED_AT_MILLIS] = System.currentTimeMillis()
            it[Keys.TRIAL_SOURCE] = source
            it[Keys.TRIAL_EXPIRED_DISMISSED] = false
        }
        return true
    }

    suspend fun dismissTrialExpired() {
        dataStore.edit { it[Keys.TRIAL_EXPIRED_DISMISSED] = true }
    }

    // ===== Celebrations =====

    val celebrationsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.CELEBRATIONS_ENABLED] ?: true
    }

    suspend fun setCelebrationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.CELEBRATIONS_ENABLED] = enabled }
    }

    val lastCelebratedStreak: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.LAST_CELEBRATED_STREAK] ?: 0
    }

    suspend fun setLastCelebratedStreak(streak: Int) {
        dataStore.edit { it[Keys.LAST_CELEBRATED_STREAK] = streak }
    }

    /**
     * Picks a celebration-copy index for a pool of [poolSize] lines, avoiding the last 5 shown so
     * the same message never repeats too soon. Records the choice in a small ring buffer.
     */
    suspend fun pickCelebrationIndex(poolSize: Int): Int {
        if (poolSize <= 0) return 0
        var chosen = 0
        dataStore.edit { prefs ->
            val recent = (prefs[Keys.CELEBRATION_RECENT] ?: "")
                .split(",").mapNotNull { it.toIntOrNull() }
            val candidates = (0 until poolSize).filter { it !in recent }
            chosen = (if (candidates.isNotEmpty()) candidates else (0 until poolSize).toList()).random()
            val updated = (recent + chosen).takeLast(5)
            prefs[Keys.CELEBRATION_RECENT] = updated.joinToString(",")
        }
        return chosen
    }

    // ===== Brain Dump =====

    val brainDumpItems: Flow<List<String>> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.BRAIN_DUMP] ?: ""
        raw.split("\n").filter { it.isNotBlank() }
    }

    suspend fun addBrainDumpItem(text: String) {
        dataStore.edit { prefs ->
            val existing = (prefs[Keys.BRAIN_DUMP] ?: "").split("\n").filter { it.isNotBlank() }
            val updated = (existing + text.replace("\n", " "))
                .takeLast(com.dosetap.util.Constants.BRAIN_DUMP_MAX_ITEMS)
            prefs[Keys.BRAIN_DUMP] = updated.joinToString("\n")
        }
    }

    suspend fun removeBrainDumpItem(text: String) {
        dataStore.edit { prefs ->
            val existing = (prefs[Keys.BRAIN_DUMP] ?: "").split("\n").filter { it.isNotBlank() }
            prefs[Keys.BRAIN_DUMP] = existing.filter { it != text }.joinToString("\n")
        }
    }

    suspend fun clearBrainDump() {
        dataStore.edit { it[Keys.BRAIN_DUMP] = "" }
    }

    /** Moves a brain-dump thought to the Quicklist in one edit (Spec #6). */
    suspend fun promoteBrainDumpItemToQuicklist(text: String) {
        dataStore.edit { prefs ->
            val dump = (prefs[Keys.BRAIN_DUMP] ?: "").split("\n").filter { it.isNotBlank() }
            prefs[Keys.BRAIN_DUMP] = dump.filter { it != text }.joinToString("\n")

            val quick = prefs[Keys.QUICKLIST] ?: ""
            val newLine = "0|${text.replace("\n", " ")}"
            prefs[Keys.QUICKLIST] = if (quick.isBlank()) newLine else "$quick\n$newLine"
        }
    }

    // ===== Quicklist =====

    /** Each item is stored as "0|text" (todo) or "1|text" (done). */
    val quicklistItems: Flow<List<Pair<String, Boolean>>> = dataStore.data.map { prefs ->
        (prefs[Keys.QUICKLIST] ?: "").split("\n").filter { it.isNotBlank() }.map { line ->
            val idx = line.indexOf('|')
            if (idx > 0) Pair(line.substring(idx + 1), line[0] == '1') else Pair(line, false)
        }
    }

    suspend fun addQuickItem(text: String) {
        dataStore.edit { prefs ->
            val existing = prefs[Keys.QUICKLIST] ?: ""
            val newLine = "0|${text.replace("\n", " ")}"
            prefs[Keys.QUICKLIST] = if (existing.isBlank()) newLine else "$existing\n$newLine"
        }
    }

    suspend fun toggleQuickItem(text: String) {
        dataStore.edit { prefs ->
            val lines = (prefs[Keys.QUICKLIST] ?: "").split("\n").filter { it.isNotBlank() }
            prefs[Keys.QUICKLIST] = lines.map { line ->
                val idx = line.indexOf('|')
                val lineText = if (idx > 0) line.substring(idx + 1) else line
                if (lineText == text) {
                    val done = idx > 0 && line[0] == '1'
                    "${if (done) 0 else 1}|$lineText"
                } else line
            }.joinToString("\n")
        }
    }

    suspend fun clearDoneQuickItems() {
        dataStore.edit { prefs ->
            val lines = (prefs[Keys.QUICKLIST] ?: "").split("\n").filter { it.isNotBlank() }
            prefs[Keys.QUICKLIST] = lines.filter { it.startsWith("0|") || it.indexOf('|') < 0 }.joinToString("\n")
        }
    }

    suspend fun clearQuickList() {
        dataStore.edit { it[Keys.QUICKLIST] = "" }
    }

    // ===== Focus Timer sessions =====

    val focusSessionsToday: Flow<Int> = dataStore.data.map { prefs ->
        val dayStart = (System.currentTimeMillis() / 86_400_000L) * 86_400_000L
        val savedDay = prefs[Keys.FOCUS_SESSIONS_DAY] ?: 0L
        if (savedDay == dayStart) prefs[Keys.FOCUS_SESSIONS_COUNT] ?: 0 else 0
    }

    suspend fun incrementFocusSessions() {
        dataStore.edit { prefs ->
            val dayStart = (System.currentTimeMillis() / 86_400_000L) * 86_400_000L
            val savedDay = prefs[Keys.FOCUS_SESSIONS_DAY] ?: 0L
            val count = if (savedDay == dayStart) (prefs[Keys.FOCUS_SESSIONS_COUNT] ?: 0) + 1 else 1
            prefs[Keys.FOCUS_SESSIONS_DAY] = dayStart
            prefs[Keys.FOCUS_SESSIONS_COUNT] = count
        }
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
        val GIFT_CODE_UNLOCKED = booleanPreferencesKey("gift_code_unlocked")
        val SPONSOR_SLOTS_USED = intPreferencesKey("sponsor_slots_used")
        val SELECTED_MEDICATION_ID = longPreferencesKey("selected_medication_id")
        val SKIP_TOMORROW_DATE = longPreferencesKey("skip_tomorrow_date")
        val TRIAL_STARTED_AT_MILLIS = longPreferencesKey("trial_started_at_millis")
        val TRIAL_SOURCE = stringPreferencesKey("trial_source")
        val TRIAL_EXPIRED_DISMISSED = booleanPreferencesKey("trial_expired_dismissed")
        val DEV_GRANT_UNLOCKED = booleanPreferencesKey("dev_grant_unlocked")
        val PRO_ENTITLEMENT_CACHED = booleanPreferencesKey("pro_entitlement_cached")
        val PAUSED_UNTIL_MILLIS = longPreferencesKey("paused_until_millis")
        val CELEBRATIONS_ENABLED = booleanPreferencesKey("celebrations_enabled")
        val LAST_CELEBRATED_STREAK = intPreferencesKey("last_celebrated_streak")
        val CELEBRATION_RECENT = stringPreferencesKey("celebration_recent_indices")
        val BRAIN_DUMP = stringPreferencesKey("brain_dump")
        val FOCUS_SESSIONS_DAY = longPreferencesKey("focus_sessions_day")
        val FOCUS_SESSIONS_COUNT = intPreferencesKey("focus_sessions_count")
        val QUICKLIST = stringPreferencesKey("quicklist")
    }
}
