package com.dosetap.ui.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosetap.AppContainer
import com.dosetap.domain.model.DoseAction
import com.dosetap.domain.model.DoseEvent
import com.dosetap.util.Constants
import com.dosetap.util.TimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "HistoryViewModel"

data class HistoryUiState(
    val groupedEvents: Map<String, List<DoseEvent>> = emptyMap(),
    val activeFilter: DoseAction? = null,
    val isLoading: Boolean = true,
)

class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    private val activeFilter = MutableStateFlow<DoseAction?>(null)

    private val fromMillis = TimeFormatter.startOfDayDaysAgo(Constants.HISTORY_DAYS)

    val uiState: StateFlow<HistoryUiState> = combine(
        container.doseEventDao.observeRecent(fromMillis),
        activeFilter,
    ) { events, filter ->
        val filtered = if (filter == null) events else events.filter { it.action == filter }
        val grouped = filtered.groupBy { event ->
            when {
                TimeFormatter.isToday(event.actionedAt) -> "TODAY"
                TimeFormatter.isYesterday(event.actionedAt) -> "YESTERDAY"
                else -> TimeFormatter.formatDate(event.actionedAt)
            }
        }
        HistoryUiState(
            groupedEvents = grouped,
            activeFilter = filter,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState(isLoading = true),
    )

    fun setFilter(action: DoseAction?) {
        activeFilter.value = action
    }

    fun deleteEvent(event: DoseEvent) {
        viewModelScope.launch {
            try {
                if (event.action == DoseAction.INITIAL_TAP) {
                    container.alarmScheduler.cancelFollowUps(event.id)
                }
                container.doseEventDao.deleteById(event.id)
                container.refreshWidgets()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete event ${event.id}", e)
            }
        }
    }
}
