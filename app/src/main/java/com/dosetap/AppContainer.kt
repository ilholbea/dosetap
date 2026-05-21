package com.dosetap

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.dosetap.alarm.AlarmScheduler
import com.dosetap.widget.DoseWidget
import com.dosetap.widget.LastDoseTimeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.dosetap.data.db.AlarmDao
import com.dosetap.data.db.AppDatabase
import com.dosetap.data.db.DoseEventDao
import com.dosetap.data.db.MedicationDao
import com.dosetap.data.prefs.SettingsRepository
import com.dosetap.domain.usecase.HandleDoseActionUseCase
import com.dosetap.domain.usecase.LogDoseUseCase
import com.dosetap.domain.usecase.ScheduleFollowUpsUseCase
import com.dosetap.billing.BillingManager
import com.dosetap.billing.GiftCodeManager
import com.dosetap.util.BackupManager

/**
 * Manual DI container. Created once in [DoseTapApp] and held for the app lifetime.
 *
 * All dependencies use [Context.applicationContext] to avoid Activity/Fragment leaks.
 *
 * Architecture for Pro upgrade: inject FeatureFlag into use cases here when billing is added.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    // ===== Database =====
    private val database: AppDatabase by lazy {
        AppDatabase.getInstance(appContext)
    }

    val medicationDao: MedicationDao by lazy { database.medicationDao() }
    val doseEventDao: DoseEventDao by lazy { database.doseEventDao() }
    val alarmDao: AlarmDao by lazy { database.alarmDao() }

    // ===== Preferences =====
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(appContext)
    }

    // ===== Alarm =====
    val alarmScheduler: AlarmScheduler by lazy {
        AlarmScheduler(
            context = appContext,
            alarmDao = alarmDao,
            medicationDao = medicationDao,
            settingsRepository = settingsRepository,
        )
    }

    // ===== Use cases =====
    val logDoseUseCase: LogDoseUseCase by lazy {
        LogDoseUseCase(
            doseEventDao = doseEventDao,
            alarmScheduler = alarmScheduler,
            medicationDao = medicationDao,
        )
    }

    val scheduleFollowUpsUseCase: ScheduleFollowUpsUseCase by lazy {
        ScheduleFollowUpsUseCase(
            alarmScheduler = alarmScheduler,
            medicationDao = medicationDao,
        )
    }

    val handleDoseActionUseCase: HandleDoseActionUseCase by lazy {
        HandleDoseActionUseCase(
            doseEventDao = doseEventDao,
            alarmScheduler = alarmScheduler,
            medicationDao = medicationDao,
        )
    }

    // ===== Billing =====
    val billingManager: BillingManager by lazy { BillingManager(appContext) }
    val giftCodeManager: GiftCodeManager by lazy { GiftCodeManager(appContext) }

    private val containerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** True when user has Pro via either Play purchase or a redeemed gift code. */
    val isPro: StateFlow<Boolean> by lazy {
        combine(billingManager.isPro, settingsRepository.giftCodeUnlocked) { billing, gift ->
            billing || gift
        }.stateIn(containerScope, SharingStarted.Eagerly, false)
    }

    val backupManager: BackupManager by lazy {
        BackupManager(
            medicationDao = medicationDao,
            doseEventDao = doseEventDao,
            alarmScheduler = alarmScheduler,
        )
    }

    /** Pushes a fresh render to every DoseTap widget on the home screen. */
    fun refreshWidgets() {
        (appContext as DoseTapApp).appScope.launch {
            try {
                val glanceManager = GlanceAppWidgetManager(appContext)
                val glanceIds = glanceManager.getGlanceIds(DoseWidget::class.java)
                if (glanceIds.isEmpty()) return@launch
                // Glance reuses a running session on update() and only re-reads DataStore,
                // so we must write the current dose time to DataStore before calling update().
                val medication = medicationDao.getFirst()
                val lastDose = medication?.let { doseEventDao.getLastConfirmedDose(it.id) }
                val lastDoseTimeMillis = lastDose?.actionedAt ?: 0L
                glanceIds.forEach { glanceId ->
                    updateAppWidgetState(appContext, glanceId) { prefs ->
                        prefs[LastDoseTimeKey] = lastDoseTimeMillis
                    }
                    DoseWidget().update(appContext, glanceId)
                }
                Log.d("AppContainer", "refreshWidgets: updated ${glanceIds.size} widget(s)")
            } catch (e: Exception) {
                Log.e("AppContainer", "refreshWidgets failed", e)
            }
        }
    }
}
