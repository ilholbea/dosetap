package com.dosetap.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosetap.AppContainer
import com.dosetap.domain.model.DoseAction
import com.dosetap.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

        fun dayHasDose(daysAgo: Int): Boolean {
            val dayStart = TimeFormatter.startOfDayDaysAgo(daysAgo)
            return eventsByDay[dayStart]?.any { it.action in confirmedActions } == true
        }

        // Current streak: consecutive days ending today with at least one confirmed dose
        var currentStreak = 0
        var i = 0
        while (dayHasDose(i)) {
            currentStreak++
            i++
        }

        // Best streak: scan all days from first event to today
        val oneDayMillis = 24L * 60 * 60 * 1000
        val firstEventDay = allEvents.minOf { TimeFormatter.startOfDay(it.actionedAt) }
        val todayStart = TimeFormatter.startOfDay(System.currentTimeMillis())
        var bestStreak = 0
        var run = 0
        var cursor = firstEventDay
        while (cursor <= todayStart) {
            if (eventsByDay[cursor]?.any { it.action in confirmedActions } == true) {
                run++
                if (run > bestStreak) bestStreak = run
            } else {
                run = 0
            }
            cursor += oneDayMillis
        }
        bestStreak = maxOf(bestStreak, currentStreak)

        // Adherence percentages
        fun adherence(days: Int): Float {
            val taken = (0 until days).count { dayHasDose(it) }
            return taken.toFloat() / days
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
        )
    }
}
