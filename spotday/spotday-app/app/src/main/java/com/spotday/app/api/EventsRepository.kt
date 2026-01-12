package com.spotday.app.api

import android.util.Log
import com.spotday.app.model.Event
import com.spotday.app.model.EventSource
import com.spotday.app.model.EventType
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository for events.
 * Reads from Supabase cache first, falls back to direct Ticketmaster API.
 */
class EventsRepository(private val cityId: String = "san_francisco") {

    companion object {
        private const val TAG = "EventsRepository"
        
        // City ID to (city name, state code) mapping for Ticketmaster API
        private val CITY_INFO = mapOf(
            "san_francisco" to Pair("San Francisco", "CA"),
            "charlotte" to Pair("Charlotte", "NC"),
            "phoenix" to Pair("Phoenix", "AZ"),
            "tucson" to Pair("Tucson", "AZ"),
            "austin" to Pair("Austin", "TX"),
            "new_york" to Pair("New York", "NY")
        )
    }
    
    private val cityName: String = CITY_INFO[cityId]?.first ?: "San Francisco"
    private val stateCode: String = CITY_INFO[cityId]?.second ?: "CA"

    /**
     * Get all available events for the selected time window.
     * Tries Supabase cache first, falls back to direct API.
     * Returns empty list if all sources fail (no mock data fallback).
     * 
     * @param startHour The earliest hour the user wants to start (0-23)
     * @param endHour The latest hour the user wants to end (0-23)
     * @return List of events that start within the time window, or empty list if unavailable
     */
    suspend fun getEventsForTimeWindow(startHour: Int, endHour: Int): List<Event> {
        Log.d(TAG, "Getting events for city=$cityId, time window $startHour - $endHour")
        
        // Try Supabase cache first
        try {
            val cachedEvents = fetchFromSupabase(startHour, endHour)
            if (cachedEvents.isNotEmpty()) {
                Log.d(TAG, "Returning ${cachedEvents.size} events from Supabase cache")
                return cachedEvents
            }
            Log.d(TAG, "Supabase cache empty, falling back to direct API")
        } catch (e: Exception) {
            Log.w(TAG, "Supabase fetch failed, falling back to direct API", e)
        }
        
        // Fall back to direct Ticketmaster API
        return try {
            val events = fetchFromTicketmaster()
            Log.d(TAG, "Fetched ${events.size} events from Ticketmaster API")
            
            events.filter { event ->
                event.startHour >= startHour && event.startHour < endHour
            }.also {
                Log.d(TAG, "Filtered to ${it.size} events in time window")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ticketmaster API failed, no events available", e)
            // Return empty list - UI will handle this gracefully
            emptyList()
        }
    }

    /**
     * Get events by their IDs.
     */
    suspend fun getEventsByIds(eventIds: List<String>): List<Event> {
        return try {
            // Try Supabase first
            val allEvents = fetchFromSupabase(0, 24)
            allEvents.filter { it.id in eventIds }.ifEmpty {
                // Fall back to direct API
                fetchFromTicketmaster().filter { it.id in eventIds }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch events by IDs", e)
            // Return empty instead of mock data
            emptyList()
        }
    }
    
    /**
     * Fetch events from Supabase cache.
     */
    private suspend fun fetchFromSupabase(startHour: Int, endHour: Int): List<Event> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        Log.d(TAG, "Fetching events from Supabase for date: $today, hours: $startHour-$endHour")
        
        val cachedEvents = SupabaseClient.postgrest
            .from("cached_events")
            .select {
                filter {
                    eq("city_id", cityId)
                    eq("start_date", today)  // Only today's events, not future dates
                    gte("start_hour", startHour)
                    lt("start_hour", endHour)
                }
                order("popularity", Order.DESCENDING)
            }
            .decodeList<SupabaseCachedEvent>()
        
        return cachedEvents.map { cached ->
            Event(
                id = cached.id,
                name = cached.name,
                description = cached.description ?: "",
                eventType = EventType.valueOf(cached.event_type),
                venueName = cached.venue_name,
                venueLatitude = cached.venue_lat,
                venueLongitude = cached.venue_lng,
                startHour = cached.start_hour,
                startMinute = cached.start_minute,
                durationMinutes = cached.duration_minutes,
                priceMin = cached.price_min,
                priceMax = cached.price_max,
                isSoldOut = cached.is_sold_out,
                ticketUrl = cached.ticket_url,
                popularity = cached.popularity,
                source = EventSource.valueOf(cached.source)
            )
        }
    }
    
    /**
     * Fetch events directly from Ticketmaster API.
     */
    private suspend fun fetchFromTicketmaster(): List<Event> {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        
        val startDateTime = dateFormat.format(calendar.time)
        
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 6)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val endDateTime = dateFormat.format(calendar.time)
        
        Log.d(TAG, "Fetching events from $startDateTime to $endDateTime")
        
        val response = TicketmasterApiClient.api.searchEvents(
            city = cityName,
            stateCode = stateCode,
            startDateTime = startDateTime,
            endDateTime = endDateTime,
            sort = "relevance,desc",
            size = 50
        )
        
        val ticketmasterEvents = response.embedded?.events ?: emptyList()
        Log.d(TAG, "Received ${ticketmasterEvents.size} events from API")
        
        return ticketmasterEvents.mapNotNull { tmEvent ->
            convertToEvent(tmEvent)
        }
    }
    
    /**
     * Convert Ticketmaster event to our Event model.
     */
    private fun convertToEvent(tmEvent: TicketmasterEvent): Event? {
        val startTime = tmEvent.dates?.start
        if (startTime?.localTime == null && startTime?.noSpecificTime == true) {
            return null
        }
        
        val timeParts = startTime?.localTime?.split(":") ?: return null
        val startHour = timeParts.getOrNull(0)?.toIntOrNull() ?: return null
        val startMinute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
        
        val venue = tmEvent.embedded?.venues?.firstOrNull()
        val venueName = venue?.name ?: "TBD"
        val latitude = venue?.location?.latitude?.toDoubleOrNull() ?: 37.7749
        val longitude = venue?.location?.longitude?.toDoubleOrNull() ?: -122.4194
        
        val priceRange = tmEvent.priceRanges?.firstOrNull { it.type == "standard" }
            ?: tmEvent.priceRanges?.firstOrNull()
        
        val eventType = determineEventType(tmEvent.classifications)
        val isSoldOut = tmEvent.dates?.status?.code in listOf("offsale", "cancelled")
        val durationMinutes = estimateDuration(eventType)
        
        return Event(
            id = "tm_${tmEvent.id}",
            name = tmEvent.name,
            description = tmEvent.info ?: getDefaultDescription(eventType, venueName),
            eventType = eventType,
            venueName = venueName,
            venueLatitude = latitude,
            venueLongitude = longitude,
            startHour = startHour,
            startMinute = startMinute,
            durationMinutes = durationMinutes,
            priceMin = priceRange?.min,
            priceMax = priceRange?.max,
            isSoldOut = isSoldOut,
            ticketUrl = tmEvent.url,
            popularity = 4,
            source = EventSource.TICKETMASTER
        )
    }
    
    private fun determineEventType(classifications: List<TicketmasterClassification>?): EventType {
        val primary = classifications?.firstOrNull { it.primary == true } 
            ?: classifications?.firstOrNull()
        
        val segmentName = primary?.segment?.name?.lowercase() ?: ""
        val genreName = primary?.genre?.name?.lowercase() ?: ""
        
        return when {
            segmentName.contains("music") -> EventType.CONCERT
            segmentName.contains("sport") -> EventType.SPORTS
            segmentName.contains("arts") || segmentName.contains("theatre") -> {
                when {
                    genreName.contains("comedy") -> EventType.COMEDY
                    else -> EventType.THEATER
                }
            }
            genreName.contains("comedy") -> EventType.COMEDY
            else -> EventType.CONCERT
        }
    }
    
    private fun estimateDuration(eventType: EventType): Int {
        return when (eventType) {
            EventType.CONCERT -> 150
            EventType.SPORTS -> 180
            EventType.THEATER -> 165
            EventType.COMEDY -> 90
            EventType.FOOD_FESTIVAL -> 180
            EventType.STREET_FAIR -> 240
            EventType.CLASS_WORKSHOP -> 120
        }
    }
    
    private fun getDefaultDescription(eventType: EventType, venueName: String): String {
        return when (eventType) {
            EventType.CONCERT -> "Live performance at $venueName"
            EventType.SPORTS -> "Live game at $venueName"
            EventType.THEATER -> "Live show at $venueName"
            EventType.COMEDY -> "Stand-up comedy at $venueName"
            EventType.FOOD_FESTIVAL -> "Food festival at $venueName"
            EventType.STREET_FAIR -> "Street fair at $venueName"
            EventType.CLASS_WORKSHOP -> "Workshop at $venueName"
        }
    }
}
