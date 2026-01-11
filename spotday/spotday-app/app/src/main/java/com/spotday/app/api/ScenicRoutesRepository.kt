package com.spotday.app.api

import com.spotday.app.model.ScenicRoute
import com.spotday.app.model.Waypoint
import kotlin.math.abs

/**
 * Repository of curated scenic walking routes between SF neighborhoods.
 * These turn transit time into part of the experience.
 */
class ScenicRoutesRepository {
    
    private val scenicRoutes = listOf(
        // North Beach <-> Chinatown via Jack Kerouac Alley
        ScenicRoute(
            id = "north_beach_chinatown",
            fromArea = "north_beach",
            toArea = "chinatown",
            waypoints = listOf(
                Waypoint("Jack Kerouac Alley", 37.7976, -122.4066, "Historic Beat Generation alley with literary quotes"),
                Waypoint("City Lights Bookstore", 37.7976, -122.4064, "Iconic Beat poetry bookstore")
            ),
            description = "Walk through Jack Kerouac Alley, past City Lights Bookstore",
            addedMinutes = 5
        ),
        
        // Ferry Building <-> Fisherman's Wharf via Embarcadero
        ScenicRoute(
            id = "ferry_fishermans",
            fromArea = "embarcadero",
            toArea = "fishermans_wharf",
            waypoints = listOf(
                Waypoint("Pier 7", 37.7985, -122.3963, "Scenic pier with bay views"),
                Waypoint("Exploratorium", 37.8017, -122.3975, "Pass by the science museum"),
                Waypoint("Pier 39 Sea Lions", 37.8087, -122.4098, "Watch the famous sea lions")
            ),
            description = "Stroll along the Embarcadero waterfront promenade",
            addedMinutes = 10
        ),
        
        // Dolores Park <-> Castro via Victorian homes
        ScenicRoute(
            id = "dolores_castro",
            fromArea = "mission",
            toArea = "castro",
            waypoints = listOf(
                Waypoint("Liberty Street Victorians", 37.7575, -122.4270, "Beautifully restored Victorian homes"),
                Waypoint("Mission Dolores", 37.7600, -122.4269, "Historic Spanish mission")
            ),
            description = "Walk past colorful Victorian homes and Mission Dolores",
            addedMinutes = 8
        ),
        
        // Crissy Field <-> Palace of Fine Arts
        ScenicRoute(
            id = "crissy_palace",
            fromArea = "presidio",
            toArea = "marina",
            waypoints = listOf(
                Waypoint("Crissy Field Beach", 37.8039, -122.4650, "Sandy beach with Golden Gate views"),
                Waypoint("Warming Hut", 37.8057, -122.4735, "Cozy cafe with bay views")
            ),
            description = "Beach walk with stunning Golden Gate Bridge views",
            addedMinutes = 12
        ),
        
        // Golden Gate Park <-> Haight via Panhandle
        ScenicRoute(
            id = "ggp_haight",
            fromArea = "golden_gate_park",
            toArea = "haight",
            waypoints = listOf(
                Waypoint("The Panhandle", 37.7720, -122.4380, "Tree-lined park corridor"),
                Waypoint("Buena Vista Park overlook", 37.7680, -122.4420, "Panoramic city views")
            ),
            description = "Walk through the Panhandle to Haight-Ashbury",
            addedMinutes = 7
        ),
        
        // Union Square <-> Chinatown via Dragon Gate
        ScenicRoute(
            id = "union_chinatown",
            fromArea = "union_square",
            toArea = "chinatown",
            waypoints = listOf(
                Waypoint("Dragon Gate", 37.7905, -122.4058, "Iconic entrance to Chinatown"),
                Waypoint("Grant Avenue", 37.7940, -122.4067, "Oldest street in SF, lanterns and shops")
            ),
            description = "Enter Chinatown through the iconic Dragon Gate",
            addedMinutes = 5
        ),
        
        // Russian Hill <-> North Beach via Lombard Street
        ScenicRoute(
            id = "russian_north_beach",
            fromArea = "russian_hill",
            toArea = "north_beach",
            waypoints = listOf(
                Waypoint("Lombard Street", 37.8021, -122.4187, "World-famous crooked street"),
                Waypoint("Coit Tower viewpoint", 37.8024, -122.4058, "360° city views")
            ),
            description = "Descend the famous Lombard Street switchbacks",
            addedMinutes = 15
        ),
        
        // SOMA <-> Mission via Valencia Street
        ScenicRoute(
            id = "soma_mission",
            fromArea = "soma",
            toArea = "mission",
            waypoints = listOf(
                Waypoint("Valencia Street murals", 37.7580, -122.4210, "Vibrant street art corridor"),
                Waypoint("Clarion Alley", 37.7630, -122.4220, "Famous mural alley")
            ),
            description = "Walk Valencia Street's murals and boutiques",
            addedMinutes = 8
        ),
        
        // Marina <-> Fort Mason
        ScenicRoute(
            id = "marina_fort_mason",
            fromArea = "marina",
            toArea = "fort_mason",
            waypoints = listOf(
                Waypoint("Marina Green", 37.8038, -122.4388, "Waterfront park with kite flyers"),
                Waypoint("Wave Organ", 37.8073, -122.4345, "Unique sound sculpture")
            ),
            description = "Stroll Marina Green to the Wave Organ sound sculpture",
            addedMinutes = 10
        ),
        
        // Nob Hill <-> Chinatown via Cable Car route
        ScenicRoute(
            id = "nob_chinatown",
            fromArea = "nob_hill",
            toArea = "chinatown",
            waypoints = listOf(
                Waypoint("Grace Cathedral", 37.7918, -122.4130, "Gothic cathedral with labyrinths"),
                Waypoint("Cable Car Museum", 37.7948, -122.4115, "See the cable car machinery")
            ),
            description = "Walk the cable car route past Grace Cathedral",
            addedMinutes = 6
        ),
        
        // Hayes Valley <-> Alamo Square
        ScenicRoute(
            id = "hayes_alamo",
            fromArea = "hayes_valley",
            toArea = "western_addition",
            waypoints = listOf(
                Waypoint("Patricia's Green", 37.7762, -122.4240, "Urban park with rotating art"),
                Waypoint("Painted Ladies", 37.7762, -122.4328, "Iconic Victorian row houses")
            ),
            description = "Walk to the famous Painted Ladies Victorian homes",
            addedMinutes = 8
        ),
        
        // Fisherman's Wharf <-> Ghirardelli Square
        ScenicRoute(
            id = "wharf_ghirardelli",
            fromArea = "fishermans_wharf",
            toArea = "aquatic_park",
            waypoints = listOf(
                Waypoint("Hyde Street Pier", 37.8069, -122.4218, "Historic ships museum"),
                Waypoint("Aquatic Park Beach", 37.8062, -122.4232, "Urban beach cove")
            ),
            description = "Walk past historic ships to Ghirardelli Square",
            addedMinutes = 5
        ),
        
        // Twin Peaks <-> Castro
        ScenicRoute(
            id = "twin_peaks_castro",
            fromArea = "twin_peaks",
            toArea = "castro",
            waypoints = listOf(
                Waypoint("Tank Hill", 37.7596, -122.4473, "Hidden viewpoint"),
                Waypoint("Corona Heights", 37.7653, -122.4395, "Rocky outcrop with views")
            ),
            description = "Descend through hidden viewpoints to Castro",
            addedMinutes = 15
        )
    )
    
    /**
     * Find a scenic route between two locations.
     * Returns null if no scenic route exists for this pair.
     */
    fun findScenicRoute(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): ScenicRoute? {
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
     * Determine which SF neighborhood a coordinate is in.
     */
    private fun getNeighborhood(lat: Double, lng: Double): String? {
        // Simplified neighborhood boundaries (approximate centers with radius)
        val neighborhoods = mapOf(
            "north_beach" to Pair(37.8000, -122.4080),
            "chinatown" to Pair(37.7940, -122.4070),
            "embarcadero" to Pair(37.7950, -122.3930),
            "fishermans_wharf" to Pair(37.8080, -122.4170),
            "mission" to Pair(37.7590, -122.4180),
            "castro" to Pair(37.7620, -122.4350),
            "presidio" to Pair(37.8000, -122.4650),
            "marina" to Pair(37.8030, -122.4360),
            "golden_gate_park" to Pair(37.7700, -122.4780),
            "haight" to Pair(37.7700, -122.4470),
            "union_square" to Pair(37.7880, -122.4070),
            "russian_hill" to Pair(37.8010, -122.4180),
            "soma" to Pair(37.7780, -122.4050),
            "nob_hill" to Pair(37.7920, -122.4150),
            "hayes_valley" to Pair(37.7760, -122.4250),
            "western_addition" to Pair(37.7800, -122.4350),
            "aquatic_park" to Pair(37.8060, -122.4230),
            "twin_peaks" to Pair(37.7544, -122.4477)
        )
        
        // Find closest neighborhood (within 0.015 degrees, ~1.5km)
        val threshold = 0.015
        return neighborhoods.entries
            .filter { (_, center) ->
                abs(lat - center.first) < threshold && abs(lng - center.second) < threshold
            }
            .minByOrNull { (_, center) ->
                abs(lat - center.first) + abs(lng - center.second)
            }?.key
    }
    
    /**
     * Get all scenic routes (for debugging/display).
     */
    fun getAllRoutes(): List<ScenicRoute> = scenicRoutes
}
