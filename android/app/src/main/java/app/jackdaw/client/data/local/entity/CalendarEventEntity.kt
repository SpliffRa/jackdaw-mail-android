package app.jackdaw.client.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.jackdaw.client.core.model.CalendarEvent
import app.jackdaw.client.core.model.EventRsvpStatus

@Entity(
    tableName = "calendar_events",
    indices = [
        Index("accountId"),
        Index("startTimestamp"),
        Index("endTimestamp")
    ]
)
data class CalendarEventEntity(
    @PrimaryKey
    val id: String,
    val accountId: String,
    val title: String,
    val description: String = "",
    val location: String = "",
    val meetingLink: String? = null,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val isAllDay: Boolean = false,
    val organizerEmail: String = "",
    val organizerName: String = "",
    val attendees: List<String> = emptyList(),
    val rsvpStatus: EventRsvpStatus = EventRsvpStatus.NONE,
    val colorHex: Long = 0xFF2563EBL
) {
    fun toDomain(): CalendarEvent {
        return CalendarEvent(
            id = id,
            accountId = accountId,
            title = title,
            description = description,
            location = location,
            meetingLink = meetingLink,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            isAllDay = isAllDay,
            organizerEmail = organizerEmail,
            organizerName = organizerName,
            attendees = attendees,
            rsvpStatus = rsvpStatus,
            colorHex = colorHex
        )
    }

    companion object {
        fun fromDomain(event: CalendarEvent): CalendarEventEntity {
            return CalendarEventEntity(
                id = event.id,
                accountId = event.accountId,
                title = event.title,
                description = event.description,
                location = event.location,
                meetingLink = event.meetingLink,
                startTimestamp = event.startTimestamp,
                endTimestamp = event.endTimestamp,
                isAllDay = event.isAllDay,
                organizerEmail = event.organizerEmail,
                organizerName = event.organizerName,
                attendees = event.attendees,
                rsvpStatus = event.rsvpStatus,
                colorHex = event.colorHex
            )
        }
    }
}
