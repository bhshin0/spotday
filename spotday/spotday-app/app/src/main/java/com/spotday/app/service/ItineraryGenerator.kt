package com.spotday.app.service

import android.util.Log
import com.spotday.app.api.EventsRepository
import com.spotday.app.api.NeighborhoodsRepository
import com.spotday.app.api.PlacesRepository
import com.spotday.app.api.QuickStopsRepository
import com.spotday.app.api.ScenicRoutesRepository
import com.spotday.app.model.DayHours
import com.spotday.app.model.Event
import com.spotday.app.model.ExplorationMode
import com.spotday.app.model.ItineraryStop
import com.spotday.app.model.Neighborhood
import com.spotday.app.model.Place
import com.spotday.app.model.PlaceType
import com.spotday.app.model.qualityScore
import com.spotday.app.model.QuickStopType
import com.spotday.app.model.ServiceStyle
import com.spotday.app.model.StopType
import com.spotday.app.model.Waypoint
import com.spotday.app.model.WeeklyHours
import com.spotday.app.util.TransitHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.*

class ItineraryGenerator(
    private val placesRepository: PlacesRepository,
    private val neighborhoodsRepository: NeighborhoodsRepository,
    private val eventsRepository: EventsRepository = EventsRepository(),
    private val scenicRoutesRepository: ScenicRoutesRepository = ScenicRoutesRepository(),
    private val quickStopsRepository: QuickStopsRepository = QuickStopsRepository()
) {

    companion object {
        private const val MUSEUM_DURATION_MINUTES = 120 // 2 hours
        private const val PARK_DURATION_MINUTES = 90   // 1.5 hours
        private const val RESTAURANT_DURATION_MINUTES = 60 // 1 hour
        private const val WATERFRONT_DURATION_MINUTES = 90 // 1.5 hours
        private const val HISTORIC_SITE_DURATION_MINUTES = 60 // 1 hour
        private const val SHOPPING_DURATION_MINUTES = 90 // 1.5 hours
        private const val NIGHTLIFE_DURATION_MINUTES = 120 // 2 hours at a bar/club
        // New activity durations
        private const val ENTERTAINMENT_DURATION_MINUTES = 120 // 2 hours (show/performance)
        private const val GAMES_DURATION_MINUTES = 90 // 1.5 hours (escape room, bowling)
        private const val OUTDOOR_DURATION_MINUTES = 120 // 2 hours (kayaking, biking)
        private const val MASSAGE_DURATION_MINUTES = 90 // 1.5 hours
        private const val SAUNA_DURATION_MINUTES = 120 // 2 hours
        private const val BEACH_DURATION_MINUTES = 120 // 2 hours
        private const val BREWERY_DURATION_MINUTES = 75 // 1.25 hours (tasting)
        private const val CLASS_DURATION_MINUTES = 120 // 2 hours (cooking, pottery)
        private const val MARKET_DURATION_MINUTES = 60 // 1 hour (browsing)
        private const val SPORTS_DURATION_MINUTES = 120 // 2 hours (golf, climbing)
        private const val ZOO_DURATION_MINUTES = 180 // 3 hours (zoo, aquarium)
        private const val CINEMA_DURATION_MINUTES = 150 // 2.5 hours (movie)
        private const val ATTRACTION_DURATION_MINUTES = 90 // 1.5 hours (tourist spot)
        
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
    
    /**
     * Select the "home" neighborhood for the itinerary based on actual venue concentration.
     * Uses weighted random selection - neighborhoods with more matching venues have higher probability.
     * 
     * @param candidateVenues All venues matching the user's selected activity/food types
     * @param cityId The current city ID for scoped neighborhood lookups
     * @param isSpontaneous If true, skip neighborhood selection (city-wide mode)
     * @return The selected neighborhood, or null for city-wide mode
     */
    private fun selectHomeNeighborhood(
        candidateVenues: List<Place>,
        cityId: String,
        isSpontaneous: Boolean
    ): Neighborhood? {
        // Spontaneous mode = city-wide, no neighborhood filtering
        if (isSpontaneous) {
            Log.d("ItineraryGenerator", "Spontaneous mode: skipping neighborhood selection")
            return null
        }
        
        // Count venues per neighborhood (additive across all types)
        val counts = candidateVenues
            .mapNotNull { it.neighborhood }
            .groupingBy { it }
            .eachCount()
        
        if (counts.isEmpty()) {
            Log.d("ItineraryGenerator", "No venues with neighborhoods assigned, going city-wide")
            return null
        }
        
        Log.d("ItineraryGenerator", "Venue counts by neighborhood for $cityId: $counts")
        
        // Weighted random selection - more venues = higher probability
        val totalWeight = counts.values.sum()
        var randomValue = (0 until totalWeight).random()
        
        for ((neighborhoodId, count) in counts) {
            randomValue -= count
            if (randomValue < 0) {
                // Use city-scoped lookup to avoid matching wrong city's neighborhoods
                val selected = neighborhoodsRepository.getNeighborhood(cityId, neighborhoodId)
                Log.d("ItineraryGenerator", "Selected neighborhood: ${selected?.name ?: neighborhoodId} (had $count/${totalWeight} venues)")
                return selected
            }
        }
        
        // Fallback (shouldn't reach here)
        return counts.keys.firstOrNull()?.let { neighborhoodsRepository.getNeighborhood(cityId, it) }
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
     * Weighted random selection - higher quality scores get picked more often.
     * Uses 4th power weighting for aggressive quality preference.
     * 
     * Example with 4th power:
     *   - 0.9^4 = 0.66 (high quality → high weight)
     *   - 0.5^4 = 0.06 (low quality → low weight)
     *   - This creates ~11x difference, so top spots dominate but variety still exists
     */
    private fun <T> weightedRandomPick(items: List<T>, scoreFunc: (T) -> Float): T? {
        if (items.isEmpty()) return null
        if (items.size == 1) return items.first()
        
        // 4th power weighting for aggressive quality preference
        val weights = items.map { scoreFunc(it).pow(4) }
        val totalWeight = weights.sum()
        
        if (totalWeight <= 0f) return items.random() // Fallback if all scores are 0
        
        var random = kotlin.random.Random.nextFloat() * totalWeight
        for ((index, weight) in weights.withIndex()) {
            random -= weight
            if (random <= 0) return items[index]
        }
        return items.last()
    }
    
    /**
     * Check if a place is open at the given hour on a specific day.
     * Uses weeklyHours to determine if open. Handles overnight hours (e.g., bar open 4 PM - 2 AM)
     * 
     * @param place The place to check
     * @param hour The hour to check (0-23)
     * @param dayOfWeek The day of week (Calendar.SUNDAY through Calendar.SATURDAY)
     */
    private fun isOpenAt(place: Place, hour: Int, dayOfWeek: Int = Calendar.SATURDAY): Boolean {
        val dayHours = place.weeklyHours.getHoursForDay(dayOfWeek)
        
        // null means closed on this day
        if (dayHours == null) return false
        
        // Parse hours from "HH:mm" format
        val openHour = dayHours.open.substringBefore(":").toIntOrNull() ?: return false
        val closeHour = dayHours.close.substringBefore(":").toIntOrNull() ?: return false
        
        return if (closeHour > openHour) {
            // Normal hours (e.g., 10 AM - 5 PM)
            hour >= openHour && hour < closeHour
        } else {
            // Overnight hours (e.g., 4 PM - 2 AM)
            hour >= openHour || hour < closeHour
        }
    }
    
    /**
     * Check if a venue is open for the entire duration starting at the given hour.
     * For example, if an activity takes 2 hours starting at 8 PM, ensure venue
     * doesn't close before 10 PM.
     * 
     * @param venue The venue to check
     * @param startHour The start hour (0-23)
     * @param durationMinutes The duration in minutes
     * @param dayOfWeek The day of week (Calendar.SUNDAY through Calendar.SATURDAY)
     */
    private fun isOpenForDuration(venue: Place, startHour: Int, durationMinutes: Int, dayOfWeek: Int = Calendar.SATURDAY): Boolean {
        // Check start time
        if (!isOpenAt(venue, startHour, dayOfWeek)) return false
        
        // Get hours for this day
        val dayHours = venue.weeklyHours.getHoursForDay(dayOfWeek) ?: return false
        
        val openHour = dayHours.open.substringBefore(":").toIntOrNull() ?: return false
        val closeHour = dayHours.close.substringBefore(":").toIntOrNull() ?: return false
        
        // Check end time (when the activity would finish)
        val endHour = startHour + (durationMinutes / 60)
        val endMinutes = durationMinutes % 60
        
        // For normal hours (not overnight), check if we finish before closing
        return if (closeHour > openHour) {
            // Normal hours: must finish before close time
            endHour < closeHour || (endHour == closeHour && endMinutes == 0)
        } else {
            // Overnight hours (e.g., bar 4 PM - 2 AM)
            true // Simplified: assume overnight venues accommodate the activity
        }
    }
    
    /**
     * Find the earliest hour when a venue can accommodate a full activity duration.
     * Returns null if venue can never fit the activity in the given window.
     * 
     * Example: Brewery opens at 12 PM, closes 9 PM, duration 75 min
     *          User window is 11 AM - 8 PM
     *          Returns 12 (can start at noon and finish by 1:15 PM, well before 9 PM)
     * 
     * @param venue The venue to check
     * @param fromHour Start of the search window
     * @param toHour End of the search window
     * @param durationMinutes The activity duration
     * @param dayOfWeek The day of week (Calendar.SUNDAY through Calendar.SATURDAY)
     */
    private fun findEarliestOpenHour(venue: Place, fromHour: Int, toHour: Int, durationMinutes: Int = 60, dayOfWeek: Int = Calendar.SATURDAY): Int? {
        for (hour in fromHour until toHour) {
            if (isOpenForDuration(venue, hour, durationMinutes, dayOfWeek)) return hour
        }
        return null
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
        
        // Handle overnight hours (e.g., 22 to 2 = 4 hours, not -20)
        val totalHours = if (endHour < startHour) {
            (24 - startHour) + endHour  // Cross midnight
        } else {
            endHour - startHour
        }
        val budgetBuffer = (totalBudget * BUDGET_BUFFER_PERCENTAGE).toInt()
        
        // Bar crawl mode: 45 minutes per bar for maximum hopping
        val minutesPerBar = 45
        val totalMinutes = totalHours * 60
        
        // Calculate how many bars fit with 45 min per bar + 30 min travel between
        // Formula: (barCount × 45) + ((barCount - 1) × 30) <= totalMinutes
        // Solve: 45×barCount + 30×barCount - 30 <= totalMinutes
        //        75×barCount <= totalMinutes + 30
        val maxBars = ((totalMinutes + 30) / 75).coerceAtLeast(1).coerceAtMost(6)
        val barCount = maxBars
        
        val travelTimeTotal = (barCount - 1) * TRAVEL_TIME_MINUTES  // No travel after last bar
        val availableMinutes = totalMinutes - travelTimeTotal
        
        Log.d("ItineraryGenerator", "Bar crawl: $barCount venues over $totalHours hours ($minutesPerBar min each, total time: ${totalMinutes}min)")
        
        val candidateNightlife = placesRepository.searchNightlife(nightlifeTypes)
        val sortedNightlife = candidateNightlife
            .shuffled()
            .sortedWith(
                compareByDescending<Place> { it.rating }
                    .thenBy { it.estimatedCost }
            )
        
        // Initialize starting location using city-aware neighborhoods
        val cityId = placesRepository.currentCityId
        val (initialLat, initialLng) = when {
            userStartLat != null && userStartLng != null -> {
                Log.d("ItineraryGenerator", "Bar crawl using provided location: ($userStartLat, $userStartLng)")
                Pair(userStartLat, userStartLng)
            }
            else -> {
                // Weighted random selection: neighborhoods with more bars get higher probability
                // but there's still variety to explore different areas
                val venueCounts = candidateNightlife
                    .mapNotNull { it.neighborhood }
                    .groupingBy { it }
                    .eachCount()
                
                if (venueCounts.isNotEmpty()) {
                    // Weighted random pick based on venue count
                    val totalWeight = venueCounts.values.sum()
                    var randomValue = (0 until totalWeight).random()
                    
                    var selectedNeighborhoodId: String? = null
                    for ((neighborhoodId, count) in venueCounts) {
                        randomValue -= count
                        if (randomValue < 0) {
                            selectedNeighborhoodId = neighborhoodId
                            break
                        }
                    }
                    
                    // Get the neighborhood data for coordinates
                    val selectedNeighborhood = selectedNeighborhoodId?.let { 
                        neighborhoodsRepository.getNeighborhood(cityId, it) 
                    }
                    
                    if (selectedNeighborhood != null) {
                        val venueCount = venueCounts[selectedNeighborhoodId] ?: 0
                        Log.d("ItineraryGenerator", "Bar crawl starting from ${selectedNeighborhood.name} (weighted: $venueCount/$totalWeight venues)")
                        Pair(selectedNeighborhood.centerLat, selectedNeighborhood.centerLng)
                    } else {
                        // Fallback: pick first venue from the selected neighborhood
                        val firstVenueInNeighborhood = candidateNightlife.find { it.neighborhood == selectedNeighborhoodId }
                        if (firstVenueInNeighborhood != null) {
                            Log.d("ItineraryGenerator", "Bar crawl starting from venue in $selectedNeighborhoodId")
                            Pair(firstVenueInNeighborhood.lat, firstVenueInNeighborhood.lng)
                        } else {
                            // Use first available venue
                            val firstVenue = candidateNightlife.firstOrNull()
                            if (firstVenue != null) {
                                Log.d("ItineraryGenerator", "Bar crawl starting from first venue: ${firstVenue.name}")
                                Pair(firstVenue.lat, firstVenue.lng)
                            } else {
                                // Last resort: city center
                                val cityProfile = neighborhoodsRepository.getCityProfile(cityId)
                                Log.d("ItineraryGenerator", "Bar crawl using $cityId city center (no venues found)")
                                Pair(cityProfile?.centerLat ?: 33.4484, cityProfile?.centerLng ?: -112.074)
                            }
                        }
                    }
                } else {
                    // No neighborhood data on venues - use first venue or city center
                    val firstVenue = candidateNightlife.firstOrNull()
                    if (firstVenue != null) {
                        Log.d("ItineraryGenerator", "Bar crawl starting from first venue (no neighborhoods): ${firstVenue.name}")
                        Pair(firstVenue.lat, firstVenue.lng)
                    } else {
                        val cityProfile = neighborhoodsRepository.getCityProfile(cityId)
                        Log.e("ItineraryGenerator", "No venues for bar crawl in $cityId, using city center")
                        Pair(cityProfile?.centerLat ?: 33.4484, cityProfile?.centerLng ?: -112.074)
                    }
                }
            }
        }
        
        // Build bar crawl itinerary
        val itinerary = mutableListOf<ItineraryStop>()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        
        // Get the day of week for checking opening hours
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        Log.d("ItineraryGenerator", "Bar crawl day of week: $dayOfWeek (1=Sun, 7=Sat)")
        
        var currentCost = 0
        var currentLat = initialLat
        var currentLng = initialLng
        var totalDistance = 0.0
        val usedPlaces = mutableSetOf<String>()
        
        for (i in 0 until barCount) {
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            
            // Check if we've reached the end time (handling overnight)
            val shouldStop = if (endHour < startHour) {
                // Overnight: stop if we're past endHour but before startHour
                currentHour >= endHour && currentHour < startHour
            } else {
                // Same day: stop if we've reached or passed endHour
                currentHour >= endHour
            }
            
            if (shouldStop) {
                Log.d("ItineraryGenerator", "Reached end time at ${currentHour}:00")
                break
            }
            
            // Find closest unused bar within budget and open at this hour (using day-specific hours)
            val availableBars = sortedNightlife
                .filterNot { usedPlaces.contains(it.id) }
                .filter { currentCost + it.estimatedCost <= budgetBuffer }
                .filter { isOpenForDuration(it, currentHour, minutesPerBar, dayOfWeek) }
            
            if (availableBars.isEmpty()) {
                Log.d("ItineraryGenerator", "No more bars available within budget")
                break
            }
            
            val sortedByDistance = availableBars.sortedBy { 
                calculateDistance(currentLat, currentLng, it.lat, it.lng) 
            }
            val topClosest = sortedByDistance.take(5)
            val selectedBar = weightedRandomPick(topClosest) { it.qualityScore() } ?: continue
            
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
            
            Log.d("ItineraryGenerator", "Bar ${i+1}/$barCount: ${selectedBar.name} at ${currentHour}:00 (cost: $${selectedBar.estimatedCost}, distance: ${"%.2f".format(distance)} km)")
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
        serviceStyles: List<String> = emptyList(), // Empty = all styles allowed
        isHungryNow: Boolean = false,
        isSpontaneousMode: Boolean = false,
        userStartLat: Double? = null,
        userStartLng: Double? = null,
        nightlifeTypes: List<String> = emptyList(),
        avoidOutdoor: Boolean = false,
        selectedEventIds: List<String> = emptyList(),
        explorationMode: ExplorationMode = ExplorationMode.ONE_AREA
    ): List<ItineraryStop> {
        // Ensure data is loaded for the current city before generating
        val cityId = placesRepository.currentCityId
        Log.d("ItineraryGenerator", "Ensuring data loaded for city: $cityId")
        placesRepository.prefetchForCity(cityId)
        
        Log.d("ItineraryGenerator", "Generating itinerary from $startHour to $endHour, budget: $$totalBudget, activities: $activityTypes, food: $foodTypes, serviceStyles: ${if (serviceStyles.isEmpty()) "ALL" else serviceStyles}, hungryNow: $isHungryNow, spontaneous: $isSpontaneousMode, nightlife: $nightlifeTypes, avoidOutdoor: $avoidOutdoor, events: ${selectedEventIds.size}, explorationMode: $explorationMode")

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

        // Get the day of week for the itinerary (used for opening hours checks)
        // This uses TODAY's date - the itinerary is for today
        val itineraryDayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        Log.d("ItineraryGenerator", "Itinerary day of week: $itineraryDayOfWeek (1=Sun, 7=Sat)")

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
        // New activity categories
        if (activityTypes.contains("entertainment")) {
            candidateActivities.addAll(placesRepository.searchEntertainment())
        }
        if (activityTypes.contains("games")) {
            candidateActivities.addAll(placesRepository.searchGames())
        }
        if (activityTypes.contains("outdoor")) {
            candidateActivities.addAll(placesRepository.searchOutdoor())
        }
        if (activityTypes.contains("massage")) {
            candidateActivities.addAll(placesRepository.searchMassage())
        }
        if (activityTypes.contains("sauna")) {
            candidateActivities.addAll(placesRepository.searchSauna())
        }
        if (activityTypes.contains("beach")) {
            candidateActivities.addAll(placesRepository.searchBeach())
        }
        if (activityTypes.contains("breweries")) {
            candidateActivities.addAll(placesRepository.searchBreweries())
        }
        if (activityTypes.contains("classes")) {
            candidateActivities.addAll(placesRepository.searchClasses())
        }
        if (activityTypes.contains("markets")) {
            candidateActivities.addAll(placesRepository.searchMarkets())
        }
        if (activityTypes.contains("sports")) {
            candidateActivities.addAll(placesRepository.searchSports())
        }
        if (activityTypes.contains("zoos")) {
            candidateActivities.addAll(placesRepository.searchZoos())
        }
        if (activityTypes.contains("cinema")) {
            candidateActivities.addAll(placesRepository.searchCinema())
        }
        if (activityTypes.contains("attractions")) {
            candidateActivities.addAll(placesRepository.searchAttractions())
        }
        
        Log.d("ItineraryGenerator", "Loaded ${candidateActivities.size} candidate activities for types: $activityTypes")
        // Log breakdown by type
        candidateActivities.groupBy { it.type }.forEach { (type, places) ->
            val sampleHours = places.firstOrNull()?.weeklyHours?.friday
            Log.d("ItineraryGenerator", "  - $type: ${places.size} venues (sample Fri hours: ${sampleHours?.open}-${sampleHours?.close})")
        }
        
        // Pre-filter to venues that can fit their full activity duration within user's time window
        // This prevents scheduling failures when venues open later (e.g., breweries at noon)
        // Uses day-specific hours (e.g., museums closed on Mondays)
        val activitiesAvailableToday = candidateActivities.filter { venue ->
            val duration = getDuration(venue.type)
            findEarliestOpenHour(venue, startHour, endHour, duration, itineraryDayOfWeek) != null
        }
        
        if (activitiesAvailableToday.size < candidateActivities.size) {
            val filtered = candidateActivities.size - activitiesAvailableToday.size
            Log.d("ItineraryGenerator", "Filtered out $filtered venues not open during $startHour:00-$endHour:00 window")
        }
        Log.d("ItineraryGenerator", "Venues available during window: ${activitiesAvailableToday.size}/${candidateActivities.size}")

        val allCandidateRestaurants = placesRepository.searchRestaurants(foodTypes)
        
        // Parse service styles from strings
        val allowedStyles: Set<ServiceStyle> = if (serviceStyles.isEmpty()) {
            // Empty = all styles allowed
            ServiceStyle.entries.toSet()
        } else {
            serviceStyles.mapNotNull { style ->
                when (style.lowercase()) {
                    "quick" -> ServiceStyle.QUICK
                    "casual" -> ServiceStyle.CASUAL
                    "formal" -> ServiceStyle.FORMAL
                    else -> null
                }
            }.toSet()
        }
        
        Log.d("ItineraryGenerator", "Filtering restaurants by service styles: $allowedStyles")
        
        // Filter restaurants by allowed service styles
        val candidateRestaurants = allCandidateRestaurants.filter { restaurant ->
            restaurant.serviceStyle in allowedStyles
        }
        
        Log.d("ItineraryGenerator", "Restaurants after style filtering: ${candidateRestaurants.size}/${allCandidateRestaurants.size}")

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
        
        // Initialize starting location (cityId already defined above)
        val (initialLat, initialLng) = when {
            userStartLat != null && userStartLng != null -> {
                Log.d("ItineraryGenerator", "Using provided start location: ($userStartLat, $userStartLng)")
                Pair(userStartLat, userStartLng)
            }
            else -> {
                // Get neighborhoods for the current city and pick a random one
                val cityNeighborhoods = neighborhoodsRepository.getNeighborhoodsForCity(cityId)
                
                if (cityNeighborhoods.isNotEmpty()) {
                    val randomNeighborhood = cityNeighborhoods.random()
                    Log.d("ItineraryGenerator", "Using random $cityId neighborhood: ${randomNeighborhood.name}")
                    Pair(randomNeighborhood.centerLat, randomNeighborhood.centerLng)
                } else {
                    // Fallback: use first available venue's location
                    val firstPlace = activitiesAvailableToday.firstOrNull() ?: candidateRestaurants.firstOrNull()
                    if (firstPlace != null) {
                        Log.d("ItineraryGenerator", "Using first venue location: ${firstPlace.name}")
                        Pair(firstPlace.lat, firstPlace.lng)
                    } else {
                        // Last resort: use city profile center if available
                        val cityProfile = neighborhoodsRepository.getCityProfile(cityId)
                        if (cityProfile != null) {
                            Log.d("ItineraryGenerator", "Using $cityId city center")
                            Pair(cityProfile.centerLat, cityProfile.centerLng)
                        } else {
                            Log.e("ItineraryGenerator", "No location data available for $cityId")
                            Pair(0.0, 0.0) // Will show error state
                        }
                    }
                }
            }
        }
        
        var currentLat = initialLat
        var currentLng = initialLng
        var totalDistance = 0.0
        
        Log.d("ItineraryGenerator", "Starting from location at ($currentLat, $currentLng)")
        
        // Combine all candidate venues for neighborhood selection (using pre-filtered activities)
        val allCandidateVenues = activitiesAvailableToday + candidateRestaurants
        Log.d("ItineraryGenerator", "Total candidate venues for neighborhood selection: ${allCandidateVenues.size}")
        
        // Determine "home" neighborhood based on venue concentration (weighted random)
        var homeNeighborhood = selectHomeNeighborhood(
            candidateVenues = allCandidateVenues,
            cityId = cityId,
            isSpontaneous = isSpontaneousMode
        )
        
        var adjacentNeighborhoods = homeNeighborhood?.let { 
            neighborhoodsRepository.getAdjacentNeighborhoods(cityId, it.id).map { n -> n.id }.toSet()
        } ?: emptySet()
        
        Log.d("ItineraryGenerator", "Home neighborhood: ${homeNeighborhood?.name ?: "city-wide"}, adjacent: $adjacentNeighborhoods, mode: $explorationMode")
        
        // Neighborhood scoring function
        fun neighborhoodScore(place: Place): Int {
            val home = homeNeighborhood ?: return 0
            
            return when {
                place.neighborhood == home.id -> 50  // Same neighborhood: +50
                place.neighborhood in adjacentNeighborhoods -> 20 // Adjacent: +20
                else -> 0 // Other neighborhoods: no bonus
            }
        }
        
        // Threshold for minimum venues before expanding search
        val VENUE_THRESHOLD = 2
        
        // Filter to home neighborhood only
        fun filterByHomeNeighborhood(places: List<Place>): List<Place> {
            val home = homeNeighborhood ?: return places
            return places.filter { place ->
                place.neighborhood == null || place.neighborhood == home.id
            }
        }
        
        // Filter to home + adjacent neighborhoods
        fun filterByHomeAndAdjacent(places: List<Place>): List<Place> {
            val home = homeNeighborhood ?: return places
            val allowedNeighborhoods = adjacentNeighborhoods + home.id
            return places.filter { place ->
                place.neighborhood == null || place.neighborhood in allowedNeighborhoods
            }
        }
        
        // Apply neighborhood filtering with threshold-based fallback
        // Spontaneous mode or CITY_WIDE: no filtering
        // ONE_AREA: try home → home+adjacent → city-wide based on threshold
        var filteredActivities: List<Place>
        var filteredRestaurants: List<Place>
        
        if (isSpontaneousMode || explorationMode == ExplorationMode.CITY_WIDE || homeNeighborhood == null) {
            // City-wide mode: use all venues (pre-filtered for time window)
            filteredActivities = activitiesAvailableToday
            filteredRestaurants = candidateRestaurants
            Log.d("ItineraryGenerator", "Using city-wide venue pool (mode: ${if (isSpontaneousMode) "spontaneous" else explorationMode})")
        } else {
            // ONE_AREA mode: cascade filtering with threshold
            val homeOnlyActivities = filterByHomeNeighborhood(activitiesAvailableToday)
            val homeOnlyRestaurants = filterByHomeNeighborhood(candidateRestaurants)
            val homeOnlyTotal = homeOnlyActivities.size + homeOnlyRestaurants.size
            
            if (homeOnlyTotal >= VENUE_THRESHOLD) {
                // Threshold met with home neighborhood only
                filteredActivities = homeOnlyActivities
                filteredRestaurants = homeOnlyRestaurants
                Log.d("ItineraryGenerator", "Using home neighborhood only: $homeOnlyTotal venues (threshold: $VENUE_THRESHOLD)")
            } else {
                // Expand to adjacent neighborhoods
                val adjacentActivities = filterByHomeAndAdjacent(activitiesAvailableToday)
                val adjacentRestaurants = filterByHomeAndAdjacent(candidateRestaurants)
                val adjacentTotal = adjacentActivities.size + adjacentRestaurants.size
                
                if (adjacentTotal >= VENUE_THRESHOLD) {
                    filteredActivities = adjacentActivities
                    filteredRestaurants = adjacentRestaurants
                    Log.d("ItineraryGenerator", "Expanded to adjacent neighborhoods: $adjacentTotal venues (threshold: $VENUE_THRESHOLD)")
                } else {
                    // Fall back to city-wide
                    filteredActivities = activitiesAvailableToday
                    filteredRestaurants = candidateRestaurants
                    homeNeighborhood = null  // Clear for UI to show city-wide
                    adjacentNeighborhoods = emptySet()
                    Log.d("ItineraryGenerator", "Threshold not met, using city-wide: ${activitiesAvailableToday.size + candidateRestaurants.size} venues")
                }
            }
        }
        
        Log.d("ItineraryGenerator", "After neighborhood filter: ${filteredActivities.size}/${activitiesAvailableToday.size} activities, ${filteredRestaurants.size}/${candidateRestaurants.size} restaurants")
        
        // Helper function to calculate distance from user's start location
        fun distanceFromStart(place: Place): Double {
            return calculateDistance(initialLat, initialLng, place.lat, place.lng)
        }
        
        // Sort venues based on mode:
        // - Spontaneous: proximity first (closest venues first)
        // - Normal: neighborhood score first, then quality
        val sortedActivities = if (isSpontaneousMode) {
            // Spontaneous mode: sort by proximity, then rating
            filteredActivities.sortedWith(
                compareBy<Place> { place -> distanceFromStart(place) }
                    .thenBy { place -> if (avoidOutdoor && place.isOutdoor) 1 else 0 }
                    .thenByDescending { place -> place.rating }
            ).also {
                Log.d("ItineraryGenerator", "Spontaneous mode: sorted ${it.size} activities by proximity")
            }
        } else {
            // Normal mode: neighborhood score, then shuffle for variety
            filteredActivities
                .shuffled()
                .sortedWith(
                    compareByDescending<Place> { place -> neighborhoodScore(place) }
                        .thenBy { place -> if (avoidOutdoor && place.isOutdoor) 1 else 0 }
                        .thenByDescending { place -> place.rating }
                        .thenBy { place -> place.estimatedCost }
                )
        }
        
        if (avoidOutdoor) {
            val outdoorCount = filteredActivities.count { place -> place.isOutdoor }
            val indoorCount = filteredActivities.size - outdoorCount
            Log.d("ItineraryGenerator", "Weather mode: Prioritizing indoor ($indoorCount indoor, $outdoorCount outdoor)")
        }
        
        // Sort restaurants based on mode
        val sortedRestaurants = if (isSpontaneousMode) {
            // Spontaneous mode: sort by proximity, then rating
            filteredRestaurants.sortedWith(
                compareBy<Place> { place -> distanceFromStart(place) }
                    .thenByDescending { place -> place.rating }
            ).also {
                Log.d("ItineraryGenerator", "Spontaneous mode: sorted ${it.size} restaurants by proximity")
            }
        } else {
            // Normal mode: neighborhood score, then shuffle for variety
            filteredRestaurants
                .shuffled()
                .sortedWith(
                    compareByDescending<Place> { place -> neighborhoodScore(place) }
                        .thenByDescending { place -> place.rating }
                        .thenBy { place -> place.estimatedCost }
                )
        }
        
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        
        // Use the itinerary day of week we determined earlier for all opening hours checks
        val dayOfWeek = itineraryDayOfWeek
        
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
                    val isDinnerTime = currentHour >= DINNER_START_HOUR
                    
                    // Find unselected restaurants within budget using nearest-neighbor
                    // Also filter by operating hours (using day-specific hours)
                    // FORMAL restaurants are only allowed for dinner (17:00+)
                    val availableRestaurants = sortedRestaurants
                        .filterNot { usedPlaces.contains(it.id) }
                        .filter { currentCost + it.estimatedCost <= budgetBuffer }
                        .filter { isOpenForDuration(it, currentHour, getDuration(it), dayOfWeek) }
                        .filter { restaurant ->
                            // Only allow formal dining for dinner slots
                            if (restaurant.serviceStyle == ServiceStyle.FORMAL) {
                                isDinnerTime
                            } else {
                                true
                            }
                        }
                    
                    if (availableRestaurants.isNotEmpty()) {
                        // Pick from top 5 closest restaurants using weighted random by quality
                        val sortedByDistance = availableRestaurants.sortedBy { 
                            calculateDistance(currentLat, currentLng, it.lat, it.lng) 
                        }
                        val topClosest = sortedByDistance.take(5)
                        val selectedRestaurant = weightedRandomPick(topClosest) { it.qualityScore() }
                        
                        if (selectedRestaurant != null) {
                            val distance = calculateDistance(currentLat, currentLng, selectedRestaurant.lat, selectedRestaurant.lng)
                            val restaurantDuration = getDuration(selectedRestaurant)
                            val stop = createStop(selectedRestaurant, calendar, restaurantDuration, distance)
                            itinerary.add(stop)
                            currentCost += selectedRestaurant.estimatedCost
                            usedPlaces.add(selectedRestaurant.id)
                            
                            // Update current location
                            currentLat = selectedRestaurant.lat
                            currentLng = selectedRestaurant.lng
                            totalDistance += distance
                            
                            Log.d("ItineraryGenerator", "Added ${slot.mealType}: ${selectedRestaurant.name} (${selectedRestaurant.serviceStyle}, ${restaurantDuration}min, cost: $${selectedRestaurant.estimatedCost}, distance: ${"%.2f".format(distance)} km, total: $$currentCost)")
                            
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
                    // Also filter by operating hours (using day-specific hours)
                    val availableNow = sortedActivities
                        .filterNot { usedPlaces.contains(it.id) }
                        .filter { currentCost + it.estimatedCost <= budgetBuffer }
                        .filter { isOpenForDuration(it, currentHour, getDuration(it.type), dayOfWeek) }
                    
                    // Look-ahead scheduling: if nothing open now, find what opens soonest
                    val (availableActivities, waitMinutes) = if (availableNow.isNotEmpty()) {
                        availableNow to 0
                    } else {
                        // Find venues that open later in the day (considering full activity duration)
                        val opensLater = sortedActivities
                            .filterNot { usedPlaces.contains(it.id) }
                            .filter { currentCost + it.estimatedCost <= budgetBuffer }
                            .mapNotNull { venue ->
                                val duration = getDuration(venue.type)
                                findEarliestOpenHour(venue, currentHour, endHour, duration, dayOfWeek)?.let { opensAt ->
                                    venue to ((opensAt - currentHour) * 60)
                                }
                            }
                            .sortedBy { it.second }
                        
                        if (opensLater.isNotEmpty()) {
                            val (_, minWait) = opensLater.first()
                            // Only wait if reasonable (<= 90 min)
                            if (minWait <= 90) {
                                val venuesOpeningSoon = opensLater
                                    .filter { it.second == minWait }
                                    .map { it.first }
                                Log.d("ItineraryGenerator", "Nothing open now, waiting $minWait min for ${venuesOpeningSoon.size} venues to open")
                                venuesOpeningSoon to minWait
                            } else {
                                Log.d("ItineraryGenerator", "Next venue opens in $minWait min (too long to wait)")
                                emptyList<Place>() to 0
                            }
                        } else {
                            emptyList<Place>() to 0
                        }
                    }
                    
                    if (availableActivities.isNotEmpty()) {
                        // If we need to wait, try to fill the gap productively
                        if (waitMinutes > 0) {
                            val gapCurrentHour = calendar.get(Calendar.HOUR_OF_DAY)
                            var gapFilled = false
                            
                            // Try to fill with a meal if it's mealtime and gap is long enough
                            if (waitMinutes >= 45 && !gapFilled) {
                                val isMealTime = (gapCurrentHour in 7..9) || // Breakfast
                                                (gapCurrentHour in 11..13) || // Lunch
                                                (gapCurrentHour in 17..19)    // Dinner
                                
                                if (isMealTime) {
                                    // Find a quick restaurant (using day-specific hours)
                                    val quickMealOptions = sortedRestaurants
                                        .filterNot { usedPlaces.contains(it.id) }
                                        .filter { currentCost + it.estimatedCost <= budgetBuffer }
                                        .filter { it.serviceStyle == ServiceStyle.QUICK || it.serviceStyle == ServiceStyle.CASUAL }
                                        .filter { isOpenAt(it, gapCurrentHour, dayOfWeek) }
                                        .take(3)
                                    
                                    if (quickMealOptions.isNotEmpty()) {
                                        val meal = quickMealOptions.random()
                                        val mealDuration = if (meal.serviceStyle == ServiceStyle.QUICK) 30 else 45
                                        val distance = calculateDistance(currentLat, currentLng, meal.lat, meal.lng)
                                        
                                        val mealStop = createStop(meal, calendar, mealDuration, distance)
                                        itinerary.add(mealStop)
                                        currentCost += meal.estimatedCost
                                        usedPlaces.add(meal.id)
                                        currentLat = meal.lat
                                        currentLng = meal.lng
                                        totalDistance += distance
                                        
                                        calendar.add(Calendar.MINUTE, mealDuration + TRAVEL_TIME_MINUTES)
                                        gapFilled = true
                                        Log.d("ItineraryGenerator", "Filled wait gap with meal: ${meal.name}")
                                    }
                                }
                            }
                            
                            // If gap not filled and still have time to wait, just advance
                            if (!gapFilled) {
                                // Calculate remaining wait after any partial fill
                                val remainingWait = waitMinutes - (if (gapFilled) 0 else 0)
                                if (remainingWait > 0) {
                                    calendar.add(Calendar.MINUTE, remainingWait)
                                    Log.d("ItineraryGenerator", "Advanced time by $remainingWait min to when venues open")
                                }
                            }
                        }
                        
                        // Pick from top 5 closest activities using weighted random by quality
                        val sortedByDistance = availableActivities.sortedBy { 
                            calculateDistance(currentLat, currentLng, it.lat, it.lng) 
                        }
                        val topClosest = sortedByDistance.take(5)
                        val selectedActivity = weightedRandomPick(topClosest) { it.qualityScore() }
                        
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
                        Log.d("ItineraryGenerator", "No available activities within budget or time window")
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
            val lastLat = lastStop.place?.lat ?: lastStop.event?.venueLatitude ?: 37.7749
            val lastLng = lastStop.place?.lng ?: lastStop.event?.venueLongitude ?: -122.4194
            
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
                    val nightlifeDayOfWeek = nightlifeCalendar.get(Calendar.DAY_OF_WEEK)
                    if (nightlifeHour < endHour - 1) {
                        // Find closest unused nightlife venue within budget
                        // Also filter by operating hours (using day-specific hours)
                        val availableBars = sortedNightlife
                            .filterNot { usedPlaces.contains(it.id) }
                            .filter { currentCost + it.estimatedCost <= budgetBuffer }
                            .filter { isOpenAt(it, nightlifeHour, nightlifeDayOfWeek) }
                        
                        if (availableBars.isNotEmpty()) {
                            val sortedByDistance = availableBars.sortedBy { 
                                calculateDistance(lastLat, lastLng, it.lat, it.lng) 
                            }
                            val topClosest = sortedByDistance.take(5)
                            val nightlifeVenue = weightedRandomPick(topClosest) { it.qualityScore() }
                            
                            if (nightlifeVenue != null) {
                                val distance = calculateDistance(lastLat, lastLng, nightlifeVenue.lat, nightlifeVenue.lng)
                                val stop = createStop(nightlifeVenue, nightlifeCalendar, NIGHTLIFE_DURATION_MINUTES, distance)
                                
                                itinerary.add(stop)
                                currentCost += nightlifeVenue.estimatedCost
                                totalDistance += distance
                                
                                Log.d("ItineraryGenerator", "Added nightlife: ${nightlifeVenue.name} (cost: $${nightlifeVenue.estimatedCost}, distance: ${"%.2f".format(distance)} km)")
                            }
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

        Log.d("ItineraryGenerator", "Generated itinerary with ${itinerary.size} stops (before gap filling)")
        Log.d("ItineraryGenerator", "Total cost: $$currentCost (budget: $$totalBudget)")
        Log.d("ItineraryGenerator", "Total travel distance: ${"%.2f".format(totalDistance)} km")
        
        // Fill gaps with quick stops, scenic routes, or free time
        val filledItinerary = fillGaps(itinerary.toList())
        
        // Log individual stops with distances for debugging
        filledItinerary.forEachIndexed { index, stop ->
            val stopName = stop.place?.name ?: stop.waypoint?.name ?: stop.event?.name ?: "Free Time"
            Log.d("ItineraryGenerator", "Stop ${index + 1} (${stop.stopType}): $stopName")
        }
        
        Log.d("ItineraryGenerator", "Final itinerary: ${filledItinerary.size} stops (after gap filling)")
        
        return filledItinerary
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
            isOutdoor = false,
            weeklyHours = WeeklyHours(
                monday = DayHours("00:00", "23:59"),
                tuesday = DayHours("00:00", "23:59"),
                wednesday = DayHours("00:00", "23:59"),
                thursday = DayHours("00:00", "23:59"),
                friday = DayHours("00:00", "23:59"),
                saturday = DayHours("00:00", "23:59"),
                sunday = DayHours("00:00", "23:59")
            )
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
            PlaceType.RESTAURANT -> RESTAURANT_DURATION_MINUTES // Default, but use getDuration(Place) for service style
            PlaceType.WATERFRONT -> WATERFRONT_DURATION_MINUTES
            PlaceType.HISTORIC_SITE -> HISTORIC_SITE_DURATION_MINUTES
            PlaceType.SHOPPING -> SHOPPING_DURATION_MINUTES
            PlaceType.NIGHTLIFE -> NIGHTLIFE_DURATION_MINUTES
            // New activity types
            PlaceType.ENTERTAINMENT -> ENTERTAINMENT_DURATION_MINUTES
            PlaceType.GAMES -> GAMES_DURATION_MINUTES
            PlaceType.OUTDOOR -> OUTDOOR_DURATION_MINUTES
            PlaceType.MASSAGE -> MASSAGE_DURATION_MINUTES
            PlaceType.SAUNA -> SAUNA_DURATION_MINUTES
            PlaceType.BEACH -> BEACH_DURATION_MINUTES
            PlaceType.BREWERY -> BREWERY_DURATION_MINUTES
            PlaceType.CLASS -> CLASS_DURATION_MINUTES
            PlaceType.MARKET -> MARKET_DURATION_MINUTES
            PlaceType.SPORTS -> SPORTS_DURATION_MINUTES
            PlaceType.ZOO -> ZOO_DURATION_MINUTES
            PlaceType.CINEMA -> CINEMA_DURATION_MINUTES
            PlaceType.ATTRACTION -> ATTRACTION_DURATION_MINUTES
        }
    }
    
    /**
     * Get duration for a place, accounting for service style for restaurants.
     * - QUICK: 30 minutes (food trucks, cafes, fast casual)
     * - CASUAL: 60 minutes (sit-down restaurants)
     * - FORMAL: 90 minutes (fine dining)
     */
    private fun getDuration(place: Place): Int {
        return if (place.type == PlaceType.RESTAURANT) {
            when (place.serviceStyle) {
                ServiceStyle.QUICK -> 30
                ServiceStyle.CASUAL -> 60
                ServiceStyle.FORMAL -> 90
            }
        } else {
            getDuration(place.type)
        }
    }
    
    /**
     * Fill gaps in the itinerary with quick stops, scenic routes, or free time.
     * 
     * Gap handling strategy:
     * - Gaps > 45 min: Insert quick stop (coffee, photo spot)
     * - Gaps 30-45 min: Try scenic route if available, else free time
     * - Gaps 15-30 min: Label as "free time to explore"
     * - Gaps < 15 min: Acceptable buffer, no action
     */
    private fun fillGaps(itinerary: List<ItineraryStop>): List<ItineraryStop> {
        if (itinerary.size < 2) return itinerary
        
        val filledItinerary = mutableListOf<ItineraryStop>()
        val usedQuickStops = mutableSetOf<String>()
        
        // Get cached places for coffee stop fallback
        val cachedPlaces = placesRepository.getCachedPlaces()
        
        for (i in itinerary.indices) {
            val currentStop = itinerary[i]
            filledItinerary.add(currentStop)
            
            // Check for gap before next stop
            if (i < itinerary.size - 1) {
                val nextStop = itinerary[i + 1]
                val gapMinutes = calculateGapMinutes(currentStop.endTime, nextStop.startTime)
                
                if (gapMinutes > 15) {
                    val currentLat = currentStop.place?.lat ?: currentStop.event?.venueLatitude ?: 37.7749
                    val currentLng = currentStop.place?.lng ?: currentStop.event?.venueLongitude ?: -122.4194
                    val nextLat = nextStop.place?.lat ?: nextStop.event?.venueLatitude ?: 37.7749
                    val nextLng = nextStop.place?.lng ?: nextStop.event?.venueLongitude ?: -122.4194
                    
                    // Get neighborhood name for free time suggestions
                    val neighborhoodName = neighborhoodsRepository.findNearestNeighborhood(currentLat, currentLng)?.name 
                        ?: "the area"
                    
                    Log.d("ItineraryGenerator", "Found gap of $gapMinutes min between ${currentStop.place?.name ?: "event"} and ${nextStop.place?.name ?: "event"}")
                    
                    when {
                        gapMinutes > 45 -> {
                            // Try to insert a quick stop (viewpoints, murals, or coffee)
                            val quickStop = quickStopsRepository.findStopForGap(
                                currentLat, currentLng, gapMinutes - 15, usedQuickStops, cachedPlaces
                            )
                            
                            if (quickStop != null) {
                                usedQuickStops.add(quickStop.waypoint.name)
                                val quickStopItem = createQuickStop(
                                    quickStop.waypoint,
                                    currentStop.endTime,
                                    quickStop.durationMinutes,
                                    calculateDistance(currentLat, currentLng, quickStop.waypoint.lat, quickStop.waypoint.lng),
                                    quickStop.type
                                )
                                filledItinerary.add(quickStopItem)
                                Log.d("ItineraryGenerator", "Added quick stop: ${quickStop.waypoint.name}")
                            } else {
                                // No quick stop available, add free time
                                val freeTime = createFreeTime(
                                    currentStop.endTime,
                                    gapMinutes,
                                    neighborhoodName
                                )
                                filledItinerary.add(freeTime)
                                Log.d("ItineraryGenerator", "Added free time: $gapMinutes min")
                            }
                        }
                        gapMinutes in 30..45 -> {
                            // Try scenic route, else add waypoints or free time
                            val scenicRoute = scenicRoutesRepository.findScenicRoute(
                                currentLat, currentLng, nextLat, nextLng
                            )
                            
                            if (scenicRoute != null && scenicRoute.waypoints.isNotEmpty()) {
                                // Add waypoint stops for scenic route
                                for (waypoint in scenicRoute.waypoints) {
                                    val waypointStop = createWaypointStop(
                                        waypoint,
                                        currentStop.endTime,
                                        scenicRoute.description
                                    )
                                    filledItinerary.add(waypointStop)
                                }
                                Log.d("ItineraryGenerator", "Added scenic route: ${scenicRoute.description}")
                            } else {
                                // Add free time
                                val freeTime = createFreeTime(
                                    currentStop.endTime,
                                    gapMinutes,
                                    neighborhoodName
                                )
                                filledItinerary.add(freeTime)
                                Log.d("ItineraryGenerator", "Added free time: $gapMinutes min")
                            }
                        }
                        gapMinutes in 16..29 -> {
                            // Short gap - just add free time
                            val freeTime = createFreeTime(
                                currentStop.endTime,
                                gapMinutes,
                                neighborhoodName
                            )
                            filledItinerary.add(freeTime)
                            Log.d("ItineraryGenerator", "Added short free time: $gapMinutes min")
                        }
                    }
                }
            }
        }
        
        return filledItinerary
    }
    
    /**
     * Calculate gap in minutes between two times.
     */
    private fun calculateGapMinutes(endTime: String, startTime: String): Int {
        return try {
            val endCal = Calendar.getInstance()
            val startCal = Calendar.getInstance()
            endCal.time = timeFormat.parse(endTime) ?: return 0
            startCal.time = timeFormat.parse(startTime) ?: return 0
            
            val diffMillis = startCal.timeInMillis - endCal.timeInMillis
            (diffMillis / (1000 * 60)).toInt()
        } catch (e: Exception) {
            Log.e("ItineraryGenerator", "Error calculating gap", e)
            0
        }
    }
    
    /**
     * Create a quick stop (coffee, photo spot, etc).
     */
    private fun createQuickStop(
        waypoint: Waypoint,
        afterTime: String,
        durationMinutes: Int,
        distanceKm: Double,
        quickStopType: QuickStopType
    ): ItineraryStop {
        val calendar = Calendar.getInstance()
        calendar.time = timeFormat.parse(afterTime) ?: calendar.time
        calendar.add(Calendar.MINUTE, 5) // Small buffer
        
        val startTime = timeFormat.format(calendar.time)
        calendar.add(Calendar.MINUTE, durationMinutes)
        val endTime = timeFormat.format(calendar.time)
        
        return ItineraryStop(
            place = null,
            startTime = startTime,
            endTime = endTime,
            durationMinutes = durationMinutes,
            distanceFromPreviousKm = distanceKm,
            transitEstimate = TransitHelper.estimateTransit(distanceKm),
            stopType = StopType.QUICK_STOP,
            waypoint = waypoint,
            quickStopType = quickStopType
        )
    }
    
    /**
     * Create a waypoint for scenic routes.
     */
    private fun createWaypointStop(
        waypoint: Waypoint,
        afterTime: String,
        routeDescription: String
    ): ItineraryStop {
        val calendar = Calendar.getInstance()
        calendar.time = timeFormat.parse(afterTime) ?: calendar.time
        
        val startTime = timeFormat.format(calendar.time)
        calendar.add(Calendar.MINUTE, 5) // Brief pass-by
        val endTime = timeFormat.format(calendar.time)
        
        return ItineraryStop(
            place = null,
            startTime = startTime,
            endTime = endTime,
            durationMinutes = 5,
            stopType = StopType.WAYPOINT,
            waypoint = waypoint,
            neighborhoodName = routeDescription
        )
    }
    
    /**
     * Create a free time / exploration period.
     */
    private fun createFreeTime(
        afterTime: String,
        durationMinutes: Int,
        neighborhoodName: String
    ): ItineraryStop {
        val calendar = Calendar.getInstance()
        calendar.time = timeFormat.parse(afterTime) ?: calendar.time
        
        val startTime = timeFormat.format(calendar.time)
        calendar.add(Calendar.MINUTE, durationMinutes)
        val endTime = timeFormat.format(calendar.time)
        
        return ItineraryStop(
            place = null,
            startTime = startTime,
            endTime = endTime,
            durationMinutes = durationMinutes,
            stopType = StopType.FREE_TIME,
            neighborhoodName = neighborhoodName
        )
    }
}

