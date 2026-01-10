package com.spotday.app.service

import android.util.Log
import com.spotday.app.api.EventsRepository
import com.spotday.app.api.PlacesRepository
import com.spotday.app.model.Event
import com.spotday.app.model.ItineraryStop
import com.spotday.app.model.Place
import com.spotday.app.model.PlaceType
import com.spotday.app.util.TransitHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.*

class ItineraryGenerator(
    private val placesRepository: PlacesRepository,
    private val eventsRepository: EventsRepository = EventsRepository()
) {

    companion object {
        private const val MUSEUM_DURATION_MINUTES = 120 // 2 hours
        private const val PARK_DURATION_MINUTES = 90   // 1.5 hours
        private const val RESTAURANT_DURATION_MINUTES = 60 // 1 hour
        private const val WATERFRONT_DURATION_MINUTES = 90 // 1.5 hours
        private const val HISTORIC_SITE_DURATION_MINUTES = 60 // 1 hour
        private const val SHOPPING_DURATION_MINUTES = 90 // 1.5 hours
        private const val NIGHTLIFE_DURATION_MINUTES = 120 // 2 hours at a bar/club
        private const val TRAVEL_TIME_MINUTES = 30
        private const val BUDGET_BUFFER_PERCENTAGE = 1.2 // Allow 20% over budget
        private const val MINIMUM_MEAL_SPACING_HOURS = 4 // Don't schedule meals closer than 4 hours apart
        private const val HUNGRY_MODE_MEAL_INTERVAL_HOURS = 5 // Space meals 5 hours apart in hungry mode
        
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
    
    /**
     * Check if a place is open at the given hour.
     * Handles overnight hours (e.g., bar open 4 PM - 2 AM)
     */
    private fun isOpenAt(place: Place, hour: Int): Boolean {
        return if (place.closeHour > place.openHour) {
            // Normal hours (e.g., 10 AM - 5 PM)
            hour >= place.openHour && hour < place.closeHour
        } else {
            // Overnight hours (e.g., 4 PM - 2 AM means openHour=16, closeHour=2)
            hour >= place.openHour || hour < place.closeHour
        }
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
    
    // Create interval-based schedule for hungry mode (meals every 5 hours)
    private fun createIntervalBasedSchedule(
        startHour: Int,
        endHour: Int, 
        activityCount: Int
    ): List<StopSlot> {
        val slots = mutableListOf<StopSlot>()
        val totalHours = endHour - startHour
        
        Log.d("ItineraryGenerator", "Creating interval-based schedule (hungry mode)")
        
        // First meal at start
        slots.add(StopSlot(startHour, "meal", "meal1"))
        
        // Add meals every 5 hours
        var nextMealHour = startHour + HUNGRY_MODE_MEAL_INTERVAL_HOURS
        var mealIndex = 2
        while (nextMealHour < endHour - 1) {
            slots.add(StopSlot(nextMealHour, "meal", "meal$mealIndex"))
            nextMealHour += HUNGRY_MODE_MEAL_INTERVAL_HOURS
            mealIndex++
        }
        
        Log.d("ItineraryGenerator", "Scheduled ${slots.size} meals in hungry mode")
        
        // Distribute activities between meals
        if (slots.size == 1) {
            // Only one meal, distribute activities after it
            val hourGap = if (activityCount > 1) {
                (endHour - startHour - 1) / activityCount
            } else {
                (endHour - startHour) / 2
            }
            for (i in 0 until activityCount) {
                val activityHour = startHour + 1 + (hourGap * i)
                if (activityHour < endHour) {
                    slots.add(StopSlot(activityHour, "activity"))
                }
            }
        } else {
            // Multiple meals, distribute activities between them
            val allSlots = mutableListOf<StopSlot>()
            
            // Activity before first meal (if time allows)
            val mealSlots = slots.filter { it.type == "meal" }
            if (mealSlots.first().idealHour - startHour >= 2) {
                allSlots.add(StopSlot(startHour, "activity"))
            }
            
            // Add meals and activities between them
            for (i in mealSlots.indices) {
                allSlots.add(mealSlots[i])
                
                // Add activity between this meal and next (or before end)
                if (i < mealSlots.size - 1) {
                    val gapHours = mealSlots[i + 1].idealHour - mealSlots[i].idealHour
                    if (gapHours >= 3 && allSlots.count { it.type == "activity" } < activityCount) {
                        val activityHour = mealSlots[i].idealHour + (gapHours / 2)
                        allSlots.add(StopSlot(activityHour, "activity"))
                    }
                }
            }
            
            // Activity after last meal (if time allows and we need more activities)
            if (endHour - mealSlots.last().idealHour >= 2 && allSlots.count { it.type == "activity" } < activityCount) {
                allSlots.add(StopSlot(mealSlots.last().idealHour + 2, "activity"))
            }
            
            slots.clear()
            slots.addAll(allSlots)
        }
        
        // Sort by ideal hour
        slots.sortBy { it.idealHour }
        
        Log.d("ItineraryGenerator", "Interval-based timeline: ${slots.size} total slots")
        return slots
    }
    
    // Create timeline skeleton with meal slots at appropriate times
    private fun createTimelineSkeleton(
        startHour: Int, 
        endHour: Int, 
        activityCount: Int,
        isHungryNow: Boolean = false
    ): List<StopSlot> {
        // Branch based on mode
        if (isHungryNow) {
            return createIntervalBasedSchedule(startHour, endHour, activityCount)
        }
        
        // Traditional time-based scheduling
        val slots = mutableListOf<StopSlot>()
        val totalHours = endHour - startHour
        
        // Determine which meals to include with proper spacing
        val hasBreakfast = shouldIncludeBreakfast(startHour)
        val hasLunch = shouldIncludeLunch(startHour, endHour)
        val hasDinner = shouldIncludeDinner(endHour)
        
        Log.d("ItineraryGenerator", "Meal candidates: breakfast=$hasBreakfast, lunch=$hasLunch, dinner=$hasDinner")
        
        // Build meals list with spacing enforcement
        val meals = mutableListOf<Pair<String, Int>>() // (mealType, idealHour)
        
        if (hasBreakfast) {
            val breakfastHour = getIdealMealHour("breakfast", startHour, endHour)
            meals.add("breakfast" to breakfastHour)
        }
        
        if (hasLunch) {
            val lunchHour = getIdealMealHour("lunch", startHour, endHour)
            // Only add lunch if it's at least 4 hours after breakfast
            if (meals.isEmpty() || lunchHour - meals.last().second >= MINIMUM_MEAL_SPACING_HOURS) {
                meals.add("lunch" to lunchHour)
            } else {
                Log.d("ItineraryGenerator", "Skipping lunch - too close to previous meal (${lunchHour - meals.last().second} hours)")
            }
        }
        
        if (hasDinner) {
            val dinnerHour = getIdealMealHour("dinner", startHour, endHour)
            // Only add dinner if it's at least 4 hours after last meal
            if (meals.isEmpty() || dinnerHour - meals.last().second >= MINIMUM_MEAL_SPACING_HOURS) {
                meals.add("dinner" to dinnerHour)
            } else {
                Log.d("ItineraryGenerator", "Skipping dinner - too close to previous meal (${dinnerHour - meals.last().second} hours)")
            }
        }
        
        // Convert to StopSlots
        for ((mealType, hour) in meals) {
            slots.add(StopSlot(hour, "meal", mealType))
        }
        
        Log.d("ItineraryGenerator", "Final meals: ${meals.map { it.first }}")
        
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

    private suspend fun generateBarCrawl(
        startHour: Int,
        endHour: Int,
        totalBudget: Int,
        nightlifeTypes: List<String>,
        userStartLat: Double?,
        userStartLng: Double?
    ): List<ItineraryStop> {
        Log.d("ItineraryGenerator", "BAR CRAWL MODE: Creating nightlife-only itinerary")
        
        val totalHours = endHour - startHour
        val budgetBuffer = (totalBudget * BUDGET_BUFFER_PERCENTAGE).toInt()
        
        // Calculate number of bars based on duration
        // 1-3 hours: 1 bar, 4-5 hours: 2 bars, 6-7 hours: 3 bars, 8+ hours: 4+ bars
        val barCount = when {
            totalHours <= 3 -> 1
            totalHours <= 5 -> 2
            totalHours <= 7 -> 3
            totalHours <= 10 -> 4
            else -> 5
        }
        
        // Calculate time per bar to fill the whole duration
        val totalMinutes = totalHours * 60
        val travelTimeTotal = (barCount - 1) * TRAVEL_TIME_MINUTES  // No travel after last bar
        val availableMinutes = totalMinutes - travelTimeTotal
        val minutesPerBar = (availableMinutes / barCount).coerceAtLeast(60).coerceAtMost(180)  // 1-3 hours per bar
        
        Log.d("ItineraryGenerator", "Bar crawl: $barCount venues over $totalHours hours ($minutesPerBar min each)")
        
        val candidateNightlife = placesRepository.searchNightlife(nightlifeTypes)
        val sortedNightlife = candidateNightlife
            .shuffled()
            .sortedWith(
                compareByDescending<Place> { it.rating }
                    .thenBy { it.estimatedCost }
            )
        
        // Initialize starting location
        val (initialLat, initialLng) = when {
            userStartLat != null && userStartLng != null -> Pair(userStartLat, userStartLng)
            else -> {
                val startingLocations = listOf(
                    Pair(37.8080, -122.4177), Pair(37.7749, -122.4194), Pair(37.7599, -122.4148),
                    Pair(37.8000, -122.4100), Pair(37.7700, -122.4500), Pair(37.7615, -122.4350),
                    Pair(37.7800, -122.4600), Pair(37.8000, -122.4350)
                )
                startingLocations.random()
            }
        }
        
        // Build bar crawl itinerary
        val itinerary = mutableListOf<ItineraryStop>()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        
        var currentCost = 0
        var currentLat = initialLat
        var currentLng = initialLng
        var totalDistance = 0.0
        val usedPlaces = mutableSetOf<String>()
        
        for (i in 0 until barCount) {
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            // Find closest unused bar within budget and open at this hour
            val availableBars = sortedNightlife
                .filterNot { usedPlaces.contains(it.id) }
                .filter { currentCost + it.estimatedCost <= budgetBuffer }
                .filter { isOpenAt(it, currentHour) }
            
            if (availableBars.isEmpty()) {
                Log.d("ItineraryGenerator", "No more bars available within budget")
                break
            }
            
            val sortedByDistance = availableBars.sortedBy { 
                calculateDistance(currentLat, currentLng, it.lat, it.lng) 
            }
            val topClosest = sortedByDistance.take(3)
            val selectedBar = topClosest.random()
            
            val distance = calculateDistance(currentLat, currentLng, selectedBar.lat, selectedBar.lng)
            val stop = createStop(selectedBar, calendar, minutesPerBar, distance)
            
            itinerary.add(stop)
            currentCost += selectedBar.estimatedCost
            usedPlaces.add(selectedBar.id)
            currentLat = selectedBar.lat
            currentLng = selectedBar.lng
            totalDistance += distance
            
            // Add travel time between bars
            calendar.add(Calendar.MINUTE, TRAVEL_TIME_MINUTES)
            
            Log.d("ItineraryGenerator", "Bar ${i+1}/$barCount: ${selectedBar.name} (cost: $${selectedBar.estimatedCost}, distance: ${"%.2f".format(distance)} km)")
        }
        
        Log.d("ItineraryGenerator", "Bar crawl complete: ${itinerary.size} venues, $$currentCost total, ${"%.2f".format(totalDistance)} km")
        return itinerary
    }

    suspend fun generateItinerary(
        startHour: Int,
        endHour: Int,
        totalBudget: Int,
        activityTypes: List<String>,
        foodTypes: List<String>,
        isHungryNow: Boolean = false,
        isSpontaneousMode: Boolean = false,
        userStartLat: Double? = null,
        userStartLng: Double? = null,
        nightlifeTypes: List<String> = emptyList(),
        avoidOutdoor: Boolean = false,
        selectedEventIds: List<String> = emptyList()
    ): List<ItineraryStop> {
        Log.d("ItineraryGenerator", "Generating itinerary from $startHour to $endHour, budget: $$totalBudget, activities: $activityTypes, food: $foodTypes, hungryNow: $isHungryNow, spontaneous: $isSpontaneousMode, nightlife: $nightlifeTypes, avoidOutdoor: $avoidOutdoor, events: ${selectedEventIds.size}")

        val totalHours = endHour - startHour
        val budgetBuffer = (totalBudget * BUDGET_BUFFER_PERCENTAGE).toInt()
        val activityCount = calculateActivityCount(totalHours)
        
        Log.d("ItineraryGenerator", "Total hours: $totalHours, target activities: $activityCount")

        // Early detection - check if this is a bar crawl scenario
        // Trigger when: nightlife selected + no activities (any time of day)
        val isBarCrawl = nightlifeTypes.isNotEmpty() && activityTypes.isEmpty()
        
        if (isBarCrawl) {
            Log.d("ItineraryGenerator", "BAR CRAWL MODE triggered (nightlife only, no activities)")
            return generateBarCrawl(startHour, endHour, totalBudget, nightlifeTypes, userStartLat, userStartLng)
        }

        // Fetch selected events (if any) - these are FIXED anchors
        val selectedEvents = if (selectedEventIds.isNotEmpty()) {
            eventsRepository.getEventsByIds(selectedEventIds).sortedBy { it.startHour * 60 + it.startMinute }
        } else {
            emptyList()
        }
        
        if (selectedEvents.isNotEmpty()) {
            Log.d("ItineraryGenerator", "EVENT MODE: ${selectedEvents.size} fixed events")
            selectedEvents.forEach { event ->
                Log.d("ItineraryGenerator", "  - ${event.name} at ${event.startHour}:${event.startMinute.toString().padStart(2, '0')} (${event.durationMinutes} min)")
            }
        }

        // Create timeline skeleton with meal slots
        val timelineSlots = createTimelineSkeleton(startHour, endHour, activityCount, isHungryNow)
        
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

        // Shuffle first for variety, then sort by:
        // 1. Outdoor preference (indoor first if avoidOutdoor is true)
        // 2. Rating (descending)
        // 3. Cost (ascending)
        val sortedActivities = candidateActivities
            .shuffled()
            .sortedWith(
                compareBy<Place> { if (avoidOutdoor && it.isOutdoor) 1 else 0 }
                    .thenByDescending { it.rating }
                    .thenBy { it.estimatedCost }
            )
        
        if (avoidOutdoor) {
            val outdoorCount = candidateActivities.count { it.isOutdoor }
            val indoorCount = candidateActivities.size - outdoorCount
            Log.d("ItineraryGenerator", "Weather mode: Prioritizing indoor ($indoorCount indoor, $outdoorCount outdoor)")
        }
        
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
        
        // Track event time windows to avoid scheduling conflicts
        // Each event blocks (startHour*60 + startMinute) to (startHour*60 + startMinute + duration)
        val eventTimeBlocks = selectedEvents.map { event ->
            val startMinutes = event.startHour * 60 + event.startMinute
            val endMinutes = startMinutes + event.durationMinutes
            Triple(event, startMinutes, endMinutes)
        }
        
        // Helper to check if a proposed slot conflicts with any event
        fun conflictsWithEvent(hour: Int, durationMinutes: Int): Boolean {
            val slotStart = hour * 60
            val slotEnd = slotStart + durationMinutes
            return eventTimeBlocks.any { (_, eventStart, eventEnd) ->
                // Check for overlap: slot starts during event or event starts during slot
                (slotStart in eventStart until eventEnd) || (eventStart in slotStart until slotEnd)
            }
        }
        
        // Initialize starting location
        val (initialLat, initialLng) = when {
            userStartLat != null && userStartLng != null -> {
                Log.d("ItineraryGenerator", "Using provided start location: ($userStartLat, $userStartLng)")
                Pair(userStartLat, userStartLng)
            }
            else -> {
                // Existing behavior: random SF neighborhood for variety on regeneration
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
                Log.d("ItineraryGenerator", "Using random start location: ($randomStart)")
                randomStart
            }
        }
        
        var currentLat = initialLat
        var currentLng = initialLng
        var totalDistance = 0.0
        
        Log.d("ItineraryGenerator", "Starting from location at ($currentLat, $currentLng)")
        
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        
        // Build a combined timeline of slots + events, sorted by time
        var eventIndex = 0
        
        for (slot in timelineSlots) {
            val slotTimeMinutes = slot.idealHour * 60
            
            // First, add any events that start before this slot
            while (eventIndex < selectedEvents.size) {
                val event = selectedEvents[eventIndex]
                val eventStartMinutes = event.startHour * 60 + event.startMinute
                
                if (eventStartMinutes <= slotTimeMinutes) {
                    // Add this event
                    val distance = calculateDistance(currentLat, currentLng, event.venueLatitude, event.venueLongitude)
                    val eventStop = createEventStop(event, distance)
                    itinerary.add(eventStop)
                    
                    // Update cost and location
                    currentCost += event.priceMin?.toInt() ?: 0
                    currentLat = event.venueLatitude
                    currentLng = event.venueLongitude
                    totalDistance += distance
                    
                    Log.d("ItineraryGenerator", "Added EVENT: ${event.name} at ${event.startHour}:${event.startMinute.toString().padStart(2, '0')} (fixed anchor)")
                    
                    // Update calendar to after event ends
                    calendar.set(Calendar.HOUR_OF_DAY, event.startHour)
                    calendar.set(Calendar.MINUTE, event.startMinute)
                    calendar.add(Calendar.MINUTE, event.durationMinutes + TRAVEL_TIME_MINUTES)
                    
                    eventIndex++
                } else {
                    break
                }
            }
            
            // Check if this slot conflicts with any event
            val slotDuration = when (slot.type) {
                "meal" -> RESTAURANT_DURATION_MINUTES
                else -> MUSEUM_DURATION_MINUTES // Default activity duration
            }
            
            if (conflictsWithEvent(slot.idealHour, slotDuration)) {
                Log.d("ItineraryGenerator", "Skipping ${slot.type} at ${slot.idealHour}:00 - conflicts with event")
                continue
            }
            
            // Set calendar to ideal hour for this slot
            calendar.set(Calendar.HOUR_OF_DAY, slot.idealHour)
            calendar.set(Calendar.MINUTE, 0)
            
            when (slot.type) {
                "meal" -> {
                    val currentHour = slot.idealHour
                    // Find unselected restaurants within budget using nearest-neighbor
                    // Also filter by operating hours
                    val availableRestaurants = sortedRestaurants
                        .filterNot { usedPlaces.contains(it.id) }
                        .filter { currentCost + it.estimatedCost <= budgetBuffer }
                        .filter { isOpenAt(it, currentHour) }
                    
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
                    val currentHour = slot.idealHour
                    // Find unselected activities within budget using nearest-neighbor
                    // Also filter by operating hours
                    val availableActivities = sortedActivities
                        .filterNot { usedPlaces.contains(it.id) }
                        .filter { currentCost + it.estimatedCost <= budgetBuffer }
                        .filter { isOpenAt(it, currentHour) }
                    
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
        
        // Add any remaining events that come after all slots
        while (eventIndex < selectedEvents.size) {
            val event = selectedEvents[eventIndex]
            val distance = calculateDistance(currentLat, currentLng, event.venueLatitude, event.venueLongitude)
            val eventStop = createEventStop(event, distance)
            itinerary.add(eventStop)
            
            currentCost += event.priceMin?.toInt() ?: 0
            currentLat = event.venueLatitude
            currentLng = event.venueLongitude
            totalDistance += distance
            
            Log.d("ItineraryGenerator", "Added EVENT (end): ${event.name} at ${event.startHour}:${event.startMinute.toString().padStart(2, '0')}")
            eventIndex++
        }
        
        // Add nightlife venue after activities/meals if selected (standard mode)
        if (nightlifeTypes.isNotEmpty() && !isBarCrawl && itinerary.isNotEmpty()) {
            Log.d("ItineraryGenerator", "Adding nightlife venue to itinerary")
            
            val candidateNightlife = placesRepository.searchNightlife(nightlifeTypes)
            val sortedNightlife = candidateNightlife
                .shuffled()
                .sortedWith(
                    compareByDescending<Place> { it.rating }
                        .thenBy { it.estimatedCost }
                )
            
            // Get location of last stop
            val lastStop = itinerary.last()
            val lastLat = lastStop.place.lat
            val lastLng = lastStop.place.lng
            
            // Parse end time of last stop and add 30 min gap
            val nightlifeCalendar = Calendar.getInstance()
            val timeParser = SimpleDateFormat("h:mm a", Locale.US)
            try {
                val lastEndTime = timeParser.parse(lastStop.endTime)
                if (lastEndTime != null) {
                    nightlifeCalendar.time = lastEndTime
                    nightlifeCalendar.add(Calendar.MINUTE, 30) // 30 min gap after dinner
                    
                    // Only add if we have time before end hour
                    val nightlifeHour = nightlifeCalendar.get(Calendar.HOUR_OF_DAY)
                    if (nightlifeHour < endHour - 1) {
                        // Find closest unused nightlife venue within budget
                        // Also filter by operating hours
                        val availableBars = sortedNightlife
                            .filterNot { usedPlaces.contains(it.id) }
                            .filter { currentCost + it.estimatedCost <= budgetBuffer }
                            .filter { isOpenAt(it, nightlifeHour) }
                        
                        if (availableBars.isNotEmpty()) {
                            val sortedByDistance = availableBars.sortedBy { 
                                calculateDistance(lastLat, lastLng, it.lat, it.lng) 
                            }
                            val topClosest = sortedByDistance.take(3)
                            val nightlifeVenue = topClosest.random()
                            
                            val distance = calculateDistance(lastLat, lastLng, nightlifeVenue.lat, nightlifeVenue.lng)
                            val stop = createStop(nightlifeVenue, nightlifeCalendar, NIGHTLIFE_DURATION_MINUTES, distance)
                            
                            itinerary.add(stop)
                            currentCost += nightlifeVenue.estimatedCost
                            totalDistance += distance
                            
                            Log.d("ItineraryGenerator", "Added nightlife: ${nightlifeVenue.name} (cost: $${nightlifeVenue.estimatedCost}, distance: ${"%.2f".format(distance)} km)")
                        } else {
                            Log.d("ItineraryGenerator", "No nightlife venues available within budget")
                        }
                    } else {
                        Log.d("ItineraryGenerator", "Not enough time for nightlife venue")
                    }
                }
            } catch (e: Exception) {
                Log.e("ItineraryGenerator", "Error parsing last stop time for nightlife", e)
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
        
        // Calculate transit estimates for this stop
        val transitEstimate = if (distanceFromPrevious > 0) {
            TransitHelper.estimateTransit(distanceFromPrevious)
        } else {
            null
        }

        return ItineraryStop(
            place = place,
            startTime = startTime,
            endTime = endTime,
            durationMinutes = durationMinutes,
            distanceFromPreviousKm = distanceFromPrevious,
            transitEstimate = transitEstimate
        )
    }
    
    /**
     * Create an ItineraryStop for an Event.
     * Events use a temporary Place object to represent the venue.
     */
    private fun createEventStop(
        event: Event,
        distanceFromPrevious: Double = 0.0
    ): ItineraryStop {
        // Create a temporary Place for the event venue
        val eventPlace = Place(
            id = "event_${event.id}",
            name = event.name,
            type = PlaceType.MUSEUM, // Events are treated similarly to museums for display
            lat = event.venueLatitude,
            lng = event.venueLongitude,
            rating = 5.0f, // Events don't have ratings in the same way
            isOpen = true,
            priceLevel = when {
                event.priceMin == null -> 1
                event.priceMin < 30 -> 1
                event.priceMin < 60 -> 2
                event.priceMin < 100 -> 3
                else -> 4
            },
            estimatedCost = event.priceMin?.toInt() ?: 0,
            openHour = 0,
            closeHour = 24,
            isOutdoor = false
        )
        
        // Create calendar at event start time
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, event.startHour)
            set(Calendar.MINUTE, event.startMinute)
            set(Calendar.SECOND, 0)
        }
        
        val startTime = timeFormat.format(calendar.time)
        calendar.add(Calendar.MINUTE, event.durationMinutes)
        val endTime = timeFormat.format(calendar.time)
        
        // Calculate transit estimates
        val transitEstimate = if (distanceFromPrevious > 0) {
            TransitHelper.estimateTransit(distanceFromPrevious)
        } else {
            null
        }
        
        return ItineraryStop(
            place = eventPlace,
            startTime = startTime,
            endTime = endTime,
            durationMinutes = event.durationMinutes,
            distanceFromPreviousKm = distanceFromPrevious,
            transitEstimate = transitEstimate,
            event = event  // Attach the original event for UI purposes
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
            PlaceType.NIGHTLIFE -> NIGHTLIFE_DURATION_MINUTES
        }
    }
}

