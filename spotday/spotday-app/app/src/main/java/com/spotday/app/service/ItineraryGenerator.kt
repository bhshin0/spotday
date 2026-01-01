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
        private const val BUDGET_BUFFER_PERCENTAGE = 1.2 // Allow 20% over budget
        
        // Meal time windows
        private const val BREAKFAST_START_HOUR = 7
        private const val BREAKFAST_END_HOUR = 10
        private const val LUNCH_START_HOUR = 11
        private const val LUNCH_END_HOUR = 14
        private const val DINNER_START_HOUR = 17
        private const val DINNER_END_HOUR = 20
    }

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
    
    // Meal time determination helpers
    private fun shouldIncludeBreakfast(startHour: Int): Boolean = startHour <= 9
    
    private fun shouldIncludeLunch(startHour: Int, endHour: Int): Boolean = 
        startHour <= 13 && endHour >= 11
    
    private fun shouldIncludeDinner(endHour: Int): Boolean = endHour >= 18
    
    private fun getIdealMealHour(mealType: String, startHour: Int, endHour: Int): Int {
        return when (mealType) {
            "breakfast" -> maxOf(startHour, BREAKFAST_START_HOUR).coerceAtMost(BREAKFAST_END_HOUR)
            "lunch" -> maxOf(startHour, LUNCH_START_HOUR).coerceAtMost(minOf(endHour, LUNCH_END_HOUR))
            "dinner" -> maxOf(startHour, DINNER_START_HOUR).coerceAtMost(minOf(endHour, DINNER_END_HOUR))
            else -> startHour
        }
    }
    
    private fun calculateActivityCount(totalHours: Int): Int {
        return when {
            totalHours < 6 -> 2  // Short day: 1-2 activities
            totalHours <= 10 -> 3 // Medium day: 2-3 activities
            else -> 4  // Long day: 3-4 activities
        }
    }
    
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
    
    // Data class to represent a planned stop slot
    private data class StopSlot(
        val idealHour: Int,
        val type: String, // "meal" or "activity"
        val mealType: String? = null // "breakfast", "lunch", or "dinner" if type is "meal"
    )
    
    // Create timeline skeleton with meal slots at appropriate times
    private fun createTimelineSkeleton(
        startHour: Int, 
        endHour: Int, 
        activityCount: Int
    ): List<StopSlot> {
        val slots = mutableListOf<StopSlot>()
        val totalHours = endHour - startHour
        
        // Determine which meals to include
        val hasBreakfast = shouldIncludeBreakfast(startHour)
        val hasLunch = shouldIncludeLunch(startHour, endHour)
        val hasDinner = shouldIncludeDinner(endHour)
        
        Log.d("ItineraryGenerator", "Meals: breakfast=$hasBreakfast, lunch=$hasLunch, dinner=$hasDinner")
        
        // Build timeline with meals as anchors
        if (hasBreakfast) {
            val breakfastHour = getIdealMealHour("breakfast", startHour, endHour)
            slots.add(StopSlot(breakfastHour, "meal", "breakfast"))
        }
        
        if (hasLunch) {
            val lunchHour = getIdealMealHour("lunch", startHour, endHour)
            slots.add(StopSlot(lunchHour, "meal", "lunch"))
        }
        
        if (hasDinner) {
            val dinnerHour = getIdealMealHour("dinner", startHour, endHour)
            slots.add(StopSlot(dinnerHour, "meal", "dinner"))
        }
        
        // Sort meals by time
        slots.sortBy { it.idealHour }
        
        // Distribute activities around meals
        val activitiesToAdd = activityCount.coerceAtMost(activityCount)
        
        if (slots.isEmpty()) {
            // No meals, just distribute activities evenly
            val hourGap = if (activitiesToAdd > 1) totalHours / (activitiesToAdd + 1) else totalHours / 2
            for (i in 0 until activitiesToAdd) {
                val activityHour = startHour + (hourGap * (i + 1))
                slots.add(StopSlot(activityHour, "activity"))
            }
        } else {
            // Add activities between meals and at edges
            val allSlots = mutableListOf<StopSlot>()
            
            // Activity before first meal
            if (slots.first().idealHour - startHour >= 2) {
                allSlots.add(StopSlot(startHour, "activity"))
            }
            
            // Add meals and activities between them
            for (i in slots.indices) {
                allSlots.add(slots[i])
                
                // Add activity between this meal and next
                if (i < slots.size - 1 && allSlots.size < activitiesToAdd + slots.size) {
                    val gapHours = slots[i + 1].idealHour - slots[i].idealHour
                    if (gapHours >= 3) {
                        val activityHour = slots[i].idealHour + (gapHours / 2)
                        allSlots.add(StopSlot(activityHour, "activity"))
                    }
                }
            }
            
            // Activity after last meal
            if (endHour - slots.last().idealHour >= 2 && allSlots.count { it.type == "activity" } < activitiesToAdd) {
                allSlots.add(StopSlot(slots.last().idealHour + 2, "activity"))
            }
            
            slots.clear()
            slots.addAll(allSlots)
        }
        
        // Sort by ideal hour
        slots.sortBy { it.idealHour }
        
        Log.d("ItineraryGenerator", "Timeline skeleton: ${slots.size} slots")
        return slots
    }

    suspend fun generateItinerary(
        startHour: Int,
        endHour: Int,
        totalBudget: Int,
        activityTypes: List<String>,
        foodTypes: List<String>
    ): List<ItineraryStop> {
        Log.d("ItineraryGenerator", "Generating itinerary from $startHour to $endHour, budget: $$totalBudget, activities: $activityTypes, food: $foodTypes")

        val totalHours = endHour - startHour
        val budgetBuffer = (totalBudget * BUDGET_BUFFER_PERCENTAGE).toInt()
        val activityCount = calculateActivityCount(totalHours)
        
        Log.d("ItineraryGenerator", "Total hours: $totalHours, target activities: $activityCount")

        // Create timeline skeleton with meal slots
        val timelineSlots = createTimelineSkeleton(startHour, endHour, activityCount)
        
        // Fetch candidate places based on preferences
        val candidateActivities = mutableListOf<Place>()
        if (activityTypes.contains("museums")) {
            candidateActivities.addAll(placesRepository.searchMuseums())
        }
        if (activityTypes.contains("parks")) {
            candidateActivities.addAll(placesRepository.searchParks())
        }
        if (activityTypes.contains("waterfront")) {
            candidateActivities.addAll(placesRepository.searchWaterfront())
        }
        if (activityTypes.contains("historic_sites")) {
            candidateActivities.addAll(placesRepository.searchHistoricSites())
        }
        if (activityTypes.contains("shopping")) {
            candidateActivities.addAll(placesRepository.searchShopping())
        }

        val candidateRestaurants = placesRepository.searchRestaurants(foodTypes)

        // Shuffle first for variety, then sort by rating DESC, then by cost ASC
        val sortedActivities = candidateActivities
            .shuffled()
            .sortedWith(
                compareByDescending<Place> { it.rating }
                    .thenBy { it.estimatedCost }
            )
        
        val sortedRestaurants = candidateRestaurants
            .shuffled()
            .sortedWith(
                compareByDescending<Place> { it.rating }
                    .thenBy { it.estimatedCost }
            )

        // Build itinerary from timeline skeleton
        val itinerary = mutableListOf<ItineraryStop>()
        var currentCost = 0
        
        // Track used places to avoid duplicates and current location for route optimization
        val usedPlaces = mutableSetOf<String>()
        
        // Start from a random SF neighborhood for variety on regeneration
        val startingLocations = listOf(
            Pair(37.8080, -122.4177), // Fisherman's Wharf
            Pair(37.7749, -122.4194), // Downtown/Union Square
            Pair(37.7599, -122.4148), // Mission District
            Pair(37.8000, -122.4100), // North Beach
            Pair(37.7700, -122.4500), // Haight-Ashbury
            Pair(37.7615, -122.4350), // Castro
            Pair(37.7800, -122.4600), // Inner Richmond
            Pair(37.8000, -122.4350)  // Marina
        )
        val randomStart = startingLocations.random()
        var currentLat = randomStart.first
        var currentLng = randomStart.second
        var totalDistance = 0.0
        
        Log.d("ItineraryGenerator", "Starting from neighborhood at ($currentLat, $currentLng)")
        
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        
        for (slot in timelineSlots) {
            // Set calendar to ideal hour for this slot
            calendar.set(Calendar.HOUR_OF_DAY, slot.idealHour)
            calendar.set(Calendar.MINUTE, 0)
            
            when (slot.type) {
                "meal" -> {
                    // Find unselected restaurants within budget using nearest-neighbor
                    val availableRestaurants = sortedRestaurants
                        .filterNot { usedPlaces.contains(it.id) }
                        .filter { currentCost + it.estimatedCost <= budgetBuffer }
                    
                    if (availableRestaurants.isNotEmpty()) {
                        // Pick from top 3 closest restaurants for variety (not always the absolute closest)
                        val sortedByDistance = availableRestaurants.sortedBy { 
                            calculateDistance(currentLat, currentLng, it.lat, it.lng) 
                        }
                        val topClosest = sortedByDistance.take(3)
                        val selectedRestaurant = topClosest.random()
                        
                        if (selectedRestaurant != null) {
                            val distance = calculateDistance(currentLat, currentLng, selectedRestaurant.lat, selectedRestaurant.lng)
                            
                            val stop = createStop(selectedRestaurant, calendar, RESTAURANT_DURATION_MINUTES, distance)
                            itinerary.add(stop)
                            currentCost += selectedRestaurant.estimatedCost
                            usedPlaces.add(selectedRestaurant.id)
                            
                            // Update current location
                            currentLat = selectedRestaurant.lat
                            currentLng = selectedRestaurant.lng
                            totalDistance += distance
                            
                            Log.d("ItineraryGenerator", "Added ${slot.mealType}: ${selectedRestaurant.name} (cost: $${selectedRestaurant.estimatedCost}, distance: ${"%.2f".format(distance)} km, total: $$currentCost)")
                            
                            // Add travel time to next stop
                            calendar.add(Calendar.MINUTE, TRAVEL_TIME_MINUTES)
                        }
                    } else {
                        Log.d("ItineraryGenerator", "No available restaurants within budget for ${slot.mealType}")
                    }
                }
                "activity" -> {
                    // Find unselected activities within budget using nearest-neighbor
                    val availableActivities = sortedActivities
                        .filterNot { usedPlaces.contains(it.id) }
                        .filter { currentCost + it.estimatedCost <= budgetBuffer }
                    
                    if (availableActivities.isNotEmpty()) {
                        // Pick from top 3 closest activities for variety (not always the absolute closest)
                        val sortedByDistance = availableActivities.sortedBy { 
                            calculateDistance(currentLat, currentLng, it.lat, it.lng) 
                        }
                        val topClosest = sortedByDistance.take(3)
                        val selectedActivity = topClosest.random()
                        
                        if (selectedActivity != null) {
                            val duration = getDuration(selectedActivity.type)
                            val distance = calculateDistance(currentLat, currentLng, selectedActivity.lat, selectedActivity.lng)
                            
                            val stop = createStop(selectedActivity, calendar, duration, distance)
                            itinerary.add(stop)
                            currentCost += selectedActivity.estimatedCost
                            usedPlaces.add(selectedActivity.id)
                            
                            // Update current location
                            currentLat = selectedActivity.lat
                            currentLng = selectedActivity.lng
                            totalDistance += distance
                            
                            Log.d("ItineraryGenerator", "Added activity: ${selectedActivity.name} (cost: $${selectedActivity.estimatedCost}, distance: ${"%.2f".format(distance)} km, total: $$currentCost)")
                            
                            // Add travel time to next stop
                            calendar.add(Calendar.MINUTE, TRAVEL_TIME_MINUTES)
                        }
                    } else {
                        Log.d("ItineraryGenerator", "No available activities within budget")
                    }
                }
            }
            
            // Stop if we've exceeded the end time
            if (calendar.get(Calendar.HOUR_OF_DAY) >= endHour) {
                break
            }
        }

        Log.d("ItineraryGenerator", "Generated itinerary with ${itinerary.size} stops")
        Log.d("ItineraryGenerator", "Total cost: $$currentCost (budget: $$totalBudget)")
        Log.d("ItineraryGenerator", "Total travel distance: ${"%.2f".format(totalDistance)} km")
        
        // Log individual stops with distances for debugging
        itinerary.forEachIndexed { index, stop ->
            Log.d("ItineraryGenerator", "Stop ${index + 1}: ${stop.place.name} at (${stop.place.lat}, ${stop.place.lng})")
        }
        
        return itinerary
    }

    private fun createStop(
        place: Place, 
        calendar: Calendar, 
        durationMinutes: Int,
        distanceFromPrevious: Double = 0.0
    ): ItineraryStop {
        val startTime = timeFormat.format(calendar.time)
        calendar.add(Calendar.MINUTE, durationMinutes)
        val endTime = timeFormat.format(calendar.time)

        return ItineraryStop(
            place = place,
            startTime = startTime,
            endTime = endTime,
            durationMinutes = durationMinutes,
            distanceFromPreviousKm = distanceFromPrevious
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

