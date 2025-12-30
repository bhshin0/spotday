package com.spotday.app.service

import android.util.Log
import com.spotday.app.api.PlacesRepository
import com.spotday.app.model.ItineraryStop
import com.spotday.app.model.Place
import com.spotday.app.model.PlaceType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.*

class ItineraryGenerator(private val placesRepository: PlacesRepository) {

    companion object {
        private const val MUSEUM_DURATION_MINUTES = 120 // 2 hours
        private const val PARK_DURATION_MINUTES = 90   // 1.5 hours
        private const val RESTAURANT_DURATION_MINUTES = 60 // 1 hour
        private const val WATERFRONT_DURATION_MINUTES = 90 // 1.5 hours
        private const val HISTORIC_SITE_DURATION_MINUTES = 60 // 1 hour
        private const val SHOPPING_DURATION_MINUTES = 90 // 1.5 hours
        private const val TRAVEL_TIME_MINUTES = 30
        private const val START_HOUR = 10 // 10:00 AM
        private const val BUDGET_BUFFER_PERCENTAGE = 1.2 // Allow 20% over budget
    }

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
    
    // Calculate distance between two points in kilometers using Haversine formula
    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371.0 // Radius of Earth in kilometers
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        
        val a = sin(dLat / 2).pow(2) + 
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * 
                sin(dLng / 2).pow(2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
    
    // Find the restaurant closest to the given activities
    private fun findClosestRestaurant(activities: List<Place>, restaurants: List<Place>): Place? {
        if (restaurants.isEmpty() || activities.isEmpty()) return null
        
        // Calculate the centroid of activities
        val centerLat = activities.map { it.lat }.average()
        val centerLng = activities.map { it.lng }.average()
        
        // Find restaurant closest to the center of activities
        return restaurants.minByOrNull { restaurant ->
            calculateDistance(centerLat, centerLng, restaurant.lat, restaurant.lng)
        }
    }

    suspend fun generateItinerary(
        totalHours: Int,
        totalBudget: Int,
        activityTypes: List<String>,
        foodTypes: List<String>
    ): List<ItineraryStop> {
        Log.d("ItineraryGenerator", "Generating itinerary for $totalHours hours, budget: $$totalBudget, activities: $activityTypes, food: $foodTypes")

        val totalMinutes = totalHours * 60
        val budgetBuffer = (totalBudget * BUDGET_BUFFER_PERCENTAGE).toInt()
        val itinerary = mutableListOf<ItineraryStop>()
        var currentMinutes = 0
        var currentCost = 0

        // Start time at 10:00 AM
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, START_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        // Fetch places based on preferences
        val activities = mutableListOf<Place>()
        if (activityTypes.contains("museums")) {
            activities.addAll(placesRepository.searchMuseums().shuffled().take(2))
        }
        if (activityTypes.contains("parks")) {
            activities.addAll(placesRepository.searchParks().shuffled().take(2))
        }
        if (activityTypes.contains("waterfront")) {
            activities.addAll(placesRepository.searchWaterfront().shuffled().take(2))
        }
        if (activityTypes.contains("historic_sites")) {
            activities.addAll(placesRepository.searchHistoricSites().shuffled().take(2))
        }
        if (activityTypes.contains("shopping")) {
            activities.addAll(placesRepository.searchShopping().shuffled().take(2))
        }

        val allRestaurants = placesRepository.searchRestaurants(foodTypes)

        // Shuffle first for variety, then sort by rating DESC, then by cost ASC (prefer high-rated, lower-cost)
        val sortedActivities = activities
            .shuffled()
            .sortedWith(
                compareByDescending<Place> { it.rating }
                    .thenBy { it.estimatedCost }
            )
        
        // Shuffle restaurants similarly for variety
        val sortedRestaurants = allRestaurants
            .shuffled()
            .sortedWith(
                compareByDescending<Place> { it.rating }
                    .thenBy { it.estimatedCost }
            )
        
        // Select restaurant closest to the activities (from sorted list)
        val selectedRestaurant = findClosestRestaurant(sortedActivities, sortedRestaurants)

        // Build itinerary: Activity → Restaurant → Activity (if time and budget allow)
        var activityIndex = 0

        // First activity
        if (activityIndex < sortedActivities.size) {
            val activity = sortedActivities[activityIndex]
            val duration = getDuration(activity.type)
            
            if (currentMinutes + duration <= totalMinutes && currentCost + activity.estimatedCost <= budgetBuffer) {
                val stop = createStop(activity, calendar, duration)
                itinerary.add(stop)
                currentMinutes += duration
                currentCost += activity.estimatedCost
                activityIndex++
                
                Log.d("ItineraryGenerator", "Added activity: ${activity.name} (cost: $${activity.estimatedCost}, total: $$currentCost)")

                // Add travel time if more stops to come
                if (activityIndex < sortedActivities.size || selectedRestaurant != null) {
                    currentMinutes += TRAVEL_TIME_MINUTES
                    calendar.add(Calendar.MINUTE, TRAVEL_TIME_MINUTES)
                }
            }
        }

        // Restaurant (if selected and time/budget allow)
        if (selectedRestaurant != null && 
            currentMinutes + RESTAURANT_DURATION_MINUTES <= totalMinutes &&
            currentCost + selectedRestaurant.estimatedCost <= budgetBuffer) {
            val stop = createStop(selectedRestaurant, calendar, RESTAURANT_DURATION_MINUTES)
            itinerary.add(stop)
            currentMinutes += RESTAURANT_DURATION_MINUTES
            currentCost += selectedRestaurant.estimatedCost
            
            val prevActivity = if (itinerary.size > 1) itinerary[itinerary.size - 2].place else null
            val distance = if (prevActivity != null) {
                calculateDistance(prevActivity.lat, prevActivity.lng, selectedRestaurant.lat, selectedRestaurant.lng)
            } else 0.0
            
            Log.d("ItineraryGenerator", "Added restaurant: ${selectedRestaurant.name} (cost: $${selectedRestaurant.estimatedCost}, total: $$currentCost), distance: ${"%.2f".format(distance)} km")

            // Add travel time if more activities to come
            if (activityIndex < sortedActivities.size) {
                currentMinutes += TRAVEL_TIME_MINUTES
                calendar.add(Calendar.MINUTE, TRAVEL_TIME_MINUTES)
            }
        }

        // Second activity (if time and budget allow)
        if (activityIndex < sortedActivities.size) {
            val activity = sortedActivities[activityIndex]
            val duration = getDuration(activity.type)
            
            if (currentMinutes + duration <= totalMinutes && currentCost + activity.estimatedCost <= budgetBuffer) {
                val stop = createStop(activity, calendar, duration)
                itinerary.add(stop)
                currentMinutes += duration
                currentCost += activity.estimatedCost
                
                val prevPlace = if (itinerary.size > 1) itinerary[itinerary.size - 2].place else null
                val distance = if (prevPlace != null) {
                    calculateDistance(prevPlace.lat, prevPlace.lng, activity.lat, activity.lng)
                } else 0.0
                
                Log.d("ItineraryGenerator", "Added activity: ${activity.name} (cost: $${activity.estimatedCost}, total: $$currentCost), distance: ${"%.2f".format(distance)} km")
            }
        }

        Log.d("ItineraryGenerator", "Generated itinerary with ${itinerary.size} stops, total cost: $$currentCost (budget: $$totalBudget)")
        return itinerary
    }

    private fun createStop(place: Place, calendar: Calendar, durationMinutes: Int): ItineraryStop {
        val startTime = timeFormat.format(calendar.time)
        calendar.add(Calendar.MINUTE, durationMinutes)
        val endTime = timeFormat.format(calendar.time)

        return ItineraryStop(
            place = place,
            startTime = startTime,
            endTime = endTime,
            durationMinutes = durationMinutes
        )
    }

    private fun getDuration(type: PlaceType): Int {
        return when (type) {
            PlaceType.MUSEUM -> MUSEUM_DURATION_MINUTES
            PlaceType.PARK -> PARK_DURATION_MINUTES
            PlaceType.RESTAURANT -> RESTAURANT_DURATION_MINUTES
            PlaceType.WATERFRONT -> WATERFRONT_DURATION_MINUTES
            PlaceType.HISTORIC_SITE -> HISTORIC_SITE_DURATION_MINUTES
            PlaceType.SHOPPING -> SHOPPING_DURATION_MINUTES
        }
    }
}

