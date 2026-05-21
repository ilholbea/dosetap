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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

private const val TAG = "HomeViewModel"

enum class DoseWarningType {
    DOUBLE_DOSE,   // another dose was logged recently (within effect window)
    LATE_DOSE,     // past the "too late" cutoff for this medication
    OFF_DAY,       // today is a configured off-day
}

data class DoseWarning(
    val type: DoseWarningType,
    val message: String,
)

data class HomeUiState(
    val medication: Medication? = null,
    val todayEvents: List<DoseEvent> = emptyList(),
    val showNotificationBanner: Boolean = false,
    val showAlarmMissingBanner: Boolean = false,
    val showReliabilityBanner: Boolean = false,
    val showTimezoneChangedBanner: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val lastTapId: Long? = null,
    val lastDoseTimeMillis: Long? = null,
    val showUndoBanner: Boolean = false,
    // ADHD warnings
    val pendingWarning: DoseWarning? = null,
    val pendingMedicationId: Long? = null,
    val pendingTapTimeMillis: Long? = null,
    // Status indicators
    val effectWindowUntilMillis: Long? = null,  // null = no active effect window
    val isOffDay: Boolean = false,
    val pillsRemaining: Int? = null,
    val showRefillWarning: Boolean = false,
)

class HomeViewModel(
    private val container: AppContainer,
    private val context: Context,
) : ViewModel() {

    private val _showUndoBanner = MutableStateFlow(false)
    private val _lastTapId = MutableStateFlow<Long?>(null)
    private val _lastDoseTimeMillis = MutableStateFlow<Long?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _pendingWarning = MutableStateFlow<DoseWarning?>(null)
    private val _pendingMedicationId = MutableStateFlow<Long?>(null)
    private val _pendingTapTimeMillis = MutableStateFlow<Long?>(null)

    private val alarmMissingFlow = combine(
        container.settingsRepository.alarmsWereActive,
        container.settingsRepository.alarmBannerDismissed,
        container.alarmDao.observeActiveCount(System.currentTimeMillis()),
    ) { wasActive, dismissed, activeCount ->
        wasActive && !dismissed && activeCount == 0
    }

    // Inner combine: medication + events + alarm state → base HomeUiState
    private val baseStateFlow = combine(
        container.medicationDao.observeFirst(),
        container.doseEventDao.observeToday(TimeFormatter.startOfDay(System.currentTimeMillis())),
        alarmMissingFlow,
        container.settingsRepository.lastTimezoneChangeMillis,
    ) { medication, events, alarmMissing, lastTzChange ->
        val notificationsGranted = checkNotificationsGranted()
        val tzChangedRecently = lastTzChange > 0L &&
            (System.currentTimeMillis() - lastTzChange) < 24 * 60 * 60 * 1000L
        val effectWindowUntil = computeEffectWindow(medication, events)
        val isOffDay = isOffDay(medication)
        val pillsRemaining = medication?.pillCount
        val showRefillWarning = pillsRemaining != null && pillsRemaining <= (medication?.refillAlertAt ?: 7)
        HomeUiState(
            medication = medication,
            todayEvents = events,
            showNotificationBanner = !notificationsGranted,
            showAlarmMissingBanner = alarmMissing,
            showReliabilityBanner = OemDetector.isKnownProblematic(),
            showTimezoneChangedBanner = tzChangedRecently,
            isLoading = false,
            effectWindowUntilMillis = effectWindowUntil,
            isOffDay = isOffDay,
            pillsRemaining = pillsRemaining,
            showRefillWarning = showRefillWarning,
        )
    }

    // Middle combine: add undo/tap/error transient state (max 5 flows)
    private val midStateFlow = combine(
        baseStateFlow,
        _showUndoBanner,
        _lastTapId,
        _lastDoseTimeMillis,
        _errorMessage,
    ) { state, showUndo, tapId, doseTime, error ->
        state.copy(
            showUndoBanner = showUndo,
            lastTapId = tapId,
            lastDoseTimeMillis = doseTime,
            errorMessage = error,
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        midStateFlow,
        _pendingWarning,
        _pendingMedicationId,
        _pendingTapTimeMillis,
    ) { state, warning, pendingMedId, pendingTapTime ->
        state.copy(
            pendingWarning = warning,
            pendingMedicationId = pendingMedId,
            pendingTapTimeMillis = pendingTapTime,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true),
    )

    /** Entry point for dose logging — checks for warnings before committing. */
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

    /** Called when user confirms a warning dialog. */
    fun confirmLogDose() {
        val medicationId = _pendingMedicationId.value ?: return
        val tapTime = _pendingTapTimeMillis.value ?: System.currentTimeMillis()
        _pendingWarning.value = null
        _pendingMedicationId.value = null
        _pendingTapTimeMillis.value = null
        viewModelScope.launch { executeLogDose(medicationId, tapTime) }
    }

    /** Called when user dismisses a warning dialog without logging. */
    fun cancelLogDose() {
        _pendingWarning.value = null
        _pendingMedicationId.value = null
        _pendingTapTimeMillis.value = null
    }

    /** Log a dose at an arbitrary time (backdate). Shows warnings. */
    fun logDoseAt(medicationId: Long, timeMillis: Long) {
        requestLogDose(medicationId, timeMillis)
    }

    fun undoLastDose() {
        val tapId = _lastTapId.value ?: return
        viewModelScope.launch {
            try {
                container.logDoseUseCase.undo(tapId)
                container.refreshWidgets()
                _showUndoBanner.value = false
                _lastTapId.value = null
                _lastDoseTimeMillis.value = null
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

    // ===== Private helpers =====

    private suspend fun executeLogDose(medicationId: Long, tapTimeMillis: Long) {
        try {
            val tapId = container.logDoseUseCase.execute(medicationId, tapTimeMillis)
            container.refreshWidgets()
            _lastTapId.value = tapId
            _lastDoseTimeMillis.value = tapTimeMillis
            _showUndoBanner.value = true
            kotlinx.coroutines.delay(Constants.WIDGET_UNDO_WINDOW_MILLIS)
            _showUndoBanner.value = false
            _lastTapId.value = null
            _lastDoseTimeMillis.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log dose for medication $medicationId", e)
            _errorMessage.value = context.getString(com.dosetap.R.string.error_dose_log_failed)
        }
    }

    private suspend fun buildWarning(medication: Medication, tapTimeMillis: Long): DoseWarning? {
        val todayStart = TimeFormatter.startOfDay(tapTimeMillis)
        val todayEvents = container.doseEventDao.getForMedicationSince(medication.id, todayStart)

        // Off-day check
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

        // Late-dose check
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
                    message = "It's past %d:%02d %s — your configured cutoff for ${medication.name}. Taking it now may affect sleep. Log anyway?".format(h12, m, amPm),
                )
            }
        }

        // Double-dose check: already logged within the effect window
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
                        message = "${medication.name} may still be active (effect window hasn't ended). Logging another dose so soon could cause double-dosing. Are you sure?",
                    )
                }
            }
        } else {
            // Even without duration: warn if another dose was logged in the last 3 hours
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
