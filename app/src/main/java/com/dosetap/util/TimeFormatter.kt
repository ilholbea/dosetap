package com.dosetap.util

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Formats time values for display throughout the app.
 *
 * Respects the user's system 24h/12h preference via [DateFormat.is24HourFormat].
 * All formatting uses the device locale for correct cultural conventions.
 */
object TimeFormatter {

    /**
     * Formats a UTC millis timestamp as a time string (e.g. "09:14" or "9:14 AM").
     * Respects the device's 24h/12h setting.
     */
    fun formatTime(context: Context, millis: Long): String {
        val format = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        return SimpleDateFormat(format, Locale.getDefault()).format(Date(millis))
    }

    /**
     * Formats a UTC millis timestamp as a date string (e.g. "May 19" or "19 May").
     */
    fun formatDate(millis: Long): String {
        return SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))
    }

    /**
     * Formats a UTC millis timestamp as a full date-time string.
     */
    fun formatDateTime(context: Context, millis: Long): String {
        val timeFormat = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        return SimpleDateFormat("d MMM, $timeFormat", Locale.getDefault()).format(Date(millis))
    }

    /**
     * Returns a human-readable duration string for "how late" display.
     * Examples: "5 min", "1h 3m", "2h"
     */
    fun formatDuration(millis: Long): String {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(kotlin.math.abs(millis))
        return when {
            totalMinutes < 1L -> "<1 min"
            totalMinutes < 60L -> "$totalMinutes min"
            else -> {
                val hours = totalMinutes / 60
                val minutes = totalMinutes % 60
                if (minutes == 0L) "${hours}h" else "${hours}h ${minutes}m"
            }
        }
    }

    /**
     * Formats an interval in minutes as a readable string.
     * Examples: "30 minutes", "2 hours", "1 hour 30 min"
     */
    fun formatInterval(minutes: Int): String {
        return when {
            minutes < 60 -> "$minutes minutes"
            minutes % 60 == 0 -> "${minutes / 60} hours"
            else -> "${minutes / 60} hours ${minutes % 60} min"
        }
    }

    /**
     * Returns the start of the day (00:00:00.000) for the given UTC millis timestamp
     * in the device's local timezone.
     */
    fun startOfDay(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Returns the start of the day N days ago from the given millis.
     */
    fun startOfDayDaysAgo(days: Int, fromMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = fromMillis
        cal.add(Calendar.DAY_OF_YEAR, -days)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Returns true if the two timestamps fall on the same calendar day (device local timezone).
     */
    fun isSameDay(millis1: Long, millis2: Long): Boolean {
        val cal1 = Calendar.getInstance().also { it.timeInMillis = millis1 }
        val cal2 = Calendar.getInstance().also { it.timeInMillis = millis2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * Returns true if the timestamp falls on today (device local timezone).
     */
    fun isToday(millis: Long): Boolean = isSameDay(millis, System.currentTimeMillis())

    /**
     * Returns true if the timestamp falls on yesterday.
     */
    fun isYesterday(millis: Long): Boolean {
        val yesterday = Calendar.getInstance().also {
            it.add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        return isSameDay(millis, yesterday)
    }

    /**
     * Formats a list of follow-up times as "13:14, 17:14" for widget display.
     */
    fun formatFollowUpTimes(context: Context, triggerMillis: List<Long>): String {
        return triggerMillis.joinToString(", ") { formatTime(context, it) }
    }
}
