package com.spotday.app.model

enum class PlaceType {
    MUSEUM,
    PARK,
    RESTAURANT,
    WATERFRONT,
    HISTORIC_SITE,
    SHOPPING
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
    val estimatedCost: Int = 0     // Calculated cost per person
)

data class ItineraryStop(
    val place: Place,
    val startTime: String,
    val endTime: String,
    val durationMinutes: Int,
    val distanceFromPreviousKm: Double = 0.0
)

data class UserPreferences(
    val startHour: Int,
    val endHour: Int,
    val totalBudget: Int,
    val activityTypes: List<String>,
    val foodTypes: List<String>
)

