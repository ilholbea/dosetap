package com.dosetap

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dosetap.alarm.AlarmScheduler
import com.dosetap.data.db.AppDatabase
import com.dosetap.data.prefs.SettingsRepository
import com.dosetap.domain.model.DoseAction
import com.dosetap.domain.model.Medication
import com.dosetap.domain.usecase.LogDoseUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LogDoseUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var useCase: LogDoseUseCase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val settingsRepository = SettingsRepository(context)
        val alarmScheduler = AlarmScheduler(
            context = context,
            alarmDao = db.alarmDao(),
            medicationDao = db.medicationDao(),
            settingsRepository = settingsRepository,
            doseEventDao = db.doseEventDao(),
        )

        useCase = LogDoseUseCase(
            doseEventDao = db.doseEventDao(),
            alarmScheduler = alarmScheduler,
            medicationDao = db.medicationDao(),
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `execute returns -1 when medication not found`() = runTest {
        val result = useCase.execute(medicationId = 999L)
        assertThat(result).isEqualTo(-1L)
    }

    @Test
    fun `execute inserts INITIAL_TAP event with self-referencing sourceTapId`() = runTest {
        val medId = db.medicationDao().insert(
            Medication(name = "Test Med", followUps = listOf(240))
        )

        val tapId = useCase.execute(medicationId = medId)

        assertThat(tapId).isGreaterThan(0L)
        val event = db.doseEventDao().getById(tapId)
        assertThat(event).isNotNull()
        assertThat(event!!.action).isEqualTo(DoseAction.INITIAL_TAP)
        assertThat(event.sourceTapId).isEqualTo(tapId)
    }

    @Test
    fun `execute schedules alarms in DB`() = runTest {
        val medId = db.medicationDao().insert(
            Medication(name = "Test Med", followUps = listOf(240, 480))
        )

        val tapId = useCase.execute(medicationId = medId)

        val scheduledAlarms = db.alarmDao().getActiveByTapId(tapId)
        assertThat(scheduledAlarms).hasSize(2)
    }

    @Test
    fun `undo removes initial tap event`() = runTest {
        val medId = db.medicationDao().insert(
            Medication(name = "Test Med", followUps = listOf(240))
        )
        val tapId = useCase.execute(medicationId = medId)
        assertThat(db.doseEventDao().getById(tapId)).isNotNull()

        useCase.undo(tapId)

        assertThat(db.doseEventDao().getById(tapId)).isNull()
    }

    @Test
    fun `execute stores correct tap time`() = runTest {
        val medId = db.medicationDao().insert(
            Medication(name = "Test Med", followUps = listOf(240))
        )
        val tapTime = 1_700_000_000_000L

        val tapId = useCase.execute(medicationId = medId, tapTimeMillis = tapTime)

        val event = db.doseEventDao().getById(tapId)
        assertThat(event?.actionedAt).isEqualTo(tapTime)
    }

    @Test
    fun `followUp alarm trigger times are baseTime plus interval`() = runTest {
        val medId = db.medicationDao().insert(
            Medication(name = "Test Med", followUps = listOf(240, 480))
        )
        val tapTime = System.currentTimeMillis()

        val tapId = useCase.execute(medicationId = medId, tapTimeMillis = tapTime)

        val alarms = db.alarmDao().getActiveByTapId(tapId).sortedBy { it.followUpIndex }
        assertThat(alarms[0].triggerAtMillis).isEqualTo(tapTime + 240 * 60_000L)
        assertThat(alarms[1].triggerAtMillis).isEqualTo(tapTime + 480 * 60_000L)
    }

    @Test
    fun `backdated dose skips follow-ups that are already in the past`() = runTest {
        val medId = db.medicationDao().insert(
            Medication(name = "Test Med", followUps = listOf(240, 480))
        )
        // Logged 5h ago: the +4h follow-up is past (would fire instantly), the +8h one is not.
        val tapTime = System.currentTimeMillis() - 5 * 60 * 60_000L

        val tapId = useCase.execute(medicationId = medId, tapTimeMillis = tapTime)

        val alarms = db.alarmDao().getActiveByTapId(tapId)
        assertThat(alarms).hasSize(1)
        assertThat(alarms[0].followUpIndex).isEqualTo(1)
    }

    @Test
    fun `undo gives the pill back`() = runTest {
        val medId = db.medicationDao().insert(
            Medication(name = "Test Med", followUps = listOf(240), pillCount = 10)
        )
        val tapId = useCase.execute(medicationId = medId)
        assertThat(db.medicationDao().getById(medId)?.pillCount).isEqualTo(9)

        useCase.undo(tapId)

        assertThat(db.medicationDao().getById(medId)?.pillCount).isEqualTo(10)
    }

    @Test
    fun `undo of a dose logged at zero pills leaves the count at zero`() = runTest {
        val medId = db.medicationDao().insert(
            Medication(name = "Test Med", followUps = listOf(240), pillCount = 0)
        )
        val tapId = useCase.execute(medicationId = medId)
        assertThat(db.doseEventDao().getById(tapId)?.pillDecremented).isFalse()

        useCase.undo(tapId)

        assertThat(db.medicationDao().getById(medId)?.pillCount).isEqualTo(0)
    }

    @Test
    fun `undo without pill tracking leaves the count untracked`() = runTest {
        val medId = db.medicationDao().insert(
            Medication(name = "Test Med", followUps = listOf(240), pillCount = null)
        )
        val tapId = useCase.execute(medicationId = medId)

        useCase.undo(tapId)

        assertThat(db.medicationDao().getById(medId)?.pillCount).isNull()
    }

    @Test
    fun `double-dose conflict uses the effect window, not the calendar day`() = runTest {
        val medId = db.medicationDao().insert(
            Medication(name = "Test Med", followUps = listOf(240), durationMinutes = 480)
        )
        val now = System.currentTimeMillis()
        useCase.execute(medicationId = medId, tapTimeMillis = now - 3 * 60 * 60_000L)

        assertThat(useCase.findDoubleDoseConflict(medId, now)).isNotNull()
        assertThat(useCase.findDoubleDoseConflict(medId, now + 6 * 60 * 60_000L)).isNull()
    }
}
