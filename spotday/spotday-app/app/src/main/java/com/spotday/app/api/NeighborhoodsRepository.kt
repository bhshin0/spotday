package com.spotday.app.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.spotday.app.model.CityProfile
import com.spotday.app.model.DataSource
import com.spotday.app.model.Neighborhood
import com.spotday.app.model.NeighborhoodTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repository of neighborhoods across multiple cities.
 * Supports tiered neighborhoods (Essential/Classic/Local) and multiple data sources.
 * 
 * Data flow:
 * 1. Check SharedPreferences cache for city
 * 2. If not cached, fetch from Supabase → save to cache
 * 3. Return cached data
 * 
 * Scaling strategy:
 * - Tier 1 cities (SF, NYC, LA): Hand-curated, 8-15 neighborhoods
 * - Tier 2 cities (Austin, Portland): LLM-seeded, 5-10 neighborhoods  
 * - Tier 3 cities: Algorithm-derived from venue clustering, 3-6 neighborhoods
 */
class NeighborhoodsRepository(private val context: Context? = null) {
    
    companion object {
        private const val TAG = "NeighborhoodsRepo"
        private const val PREFS_NAME = "neighborhoods_cache"
        private const val KEY_PREFIX = "neighborhoods_"
        private const val CACHE_TTL_HOURS = 24 * 7 // 1 week
        
        // Popular neighborhood combinations for full-day itineraries
        val SF_CLASSIC_ROUTE = listOf("north_beach", "chinatown", "embarcadero")
        val SF_FOODIE_ROUTE = listOf("mission", "castro", "hayes_valley")
        val SF_NIGHTLIFE_ROUTE = listOf("mission", "castro", "soma")
        
        val AUSTIN_CLASSIC_ROUTE = listOf("south_congress", "downtown", "east_austin")
        val AUSTIN_FOODIE_ROUTE = listOf("east_austin", "south_lamar", "rainey_street")
    }
    
    private val prefs: SharedPreferences? = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    
    // In-memory cache for current session
    private val memoryCache = mutableMapOf<String, List<Neighborhood>>()
    
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
    
    // Combined local neighborhoods (fallback data)
    private val localNeighborhoods: List<Neighborhood> by lazy {
        sanFranciscoNeighborhoods + austinNeighborhoods
    }
    
    // ============================================
    // CACHING HELPERS
    // ============================================
    
    private fun getCacheKey(cityId: String): String = "$KEY_PREFIX$cityId"
    private fun getTimestampKey(cityId: String): String = "${KEY_PREFIX}${cityId}_ts"
    
    private fun saveToCache(cityId: String, neighborhoods: List<Neighborhood>) {
        prefs ?: return
        try {
            val jsonString = json.encodeToString(neighborhoods)
            prefs.edit()
                .putString(getCacheKey(cityId), jsonString)
                .putLong(getTimestampKey(cityId), System.currentTimeMillis())
                .apply()
            Log.d(TAG, "Saved ${neighborhoods.size} neighborhoods to cache for $cityId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save neighborhoods to cache", e)
        }
    }
    
    private fun loadFromCache(cityId: String): List<Neighborhood>? {
        prefs ?: return null
        try {
            val timestamp = prefs.getLong(getTimestampKey(cityId), 0)
            val age = System.currentTimeMillis() - timestamp
            val maxAge = CACHE_TTL_HOURS * 60 * 60 * 1000L
            
            if (age > maxAge) {
                Log.d(TAG, "Cache expired for $cityId (age: ${age / 3600000}h)")
                return null
            }
            
            val jsonString = prefs.getString(getCacheKey(cityId), null) ?: return null
            val neighborhoods = json.decodeFromString<List<Neighborhood>>(jsonString)
            Log.d(TAG, "Loaded ${neighborhoods.size} neighborhoods from cache for $cityId")
            return neighborhoods
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load neighborhoods from cache", e)
            return null
        }
    }
    
    private fun convertToAppNeighborhood(supabase: SupabaseNeighborhood): Neighborhood {
        return Neighborhood(
            id = supabase.id,
            name = supabase.name,
            cityId = supabase.city_id,
            tier = NeighborhoodTier.valueOf(supabase.tier),
            centerLat = supabase.center_lat,
            centerLng = supabase.center_lng,
            radiusMeters = supabase.radius_meters,
            vibes = supabase.vibes,
            dataSource = DataSource.valueOf(supabase.data_source),
            adjacentNeighborhoods = supabase.adjacent_neighborhoods,
            description = supabase.description
        )
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
     * Get all neighborhoods for a specific city.
     * This is the synchronous version that uses local data only.
     * Prefer getNeighborhoodsForCityAsync() for fresh data from Supabase.
     */
    fun getNeighborhoodsForCity(cityId: String): List<Neighborhood> {
        // Check memory cache first
        memoryCache[cityId]?.let { return it }
        
        // Check SharedPrefs cache
        loadFromCache(cityId)?.let { cached ->
            memoryCache[cityId] = cached
            return cached
        }
        
        // Fallback to local hardcoded data
        return localNeighborhoods.filter { it.cityId == cityId }
    }
    
    /**
     * Get all neighborhoods for a specific city from Supabase.
     * Uses caching: SharedPrefs → Supabase → cache.
     */
    suspend fun getNeighborhoodsForCityAsync(cityId: String): List<Neighborhood> = withContext(Dispatchers.IO) {
        // Check memory cache first
        memoryCache[cityId]?.let { return@withContext it }
        
        // Check SharedPrefs cache
        loadFromCache(cityId)?.let { cached ->
            memoryCache[cityId] = cached
            return@withContext cached
        }
        
        // Fetch from Supabase
        try {
            Log.d(TAG, "Fetching neighborhoods from Supabase for $cityId...")
            val supabaseNeighborhoods = SupabaseClient.getNeighborhoods(cityId)
            
            if (supabaseNeighborhoods.isNotEmpty()) {
                val neighborhoods = supabaseNeighborhoods.map { convertToAppNeighborhood(it) }
                memoryCache[cityId] = neighborhoods
                saveToCache(cityId, neighborhoods)
                Log.d(TAG, "Loaded ${neighborhoods.size} neighborhoods from Supabase for $cityId")
                return@withContext neighborhoods
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch from Supabase, using local fallback", e)
        }
        
        // Fallback to local hardcoded data
        val fallback = localNeighborhoods.filter { it.cityId == cityId }
        Log.d(TAG, "Using ${fallback.size} local fallback neighborhoods for $cityId")
        return@withContext fallback
    }
    
    /**
     * Clear cache for a city (useful for debugging or force refresh)
     */
    fun clearCache(cityId: String? = null) {
        if (cityId != null) {
            memoryCache.remove(cityId)
            prefs?.edit()
                ?.remove(getCacheKey(cityId))
                ?.remove(getTimestampKey(cityId))
                ?.apply()
        } else {
            memoryCache.clear()
            prefs?.edit()?.clear()?.apply()
        }
    }
    
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
     * Get all neighborhoods from memory/cache across all loaded cities
     */
    fun getAllNeighborhoods(): List<Neighborhood> {
        // Return all cached neighborhoods + local fallback
        val cached = memoryCache.values.flatten()
        if (cached.isNotEmpty()) return cached
        return localNeighborhoods
    }
    
    /**
     * Get a specific neighborhood by ID (searches all loaded cities)
     */
    fun getNeighborhood(id: String): Neighborhood? = 
        getAllNeighborhoods().find { it.id == id }
    
    /**
     * Get a specific neighborhood by ID within a city
     */
    fun getNeighborhood(cityId: String, neighborhoodId: String): Neighborhood? = 
        getNeighborhoodsForCity(cityId).find { it.id == neighborhoodId }
    
    /**
     * Get neighborhoods adjacent to the given neighborhood (global lookup - may match wrong city)
     */
    fun getAdjacentNeighborhoods(neighborhoodId: String): List<Neighborhood> {
        val neighborhood = getNeighborhood(neighborhoodId) ?: return emptyList()
        return neighborhood.adjacentNeighborhoods.mapNotNull { getNeighborhood(it) }
    }
    
    /**
     * Get neighborhoods adjacent to the given neighborhood within a specific city
     */
    fun getAdjacentNeighborhoods(cityId: String, neighborhoodId: String): List<Neighborhood> {
        val neighborhood = getNeighborhood(cityId, neighborhoodId) ?: return emptyList()
        return neighborhood.adjacentNeighborhoods.mapNotNull { getNeighborhood(cityId, it) }
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
        return getAllNeighborhoods().minByOrNull { neighborhood ->
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
}
