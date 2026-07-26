package com.dosetap.ui.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosetap.AppContainer
import com.dosetap.alarm.FocusAlarmReceiver
import com.dosetap.util.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FocusMode(val label: String, val emoji: String, val defaultSeconds: Int) {
    FOCUS("Focus", "🧠", 25 * 60),
    TASK("Task", "⏱️", 0),
    TRANSITION("Transition", "🔔", 10 * 60),
    BREAK("Break", "☕", 5 * 60),
}

data class FocusUiState(
    val mode: FocusMode = FocusMode.FOCUS,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val durationSeconds: Int = FocusMode.FOCUS.defaultSeconds,
    val elapsedSeconds: Int = 0,
    val taskNote: String = "",
    val medWindowEndMillis: Long? = null,
    val sessionsToday: Int = 0,
    val isComplete: Boolean = false,
) {
    val remainingSeconds: Int get() = (durationSeconds - elapsedSeconds).coerceAtLeast(0)
    val isCountUp: Boolean get() = mode == FocusMode.TASK
    /** Progress 0→1. For count-up, sweeps over 25-min laps for visual rhythm. */
    val progress: Float get() = when {
        isCountUp -> (elapsedSeconds % (25 * 60)).toFloat() / (25 * 60)
        durationSeconds == 0 -> 0f
        else -> (elapsedSeconds.toFloat() / durationSeconds).coerceIn(0f, 1f)
    }
    val displaySeconds: Int get() = if (isCountUp) elapsedSeconds else remainingSeconds
}

class FocusTimerViewModel(
    private val container: AppContainer,
    private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var adjustedStartMillis = 0L

    init {
        viewModelScope.launch {
            // Load med effect window from most recent confirmed dose
            val med = container.medicationDao.getFirst()
            if (med?.durationMinutes != null) {
                val lastDose = container.doseEventDao.getLastConfirmedDose(med.id)
                if (lastDose != null) {
                    val windowEnd = lastDose.actionedAt + med.durationMinutes * 60_000L
                    if (windowEnd > System.currentTimeMillis()) {
                        _uiState.value = _uiState.value.copy(medWindowEndMillis = windowEnd)
                    }
                }
            }
            // Load today's session count
            container.settingsRepository.focusSessionsToday.collect { count ->
                _uiState.value = _uiState.value.copy(sessionsToday = count)
            }
        }
    }

    fun selectMode(mode: FocusMode) {
        if (_uiState.value.isRunning) return
        _uiState.value = FocusUiState(
            mode = mode,
            durationSeconds = mode.defaultSeconds,
            medWindowEndMillis = _uiState.value.medWindowEndMillis,
            sessionsToday = _uiState.value.sessionsToday,
        )
    }

    fun adjustDuration(deltaSeconds: Int) {
        val state = _uiState.value
        if (state.isRunning || state.isCountUp) return
        val newDuration = (state.durationSeconds + deltaSeconds).coerceIn(60, 120 * 60)
        _uiState.value = state.copy(durationSeconds = newDuration)
    }

    fun updateTaskNote(note: String) {
        _uiState.value = _uiState.value.copy(taskNote = note)
    }

    fun start() {
        val state = _uiState.value
        if (state.isRunning) return
        val elapsedAtResume = if (state.isPaused) state.elapsedSeconds else 0
        adjustedStartMillis = System.currentTimeMillis() - (elapsedAtResume * 1000L)
        _uiState.value = state.copy(isRunning = true, isPaused = false, isComplete = false)
        startTicker()
        if (!state.isCountUp) {
            val remainingMillis = if (state.isPaused) {
                state.remainingSeconds * 1000L
            } else {
                state.durationSeconds * 1000L
            }
            scheduleAlarm(remainingMillis, state.mode)
        }
    }

    fun pause() {
        tickerJob?.cancel()
        cancelAlarm()
        _uiState.value = _uiState.value.copy(isRunning = false, isPaused = true)
    }

    fun reset() {
        tickerJob?.cancel()
        cancelAlarm()
        val state = _uiState.value
        _uiState.value = FocusUiState(
            mode = state.mode,
            durationSeconds = state.durationSeconds,
            medWindowEndMillis = state.medWindowEndMillis,
            sessionsToday = state.sessionsToday,
        )
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(250)
                val elapsed = ((System.currentTimeMillis() - adjustedStartMillis) / 1000L).toInt().coerceAtLeast(0)
                val state = _uiState.value
                _uiState.value = state.copy(elapsedSeconds = elapsed)
                if (!state.isCountUp && elapsed >= state.durationSeconds) {
                    onComplete()
                    break
                }
            }
        }
    }

    private fun onComplete() {
        tickerJob?.cancel()
        val state = _uiState.value
        _uiState.value = state.copy(
            isRunning = false,
            isComplete = true,
            elapsedSeconds = state.durationSeconds,
        )
        viewModelScope.launch {
            container.settingsRepository.incrementFocusSessions()
        }
    }

    private fun scheduleAlarm(delayMillis: Long, mode: FocusMode) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, FocusAlarmReceiver::class.java).apply {
            action = Constants.ACTION_FOCUS_TIMER_DONE
            putExtra(FocusAlarmReceiver.EXTRA_MODE, mode.name)
        }
        val pi = PendingIntent.getBroadcast(
            context, Constants.FOCUS_TIMER_REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Guard the exact-alarm permission (E-H3) — falls back to inexact if not granted, so the
        // timer never crashes with SecurityException on Android 13+ without the grant.
        val triggerAt = System.currentTimeMillis() + delayMillis
        val canExact = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun cancelAlarm() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, FocusAlarmReceiver::class.java).apply {
            action = Constants.ACTION_FOCUS_TIMER_DONE
        }
        val pi = PendingIntent.getBroadcast(
            context, Constants.FOCUS_TIMER_REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarmManager.cancel(pi)
    }

    override fun onCleared() {
        tickerJob?.cancel()
        // Alarm intentionally left active — fires notification even when app is closed
    }
}
