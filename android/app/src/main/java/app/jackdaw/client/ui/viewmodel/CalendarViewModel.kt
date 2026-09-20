package app.jackdaw.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.jackdaw.client.core.model.CalendarEvent
import app.jackdaw.client.core.model.EventRsvpStatus
import app.jackdaw.client.data.repository.CalendarRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    private val _currentAccountId = MutableStateFlow<String?>(null)
    private val _selectedDate = MutableStateFlow(LocalDate.now())

    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    // All events for current account
    private val accountEvents: StateFlow<List<CalendarEvent>> = _currentAccountId
        .flatMapLatest { accountId ->
            if (accountId != null) {
                calendarRepository.getAllEvents(accountId)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    // Events for selected date
    val selectedDateEvents: StateFlow<List<CalendarEvent>> = combine(
        accountEvents,
        _selectedDate
    ) { events, selectedDay ->
        val zoneId = ZoneId.systemDefault()
        events.filter { event ->
            val eventStartDate = Instant.ofEpochMilli(event.startTimestamp).atZone(zoneId).toLocalDate()
            val eventEndDate = Instant.ofEpochMilli(event.endTimestamp).atZone(zoneId).toLocalDate()
            !selectedDay.isBefore(eventStartDate) && !selectedDay.isAfter(eventEndDate)
        }.sortedBy { it.startTimestamp }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    // Set of dates that have events (for dots on calendar strip)
    val datesWithEvents: StateFlow<Set<LocalDate>> = accountEvents.combine(_selectedDate) { events, _ ->
        val zoneId = ZoneId.systemDefault()
        val set = mutableSetOf<LocalDate>()
        for (event in events) {
            val startDate = Instant.ofEpochMilli(event.startTimestamp).atZone(zoneId).toLocalDate()
            val endDate = Instant.ofEpochMilli(event.endTimestamp).atZone(zoneId).toLocalDate()
            var curr = startDate
            while (!curr.isAfter(endDate)) {
                set.add(curr)
                curr = curr.plusDays(1)
            }
        }
        set
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptySet()
    )

    fun setAccountId(accountId: String?) {
        _currentAccountId.value = accountId
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun jumpToToday() {
        _selectedDate.value = LocalDate.now()
    }

    fun saveEvent(
        title: String,
        description: String,
        location: String,
        meetingLink: String?,
        startTimestamp: Long,
        endTimestamp: Long,
        isAllDay: Boolean,
        attendees: List<String>,
        colorHex: Long,
        existingId: String? = null
    ) {
        val accountId = _currentAccountId.value ?: return
        val event = CalendarEvent(
            id = existingId ?: "evt_${System.currentTimeMillis()}",
            accountId = accountId,
            title = title,
            description = description,
            location = location,
            meetingLink = meetingLink?.takeIf { it.isNotBlank() },
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            isAllDay = isAllDay,
            organizerEmail = "",
            organizerName = "Вы",
            attendees = attendees,
            rsvpStatus = EventRsvpStatus.ACCEPTED,
            colorHex = colorHex
        )
        viewModelScope.launch {
            calendarRepository.saveEvent(event)
        }
    }

    fun updateRsvpStatus(eventId: String, status: EventRsvpStatus) {
        viewModelScope.launch {
            calendarRepository.updateRsvpStatus(eventId, status)
        }
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            calendarRepository.deleteEvent(eventId)
        }
    }

    class Factory(
        private val calendarRepository: CalendarRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
                return CalendarViewModel(calendarRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
