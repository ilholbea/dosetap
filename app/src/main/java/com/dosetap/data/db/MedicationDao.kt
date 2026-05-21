package com.dosetap.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dosetap.domain.model.Medication
import kotlinx.coroutines.flow.Flow

/** Data access object for [Medication] entities. */
@Dao
interface MedicationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(medication: Medication): Long

    @Update
    suspend fun update(medication: Medication)

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getById(id: Long): Medication?

    @Query("SELECT * FROM medications ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<Medication>>

    @Query("SELECT * FROM medications ORDER BY createdAt ASC LIMIT 1")
    suspend fun getFirst(): Medication?

    @Query("SELECT * FROM medications ORDER BY createdAt ASC LIMIT 1")
    fun observeFirst(): Flow<Medication?>

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM medications ORDER BY createdAt ASC")
    suspend fun getAll(): List<Medication>

    @Query("DELETE FROM medications")
    suspend fun deleteAll()
}
