package com.dosetap.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dosetap.domain.model.DoseEvent
import kotlinx.coroutines.flow.Flow

/** Data access object for [DoseEvent] entities. */
@Dao
interface DoseEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: DoseEvent): Long

    @Update
    suspend fun update(event: DoseEvent)

    @Query("SELECT * FROM dose_events WHERE id = :id")
    suspend fun getById(id: Long): DoseEvent?

    @Query("SELECT * FROM dose_events WHERE sourceTapId = :tapId ORDER BY scheduledAt ASC")
    suspend fun getByTapId(tapId: Long): List<DoseEvent>

    /**
     * Returns all dose events for the last [days] days, ordered newest-first.
     * Used for the history screen (30-day view).
     */
    @Query(
        """
        SELECT * FROM dose_events
        WHERE actionedAt >= :fromMillis
        ORDER BY actionedAt DESC
        """
    )
    fun observeRecent(fromMillis: Long): Flow<List<DoseEvent>>

    /**
     * Returns today's dose events for the home screen.
     */
    @Query(
        """
        SELECT * FROM dose_events
        WHERE actionedAt >= :startOfDayMillis
        ORDER BY actionedAt ASC
        """
    )
    fun observeToday(startOfDayMillis: Long): Flow<List<DoseEvent>>

    /**
     * Returns the most recent INITIAL_TAP event for a medication.
     * Used by the widget to display "last dose" time.
     */
    @Query(
        """
        SELECT * FROM dose_events
        WHERE medicationId = :medicationId AND action = 'INITIAL_TAP'
        ORDER BY actionedAt DESC
        LIMIT 1
        """
    )
    suspend fun getLastInitialTap(medicationId: Long): DoseEvent?

    /**
     * Returns the most recent confirmed dose (INITIAL_TAP or TAKEN) for a medication.
     *
     * Used by the widget so that confirmations coming from the alarm Activity
     * (which update an existing MISSED event to TAKEN rather than inserting an
     * INITIAL_TAP) still surface as the widget's "last dose" time.
     */
    @Query(
        """
        SELECT * FROM dose_events
        WHERE medicationId = :medicationId
          AND (action = 'INITIAL_TAP' OR action = 'TAKEN')
        ORDER BY actionedAt DESC
        LIMIT 1
        """
    )
    suspend fun getLastConfirmedDose(medicationId: Long): DoseEvent?

    /**
     * Returns pending follow-up events (MISSED action, scheduled in the future)
     * for a given tap group.
     */
    @Query(
        """
        SELECT * FROM dose_events
        WHERE sourceTapId = :tapId AND action = 'MISSED' AND scheduledAt > :nowMillis
        """
    )
    suspend fun getPendingFollowUps(tapId: Long, nowMillis: Long): List<DoseEvent>

    @Query("DELETE FROM dose_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM dose_events")
    suspend fun deleteAll()

    @Query("SELECT * FROM dose_events ORDER BY actionedAt DESC")
    suspend fun getAll(): List<DoseEvent>

    @Query("SELECT * FROM dose_events WHERE medicationId = :medicationId AND actionedAt >= :fromMillis ORDER BY actionedAt ASC")
    suspend fun getForMedicationSince(medicationId: Long, fromMillis: Long): List<DoseEvent>
}
