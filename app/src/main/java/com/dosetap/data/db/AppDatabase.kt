package com.dosetap.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.dosetap.domain.model.DoseEvent
import com.dosetap.domain.model.Medication
import com.dosetap.domain.model.ScheduledAlarm

/**
 * Main Room database for DoseTap.
 *
 * Schema version history:
 * - v1: Initial schema (Medication, DoseEvent, ScheduledAlarm)
 * - v2: Added dailyReminderMinutes and lateCutoffMinutes to Medication
 */
@Database(
    entities = [Medication::class, DoseEvent::class, ScheduledAlarm::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao
    abstract fun doseEventDao(): DoseEventDao
    abstract fun alarmDao(): AlarmDao

    companion object {
        private const val DATABASE_NAME = "dosetap_database"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN dailyReminderMinutes INTEGER")
                db.execSQL("ALTER TABLE medications ADD COLUMN lateCutoffMinutes INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN pillCount INTEGER")
                db.execSQL("ALTER TABLE medications ADD COLUMN refillAlertAt INTEGER NOT NULL DEFAULT 7")
                db.execSQL("ALTER TABLE medications ADD COLUMN offDays TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE medications ADD COLUMN durationMinutes INTEGER")
            }
        }

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
        }
    }
}
