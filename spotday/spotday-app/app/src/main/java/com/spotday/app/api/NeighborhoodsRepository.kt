package com.spotday.app.api

import com.spotday.app.model.CityProfile
import com.spotday.app.model.DataSource
import com.spotday.app.model.Neighborhood
import com.spotday.app.model.NeighborhoodTier

/**
 * Repository of neighborhoods across multiple cities.
 * Supports tiered neighborhoods (Essential/Classic/Local) and multiple data sources.
 * 
 * Scaling strategy:
 * - Tier 1 cities (SF, NYC, LA): Hand-curated, 8-15 neighborhoods
 * - Tier 2 cities (Austin, Portland): LLM-seeded, 5-10 neighborhoods  
 * - Tier 3 cities: Algorithm-derived from venue clustering, 3-6 neighborhoods
 */
class NeighborhoodsRepository {
    
    // City profiles define expected neighborhood counts based on city characteristics
    private val cityProfiles = listOf(
        CityProfile(
            id = "san_francisco",
            name = "San Francisco",
            country = "USA",
            size = "medium",
            density = "very_dense",
            centerLat = 37.7749,
            centerLng = -122.4194,
            estimatedHappeningAreas = 12
        ),
        CityProfile(
            id = "austin",
            name = "Austin",
            country = "USA",
            size = "large",
            density = "spread_out",
            centerLat = 30.2672,
            centerLng = -97.7431,
            estimatedHappeningAreas = 6
        ),
        CityProfile(
            id = "new_york",
            name = "New York City",
            country = "USA",
            size = "large",
            density = "very_dense",
            centerLat = 40.7128,
            centerLng = -74.0060,
            estimatedHappeningAreas = 20
        )
    )
    
    // ============================================
    // SAN FRANCISCO - Hand-curated (CURATED source)
    // ============================================
    private val sanFranciscoNeighborhoods = listOf(
        // TIER 1: ESSENTIAL - Must-see for first-timers, highest density
        Neighborhood(
            id = "mission",
            name = "Mission District",
            cityId = "san_francisco",
            tier = NeighborhoodTier.ESSENTIAL,
            centerLat = 37.7599,
            centerLng = -122.4148,
            radiusMeters = 1000,
            vibes = listOf("foodie", "nightlife", "artsy", "latin"),
            dataSource = DataSource.CURATED,
            adjacentNeighborhoods = listOf("castro", "bernal_heights", "potrero_hill", "soma"),
            description = "SF's most vibrant neighborhood - tacos, murals, dive bars, and the best nightlife"
        ),
        Neighborhood(
            id = "castro",
            name = "Castro",
            cityId = "san_francisco",
            tier = NeighborhoodTier.ESSENTIAL,
            centerLat = 37.7609,
            centerLng = -122.4350,
            radiusMeters = 600,
            vibes = listOf("lgbtq", "nightlife", "historic", "brunch"),
            dataSource = DataSource.CURATED,
            adjacentNeighborhoods = listOf("mission", "hayes_valley", "noe_valley", "corona_heights"),
            description = "Historic LGBTQ+ hub with great bars, brunch spots, and iconic streetscape"
        ),
        Neighborhood(
            id = "north_beach",
            name = "North Beach",
            cityId = "san_francisco",
            tier = NeighborhoodTier.ESSENTIAL,
            centerLat = 37.8005,
            centerLng = -122.4091,
            radiusMeters = 700,
            vibes = listOf("italian", "historic", "nightlife", "literary"),
            dataSource = DataSource.CURATED,
            adjacentNeighborhoods = listOf("chinatown", "fishermans_wharf", "russian_hill", "telegraph_hill"),
            description = "Little Italy meets Beat Generation - classic restaurants, City Lights bookstore"
        ),
        Neighborhood(
            id = "hayes_valley",
            name = "Hayes Valley",
            cityId = "san_francisco",
            tier = NeighborhoodTier.ESSENTIAL,
            centerLat = 37.7759,
            centerLng = -122.4245,
            radiusMeters = 500,
            vibes = listOf("trendy", "boutiques", "foodie", "upscale"),
            dataSource = DataSource.CURATED,
            adjacentNeighborhoods = listOf("castro", "lower_haight", "civic_center", "soma"),
            description = "Trendy boutiques, excellent restaurants, perfect for afternoon strolling"
        ),
        
        // TIER 2: CLASSIC - Worth visiting, iconic but less dense
        Neighborhood(
            id = "chinatown",
            name = "Chinatown",
            cityId = "san_francisco",
            tier = NeighborhoodTier.CLASSIC,
            centerLat = 37.7941,
            centerLng = -122.4078,
            radiusMeters = 500,
            vibes = listOf("chinese", "historic", "dim_sum", "cultural"),
            dataSource = DataSource.CURATED,
            adjacentNeighborhoods = listOf("north_beach", "financial_district", "union_square", "nob_hill"),
            description = "Oldest Chinatown in North America - dim sum, tea shops, historic temples"
        ),
        Neighborhood(
            id = "marina",
            name = "Marina",
            cityId = "san_francisco",
            tier = NeighborhoodTier.CLASSIC,
            centerLat = 37.8025,
            centerLng = -122.4382,
            radiusMeters = 800,
            vibes = listOf("brunch", "upscale", "waterfront", "fitness"),
            dataSource = DataSource.CURATED,
            adjacentNeighborhoods = listOf("cow_hollow", "pacific_heights", "presidio"),
            description = "Upscale brunch spots, waterfront views, young professional scene"
        ),
        Neighborhood(
            id = "soma",
            name = "SoMa",
            cityId = "san_francisco",
            tier = NeighborhoodTier.CLASSIC,
            centerLat = 37.7785,
            centerLng = -122.4056,
            radiusMeters = 1200,
            vibes = listOf("museums", "tech", "clubs", "industrial"),
            dataSource = DataSource.CURATED,
            adjacentNeighborhoods = listOf("mission", "hayes_valley", "financial_district", "south_beach"),
            description = "SFMOMA, tech offices, nightclubs - sprawling and varied"
        ),
        Neighborhood(
            id = "haight",
            name = "Haight-Ashbury",
            cityId = "san_francisco",
            tier = NeighborhoodTier.CLASSIC,
            centerLat = 37.7692,
            centerLng = -122.4481,
            radiusMeters = 600,
            vibes = listOf("vintage", "counterculture", "hippie", "parks"),
            dataSource = DataSource.CURATED,
            adjacentNeighborhoods = listOf("lower_haight", "cole_valley", "panhandle"),
            description = "Summer of Love history, vintage shops, near Golden Gate Park"
        ),
        Neighborhood(
            id = "embarcadero",
            name = "Embarcadero",
            cityId = "san_francisco",
            tier = NeighborhoodTier.CLASSIC,
            centerLat = 37.7936,
            centerLng = -122.3930,
            radiusMeters = 1000,
            vibes = listOf("waterfront", "ferry_building", "scenic", "foodie"),
            dataSource = DataSource.CURATED,
            adjacentNeighborhoods = listOf("financial_district", "soma", "south_beach"),
            description = "Ferry Building marketplace, waterfront promenade, bay views"
        ),
        
        // TIER 3: LOCAL FAVORITES - Skip unless exploring or local recommendation
        Neighborhood(
            id = "potrero_hill",
            name = "Potrero Hill",
            cityId = "san_francisco",
            tier = NeighborhoodTier.LOCAL,
            centerLat = 37.7601,
            centerLng = -122.4018,
            radiusMeters = 800,
            vibes = listOf("hidden_gem", "views", "brunch", "residential"),
            dataSource = DataSource.CURATED,
            adjacentNeighborhoods = listOf("mission", "dogpatch", "soma"),
            description = "Sunny hilltop with great restaurants and city views"
        ),
        Neighborhood(
            id = "dogpatch",
            name = "Dogpatch",
            cityId = "san_francisco",
            tier = NeighborhoodTier.LOCAL,
            centerLat = 37.7580,
            centerLng = -122.3870,
            radiusMeters = 600,
            vibes = listOf("breweries", "industrial", "artsy", "emerging"),
            dataSource = DataSource.CURATED,
            adjacentNeighborhoods = listOf("potrero_hill", "bayview"),
            description = "Industrial-chic breweries and restaurants, up-and-coming"
        ),
        Neighborhood(
            id = "bernal_heights",
            name = "Bernal Heights",
            cityId = "san_francisco",
            tier = NeighborhoodTier.LOCAL,
            centerLat = 37.7396,
            centerLng = -122.4156,
            radiusMeters = 700,
            vibes = listOf("family", "local", "views", "quiet"),
            dataSource = DataSource.CURATED,
            adjacentNeighborhoods = listOf("mission", "glen_park"),
            description = "Quiet neighborhood with hilltop park and local favorites"
        )
    )
    
    // ============================================
    // AUSTIN - LLM-seeded (example of scaled city)
    // ============================================
    private val austinNeighborhoods = listOf(
        // TIER 1: ESSENTIAL - Where the action is
        Neighborhood(
            id = "east_austin",
            name = "East Austin",
            cityId = "austin",
            tier = NeighborhoodTier.ESSENTIAL,
            centerLat = 30.2621,
            centerLng = -97.7206,
            radiusMeters = 1500,
            vibes = listOf("hipster", "tacos", "bars", "artsy", "live_music"),
            dataSource = DataSource.LLM,
            adjacentNeighborhoods = listOf("downtown", "east_cesar_chavez"),
            description = "Austin's coolest neighborhood - food trucks, dive bars, galleries"
        ),
        Neighborhood(
            id = "south_congress",
            name = "South Congress (SoCo)",
            cityId = "austin",
            tier = NeighborhoodTier.ESSENTIAL,
            centerLat = 30.2489,
            centerLng = -97.7498,
            radiusMeters = 1000,
            vibes = listOf("shopping", "iconic", "food", "music_venues"),
            dataSource = DataSource.LLM,
            adjacentNeighborhoods = listOf("travis_heights", "bouldin"),
            description = "Keep Austin Weird central - boutiques, restaurants, iconic views of Capitol"
        ),
        Neighborhood(
            id = "downtown",
            name = "Downtown / 6th Street",
            cityId = "austin",
            tier = NeighborhoodTier.ESSENTIAL,
            centerLat = 30.2672,
            centerLng = -97.7431,
            radiusMeters = 1200,
            vibes = listOf("nightlife", "live_music", "bars", "touristy"),
            dataSource = DataSource.LLM,
            adjacentNeighborhoods = listOf("east_austin", "rainey_street", "warehouse_district"),
            description = "Live music capital - 6th Street bars, Rainey Street, Congress Ave"
        ),
        
        // TIER 2: CLASSIC
        Neighborhood(
            id = "rainey_street",
            name = "Rainey Street",
            cityId = "austin",
            tier = NeighborhoodTier.CLASSIC,
            centerLat = 30.2590,
            centerLng = -97.7390,
            radiusMeters = 400,
            vibes = listOf("bars", "bungalows", "food_trucks", "young_professional"),
            dataSource = DataSource.LLM,
            adjacentNeighborhoods = listOf("downtown"),
            description = "Historic bungalows converted to bars, food truck heaven"
        ),
        Neighborhood(
            id = "south_lamar",
            name = "South Lamar",
            cityId = "austin",
            tier = NeighborhoodTier.CLASSIC,
            centerLat = 30.2411,
            centerLng = -97.7889,
            radiusMeters = 1000,
            vibes = listOf("foodie", "local", "alamo_drafthouse"),
            dataSource = DataSource.LLM,
            adjacentNeighborhoods = listOf("zilker", "barton_hills"),
            description = "Excellent restaurants, Alamo Drafthouse, local Austin vibe"
        ),
        
        // TIER 3: LOCAL
        Neighborhood(
            id = "mueller",
            name = "Mueller",
            cityId = "austin",
            tier = NeighborhoodTier.LOCAL,
            centerLat = 30.2983,
            centerLng = -97.7050,
            radiusMeters = 800,
            vibes = listOf("family", "farmers_market", "planned"),
            dataSource = DataSource.LLM,
            adjacentNeighborhoods = listOf("east_austin"),
            description = "New urbanist development with farmers market and family-friendly spots"
        )
    )
    
    // Combined neighborhoods from all cities
    private val _allNeighborhoods: List<Neighborhood> by lazy {
        sanFranciscoNeighborhoods + austinNeighborhoods
    }
    
    // ============================================
    // PUBLIC API
    // ============================================
    
    /**
     * Get all city profiles
     */
    fun getCityProfiles(): List<CityProfile> = cityProfiles
    
    /**
     * Get city profile by ID
     */
    fun getCityProfile(cityId: String): CityProfile? = 
        cityProfiles.find { it.id == cityId }
    
    /**
     * Get all neighborhoods for a specific city
     */
    fun getNeighborhoodsForCity(cityId: String): List<Neighborhood> =
        _allNeighborhoods.filter { it.cityId == cityId }
    
    /**
     * Get neighborhoods by tier for a city
     */
    fun getNeighborhoodsByTier(cityId: String, tier: NeighborhoodTier): List<Neighborhood> =
        getNeighborhoodsForCity(cityId).filter { it.tier == tier }
    
    /**
     * Get essential neighborhoods (Tier 1) - best for first-time visitors
     */
    fun getEssentialNeighborhoods(cityId: String): List<Neighborhood> =
        getNeighborhoodsByTier(cityId, NeighborhoodTier.ESSENTIAL)
    
    /**
     * Get all defined neighborhoods across all cities
     */
    fun getAllNeighborhoods(): List<Neighborhood> = _allNeighborhoods
    
    /**
     * Get a specific neighborhood by ID (searches all cities)
     */
    fun getNeighborhood(id: String): Neighborhood? = 
        _allNeighborhoods.find { it.id == id }
    
    /**
     * Get a specific neighborhood by ID within a city
     */
    fun getNeighborhood(cityId: String, neighborhoodId: String): Neighborhood? = 
        getNeighborhoodsForCity(cityId).find { it.id == neighborhoodId }
    
    /**
     * Get neighborhoods adjacent to the given neighborhood
     */
    fun getAdjacentNeighborhoods(neighborhoodId: String): List<Neighborhood> {
        val neighborhood = getNeighborhood(neighborhoodId) ?: return emptyList()
        return neighborhood.adjacentNeighborhoods.mapNotNull { getNeighborhood(it) }
    }
    
    /**
     * Check if two neighborhoods are adjacent (walkable to each other)
     */
    fun areAdjacent(neighborhoodId1: String?, neighborhoodId2: String?): Boolean {
        if (neighborhoodId1 == null || neighborhoodId2 == null) return false
        val neighborhood = getNeighborhood(neighborhoodId1) ?: return false
        return neighborhood.adjacentNeighborhoods.contains(neighborhoodId2)
    }
    
    /**
     * Find the nearest neighborhood to a given lat/lng within a city
     */
    fun findNearestNeighborhood(cityId: String, lat: Double, lng: Double): Neighborhood? {
        return getNeighborhoodsForCity(cityId).minByOrNull { neighborhood ->
            val latDiff = neighborhood.centerLat - lat
            val lngDiff = neighborhood.centerLng - lng
            latDiff * latDiff + lngDiff * lngDiff
        }
    }
    
    /**
     * Find the nearest neighborhood to a given lat/lng (auto-detect city)
     */
    fun findNearestNeighborhood(lat: Double, lng: Double): Neighborhood? {
        return _allNeighborhoods.minByOrNull { neighborhood ->
            val latDiff = neighborhood.centerLat - lat
            val lngDiff = neighborhood.centerLng - lng
            latDiff * latDiff + lngDiff * lngDiff
        }
    }
    
    /**
     * Detect which city a coordinate is in
     */
    fun detectCity(lat: Double, lng: Double): CityProfile? {
        return cityProfiles.minByOrNull { city ->
            val latDiff = city.centerLat - lat
            val lngDiff = city.centerLng - lng
            latDiff * latDiff + lngDiff * lngDiff
        }?.takeIf { city ->
            // Only return if within reasonable distance (roughly 50km)
            val latDiff = city.centerLat - lat
            val lngDiff = city.centerLng - lng
            (latDiff * latDiff + lngDiff * lngDiff) < 0.25
        }
    }
    
    /**
     * Get neighborhoods matching specific vibes
     */
    fun getNeighborhoodsByVibe(cityId: String, vibe: String): List<Neighborhood> =
        getNeighborhoodsForCity(cityId).filter { it.vibes.contains(vibe.lowercase()) }
    
    /**
     * Get neighborhoods that are good for specific activities.
     * This helps select a starting neighborhood based on user preferences.
     */
    fun getNeighborhoodsForActivity(cityId: String, activityType: String): List<Neighborhood> {
        val vibeMapping = when (activityType.lowercase()) {
            "nightlife", "bars", "cocktails", "clubs" -> listOf("nightlife", "bars", "clubs")
            "mexican", "tacos", "burritos" -> listOf("latin", "tacos")
            "italian" -> listOf("italian")
            "chinese", "dim sum" -> listOf("chinese", "dim_sum")
            "brunch" -> listOf("brunch")
            "museums", "culture" -> listOf("museums", "cultural")
            "parks", "outdoors" -> listOf("parks", "waterfront")
            "shopping" -> listOf("boutiques", "shopping")
            "live_music", "music" -> listOf("live_music", "music_venues")
            else -> emptyList()
        }
        
        return if (vibeMapping.isEmpty()) {
            // Return essential neighborhoods as default
            getEssentialNeighborhoods(cityId)
        } else {
            getNeighborhoodsForCity(cityId)
                .filter { neighborhood -> neighborhood.vibes.any { it in vibeMapping } }
                .sortedBy { it.tier.value } // Essential first
        }
    }
    
    /**
     * Get recommended neighborhood count for a city based on its profile
     */
    fun getRecommendedNeighborhoodCount(cityId: String): Int {
        val profile = getCityProfile(cityId) ?: return 5
        return profile.estimatedHappeningAreas
    }
    
    companion object {
        // Popular neighborhood combinations for full-day itineraries
        val SF_CLASSIC_ROUTE = listOf("north_beach", "chinatown", "embarcadero")
        val SF_FOODIE_ROUTE = listOf("mission", "castro", "hayes_valley")
        val SF_NIGHTLIFE_ROUTE = listOf("mission", "castro", "soma")
        
        val AUSTIN_CLASSIC_ROUTE = listOf("south_congress", "downtown", "east_austin")
        val AUSTIN_FOODIE_ROUTE = listOf("east_austin", "south_lamar", "rainey_street")
    }
}
