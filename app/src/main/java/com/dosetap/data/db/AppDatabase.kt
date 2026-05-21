package com.dosetap.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.dosetap.domain.model.DoseEvent
import com.dosetap.domain.model.Medication
import com.dosetap.domain.model.ScheduledAlarm

/**
 * Main Room database for DoseTap.
 *
 * Schema version history:
 * - v1: Initial schema (Medication, DoseEvent, ScheduledAlarm)
 */
@Database(
    entities = [Medication::class, DoseEvent::class, ScheduledAlarm::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao
    abstract fun doseEventDao(): DoseEventDao
    abstract fun alarmDao(): AlarmDao

    companion object {
        private const val DATABASE_NAME = "dosetap_database"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                DATABASE_NAME,
            )
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
        }
    }
}
