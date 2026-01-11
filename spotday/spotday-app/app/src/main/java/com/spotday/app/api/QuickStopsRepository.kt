package com.spotday.app.api

import com.spotday.app.model.Waypoint
import kotlin.math.sqrt

/**
 * Repository of quick stops (15-30 min) to fill gaps in itineraries.
 * Includes coffee shops, photo spots, and notable viewpoints.
 */
class QuickStopsRepository {
    
    enum class QuickStopType {
        COFFEE,
        PHOTO_SPOT,
        VIEWPOINT,
        STREET_ART
    }
    
    data class QuickStop(
        val waypoint: Waypoint,
        val type: QuickStopType,
        val durationMinutes: Int = 20
    )
    
    private val quickStops = listOf(
        // Coffee Shops - North Beach
        QuickStop(
            Waypoint("Caffe Trieste", 37.7978, -122.4078, "Historic Beat Generation coffeehouse"),
            QuickStopType.COFFEE, 20
        ),
        QuickStop(
            Waypoint("Reveille Coffee", 37.7985, -122.4070, "Specialty coffee with outdoor seating"),
            QuickStopType.COFFEE, 15
        ),
        
        // Coffee Shops - Mission
        QuickStop(
            Waypoint("Four Barrel Coffee", 37.7572, -122.4213, "Iconic Mission roaster"),
            QuickStopType.COFFEE, 20
        ),
        QuickStop(
            Waypoint("Ritual Coffee", 37.7563, -122.4218, "Pioneer of SF third-wave coffee"),
            QuickStopType.COFFEE, 15
        ),
        QuickStop(
            Waypoint("Sightglass Coffee", 37.7580, -122.4115, "Industrial-chic coffee bar"),
            QuickStopType.COFFEE, 20
        ),
        
        // Coffee Shops - Castro/Hayes
        QuickStop(
            Waypoint("Blue Bottle Hayes", 37.7761, -122.4239, "Famous pour-over coffee"),
            QuickStopType.COFFEE, 15
        ),
        QuickStop(
            Waypoint("Ritual Noe Valley", 37.7508, -122.4314, "Cozy neighborhood spot"),
            QuickStopType.COFFEE, 15
        ),
        
        // Coffee Shops - SOMA/FiDi
        QuickStop(
            Waypoint("Blue Bottle Ferry Building", 37.7956, -122.3936, "Coffee with bay views"),
            QuickStopType.COFFEE, 15
        ),
        QuickStop(
            Waypoint("Equator Coffee", 37.7872, -122.4008, "Sustainably sourced beans"),
            QuickStopType.COFFEE, 15
        ),
        
        // Coffee Shops - Marina/Cow Hollow
        QuickStop(
            Waypoint("The Warming Hut", 37.8057, -122.4735, "Coffee with Golden Gate views"),
            QuickStopType.COFFEE, 20
        ),
        QuickStop(
            Waypoint("Jane on Fillmore", 37.7898, -122.4354, "Pastries and coffee"),
            QuickStopType.COFFEE, 20
        ),
        
        // Photo Spots
        QuickStop(
            Waypoint("Painted Ladies View", 37.7762, -122.4328, "Classic SF postcard shot"),
            QuickStopType.PHOTO_SPOT, 15
        ),
        QuickStop(
            Waypoint("Palace of Fine Arts", 37.8020, -122.4483, "Roman-style rotunda"),
            QuickStopType.PHOTO_SPOT, 20
        ),
        QuickStop(
            Waypoint("Lombard Street Top", 37.8024, -122.4186, "Crooked street from above"),
            QuickStopType.PHOTO_SPOT, 15
        ),
        QuickStop(
            Waypoint("Ferry Building Clock Tower", 37.7956, -122.3936, "Iconic waterfront landmark"),
            QuickStopType.PHOTO_SPOT, 10
        ),
        QuickStop(
            Waypoint("Transamerica Pyramid", 37.7952, -122.4028, "SF's distinctive skyline"),
            QuickStopType.PHOTO_SPOT, 10
        ),
        QuickStop(
            Waypoint("Dragon Gate", 37.7905, -122.4058, "Chinatown entrance"),
            QuickStopType.PHOTO_SPOT, 10
        ),
        QuickStop(
            Waypoint("Castro Theatre", 37.7621, -122.4349, "Historic movie palace"),
            QuickStopType.PHOTO_SPOT, 10
        ),
        
        // Viewpoints
        QuickStop(
            Waypoint("Coit Tower Viewpoint", 37.8024, -122.4058, "360° panoramic city views"),
            QuickStopType.VIEWPOINT, 25
        ),
        QuickStop(
            Waypoint("Tank Hill", 37.7596, -122.4473, "Hidden sunset viewpoint"),
            QuickStopType.VIEWPOINT, 20
        ),
        QuickStop(
            Waypoint("Corona Heights", 37.7653, -122.4395, "Rocky outcrop city views"),
            QuickStopType.VIEWPOINT, 20
        ),
        QuickStop(
            Waypoint("Bernal Hill", 37.7417, -122.4152, "Neighborhood panorama"),
            QuickStopType.VIEWPOINT, 25
        ),
        QuickStop(
            Waypoint("Pier 7", 37.7985, -122.3963, "Bay Bridge and skyline"),
            QuickStopType.VIEWPOINT, 15
        ),
        QuickStop(
            Waypoint("Fort Point Overlook", 37.8108, -122.4764, "Under the Golden Gate"),
            QuickStopType.VIEWPOINT, 20
        ),
        QuickStop(
            Waypoint("Baker Beach", 37.7930, -122.4836, "Golden Gate from beach level"),
            QuickStopType.VIEWPOINT, 20
        ),
        
        // Street Art
        QuickStop(
            Waypoint("Clarion Alley", 37.7630, -122.4220, "Famous mural alley in Mission"),
            QuickStopType.STREET_ART, 20
        ),
        QuickStop(
            Waypoint("Balmy Alley", 37.7508, -122.4123, "Political murals since 1970s"),
            QuickStopType.STREET_ART, 20
        ),
        QuickStop(
            Waypoint("Women's Building Murals", 37.7601, -122.4215, "MaestraPeace mural"),
            QuickStopType.STREET_ART, 15
        ),
        QuickStop(
            Waypoint("Lilac Alley", 37.7520, -122.4175, "Hidden mural corridor"),
            QuickStopType.STREET_ART, 15
        ),
        QuickStop(
            Waypoint("Haight Street Murals", 37.7697, -122.4478, "Psychedelic Haight artwork"),
            QuickStopType.STREET_ART, 15
        ),
        QuickStop(
            Waypoint("Chinatown Murals", 37.7947, -122.4070, "Cultural heritage murals"),
            QuickStopType.STREET_ART, 15
        )
    )
    
    /**
     * Find nearby quick stops within a radius.
     * @param lat Center latitude
     * @param lng Center longitude
     * @param radiusKm Search radius in kilometers
     * @param type Optional filter by stop type
     */
    fun findNearbyStops(
        lat: Double,
        lng: Double,
        radiusKm: Double = 0.5,
        type: QuickStopType? = null
    ): List<QuickStop> {
        val radiusDegrees = radiusKm / 111.0 // Approximate km to degrees
        
        return quickStops
            .filter { stop ->
                val dLat = stop.waypoint.lat - lat
                val dLng = stop.waypoint.lng - lng
                sqrt(dLat * dLat + dLng * dLng) <= radiusDegrees
            }
            .filter { type == null || it.type == type }
            .sortedBy { stop ->
                val dLat = stop.waypoint.lat - lat
                val dLng = stop.waypoint.lng - lng
                sqrt(dLat * dLat + dLng * dLng)
            }
    }
    
    /**
     * Find a quick stop that fits a time gap.
     * @param lat Current location latitude
     * @param lng Current location longitude
     * @param availableMinutes Time available for the stop
     * @param excludeIds Waypoint names to exclude (already visited)
     */
    fun findStopForGap(
        lat: Double,
        lng: Double,
        availableMinutes: Int,
        excludeIds: Set<String> = emptySet()
    ): QuickStop? {
        return findNearbyStops(lat, lng, 0.8)
            .filter { it.waypoint.name !in excludeIds }
            .filter { it.durationMinutes <= availableMinutes }
            .firstOrNull()
    }
    
    /**
     * Get neighborhood name for a location.
     */
    fun getNeighborhoodName(lat: Double, lng: Double): String {
        // Simplified neighborhood lookup
        return when {
            lat > 37.80 && lng < -122.43 -> "Marina"
            lat > 37.80 && lng > -122.42 -> "North Beach"
            lat > 37.79 && lng > -122.40 -> "Chinatown"
            lat > 37.78 && lng > -122.39 -> "Financial District"
            lat > 37.77 && lng > -122.40 && lng < -122.42 -> "SOMA"
            lat > 37.76 && lng < -122.44 -> "Hayes Valley"
            lat > 37.75 && lng < -122.42 -> "Mission"
            lat > 37.76 && lng < -122.43 -> "Castro"
            lat > 37.76 && lng < -122.47 -> "Haight"
            lat > 37.79 && lng < -122.46 -> "Richmond"
            lat > 37.76 && lng < -122.50 -> "Sunset"
            else -> "San Francisco"
        }
    }
}
