package com.dosetap.util

/**
 * App-wide constants. No magic numbers in alarm or scheduling logic.
 */
object Constants {

    // ===== Notification channels =====
    const val NOTIFICATION_CHANNEL_ID = "dose_alarms"
    const val NOTIFICATION_CHANNEL_NAME = "Medication Alarms"
    const val NOTIFICATION_CHANNEL_AMBIENT = "dose_ambient"
    const val NOTIFICATION_CHANNEL_AMBIENT_NAME = "Dose Status"
    const val NOTIFICATION_CHANNEL_WARNINGS = "dose_warnings"
    const val NOTIFICATION_CHANNEL_WARNINGS_NAME = "Dose Warnings"

    // ===== Ambient notification =====
    const val AMBIENT_NOTIFICATION_ID = 1
    const val AMBIENT_OPEN_APP_REQUEST_CODE = 0xAA000
    const val AMBIENT_LOG_DOSE_REQUEST_CODE = 0xAA001
    const val ACTION_AMBIENT_LOG_DOSE = "com.dosetap.action.AMBIENT_LOG_DOSE"

    // ===== Alarm =====
    /** Snooze duration in minutes when user taps "Snooze". */
    const val SNOOZE_DURATION_MINUTES = 15

    /** Snooze duration in milliseconds. */
    const val SNOOZE_DURATION_MILLIS = SNOOZE_DURATION_MINUTES * 60 * 1000L

    /** WakeLock timeout in milliseconds (60 seconds max per spec). */
    const val WAKELOCK_TIMEOUT_MILLIS = 60_000L

    /** Test reminder fires this many seconds from now. */
    const val TEST_REMINDER_DELAY_MILLIS = 10_000L

    /** PendingIntent request code for test reminder. */
    const val TEST_ALARM_REQUEST_CODE = 0xFFFF

    // ===== Widget debounce =====
    /** Ignore widget taps within this window of each other (milliseconds). */
    const val WIDGET_DEBOUNCE_MILLIS = 1500L

    /** "Undo" window after logging a dose from the widget (milliseconds). */
    const val WIDGET_UNDO_WINDOW_MILLIS = 10_000L

    // ===== History =====
    /** Number of days shown in history. */
    const val HISTORY_DAYS = 30

    // ===== Follow-up limits =====
    const val MIN_FOLLOWUP_MINUTES = 30
    const val MAX_FOLLOWUP_MINUTES = 720 // 12 hours
    const val FOLLOWUP_STEP_MINUTES = 15
    const val MAX_FOLLOWUP_COUNT = 4

    // ===== Intent extras =====
    const val EXTRA_TAP_ID = "extra_tap_id"
    const val EXTRA_MEDICATION_ID = "extra_medication_id"
    const val EXTRA_DOSE_EVENT_ID = "extra_dose_event_id"
    const val EXTRA_FOLLOWUP_INDEX = "extra_followup_index"
    const val EXTRA_WIDGET_ID = "extra_widget_id"

    // ===== PendingIntent request code formula =====
    // (tapId.toInt() shl 4) or followUpIndex
    // This ensures unique codes per alarm. tapId is truncated to 28 bits, followUpIndex uses 4 bits (0-15).
    const val REQUEST_CODE_SHIFT = 4

    // ===== DoseAction BroadcastReceiver actions =====
    const val ACTION_DOSE_TAKEN = "com.dosetap.action.DOSE_TAKEN"
    const val ACTION_DOSE_SNOOZED = "com.dosetap.action.DOSE_SNOOZED"
    const val ACTION_DOSE_SKIPPED = "com.dosetap.action.DOSE_SKIPPED"

    // ===== Default follow-up intervals =====
    val DEFAULT_FOLLOWUPS = listOf(240, 480) // +4h, +8h in minutes

    // ===== Daily reminder =====
    const val EXTRA_IS_DAILY_REMINDER = "extra_is_daily_reminder"
    /** Request code base for daily reminder alarms. Well above follow-up code space. */
    const val DAILY_REMINDER_REQUEST_CODE_BASE = 0x100_000

    // ===== Weekly summary =====
    const val WEEKLY_SUMMARY_REQUEST_CODE = 0x200_000
    const val WEEKLY_SUMMARY_NOTIFICATION_ID = 99_998
    const val WEEKLY_SUMMARY_DAY = 1 // Calendar.SUNDAY
    const val WEEKLY_SUMMARY_HOUR = 20 // 8 PM

    // ===== Comedown / wear-off warning =====
    const val WEAROFF_WARNING_REQUEST_CODE_BASE = 0x300_000
    const val WEAROFF_WARNING_NOTIFICATION_ID_BASE = 99_000
    const val ACTION_WEAROFF_WARNING = "com.dosetap.action.WEAROFF_WARNING"

    // ===== Smart escalation =====
    const val ESCALATION_REQUEST_CODE_BASE = 0x400_000
    const val ESCALATION_FIRST_DELAY_MILLIS = 10 * 60 * 1000L   // +10 min
    const val ESCALATION_SECOND_DELAY_MILLIS = 25 * 60 * 1000L  // +25 min

    // ===== Refill warning =====
    const val REFILL_WARNING_NOTIFICATION_ID_BASE = 98_000

    // ===== Sponsor codes =====
    const val SPONSOR_CODE_LENGTH = 11  // "XXX-XXX-XXX" format
}
