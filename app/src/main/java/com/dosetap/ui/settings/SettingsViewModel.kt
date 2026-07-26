package com.dosetap.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosetap.AppContainer
import com.dosetap.domain.model.Medication
import com.dosetap.util.BackupManager
import com.dosetap.util.BatteryOptimizationHelper
import com.dosetap.util.OemDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "SettingsViewModel"

data class SettingsUiState(
    val medication: Medication? = null,
    val medications: List<Medication> = emptyList(),
    val isBatteryOptimized: Boolean = false,
    val isKnownBadOem: Boolean = false,
    val oemName: String = "",
    val testReminderScheduled: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val exportFileUri: Uri? = null,
    val showImportConfirm: Boolean = false,
    val importResult: BackupManager.ImportResult? = null,
    val importError: String? = null,
    val isLoading: Boolean = true,
)

private data class Extras(
    val testReminderScheduled: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val exportFileUri: Uri? = null,
    val showImportConfirm: Boolean = false,
    val importResult: BackupManager.ImportResult? = null,
    val importError: String? = null,
)

class SettingsViewModel(
    private val container: AppContainer,
    private val context: Context,
) : ViewModel() {

    private val _extras = MutableStateFlow(Extras())

    val uiState: StateFlow<SettingsUiState> = combine(
        container.medicationDao.observeAll(),
        _extras,
    ) { medications, extras ->
        SettingsUiState(
            medication = medications.firstOrNull(),
            medications = medications,
            isBatteryOptimized = !BatteryOptimizationHelper.isIgnoringBatteryOptimization(context),
            isKnownBadOem = OemDetector.isKnownProblematic(),
            oemName = OemDetector.getOemDisplayName(),
            testReminderScheduled = extras.testReminderScheduled,
            showDeleteConfirm = extras.showDeleteConfirm,
            exportFileUri = extras.exportFileUri,
            showImportConfirm = extras.showImportConfirm,
            importResult = extras.importResult,
            importError = extras.importError,
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
                val isNew = medication.id == 0L
                if (isNew) {
                    container.medicationDao.insert(medication)
                } else {
                    val old = container.medicationDao.getById(medication.id)
                    container.medicationDao.update(medication)

                    val lastTap = container.doseEventDao.getLastInitialTap(medication.id)
                    if (lastTap != null) {
                        container.scheduleFollowUpsUseCase.execute(
                            tapId = lastTap.sourceTapId,
                            medicationId = medication.id,
                            baseTimeMillis = lastTap.actionedAt,
                        )
                    }

                    // Daily reminder: cancel old, schedule new if configured
                    if (old != null && old.dailyReminderMinutes != medication.dailyReminderMinutes) {
                        container.alarmScheduler.cancelDailyReminder(old)
                    }
                    container.alarmScheduler.scheduleDailyReminder(medication)
                }
                container.refreshWidgets()
                Log.i(TAG, "Medication saved: ${medication.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save medication", e)
            }
        }
    }

    fun deleteMedication(id: Long) {
        viewModelScope.launch {
            try {
                container.alarmScheduler.cancelAllForMedication(id)
                container.medicationDao.deleteById(id)
                container.refreshWidgets()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete medication $id", e)
            }
        }
    }

    fun scheduleTestReminder() {
        viewModelScope.launch {
            try {
                val medication = container.medicationDao.getFirst() ?: return@launch
                container.alarmScheduler.scheduleTestAlarm(medication.id)
                _extras.value = _extras.value.copy(testReminderScheduled = true)
                kotlinx.coroutines.delay(3000)
                _extras.value = _extras.value.copy(testReminderScheduled = false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule test reminder", e)
            }
        }
    }

    fun showDeleteConfirm() { _extras.value = _extras.value.copy(showDeleteConfirm = true) }
    fun hideDeleteConfirm() { _extras.value = _extras.value.copy(showDeleteConfirm = false) }

    fun deleteAllData() {
        viewModelScope.launch {
            try {
                container.doseEventDao.deleteAll()
                container.medicationDao.deleteAll()
                container.alarmDao.deleteAll()
                container.settingsRepository.clearAll()
                container.refreshWidgets()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete all data", e)
            }
            _extras.value = _extras.value.copy(showDeleteConfirm = false)
        }
    }

    fun exportData() {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) { container.backupManager.exportToJson() }
                val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                val file = File(context.cacheDir, "dosetap_backup_$dateStr.json")
                file.writeText(json)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                _extras.value = _extras.value.copy(exportFileUri = uri)
                Log.i(TAG, "Data exported to $uri")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export data", e)
            }
        }
    }

    fun clearExportUri() {
        _extras.value = _extras.value.copy(exportFileUri = null)
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                        ?: throw IllegalArgumentException("Could not read file")
                }
                val result = withContext(Dispatchers.IO) {
                    container.backupManager.importFromJson(json)
                }
                container.refreshWidgets()
                _extras.value = _extras.value.copy(
                    importResult = result,
                    importError = null,
                    showImportConfirm = false,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import data", e)
                _extras.value = _extras.value.copy(
                    importError = "Import failed: ${e.message ?: "invalid file"}",
                    showImportConfirm = false,
                )
            }
        }
    }

    fun clearImportResult() {
        _extras.value = _extras.value.copy(importResult = null, importError = null)
    }

    fun unlockProViaGiftCode() {
        viewModelScope.launch { container.settingsRepository.startTrial("gift") }
    }

    fun redeemDevGrant() {
        viewModelScope.launch { container.settingsRepository.setDevGrantUnlocked() }
    }

    fun redeemPermanentGift() {
        viewModelScope.launch { container.settingsRepository.setPermanentGiftUnlocked() }
    }
}
