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
    val isOutdoor: Boolean = false // For weather-aware recommendations
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
    val transitEstimate: TransitEstimate? = null
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
    val nightlifeTypes: List<String> = emptyList()
)

