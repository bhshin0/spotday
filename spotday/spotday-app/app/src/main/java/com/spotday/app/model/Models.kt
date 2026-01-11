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

enum class StopType {
    MAIN,           // Primary itinerary stop (numbered pin)
    WAYPOINT,       // Scenic pass-through point (small dot)
    QUICK_STOP,     // Coffee/photo break (small icon)
    FREE_TIME       // Exploration period (no pin, just suggestion)
}

data class Waypoint(
    val name: String,
    val lat: Double,
    val lng: Double,
    val description: String? = null  // "Pass by Saints Peter & Paul Church"
)

data class ScenicRoute(
    val id: String,
    val fromArea: String,           // e.g., "north_beach"
    val toArea: String,             // e.g., "chinatown"
    val waypoints: List<Waypoint>,
    val description: String,        // "Walk through Washington Square..."
    val addedMinutes: Int           // Extra time vs direct route
)

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
    val place: Place? = null,       // Nullable for free time / waypoint-only stops
    val startTime: String,
    val endTime: String,
    val durationMinutes: Int,
    val distanceFromPreviousKm: Double = 0.0,
    val transitEstimate: TransitEstimate? = null,
    val event: Event? = null,       // Non-null if this stop is for a scheduled event
    val stopType: StopType = StopType.MAIN,
    val waypoint: Waypoint? = null, // For waypoint/quick stop types
    val scenicRoute: ScenicRoute? = null, // Scenic route to this stop (if any)
    val neighborhoodName: String? = null  // For free time suggestions
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

