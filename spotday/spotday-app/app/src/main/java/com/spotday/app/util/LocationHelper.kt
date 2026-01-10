package com.spotday.app.util

import android.content.Context

/**
 * Helper for managing user location, with mocked locations for testing
 */
object LocationHelper {
    // Famous SF locations for testing spontaneous mode
    private val SF_TEST_LOCATIONS = listOf(
        Pair(37.7749, -122.4194) to "Union Square",
        Pair(37.8080, -122.4177) to "Fisherman's Wharf",
        Pair(37.7599, -122.4148) to "Mission District",
        Pair(37.7955, -122.3937) to "North Beach",
        Pair(37.7694, -122.4862) to "Golden Gate Park",
        Pair(37.7897, -122.3972) to "Chinatown",
        Pair(37.7615, -122.4356) to "Castro",
        Pair(37.7694, -122.4534) to "Haight-Ashbury"
    )
    
    /**
     * Get a random SF location for testing
     * Returns (latitude, longitude)
     */
    fun getRandomSFLocation(): Pair<Double, Double> {
        return SF_TEST_LOCATIONS.random().first
    }
    
    /**
     * Get a random SF location with its name
     * Returns Pair(Pair(lat, lng), name)
     */
    fun getRandomSFLocationWithName(): Pair<Pair<Double, Double>, String> {
        val (location, name) = SF_TEST_LOCATIONS.random()
        return Pair(location, name)
    }
    
    /**
     * Future: Get current GPS location
     * For now, returns mock location
     */
    fun getCurrentLocation(context: Context): Pair<Double, Double>? {
        // TODO: Request permissions and get real GPS
        // For now, return mock location
        return getRandomSFLocation()
    }
}
