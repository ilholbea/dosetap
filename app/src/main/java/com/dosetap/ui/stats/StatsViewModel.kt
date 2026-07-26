package com.dosetap.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosetap.AppContainer
import com.dosetap.domain.model.DoseAction
import com.dosetap.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class DayStats(
    val label: String,
    val dateMillis: Long,
    val takenCount: Int,
    val skippedCount: Int,
    val missedCount: Int,
)

data class StatsUiState(
    val isLoading: Boolean = true,
    val medicationName: String? = null,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val adherence7Days: Float = 0f,
    val adherence30Days: Float = 0f,
    val totalDoses: Int = 0,
    val last14Days: List<DayStats> = emptyList(),
    val last90Days: List<DayStats> = emptyList(),
    val hasAnyData: Boolean = false,
    /** Non-null when a new milestone has just been reached this session. */
    val milestoneStreak: Int? = null,
    /** True once today has a confirmed dose. Drives the filled-vs-hollow flame. */
    val todayLogged: Boolean = false,
    /** True when today is an off-day / skip / paused day — a bridge, not a break. */
    val todayIsBridge: Boolean = false,
    /** True when a grace day is currently bridging a single-day gap in the live streak. */
    val graceApplied: Boolean = false,
)

class StatsViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = withContext(Dispatchers.IO) { compute() }
        }
    }

    private suspend fun compute(): StatsUiState {
        val medication = container.medicationDao.getFirst()
        val allEvents = container.doseEventDao.getAll()

        if (allEvents.isEmpty()) {
            return StatsUiState(
                isLoading = false,
                medicationName = medication?.name,
                hasAnyData = false,
            )
        }

        val confirmedActions = setOf(DoseAction.INITIAL_TAP, DoseAction.TAKEN)

        // Group events by start-of-day millis
        val eventsByDay: Map<Long, List<com.dosetap.domain.model.DoseEvent>> =
            allEvents.groupBy { TimeFormatter.startOfDay(it.actionedAt) }

        // ===== App-sanctioned absences (bridges — neither increment nor break a streak) =====
        val offDays: List<Int> = medication?.offDays ?: emptyList()
        val skipDate: Long = container.settingsRepository.getSkipTomorrowDate()
        val pausedUntil: Long = container.settingsRepository.pausedUntilMillis.first()
        val now = System.currentTimeMillis()

        fun weekdayOf(dayStart: Long): Int =
            Calendar.getInstance().apply { timeInMillis = dayStart }.get(Calendar.DAY_OF_WEEK)

        /**
         * A day the app told the user not to dose: an off-day (weekday in [offDays]),
         * the current skip-tomorrow date, or today while reminders are paused. Historical
         * skip/pause days aren't reconstructable, so only current ones are bridged.
         */
        fun isBridgeDay(daysAgo: Int, dayStart: Long): Boolean {
            if (weekdayOf(dayStart) in offDays) return true
            if (skipDate > 0L && dayStart == skipDate) return true
            if (daysAgo == 0 && now < pausedUntil) return true
            return false
        }

        fun dayHasDose(daysAgo: Int): Boolean {
            val dayStart = TimeFormatter.startOfDayDaysAgo(daysAgo)
            return eventsByDay[dayStart]?.any { it.action in confirmedActions } == true
        }

        val todayLogged = dayHasDose(0)
        val todayIsBridge = isBridgeDay(0, TimeFormatter.startOfDayDaysAgo(0))

        // ===== Current streak with forgiveness =====
        // Walk backward from today counting confirmed dose-days. Bridge days are neutral.
        // A real one-day gap is forgiven by a grace day (max one per rolling 7 days), so a
        // single missed day doesn't undo a long run. Today being unlogged never breaks the
        // streak — it's "still open"; in that case the count reflects the last confirmed day.
        var currentStreak = 0
        var graceApplied = false
        // Sentinel far enough below 0 that the first gap is always eligible for grace, without
        // the Int overflow that Int.MIN_VALUE would cause in `offset - lastGraceOffset`.
        var lastGraceOffset = -1000
        var offset = if (todayLogged || todayIsBridge) 0 else 1
        while (offset <= 400) {
            val dayStart = TimeFormatter.startOfDayDaysAgo(offset)
            if (dayHasDose(offset)) {
                currentStreak++
                offset++
            } else if (isBridgeDay(offset, dayStart)) {
                offset++
            } else if (currentStreak > 0 && offset - lastGraceOffset >= 7) {
                // Forgive this single missed day.
                graceApplied = true
                lastGraceOffset = offset
                offset++
            } else {
                break
            }
        }

        // ===== Best streak (all history) — DST-safe calendar stepping; off-days bridge =====
        val firstEventDay = allEvents.minOf { TimeFormatter.startOfDay(it.actionedAt) }
        val todayStart = TimeFormatter.startOfDay(now)
        var bestStreak = 0
        var run = 0
        val cursor = Calendar.getInstance().apply { timeInMillis = firstEventDay }
        while (cursor.timeInMillis <= todayStart) {
            val ds = cursor.timeInMillis
            val hasDose = eventsByDay[ds]?.any { it.action in confirmedActions } == true
            when {
                hasDose -> {
                    run++
                    if (run > bestStreak) bestStreak = run
                }
                weekdayOf(ds) in offDays -> { /* bridge — carry the run across off-days */ }
                else -> run = 0
            }
            cursor.add(Calendar.DAY_OF_YEAR, 1) // DST-safe: stays on local midnight
        }
        bestStreak = maxOf(bestStreak, currentStreak)

        // ===== Adherence — off-days excluded from the denominator (app-sanctioned) =====
        fun adherence(days: Int): Float {
            var applicable = 0
            var taken = 0
            for (d in 0 until days) {
                val ds = TimeFormatter.startOfDayDaysAgo(d)
                if (weekdayOf(ds) in offDays) continue
                applicable++
                if (dayHasDose(d)) taken++
            }
            return if (applicable == 0) 1f else taken.toFloat() / applicable
        }

        // Total confirmed doses
        val totalDoses = allEvents.count { it.action in confirmedActions }

        val dayLabels = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

        fun buildDayStats(daysAgo: Int): DayStats {
            val dayStart = TimeFormatter.startOfDayDaysAgo(daysAgo)
            val dayEvents = eventsByDay[dayStart] ?: emptyList()
            val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
            return DayStats(
                label = dayLabels[cal.get(Calendar.DAY_OF_WEEK) - 1],
                dateMillis = dayStart,
                takenCount = dayEvents.count { it.action in confirmedActions },
                skippedCount = dayEvents.count { it.action == DoseAction.SKIPPED },
                missedCount = dayEvents.count { it.action == DoseAction.MISSED },
            )
        }

        val last14Days = (13 downTo 0).map { buildDayStats(it) }
        val last90Days = (89 downTo 0).map { buildDayStats(it) }

        // Milestone detection: 7, 30, 90, 180, 365
        val milestones = setOf(7, 30, 90, 180, 365)
        val milestoneStreak: Int? = if (currentStreak in milestones) {
            val lastCelebrated = container.settingsRepository.lastCelebratedStreak.first()
            if (currentStreak > lastCelebrated) {
                container.settingsRepository.setLastCelebratedStreak(currentStreak)
                currentStreak
            } else null
        } else {
            // Reset if streak dropped so the milestone fires again when reached
            val lastCelebrated = container.settingsRepository.lastCelebratedStreak.first()
            if (currentStreak < lastCelebrated) {
                container.settingsRepository.setLastCelebratedStreak(currentStreak)
            }
            null
        }

        return StatsUiState(
            isLoading = false,
            medicationName = medication?.name,
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            adherence7Days = adherence(7),
            adherence30Days = adherence(30),
            totalDoses = totalDoses,
            last14Days = last14Days,
            last90Days = last90Days,
            hasAnyData = true,
            milestoneStreak = milestoneStreak,
            todayLogged = todayLogged,
            todayIsBridge = todayIsBridge,
            graceApplied = graceApplied,
        )
    }
}
