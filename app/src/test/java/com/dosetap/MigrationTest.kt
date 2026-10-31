package com.dosetap

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.dosetap.data.db.AppDatabase
import com.dosetap.domain.model.DoseAction
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies Room migrations against the exported schemas in app/schemas/. A wrong migration would
 * crash the app on first launch after an update, so this runs on every unit-test pass.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationTest {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun `migrate 4 to 5 keeps existing doses and defaults pillDecremented to false`() = runTest {
        helper.createDatabase(dbName, 4).apply {
            execSQL(
                "INSERT INTO medications (id, name, followUps, createdAt, refillAlertAt, offDays, refillBufferDays) " +
                    "VALUES (1, 'Med', '[240]', 0, 7, '[]', 7)"
            )
            execSQL(
                "INSERT INTO dose_events (id, medicationId, actionedAt, action, sourceTapId) " +
                    "VALUES (10, 1, 1000, 'INITIAL_TAP', 10)"
            )
            close()
        }

        // Validates the migrated schema matches 5.json exactly (columns, types, defaults).
        helper.runMigrationsAndValidate(dbName, 5, true, *AppDatabase.ALL_MIGRATIONS)

        // And the real app database opens on top of it with its registered migrations.
        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            dbName,
        ).addMigrations(*AppDatabase.ALL_MIGRATIONS).allowMainThreadQueries().build()
        val event = db.doseEventDao().getById(10)
        assertThat(event?.action).isEqualTo(DoseAction.INITIAL_TAP)
        assertThat(event?.pillDecremented).isFalse()
        db.close()
    }
}
