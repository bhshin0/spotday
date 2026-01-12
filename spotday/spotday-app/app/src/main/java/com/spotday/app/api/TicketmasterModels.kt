package com.spotday.app.api

import com.google.gson.annotations.SerializedName

/**
 * Ticketmaster Discovery API v2 response models.
 * https://developer.ticketmaster.com/products-and-docs/apis/discovery-api/v2/
 */

// Root response
data class TicketmasterEventsResponse(
    @SerializedName("_embedded")
    val embedded: TicketmasterEmbedded?,
    val page: TicketmasterPage?
)

data class TicketmasterEmbedded(
    val events: List<TicketmasterEvent>?
)

data class TicketmasterPage(
    val size: Int,
    val totalElements: Int,
    val totalPages: Int,
    val number: Int
)

// Event model
data class TicketmasterEvent(
    val id: String,
    val name: String,
    val type: String?,
    val url: String?,
    val info: String?,        // Event description
    val dates: TicketmasterDates?,
    val classifications: List<TicketmasterClassification>?,
    @SerializedName("_embedded")
    val embedded: TicketmasterEventEmbedded?,
    val priceRanges: List<TicketmasterPriceRange>?,
    val images: List<TicketmasterImage>?
)

// Date/time info
data class TicketmasterDates(
    val start: TicketmasterStart?,
    val status: TicketmasterStatus?,
    val timezone: String?
)

data class TicketmasterStart(
    val localDate: String?,     // "2025-01-11"
    val localTime: String?,     // "19:30:00"
    val dateTime: String?,      // ISO 8601 UTC
    val dateTBD: Boolean?,
    val dateTBA: Boolean?,
    val timeTBA: Boolean?,
    val noSpecificTime: Boolean?
)

data class TicketmasterStatus(
    val code: String?   // "onsale", "offsale", "cancelled", "postponed", "rescheduled"
)

// Classification (genre, segment)
data class TicketmasterClassification(
    val primary: Boolean?,
    val segment: TicketmasterSegment?,
    val genre: TicketmasterGenre?,
    val subGenre: TicketmasterGenre?
)

data class TicketmasterSegment(
    val id: String?,
    val name: String?   // "Music", "Sports", "Arts & Theatre", etc.
)

data class TicketmasterGenre(
    val id: String?,
    val name: String?   // "Rock", "Pop", "NBA", etc.
)

// Venue info (embedded in event)
data class TicketmasterEventEmbedded(
    val venues: List<TicketmasterVenue>?
)

data class TicketmasterVenue(
    val id: String?,
    val name: String?,
    val city: TicketmasterCity?,
    val state: TicketmasterState?,
    val location: TicketmasterLocation?,
    val address: TicketmasterAddress?
)

data class TicketmasterCity(
    val name: String?
)

data class TicketmasterState(
    val name: String?,
    val stateCode: String?
)

data class TicketmasterLocation(
    val latitude: String?,
    val longitude: String?
)

data class TicketmasterAddress(
    val line1: String?
)

// Price info
data class TicketmasterPriceRange(
    val type: String?,      // "standard", "platinum", etc.
    val currency: String?,
    val min: Double?,
    val max: Double?
)

// Image info
data class TicketmasterImage(
    val url: String?,
    val width: Int?,
    val height: Int?,
    val ratio: String?      // "16_9", "3_2", "4_3"
)
