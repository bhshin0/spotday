package com.spotday.app.api

import com.spotday.app.model.ScenicRoute
import kotlin.math.abs

/**
 * Repository of scenic walking routes between neighborhoods.
 * These turn transit time into part of the experience.
 * 
 * NOTE: Mock data has been shelved. This will be populated from Supabase
 * (LLM-generated per city) in a future update.
 */
class ScenicRoutesRepository {
    
    // Scenic routes - currently empty, will be populated from Supabase later
    private val scenicRoutes: List<ScenicRoute> = emptyList()
    
    /**
     * Find a scenic route between two locations.
     * Returns null if no scenic route exists for this pair.
     * 
     * TODO: Implement Supabase fetch for scenic routes per city
     */
    fun findScenicRoute(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): ScenicRoute? {
        if (scenicRoutes.isEmpty()) return null
        
        val fromArea = getNeighborhood(fromLat, fromLng)
        val toArea = getNeighborhood(toLat, toLng)
        
        if (fromArea == null || toArea == null) return null
        
        // Look for direct route or reverse route
        return scenicRoutes.find { 
            (it.fromArea == fromArea && it.toArea == toArea) ||
            (it.fromArea == toArea && it.toArea == fromArea)
        }
    }
    
    /**
     * Determine which neighborhood a coordinate is in.
     * This is a placeholder - should use actual neighborhood boundaries from Supabase.
     */
    private fun getNeighborhood(lat: Double, lng: Double): String? {
        // TODO: Use actual neighborhood data from NeighborhoodsRepository
        return null
    }
    
    /**
     * Get all scenic routes (for debugging/display).
     */
    fun getAllRoutes(): List<ScenicRoute> = scenicRoutes
}
