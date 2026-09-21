package app.jackdaw.client.data.repository

import app.jackdaw.client.core.model.CalendarEvent
import app.jackdaw.client.core.model.EventRsvpStatus
import app.jackdaw.client.core.model.MailAccount
import app.jackdaw.client.data.local.dao.CalendarEventDao
import app.jackdaw.client.data.local.entity.CalendarEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface CalendarRepository {
    fun getAllEvents(accountId: String): Flow<List<CalendarEvent>>
    fun getEventsInRange(accountId: String, startMillis: Long, endMillis: Long): Flow<List<CalendarEvent>>
    fun getEventById(eventId: String): Flow<CalendarEvent?>
    suspend fun saveEvent(event: CalendarEvent)
    suspend fun updateRsvpStatus(eventId: String, status: EventRsvpStatus)
    suspend fun deleteEvent(eventId: String)
    suspend fun syncCalendar(account: MailAccount)
}

class CalendarRepositoryImpl(
    private val calendarEventDao: CalendarEventDao,
    private val mailProtocolEngine: app.jackdaw.client.data.network.MailProtocolEngine = app.jackdaw.client.data.network.OwaProtocolEngine()
) : CalendarRepository {

    override fun getAllEvents(accountId: String): Flow<List<CalendarEvent>> {
        return calendarEventDao.getAllEvents(accountId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getEventsInRange(accountId: String, startMillis: Long, endMillis: Long): Flow<List<CalendarEvent>> {
        return calendarEventDao.getEventsInRange(accountId, startMillis, endMillis).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getEventById(eventId: String): Flow<CalendarEvent?> {
        return calendarEventDao.getEventById(eventId).map { it?.toDomain() }
    }

    override suspend fun saveEvent(event: CalendarEvent) {
        calendarEventDao.insertEvent(CalendarEventEntity.fromDomain(event))
    }

    override suspend fun updateRsvpStatus(eventId: String, status: EventRsvpStatus) {
        calendarEventDao.updateRsvpStatus(eventId, status)
    }

    override suspend fun deleteEvent(eventId: String) {
        calendarEventDao.deleteEvent(eventId)
    }

    override suspend fun syncCalendar(account: MailAccount) {
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 86400000L
        val ninetyDaysAhead = now + 90L * 86400000L
        val events = mailProtocolEngine.fetchCalendarEvents(account, thirtyDaysAgo, ninetyDaysAhead)
        if (events.isNotEmpty()) {
            calendarEventDao.insertEvents(events.map { CalendarEventEntity.fromDomain(it) })
        }
    }
}
