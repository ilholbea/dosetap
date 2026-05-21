package com.dosetap.ui.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosetap.AppContainer
import com.dosetap.domain.model.Medication
import com.dosetap.util.BatteryOptimizationHelper
import com.dosetap.util.OemDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TAG = "SettingsViewModel"

data class SettingsUiState(
    val medication: Medication? = null,
    val isBatteryOptimized: Boolean = false,
    val isKnownBadOem: Boolean = false,
    val oemName: String = "",
    val testReminderScheduled: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val exportData: String? = null,
    val isLoading: Boolean = true,
)

class SettingsViewModel(
    private val container: AppContainer,
    private val context: Context,
) : ViewModel() {

    private val _extras = MutableStateFlow(
        Triple(
            false, // testReminderScheduled
            false, // showDeleteConfirm
            null as String?, // exportData
        )
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        container.medicationDao.observeFirst(),
        _extras,
    ) { medication, (testScheduled, showDelete, exportData) ->
        SettingsUiState(
            medication = medication,
            isBatteryOptimized = !BatteryOptimizationHelper.isIgnoringBatteryOptimization(context),
            isKnownBadOem = OemDetector.isKnownProblematic(),
            oemName = OemDetector.getOemDisplayName(),
            testReminderScheduled = testScheduled,
            showDeleteConfirm = showDelete,
            exportData = exportData,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(isLoading = true),
    )

    fun saveMedication(medication: Medication) {
        viewModelScope.launch {
            try {
                if (medication.id == 0L) {
                    container.medicationDao.insert(medication)
                } else {
                    container.medicationDao.update(medication)
                    // Reschedule any pending alarms with new intervals
                    val lastTap = container.doseEventDao.getLastInitialTap(medication.id)
                    if (lastTap != null) {
                        container.scheduleFollowUpsUseCase.execute(
                            tapId = lastTap.sourceTapId,
                            medicationId = medication.id,
                            baseTimeMillis = lastTap.actionedAt,
                        )
                    }
                }
                container.refreshWidgets()
                Log.i(TAG, "Medication saved: ${medication.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save medication", e)
            }
        }
    }

    fun scheduleTestReminder() {
        viewModelScope.launch {
            try {
                val medication = container.medicationDao.getFirst() ?: return@launch
                container.alarmScheduler.scheduleTestAlarm(medication.id)
                _extras.value = _extras.value.copy(first = true)
                kotlinx.coroutines.delay(3000)
                _extras.value = _extras.value.copy(first = false)
                Log.i(TAG, "Test reminder scheduled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule test reminder", e)
            }
        }
    }

    fun showDeleteConfirm() {
        _extras.value = _extras.value.copy(second = true)
    }

    fun hideDeleteConfirm() {
        _extras.value = _extras.value.copy(second = false)
    }

    fun deleteAllData() {
        viewModelScope.launch {
            try {
                container.doseEventDao.deleteAll()
                container.medicationDao.deleteAll()
                container.alarmDao.deleteAll()
                container.settingsRepository.clearAll()
                container.refreshWidgets()
                Log.i(TAG, "All data deleted")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete all data", e)
            }
            _extras.value = _extras.value.copy(second = false)
        }
    }

    fun exportData() {
        viewModelScope.launch {
            try {
                val events = container.doseEventDao.getAll()
                val medications = container.medicationDao.getFirst()?.let { listOf(it) } ?: emptyList()
                val export: JsonObject = buildJsonObject {
                    put("medications", buildJsonArray {
                        medications.forEach { med ->
                            add(buildJsonObject {
                                put("id", med.id)
                                put("name", med.name)
                                put("followUps", buildJsonArray { med.followUps.forEach { add(JsonPrimitive(it)) } })
                                put("createdAt", med.createdAt)
                            })
                        }
                    })
                    put("doseEvents", buildJsonArray {
                        events.forEach { event ->
                            add(buildJsonObject {
                                put("id", event.id)
                                put("medicationId", event.medicationId)
                                put("scheduledAt", event.scheduledAt)
                                put("actionedAt", event.actionedAt)
                                put("action", event.action.name)
                            })
                        }
                    })
                    put("exportedAt", System.currentTimeMillis())
                    put("appVersion", "1.0.0")
                }
                val json = Json { prettyPrint = true }
                val jsonString = json.encodeToString(JsonObject.serializer(), export)
                _extras.value = _extras.value.copy(third = jsonString)
                Log.i(TAG, "Data exported: ${events.size} events")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export data", e)
            }
        }
    }

    fun clearExportData() {
        _extras.value = _extras.value.copy(third = null)
    }
}
