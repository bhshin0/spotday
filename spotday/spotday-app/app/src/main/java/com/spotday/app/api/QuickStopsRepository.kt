package com.spotday.app.api

import android.util.Log
import com.spotday.app.model.Place
import com.spotday.app.model.QuickStopType
import com.spotday.app.model.Waypoint
import kotlin.math.sqrt

/**
 * Repository for quick stops (15-30 min) to fill gaps in itineraries.
 * 
 * Data sources:
 * - VIEWPOINT, PHOTO_SPOT, STREET_ART: Fetched from Supabase (LLM-generated per city)
 * - COFFEE: Filtered from cached_places at runtime (cafes from Google Places)
 */
class QuickStopsRepository {
    
    companion object {
        private const val TAG = "QuickStopsRepository"
        
        // Keywords to identify coffee/tea shops from place names
        private val COFFEE_KEYWORDS = listOf(
            "cafe", "café", "coffee", "espresso", "roaster", "roastery",
            "tea", "boba", "bubble", "matcha", "latte", "brew"
        )
    }
    
    data class QuickStop(
        val waypoint: Waypoint,
        val type: QuickStopType,
        val durationMinutes: Int = 20
    )
    
    // In-memory cache of quick stops per city
    private val cachedStops: MutableMap<String, List<QuickStop>> = mutableMapOf()
    private var loadedCityId: String? = null
    
    /**
     * Load quick stops from Supabase for a city.
     * Call this during prefetch to have data ready.
     */
    suspend fun loadQuickStops(cityId: String): List<QuickStop> {
        // Return cached if already loaded for this city
        if (loadedCityId == cityId && cachedStops.containsKey(cityId)) {
            return cachedStops[cityId] ?: emptyList()
        }
        
        Log.d(TAG, "Loading quick stops from Supabase for $cityId...")
        
        try {
            val remoteStops = SupabaseClient.getQuickStops(cityId)
            
            val stops = remoteStops.map { remote ->
                QuickStop(
                    waypoint = Waypoint(
                        name = remote.name,
                        lat = remote.lat,
                        lng = remote.lng,
                        description = remote.description
                    ),
                    type = when (remote.stopType) {
                        "VIEWPOINT" -> QuickStopType.VIEWPOINT
                        "PHOTO_SPOT" -> QuickStopType.PHOTO_SPOT
                        "STREET_ART" -> QuickStopType.STREET_ART
                        else -> QuickStopType.PHOTO_SPOT // Fallback
                    },
                    durationMinutes = remote.durationMinutes
                )
            }
            
            cachedStops[cityId] = stops
            loadedCityId = cityId
            
            Log.d(TAG, "Loaded ${stops.size} quick stops for $cityId")
            stops.groupBy { it.type }.forEach { (type, list) ->
                Log.d(TAG, "  $type: ${list.size}")
            }
            
            return stops
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load quick stops from Supabase", e)
            return emptyList()
        }
    }
    
    /**
     * Find nearby coffee stops from cached places.
     * Filters places by name to find cafes, coffee shops, tea houses, etc.
     * 
     * @param places All cached places for the city (from PlacesRepository)
     * @param lat Current location latitude
     * @param lng Current location longitude
     * @param radiusKm Search radius in kilometers
     * @param limit Maximum number of results
     */
    fun findNearbyCoffeeStops(
        places: List<Place>,
        lat: Double,
        lng: Double,
        radiusKm: Double = 0.8,
        limit: Int = 5
    ): List<QuickStop> {
        val radiusDegrees = radiusKm / 111.0 // Approximate km to degrees
        
        return places
            .filter { place ->
                // Check if name contains coffee-related keywords
                val nameLower = place.name.lowercase()
                COFFEE_KEYWORDS.any { keyword -> nameLower.contains(keyword) }
            }
            .filter { place ->
                // Check if within radius
                val dLat = place.lat - lat
                val dLng = place.lng - lng
                sqrt(dLat * dLat + dLng * dLng) <= radiusDegrees
            }
            .sortedByDescending { it.rating } // Best rated first
            .take(limit)
            .map { place ->
                QuickStop(
                    waypoint = Waypoint(
                        name = place.name,
                        lat = place.lat,
                        lng = place.lng,
                        description = null // Could add rating info here
                    ),
                    type = QuickStopType.COFFEE,
                    durationMinutes = 15
                )
            }
    }
    
    /**
     * Find nearby quick stops (viewpoints, photo spots, street art) within a radius.
     * Uses cached LLM-generated stops loaded from Supabase.
     * 
     * @param lat Center latitude
     * @param lng Center longitude
     * @param radiusKm Search radius in kilometers
     * @param type Optional filter by stop type
     */
    fun findNearbyStops(
        lat: Double,
        lng: Double,
        radiusKm: Double = 0.8,
        type: QuickStopType? = null
    ): List<QuickStop> {
        val cityStops = cachedStops[loadedCityId] ?: return emptyList()
        val radiusDegrees = radiusKm / 111.0
        
        return cityStops
            .filter { stop ->
                // Filter by type if specified (exclude COFFEE as it comes from places)
                type == null || stop.type == type
            }
            .filter { stop ->
                // Filter by distance
                val dLat = stop.waypoint.lat - lat
                val dLng = stop.waypoint.lng - lng
                sqrt(dLat * dLat + dLng * dLng) <= radiusDegrees
            }
            .sortedBy { stop ->
                // Sort by distance
                val dLat = stop.waypoint.lat - lat
                val dLng = stop.waypoint.lng - lng
                sqrt(dLat * dLat + dLng * dLng)
            }
    }
    
    /**
     * Find a quick stop that fits a time gap.
     * Tries LLM-generated stops first, then coffee stops.
     * 
     * @param lat Current location latitude
     * @param lng Current location longitude
     * @param availableMinutes Time available for the stop
     * @param excludeNames Waypoint names to exclude (already visited)
     * @param places Cached places for finding coffee stops
     */
    fun findStopForGap(
        lat: Double,
        lng: Double,
        availableMinutes: Int,
        excludeNames: Set<String> = emptySet(),
        places: List<Place> = emptyList()
    ): QuickStop? {
        // First try LLM-generated stops (viewpoints, murals, photo spots)
        val lllmStop = findNearbyStops(lat, lng, 0.8)
            .filter { it.waypoint.name !in excludeNames }
            .filter { it.durationMinutes <= availableMinutes }
            .firstOrNull()
        
        if (lllmStop != null) {
            return lllmStop
        }
        
        // Fall back to coffee stops from places
        if (places.isNotEmpty()) {
            val coffeeStop = findNearbyCoffeeStops(places, lat, lng, 0.8, 1)
                .filter { it.waypoint.name !in excludeNames }
                .filter { it.durationMinutes <= availableMinutes }
                .firstOrNull()
            
            if (coffeeStop != null) {
                return coffeeStop
            }
        }
        
        return null
    }
    
    /**
     * Clear cached data (for testing or city switch).
     */
    fun clearCache() {
        cachedStops.clear()
        loadedCityId = null
    }
}
