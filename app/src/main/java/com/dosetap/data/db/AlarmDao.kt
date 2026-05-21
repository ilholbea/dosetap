package com.dosetap.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dosetap.domain.model.ScheduledAlarm

/** Data access object for [ScheduledAlarm] entities. */
@Dao
interface AlarmDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: ScheduledAlarm): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(alarms: List<ScheduledAlarm>)

    @Query("SELECT * FROM scheduled_alarms WHERE tapId = :tapId AND isCancelled = 0")
    suspend fun getActiveByTapId(tapId: Long): List<ScheduledAlarm>

    /** Returns all active (not-cancelled, future) alarms — used for rescheduling after reboot. */
    @Query(
        """
        SELECT * FROM scheduled_alarms
        WHERE isCancelled = 0 AND triggerAtMillis > :nowMillis
        ORDER BY triggerAtMillis ASC
        """
    )
    suspend fun getAllActive(nowMillis: Long): List<ScheduledAlarm>

    @Query("UPDATE scheduled_alarms SET isCancelled = 1 WHERE tapId = :tapId")
    suspend fun cancelByTapId(tapId: Long)

    @Query("UPDATE scheduled_alarms SET isCancelled = 1 WHERE id = :id")
    suspend fun cancelById(id: Long)

    @Query("DELETE FROM scheduled_alarms WHERE isCancelled = 1")
    suspend fun deleteCancelled()

    @Query("DELETE FROM scheduled_alarms")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM scheduled_alarms WHERE isCancelled = 0 AND triggerAtMillis > :nowMillis")
    fun observeActiveCount(nowMillis: Long): kotlinx.coroutines.flow.Flow<Int>
}
