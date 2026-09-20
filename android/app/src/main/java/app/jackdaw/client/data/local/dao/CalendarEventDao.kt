package app.jackdaw.client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.jackdaw.client.core.model.EventRsvpStatus
import app.jackdaw.client.data.local.entity.CalendarEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events WHERE accountId = :accountId ORDER BY startTimestamp ASC")
    fun getAllEvents(accountId: String): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE accountId = :accountId AND startTimestamp >= :startRange AND startTimestamp <= :endRange ORDER BY startTimestamp ASC")
    fun getEventsInRange(accountId: String, startRange: Long, endRange: Long): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE id = :id LIMIT 1")
    fun getEventById(id: String): Flow<CalendarEventEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CalendarEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<CalendarEventEntity>)

    @Update
    suspend fun updateEvent(event: CalendarEventEntity)

    @Query("UPDATE calendar_events SET rsvpStatus = :status WHERE id = :id")
    suspend fun updateRsvpStatus(id: String, status: EventRsvpStatus)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteEvent(id: String)

    @Query("DELETE FROM calendar_events WHERE accountId = :accountId")
    suspend fun deleteEventsByAccount(accountId: String)
}
