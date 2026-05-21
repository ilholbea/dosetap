package com.dosetap.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosetap.AppContainer
import com.dosetap.domain.model.DoseEvent
import com.dosetap.domain.model.Medication
import com.dosetap.util.OemDetector
import com.dosetap.util.TimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "HomeViewModel"

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
)

class HomeViewModel(
    private val container: AppContainer,
    private val context: Context,
) : ViewModel() {

    private val _showUndoBanner = MutableStateFlow(false)
    private val _lastTapId = MutableStateFlow<Long?>(null)
    private val _lastDoseTimeMillis = MutableStateFlow<Long?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    // Combines the 3 alarm-related signals into one banner-visible flag
    private val alarmMissingFlow = combine(
        container.settingsRepository.alarmsWereActive,
        container.settingsRepository.alarmBannerDismissed,
        container.alarmDao.observeActiveCount(System.currentTimeMillis()),
    ) { wasActive, dismissed, activeCount ->
        wasActive && !dismissed && activeCount == 0
    }

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            container.medicationDao.observeFirst(),
            container.doseEventDao.observeToday(TimeFormatter.startOfDay(System.currentTimeMillis())),
            alarmMissingFlow,
            container.settingsRepository.lastTimezoneChangeMillis,
        ) { medication, events, alarmMissing, lastTzChange ->
            val notificationsGranted = checkNotificationsGranted()
            val tzChangedRecently = lastTzChange > 0L &&
                (System.currentTimeMillis() - lastTzChange) < 24 * 60 * 60 * 1000L
            HomeUiState(
                medication = medication,
                todayEvents = events,
                showNotificationBanner = !notificationsGranted,
                showAlarmMissingBanner = alarmMissing,
                showReliabilityBanner = OemDetector.isKnownProblematic(),
                showTimezoneChangedBanner = tzChangedRecently,
                isLoading = false,
            )
        },
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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true),
    )

    fun logDose(medicationId: Long) {
        viewModelScope.launch {
            try {
                val tapTime = System.currentTimeMillis()
                val tapId = container.logDoseUseCase.execute(medicationId, tapTime)
                container.refreshWidgets()
                _lastTapId.value = tapId
                _lastDoseTimeMillis.value = tapTime
                _showUndoBanner.value = true
                kotlinx.coroutines.delay(com.dosetap.util.Constants.WIDGET_UNDO_WINDOW_MILLIS)
                _showUndoBanner.value = false
                _lastTapId.value = null
                _lastDoseTimeMillis.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log dose for medication $medicationId", e)
                _errorMessage.value = context.getString(com.dosetap.R.string.error_dose_log_failed)
            }
        }
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
        viewModelScope.launch {
            container.settingsRepository.dismissTimezoneChange()
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun checkNotificationsGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
    }
}
