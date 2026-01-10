package com.spotday.app.model

enum class PlaceType {
    MUSEUM,
    PARK,
    RESTAURANT,
    WATERFRONT,
    HISTORIC_SITE,
    SHOPPING,
    NIGHTLIFE
}

enum class EventType {
    CONCERT,
    SPORTS,
    THEATER,
    COMEDY,
    FOOD_FESTIVAL,
    STREET_FAIR,
    CLASS_WORKSHOP
}

enum class ServiceStyle {
    QUICK,      // 30 min - food trucks, cafes, fast casual, grab-and-go
    CASUAL,     // 60 min - neighborhood restaurants, diners, bistros
    FORMAL      // 90 min - fine dining, upscale (dinner only)
}

data class Event(
    val id: String,
    val name: String,
    val description: String,
    val eventType: EventType,
    val venueName: String,
    val venueLatitude: Double,
    val venueLongitude: Double,
    val startHour: Int,      // 0-23
    val startMinute: Int,    // 0-59
    val durationMinutes: Int,
    val priceMin: Double?,
    val priceMax: Double?,
    val isSoldOut: Boolean = false,
    val ticketUrl: String? = null
)

data class Place(
    val id: String,
    val name: String,
    val type: PlaceType,
    val lat: Double,
    val lng: Double,
    val rating: Float,
    val isOpen: Boolean = true,
    val priceLevel: Int = 2,      // 1=$, 2=$$, 3=$$$, 4=$$$$
    val estimatedCost: Int = 0,    // Calculated cost per person
    val openHour: Int = 6,         // Opens at 6 AM by default
    val closeHour: Int = 22,       // Closes at 10 PM by default
    val isOutdoor: Boolean = false, // For weather-aware recommendations
    val serviceStyle: ServiceStyle = ServiceStyle.CASUAL // Restaurant service style
)

data class TransitEstimate(
    val walkingMinutes: Int,
    val transitMinutes: Int,
    val drivingMinutes: Int,
    val distanceKm: Double
)

data class ItineraryStop(
    val place: Place,
    val startTime: String,
    val endTime: String,
    val durationMinutes: Int,
    val distanceFromPreviousKm: Double = 0.0,
    val transitEstimate: TransitEstimate? = null,
    val event: Event? = null  // Non-null if this stop is for a scheduled event
)

data class UserPreferences(
    val startHour: Int,
    val endHour: Int,
    val totalBudget: Int,
    val activityTypes: List<String>,
    val foodTypes: List<String>,
    val isHungryNow: Boolean = false,
    val isSpontaneousMode: Boolean = false,
    val startLatitude: Double? = null,  // null = use random SF location
    val startLongitude: Double? = null,
    val nightlifeTypes: List<String> = emptyList(),
    val selectedEventIds: List<String> = emptyList(),
    val serviceStyles: List<ServiceStyle> = emptyList() // Empty = all styles allowed
)

