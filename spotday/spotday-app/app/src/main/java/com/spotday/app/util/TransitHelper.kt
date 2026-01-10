package com.spotday.app.util

import com.spotday.app.model.TransitEstimate

/**
 * Helper for estimating transit times between locations based on distance.
 * Uses average speeds for different transport modes in an urban environment like SF.
 */
object TransitHelper {
    // Average speeds in km/h for SF
    private const val WALKING_SPEED = 5.0      // ~3 mph walking
    private const val TRANSIT_SPEED = 15.0     // Includes wait time, transfers
    private const val DRIVING_SPEED = 25.0     // City traffic, parking

    /**
     * Estimate transit times for all modes based on straight-line distance.
     * Adds a 20% buffer to account for non-straight routes.
     */
    fun estimateTransit(distanceKm: Double): TransitEstimate {
        // Add 20% for actual route distance (roads aren't straight)
        val adjustedDistance = distanceKm * 1.2
        
        return TransitEstimate(
            walkingMinutes = ((adjustedDistance / WALKING_SPEED) * 60).toInt().coerceAtLeast(1),
            transitMinutes = ((adjustedDistance / TRANSIT_SPEED) * 60).toInt().coerceAtLeast(1),
            drivingMinutes = ((adjustedDistance / DRIVING_SPEED) * 60).toInt().coerceAtLeast(1),
            distanceKm = distanceKm
        )
    }
    
    /**
     * Get the fastest transit option description
     */
    fun getFastestMode(estimate: TransitEstimate): String {
        return when {
            estimate.drivingMinutes <= estimate.transitMinutes && 
            estimate.drivingMinutes <= estimate.walkingMinutes -> "drive"
            estimate.transitMinutes <= estimate.walkingMinutes -> "transit"
            else -> "walk"
        }
    }
    
    /**
     * Format distance for display
     */
    fun formatDistance(distanceKm: Double): String {
        return if (distanceKm < 1.0) {
            "${(distanceKm * 1000).toInt()}m"
        } else {
            "${"%.1f".format(distanceKm)}km"
        }
    }
}
