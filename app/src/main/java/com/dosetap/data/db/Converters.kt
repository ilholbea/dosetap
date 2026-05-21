package com.dosetap.data.db

import androidx.room.TypeConverter
import com.dosetap.domain.model.DoseAction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room TypeConverters for non-primitive types.
 *
 * Uses kotlinx-serialization for List<Int> → JSON to avoid reflection-based approaches.
 */
class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromIntList(value: List<Int>): String = json.encodeToString(value)

    @TypeConverter
    fun toIntList(value: String): List<Int> = json.decodeFromString(value)

    @TypeConverter
    fun fromDoseAction(action: DoseAction): String = action.name

    @TypeConverter
    fun toDoseAction(value: String): DoseAction = DoseAction.valueOf(value)
}
