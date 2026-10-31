package com.dosetap

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dosetap.alarm.AlarmScheduler
import com.dosetap.data.db.AppDatabase
import com.dosetap.data.prefs.SettingsRepository
import com.dosetap.domain.model.DoseAction
import com.dosetap.domain.model.DoseEvent
import com.dosetap.domain.model.Medication
import com.dosetap.domain.usecase.HandleDoseActionUseCase
import com.dosetap.domain.usecase.LogDoseUseCase
import com.dosetap.util.Constants
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for "later reminders sometimes never show": answering one reminder must not
 * cancel the rest of the follow-up chain (TESTING.md Test 1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HandleDoseActionUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var logDose: LogDoseUseCase
    private lateinit var handleAction: HandleDoseActionUseCase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val scheduler = AlarmScheduler(
            context = context,
            alarmDao = db.alarmDao(),
            medicationDao = db.medicationDao(),
            settingsRepository = SettingsRepository(context),
            doseEventDao = db.doseEventDao(),
        )
        logDose = LogDoseUseCase(db.doseEventDao(), scheduler, db.medicationDao())
        handleAction = HandleDoseActionUseCase(db.doseEventDao(), scheduler, db.medicationDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    /** Simulates AlarmReceiver firing follow-up [index]: MISSED event + row marked fired. */
    private suspend fun fire(tapId: Long, medId: Long, index: Int): Long {
        val alarm = db.alarmDao().getActiveByTapId(tapId).first { it.followUpIndex == index }
        db.alarmDao().cancelById(alarm.id)
        val now = System.currentTimeMillis()
        return db.doseEventDao().insert(
            DoseEvent(
                medicationId = medId,
                scheduledAt = alarm.triggerAtMillis,
                firedAt = now,
                actionedAt = now,
                action = DoseAction.MISSED,
                sourceTapId = tapId,
            )
        )
    }

    @Test
    fun `taken on first reminder keeps the second reminder armed`() = runTest {
        val medId = db.medicationDao().insert(Medication(name = "Med", followUps = listOf(240, 480)))
        val tapId = logDose.execute(medId)
        val eventId = fire(tapId, medId, index = 0)

        handleAction.execute(eventId, DoseAction.TAKEN)

        val remaining = db.alarmDao().getActiveByTapId(tapId)
        assertThat(remaining.map { it.followUpIndex }).containsExactly(1)
        assertThat(db.doseEventDao().getById(eventId)?.action).isEqualTo(DoseAction.TAKEN)
    }

    @Test
    fun `skip on first reminder keeps the second reminder armed`() = runTest {
        val medId = db.medicationDao().insert(Medication(name = "Med", followUps = listOf(240, 480)))
        val tapId = logDose.execute(medId)
        val eventId = fire(tapId, medId, index = 0)

        handleAction.execute(eventId, DoseAction.SKIPPED)

        assertThat(db.alarmDao().getActiveByTapId(tapId).map { it.followUpIndex }).containsExactly(1)
    }

    @Test
    fun `snooze adds a re-ping without touching the chain, taken clears it`() = runTest {
        val medId = db.medicationDao().insert(Medication(name = "Med", followUps = listOf(240, 480)))
        val tapId = logDose.execute(medId)
        val eventId = fire(tapId, medId, index = 0)

        handleAction.execute(eventId, DoseAction.SNOOZED)
        assertThat(db.alarmDao().getActiveByTapId(tapId).map { it.followUpIndex })
            .containsExactly(1, Constants.SNOOZE_FOLLOWUP_INDEX)

        handleAction.execute(eventId, DoseAction.TAKEN)
        assertThat(db.alarmDao().getActiveByTapId(tapId).map { it.followUpIndex }).containsExactly(1)
    }
}
