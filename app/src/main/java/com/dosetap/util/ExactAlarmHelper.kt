package com.dosetap.util

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

private const val TAG = "ExactAlarmHelper"

/**
 * Helper for the `SCHEDULE_EXACT_ALARM` permission (E-H3).
 *
 * On Android 12 (API 31–32) the permission is auto-granted but revocable; on 13+ it is not granted
 * by default and must be requested via [Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM]. Below API 31
 * exact alarms need no permission. When exact scheduling isn't allowed, [com.dosetap.alarm.AlarmScheduler]
 * falls back to an inexact alarm, so reminders still fire — just not to the minute.
 */
object ExactAlarmHelper {

    /** True when the app may schedule exact alarms right now. */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /** Sends the user to the system screen where they grant exact-alarm access. */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open exact-alarm settings; falling back to app details", e)
            try {
                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (e2: Exception) {
                Log.e(TAG, "Could not open app details settings either", e2)
            }
        }
    }
}
