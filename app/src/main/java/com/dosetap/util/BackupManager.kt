package com.dosetap.util

import android.util.Log
import androidx.room.withTransaction
import com.dosetap.alarm.AlarmScheduler
import com.dosetap.data.db.AppDatabase
import com.dosetap.data.db.DoseEventDao
import com.dosetap.data.db.MedicationDao
import com.dosetap.domain.model.DoseAction
import com.dosetap.domain.model.DoseEvent
import com.dosetap.domain.model.Medication
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "BackupManager"

/** Current export schema version. Import accepts any version in 1..[CURRENT_VERSION]. */
private const val CURRENT_VERSION = 3

class BackupManager(
    private val database: AppDatabase,
    private val medicationDao: MedicationDao,
    private val doseEventDao: DoseEventDao,
    private val alarmScheduler: AlarmScheduler,
) {

    data class ImportResult(val medicationsCount: Int, val eventsCount: Int)

    suspend fun exportToJson(): String {
        val medications = medicationDao.getAll()
        val events = doseEventDao.getAll()

        val root = JSONObject()
        root.put("version", CURRENT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        val medsArray = JSONArray()
        medications.forEach { med ->
            JSONObject().apply {
                put("id", med.id)
                put("name", med.name)
                put("followUps", JSONArray(med.followUps))
                put("soundUri", med.soundUri ?: JSONObject.NULL)
                put("dailyReminderMinutes", med.dailyReminderMinutes ?: JSONObject.NULL)
                put("lateCutoffMinutes", med.lateCutoffMinutes ?: JSONObject.NULL)
                put("pillCount", med.pillCount ?: JSONObject.NULL)
                put("refillAlertAt", med.refillAlertAt)
                put("offDays", JSONArray(med.offDays))
                put("durationMinutes", med.durationMinutes ?: JSONObject.NULL)
                put("createdAt", med.createdAt)
                // v3: prescription refill tracking + morning anchor (were silently dropped — E-C4)
                put("prescriptionDate", med.prescriptionDate ?: JSONObject.NULL)
                put("daysSupply", med.daysSupply ?: JSONObject.NULL)
                put("refillBufferDays", med.refillBufferDays)
                put("morningAnchor", med.morningAnchor ?: JSONObject.NULL)
            }.also { medsArray.put(it) }
        }
        root.put("medications", medsArray)

        val eventsArray = JSONArray()
        events.forEach { event ->
            JSONObject().apply {
                put("id", event.id)
                put("medicationId", event.medicationId)
                put("scheduledAt", event.scheduledAt ?: JSONObject.NULL)
                put("firedAt", event.firedAt ?: JSONObject.NULL)
                put("actionedAt", event.actionedAt)
                put("action", event.action.name)
                put("sourceTapId", event.sourceTapId)
                // v3: dose annotations (were silently dropped — E-C4)
                put("note", event.note ?: JSONObject.NULL)
                put("tags", event.tags ?: JSONObject.NULL)
            }.also { eventsArray.put(it) }
        }
        root.put("doseEvents", eventsArray)

        Log.i(TAG, "Exported ${medications.size} medications and ${events.size} events")
        return root.toString(2)
    }

    suspend fun importFromJson(json: String): ImportResult {
        val root = JSONObject(json)

        // Validate the schema version before touching anything (E-C4).
        val version = root.optInt("version", -1)
        require(version in 1..CURRENT_VERSION) {
            "Unsupported backup version: $version (this app reads 1..$CURRENT_VERSION)"
        }

        val medsArray = root.getJSONArray("medications")
        val eventsArray = root.getJSONArray("doseEvents")

        // Parse everything up front. A malformed row throws here — before any deletion — so a bad
        // file can never leave the database wiped or half-imported.
        val medications = (0 until medsArray.length()).map { i ->
            val obj = medsArray.getJSONObject(i)
            val followUpsJson = obj.getJSONArray("followUps")
            val offDaysJson = obj.optJSONArray("offDays")
            Medication(
                id = obj.getLong("id"),
                name = obj.getString("name"),
                followUps = (0 until followUpsJson.length()).map { j -> followUpsJson.getInt(j) },
                soundUri = if (obj.isNull("soundUri")) null else obj.getString("soundUri"),
                dailyReminderMinutes = if (obj.isNull("dailyReminderMinutes")) null else obj.optInt("dailyReminderMinutes"),
                lateCutoffMinutes = if (obj.isNull("lateCutoffMinutes")) null else obj.optInt("lateCutoffMinutes"),
                pillCount = if (obj.isNull("pillCount")) null else obj.optInt("pillCount"),
                refillAlertAt = obj.optInt("refillAlertAt", 7),
                offDays = if (offDaysJson != null) (0 until offDaysJson.length()).map { j -> offDaysJson.getInt(j) } else emptyList(),
                durationMinutes = if (obj.isNull("durationMinutes")) null else obj.optInt("durationMinutes"),
                createdAt = obj.getLong("createdAt"),
                // v3 fields — absent in older backups (has()==false), so fall back to null/defaults.
                prescriptionDate = if (obj.has("prescriptionDate") && !obj.isNull("prescriptionDate")) obj.getLong("prescriptionDate") else null,
                daysSupply = if (obj.has("daysSupply") && !obj.isNull("daysSupply")) obj.getInt("daysSupply") else null,
                refillBufferDays = obj.optInt("refillBufferDays", 7),
                morningAnchor = if (obj.has("morningAnchor") && !obj.isNull("morningAnchor")) obj.getString("morningAnchor") else null,
            )
        }

        val events = (0 until eventsArray.length()).map { i ->
            val obj = eventsArray.getJSONObject(i)
            DoseEvent(
                id = obj.getLong("id"),
                medicationId = obj.getLong("medicationId"),
                scheduledAt = if (obj.isNull("scheduledAt")) null else obj.optLong("scheduledAt"),
                firedAt = if (obj.isNull("firedAt")) null else obj.optLong("firedAt"),
                actionedAt = obj.getLong("actionedAt"),
                action = DoseAction.valueOf(obj.getString("action")),
                sourceTapId = obj.getLong("sourceTapId"),
                // v3 fields — absent in older backups (has()==false).
                note = if (obj.has("note") && !obj.isNull("note")) obj.getString("note") else null,
                tags = if (obj.has("tags") && !obj.isNull("tags")) obj.getString("tags") else null,
            )
        }

        // Swap the whole dataset atomically — deleteAll + inserts either all commit or all roll back.
        database.withTransaction {
            doseEventDao.deleteAll()
            medicationDao.deleteAll()
            medications.forEach { medicationDao.insert(it) }
            events.forEach { doseEventDao.insert(it) }
        }

        alarmScheduler.rescheduleAll()

        Log.i(TAG, "Imported ${medications.size} medications and ${events.size} events")
        return ImportResult(medications.size, events.size)
    }
}
