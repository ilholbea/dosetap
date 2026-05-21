package com.dosetap

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dosetap.data.db.AppDatabase
import com.dosetap.data.db.MedicationDao
import com.dosetap.domain.model.Medication
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MedicationDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: MedicationDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.medicationDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `insert and retrieve medication`() = runTest {
        val medication = Medication(name = "Adderall XR 20mg", followUps = listOf(240, 480))
        val id = dao.insert(medication)

        val retrieved = dao.getById(id)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved!!.name).isEqualTo("Adderall XR 20mg")
        assertThat(retrieved.followUps).containsExactly(240, 480).inOrder()
    }

    @Test
    fun `getFirst returns null when no medications`() = runTest {
        val first = dao.getFirst()
        assertThat(first).isNull()
    }

    @Test
    fun `getFirst returns earliest inserted medication`() = runTest {
        val med1 = Medication(name = "Med A", followUps = listOf(120))
        val med2 = Medication(name = "Med B", followUps = listOf(240))
        dao.insert(med1)
        dao.insert(med2)

        val first = dao.getFirst()
        assertThat(first?.name).isEqualTo("Med A")
    }

    @Test
    fun `update medication persists changes`() = runTest {
        val id = dao.insert(Medication(name = "Old Name", followUps = listOf(240)))
        val existing = dao.getById(id)!!

        dao.update(existing.copy(name = "New Name", followUps = listOf(120, 360)))

        val updated = dao.getById(id)
        assertThat(updated?.name).isEqualTo("New Name")
        assertThat(updated?.followUps).containsExactly(120, 360).inOrder()
    }

    @Test
    fun `deleteById removes medication`() = runTest {
        val id = dao.insert(Medication(name = "Med", followUps = listOf(240)))
        dao.deleteById(id)

        val retrieved = dao.getById(id)
        assertThat(retrieved).isNull()
    }

    @Test
    fun `observeAll emits empty list initially`() = runTest {
        val list = dao.observeAll().first()
        assertThat(list).isEmpty()
    }

    @Test
    fun `followUps list stored and retrieved correctly via TypeConverter`() = runTest {
        val followUps = listOf(30, 60, 120, 240)
        val id = dao.insert(Medication(name = "Test", followUps = followUps))

        val retrieved = dao.getById(id)
        assertThat(retrieved?.followUps).isEqualTo(followUps)
    }
}
