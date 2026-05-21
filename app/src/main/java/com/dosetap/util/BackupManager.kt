package com.dosetap.util

import android.util.Log
import com.dosetap.alarm.AlarmScheduler
import com.dosetap.data.db.DoseEventDao
import com.dosetap.data.db.MedicationDao
import com.dosetap.domain.model.DoseAction
import com.dosetap.domain.model.DoseEvent
import com.dosetap.domain.model.Medication
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "BackupManager"

class BackupManager(
    private val medicationDao: MedicationDao,
    private val doseEventDao: DoseEventDao,
    private val alarmScheduler: AlarmScheduler,
) {

    data class ImportResult(val medicationsCount: Int, val eventsCount: Int)

    suspend fun exportToJson(): String {
        val medications = medicationDao.getAll()
        val events = doseEventDao.getAll()

        val root = JSONObject()
        root.put("version", 2)
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
            }.also { eventsArray.put(it) }
        }
        root.put("doseEvents", eventsArray)

        Log.i(TAG, "Exported ${medications.size} medications and ${events.size} events")
        return root.toString(2)
    }

    suspend fun importFromJson(json: String): ImportResult {
        val root = JSONObject(json)

        val medsArray = root.getJSONArray("medications")
        val eventsArray = root.getJSONArray("doseEvents")

        val medications = (0 until medsArray.length()).map { i ->
            val obj = medsArray.getJSONObject(i)
            val followUpsJson = obj.getJSONArray("followUps")
            val offDaysJson = obj.optJSONArray("offDays")
            Medication(
                id = obj.getLong("id"),
                name = obj.getString("name"),
                followUps = (0 until followUpsJson.length()).map { j -> followUpsJson.getInt(j) },
                soundUri = if (obj.isNull("soundUri")) null else obj.optString("soundUri", null),
                dailyReminderMinutes = if (obj.isNull("dailyReminderMinutes")) null else obj.optInt("dailyReminderMinutes"),
                lateCutoffMinutes = if (obj.isNull("lateCutoffMinutes")) null else obj.optInt("lateCutoffMinutes"),
                pillCount = if (obj.isNull("pillCount")) null else obj.optInt("pillCount"),
                refillAlertAt = obj.optInt("refillAlertAt", 7),
                offDays = if (offDaysJson != null) (0 until offDaysJson.length()).map { j -> offDaysJson.getInt(j) } else emptyList(),
                durationMinutes = if (obj.isNull("durationMinutes")) null else obj.optInt("durationMinutes"),
                createdAt = obj.getLong("createdAt"),
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
            )
        }

        doseEventDao.deleteAll()
        medicationDao.deleteAll()
        medications.forEach { medicationDao.insert(it) }
        events.forEach { doseEventDao.insert(it) }

        alarmScheduler.rescheduleAll()

        Log.i(TAG, "Imported ${medications.size} medications and ${events.size} events")
        return ImportResult(medications.size, events.size)
    }
}
