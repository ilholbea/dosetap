package com.dosetap.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosetap.AppContainer
import com.dosetap.domain.model.DoseAction
import com.dosetap.domain.model.DoseEvent
import com.dosetap.domain.model.Medication
import com.dosetap.util.Constants
import com.dosetap.util.OemDetector
import com.dosetap.util.TimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

private const val TAG = "HomeViewModel"

enum class DoseWarningType {
    DOUBLE_DOSE,
    LATE_DOSE,
    OFF_DAY,
}

data class DoseWarning(
    val type: DoseWarningType,
    val message: String,
)

data class HomeUiState(
    val medication: Medication? = null,
    val medications: List<Medication> = emptyList(),
    val todayEvents: List<DoseEvent> = emptyList(),
    val showNotificationBanner: Boolean = false,
    val showAlarmMissingBanner: Boolean = false,
    val showReliabilityBanner: Boolean = false,
    val showTimezoneChangedBanner: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val lastTapId: Long? = null,
    val lastTapTimeMillis: Long? = null,
    val showUndoBanner: Boolean = false,
    // ADHD warnings
    val pendingWarning: DoseWarning? = null,
    val pendingMedicationId: Long? = null,
    val pendingTapTimeMillis: Long? = null,
    // Status indicators
    val effectWindowUntilMillis: Long? = null,
    val isOffDay: Boolean = false,
    val pillsRemaining: Int? = null,
    val showRefillWarning: Boolean = false,
    // Elapsed time display (updated by ticker)
    val elapsedSinceLastDoseMillis: Long? = null,
    val nextDoseAvailableAtMillis: Long? = null,
    // Dose notes
    val pendingNoteForTapId: Long? = null,
    // Skip tomorrow badge
    val tomorrowSkipped: Boolean = false,
)

class HomeViewModel(
    private val container: AppContainer,
    private val context: Context,
) : ViewModel() {

    private val _showUndoBanner = MutableStateFlow(false)
    private val _lastTapId = MutableStateFlow<Long?>(null)
    private val _lastTapTimeMillis = MutableStateFlow<Long?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _pendingWarning = MutableStateFlow<DoseWarning?>(null)
    private val _pendingMedicationId = MutableStateFlow<Long?>(null)
    private val _pendingTapTimeMillis = MutableStateFlow<Long?>(null)
    private val _pendingNoteForTapId = MutableStateFlow<Long?>(null)

    // 1-second ticker for elapsed time updates
    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000L) // update every minute
        }
    }

    private val alarmMissingFlow = combine(
        container.settingsRepository.alarmsWereActive,
        container.settingsRepository.alarmBannerDismissed,
        container.alarmDao.observeActiveCount(System.currentTimeMillis()),
    ) { wasActive, dismissed, activeCount ->
        wasActive && !dismissed && activeCount == 0
    }

    // Selected medication: first try DataStore selection, fall back to first
    private val selectedMedFlow = combine(
        container.settingsRepository.selectedMedicationId,
        container.medicationDao.observeAll(),
    ) { selectedId, allMeds ->
        if (selectedId != -1L) allMeds.find { it.id == selectedId } ?: allMeds.firstOrNull()
        else allMeds.firstOrNull()
    }

    private val baseStateFlow = combine(
        selectedMedFlow,
        container.medicationDao.observeAll(),
        alarmMissingFlow,
        container.settingsRepository.lastTimezoneChangeMillis,
        container.settingsRepository.skipTomorrowDate,
    ) { medication, allMeds, alarmMissing, lastTzChange, skipTomorrowDate ->
        val notificationsGranted = checkNotificationsGranted()
        val tzChangedRecently = lastTzChange > 0L &&
            (System.currentTimeMillis() - lastTzChange) < 24 * 60 * 60 * 1000L
        val isOffDay = isOffDay(medication)
        val pillsRemaining = medication?.pillCount
        val showRefillWarning = pillsRemaining != null && pillsRemaining <= (medication?.refillAlertAt ?: 7)

        val tomorrowStart = TimeFormatter.startOfDay(System.currentTimeMillis() + 24 * 60 * 60 * 1000L)
        val tomorrowSkipped = skipTomorrowDate == tomorrowStart

        HomeUiState(
            medication = medication,
            medications = allMeds,
            showNotificationBanner = !notificationsGranted,
            showAlarmMissingBanner = alarmMissing,
            showReliabilityBanner = OemDetector.isKnownProblematic(),
            showTimezoneChangedBanner = tzChangedRecently,
            isLoading = false,
            isOffDay = isOffDay,
            pillsRemaining = pillsRemaining,
            showRefillWarning = showRefillWarning,
            tomorrowSkipped = tomorrowSkipped,
        )
    }

    // Observe today's events for the selected medication
    private val todayEventsFlow = selectedMedFlow.flatMapLatest { medication ->
        if (medication == null) flowOf(emptyList())
        else container.doseEventDao.observeToday(TimeFormatter.startOfDay(System.currentTimeMillis()))
    }

    // Effect window and elapsed time (rebuilt each ticker tick)
    private val timedStateFlow = combine(
        selectedMedFlow,
        todayEventsFlow,
        tickerFlow,
    ) { medication, events, nowMillis ->
        val effectWindowUntil = computeEffectWindow(medication, events)
        val lastDose = events.lastOrNull { it.action == DoseAction.INITIAL_TAP || it.action == DoseAction.TAKEN }
        val elapsed = lastDose?.let { nowMillis - it.actionedAt }
        val nextAvailable = if (effectWindowUntil != null && effectWindowUntil > nowMillis) effectWindowUntil else null
        Triple(effectWindowUntil, elapsed, nextAvailable)
    }

    private val midStateFlow = combine(
        baseStateFlow,
        todayEventsFlow,
        timedStateFlow,
    ) { state, events, (effectWindow, elapsed, nextAvailable) ->
        state.copy(
            todayEvents = events,
            effectWindowUntilMillis = effectWindow,
            elapsedSinceLastDoseMillis = elapsed,
            nextDoseAvailableAtMillis = nextAvailable,
        )
    }

    // Undo visibility derived from time (not transient state) to survive WhileSubscribed teardown
    private val undoVisibleFlow = combine(
        _lastTapTimeMillis,
        tickerFlow,
    ) { tapTimeMillis, nowMillis ->
        tapTimeMillis != null && nowMillis < tapTimeMillis + Constants.WIDGET_UNDO_WINDOW_MILLIS
    }

    val uiState: StateFlow<HomeUiState> = combine(
        midStateFlow,
        _showUndoBanner,
        undoVisibleFlow,
        _lastTapId,
        _lastTapTimeMillis,
    ) { state, showUndo, undoVisible, tapId, tapTime ->
        state.copy(
            showUndoBanner = showUndo && undoVisible,
            lastTapId = tapId,
            lastTapTimeMillis = tapTime,
        )
    }.let { flow1 ->
        combine(
            flow1,
            _errorMessage,
            _pendingWarning,
            _pendingMedicationId,
            _pendingTapTimeMillis,
        ) { state, error, warning, pendingMedId, pendingTapTime ->
            state.copy(
                errorMessage = error,
                pendingWarning = warning,
                pendingMedicationId = pendingMedId,
                pendingTapTimeMillis = pendingTapTime,
            )
        }
    }.let { flow2 ->
        combine(flow2, _pendingNoteForTapId) { state, noteForTapId ->
            state.copy(pendingNoteForTapId = noteForTapId)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true),
    )

    fun requestLogDose(medicationId: Long, tapTimeMillis: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val medication = container.medicationDao.getById(medicationId) ?: return@launch
            val warning = buildWarning(medication, tapTimeMillis)
            if (warning != null) {
                _pendingWarning.value = warning
                _pendingMedicationId.value = medicationId
                _pendingTapTimeMillis.value = tapTimeMillis
            } else {
                executeLogDose(medicationId, tapTimeMillis)
            }
        }
    }

    fun confirmLogDose() {
        val medicationId = _pendingMedicationId.value ?: return
        val tapTime = _pendingTapTimeMillis.value ?: System.currentTimeMillis()
        _pendingWarning.value = null
        _pendingMedicationId.value = null
        _pendingTapTimeMillis.value = null
        viewModelScope.launch { executeLogDose(medicationId, tapTime) }
    }

    fun cancelLogDose() {
        _pendingWarning.value = null
        _pendingMedicationId.value = null
        _pendingTapTimeMillis.value = null
    }

    fun logDoseAt(medicationId: Long, timeMillis: Long) {
        requestLogDose(medicationId, timeMillis)
    }

    fun undoLastDose() {
        val tapId = _lastTapId.value ?: return
        viewModelScope.launch {
            try {
                container.logDoseUseCase.undo(tapId)
                val med = uiState.value.medication
                val lastDose = med?.let { container.doseEventDao.getLastConfirmedDose(it.id) }
                container.ambientNotificationManager.update(med, lastDose)
                container.refreshWidgets()
                _showUndoBanner.value = false
                _lastTapId.value = null
                _lastTapTimeMillis.value = null
                _pendingNoteForTapId.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to undo dose tapId=$tapId", e)
            }
        }
    }

    fun rescheduleAlarms() {
        viewModelScope.launch {
            try {
                container.alarmScheduler.rescheduleAll()
                container.settingsRepository.dismissAlarmBanner()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule alarms", e)
            }
        }
    }

    fun dismissTimezoneChange() {
        viewModelScope.launch { container.settingsRepository.dismissTimezoneChange() }
    }

    fun clearError() { _errorMessage.value = null }

    fun unlockViaGiftCode() {
        viewModelScope.launch { container.settingsRepository.setGiftCodeUnlocked(true) }
    }

    fun selectMedication(medicationId: Long) {
        viewModelScope.launch { container.settingsRepository.setSelectedMedicationId(medicationId) }
    }

    fun skipTomorrow() {
        viewModelScope.launch {
            val tomorrowStart = TimeFormatter.startOfDay(
                System.currentTimeMillis() + 24 * 60 * 60 * 1000L
            )
            container.settingsRepository.setSkipTomorrow(tomorrowStart)
        }
    }

    fun clearSkipTomorrow() {
        viewModelScope.launch { container.settingsRepository.clearSkipTomorrow() }
    }

    fun saveNote(tapId: Long, note: String?, tags: String?) {
        viewModelScope.launch {
            try {
                container.doseEventDao.updateNote(tapId, note, tags)
                _pendingNoteForTapId.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save dose note", e)
            }
        }
    }

    fun dismissNoteCard() {
        _pendingNoteForTapId.value = null
    }

    // ===== Private helpers =====

    private suspend fun executeLogDose(medicationId: Long, tapTimeMillis: Long) {
        try {
            val tapId = container.logDoseUseCase.execute(medicationId, tapTimeMillis)
            val med = container.medicationDao.getById(medicationId)
            val lastDose = med?.let { container.doseEventDao.getLastConfirmedDose(it.id) }
            container.ambientNotificationManager.update(med, lastDose)
            container.refreshWidgets()
            _lastTapId.value = tapId
            _lastTapTimeMillis.value = tapTimeMillis
            _showUndoBanner.value = true
            _pendingNoteForTapId.value = tapId

            // Wait for undo window, then clear — but derive visibility from time, not this delay
            delay(Constants.WIDGET_UNDO_WINDOW_MILLIS + 500L)
            _showUndoBanner.value = false
            _lastTapId.value = null
            _lastTapTimeMillis.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log dose for medication $medicationId", e)
            _errorMessage.value = context.getString(com.dosetap.R.string.error_dose_log_failed)
        }
    }

    private suspend fun buildWarning(medication: Medication, tapTimeMillis: Long): DoseWarning? {
        val todayStart = TimeFormatter.startOfDay(tapTimeMillis)
        val todayEvents = container.doseEventDao.getForMedicationSince(medication.id, todayStart)

        if (medication.offDays.isNotEmpty()) {
            val cal = Calendar.getInstance().apply { timeInMillis = tapTimeMillis }
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            if (dow in medication.offDays) {
                return DoseWarning(
                    type = DoseWarningType.OFF_DAY,
                    message = "Today is a scheduled off-day for ${medication.name}. Are you sure you want to log a dose?",
                )
            }
        }

        val cutoff = medication.lateCutoffMinutes
        if (cutoff != null) {
            val cal = Calendar.getInstance().apply { timeInMillis = tapTimeMillis }
            val minutesNow = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            if (minutesNow >= cutoff) {
                val h = cutoff / 60
                val m = cutoff % 60
                val amPm = if (h < 12) "AM" else "PM"
                val h12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
                return DoseWarning(
                    type = DoseWarningType.LATE_DOSE,
                    message = "Heads up — taking ${medication.name} after %d:%02d %s may affect sleep. Log anyway?".format(h12, m, amPm),
                )
            }
        }

        val durationMinutes = medication.durationMinutes
        if (durationMinutes != null) {
            val lastTap = todayEvents.lastOrNull {
                it.action == DoseAction.INITIAL_TAP || it.action == DoseAction.TAKEN
            }
            if (lastTap != null) {
                val effectWindowEnd = lastTap.actionedAt + durationMinutes * 60_000L
                if (tapTimeMillis < effectWindowEnd) {
                    return DoseWarning(
                        type = DoseWarningType.DOUBLE_DOSE,
                        message = "${medication.name} may still be active. Logging another dose so soon could cause double-dosing. Are you sure?",
                    )
                }
            }
        } else {
            val recentThreshold = tapTimeMillis - 3 * 60 * 60 * 1000L
            val recentTap = todayEvents.lastOrNull {
                (it.action == DoseAction.INITIAL_TAP || it.action == DoseAction.TAKEN) &&
                    it.actionedAt >= recentThreshold
            }
            if (recentTap != null) {
                return DoseWarning(
                    type = DoseWarningType.DOUBLE_DOSE,
                    message = "You already logged a dose of ${medication.name} less than 3 hours ago. Log another?",
                )
            }
        }

        return null
    }

    private fun computeEffectWindow(medication: Medication?, events: List<DoseEvent>): Long? {
        val duration = medication?.durationMinutes ?: return null
        val lastTap = events.lastOrNull {
            it.action == DoseAction.INITIAL_TAP || it.action == DoseAction.TAKEN
        } ?: return null
        val windowEnd = lastTap.actionedAt + duration * 60_000L
        return if (windowEnd > System.currentTimeMillis()) windowEnd else null
    }

    private fun isOffDay(medication: Medication?): Boolean {
        if (medication == null || medication.offDays.isEmpty()) return false
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return dow in medication.offDays
    }

    private fun checkNotificationsGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
    }
}
