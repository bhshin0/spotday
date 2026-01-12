package com.spotday.app.api

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.spotday.app.BuildConfig
import com.spotday.app.model.Place as AppPlace
import com.spotday.app.model.PlaceType
import com.spotday.app.model.ServiceStyle
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.pow
import kotlin.math.sqrt

class PlacesRepository(private val context: Context) {
    private val placesClient: PlacesClient
    val neighborhoodsRepository = NeighborhoodsRepository(context)
    
    companion object {
        // Toggle to switch between remote Supabase data and local mock data
        // Set to true to test the Google Places cache
        const val USE_REMOTE_DATA = true
        private const val TAG = "PlacesRepository"
    }
    
    // In-memory cache of all places for the city (loaded once per city)
    private var cachedPlaces: List<AppPlace>? = null
    private var cachedCityId: String? = null
    private var cacheLoaded = false
    
    // Current city ID (can be changed via prefetch or directly set)
    var currentCityId: String = "san_francisco"

    init {
        if (!Places.isInitialized()) {
            Places.initialize(context, BuildConfig.PLACES_API_KEY)
        }
        placesClient = Places.createClient(context)
    }

    // San Francisco center coordinates
    private val SF_CENTER = LatLng(37.7749, -122.4194)
    
    // Quick stops repository for prefetching
    private val quickStopsRepository = QuickStopsRepository()
    
    /**
     * Prefetch places for a city in background.
     * Call this when user selects a city to have data ready by itinerary time.
     */
    suspend fun prefetchForCity(cityId: String) {
        Log.d(TAG, "Prefetching places for $cityId...")
        currentCityId = cityId
        
        // Clear cache if city changed
        if (cachedCityId != cityId) {
            cachedPlaces = null
            cacheLoaded = false
        }
        
        // Load in background
        loadRemotePlaces()
        
        // Also prefetch neighborhoods
        neighborhoodsRepository.getNeighborhoodsForCityAsync(cityId)
        
        // Prefetch quick stops (viewpoints, photo spots, street art)
        quickStopsRepository.loadQuickStops(cityId)
        
        Log.d(TAG, "Prefetch complete for $cityId: ${cachedPlaces?.size ?: 0} places")
    }
    
    /**
     * Get all cached places for the current city.
     * Returns empty list if not loaded yet.
     */
    fun getCachedPlaces(): List<AppPlace> = cachedPlaces ?: emptyList()
    
    /**
     * Load all places from Supabase for the current city.
     * Called once, then filters locally for each search method.
     * Filters out permanently closed and stale venues (not verified in 90 days).
     */
    private suspend fun loadRemotePlaces(): List<AppPlace> {
        // Return cached if we have it for current city
        if (cachedPlaces != null && cachedCityId == currentCityId) {
            return cachedPlaces!!
        }
        
        Log.d(TAG, "Loading places from Supabase for $currentCityId...")
        val remotePlaces = SupabaseClient.getAllPlaces(currentCityId)
        Log.d(TAG, "Loaded ${remotePlaces.size} places from Supabase")
        
        // Filter out stale and permanently closed places, then convert
        val freshPlaces = remotePlaces.filterNot { isStale(it) }
        val staleCount = remotePlaces.size - freshPlaces.size
        if (staleCount > 0) {
            Log.d(TAG, "Filtered out $staleCount stale/closed places")
        }
        
        // Convert to app Place objects
        cachedPlaces = freshPlaces.mapNotNull { remote ->
            try {
                convertToAppPlace(remote)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to convert place: ${remote.name}", e)
                null
            }
        }.applyAllDefaults()
        
        cachedCityId = currentCityId
        
        // Log breakdown by type
        cachedPlaces?.groupBy { it.type }?.forEach { (type, places) ->
            Log.d(TAG, "  $type: ${places.size} places")
        }
        
        cacheLoaded = true
        return cachedPlaces!!
    }
    
    /**
     * Convert Supabase cached place to app Place model.
     */
    private fun convertToAppPlace(remote: SupabaseCachedPlace): AppPlace {
        val placeType = when (remote.placeType) {
            "RESTAURANT" -> PlaceType.RESTAURANT
            "MUSEUM" -> PlaceType.MUSEUM
            "PARK" -> PlaceType.PARK
            "NIGHTLIFE" -> PlaceType.NIGHTLIFE
            "SHOPPING" -> PlaceType.SHOPPING
            "WELLNESS" -> PlaceType.WELLNESS
            "ENTERTAINMENT" -> PlaceType.ENTERTAINMENT
            "HISTORIC_SITE" -> PlaceType.HISTORIC_SITE
            "WATERFRONT" -> PlaceType.WATERFRONT
            "OUTDOOR" -> PlaceType.OUTDOOR
            "BREWERY" -> PlaceType.BREWERY
            "GAMES" -> PlaceType.GAMES
            "CLASS" -> PlaceType.CLASS
            "MARKET" -> PlaceType.MARKET
            "SPORTS" -> PlaceType.SPORTS
            else -> PlaceType.MUSEUM // Default fallback
        }
        
        return AppPlace(
            id = remote.id,
            name = remote.name,
            type = placeType,
            lat = remote.lat,
            lng = remote.lng,
            rating = remote.rating?.toFloat() ?: 4.0f,
            isOpen = !remote.isPermanentlyClosed, // Use permanently closed status
            priceLevel = remote.priceLevel ?: 2,
            estimatedCost = estimateCostFromPriceLevel(remote.priceLevel, placeType),
            openHour = remote.openHour,
            closeHour = remote.closeHour,
            isOutdoor = remote.isOutdoor,
            neighborhood = remote.neighborhoodId,
            reviewCount = remote.reviewCount,
            nightlifeCategory = remote.nightlifeCategory
        )
    }
    
    /**
     * Check if a place is stale (not verified within 90 days).
     * Returns true if the place should be filtered out.
     */
    private fun isStale(remote: SupabaseCachedPlace): Boolean {
        // Filter out permanently closed places
        if (remote.isPermanentlyClosed) return true
        
        // Check last_verified_at for staleness (90 days)
        val lastVerified = remote.lastVerifiedAt ?: return false // No timestamp = not stale
        return try {
            val verifiedTime = java.time.Instant.parse(lastVerified)
            val ninetyDaysAgo = java.time.Instant.now().minus(java.time.Duration.ofDays(90))
            verifiedTime.isBefore(ninetyDaysAgo)
        } catch (e: Exception) {
            false // If parsing fails, don't filter out
        }
    }
    
    /**
     * Estimate cost based on price level and place type.
     */
    private fun estimateCostFromPriceLevel(priceLevel: Int?, placeType: PlaceType): Int {
        val level = priceLevel ?: 2
        return when (placeType) {
            PlaceType.RESTAURANT -> when (level) {
                1 -> 15
                2 -> 30
                3 -> 50
                4 -> 80
                else -> 25
            }
            PlaceType.MUSEUM -> when (level) {
                1 -> 0
                2 -> 20
                3 -> 30
                else -> 15
            }
            PlaceType.NIGHTLIFE -> when (level) {
                1 -> 15
                2 -> 30
                3 -> 50
                else -> 25
            }
            PlaceType.WELLNESS -> when (level) {
                1 -> 30
                2 -> 60
                3 -> 100
                4 -> 150
                else -> 50
            }
            else -> 0 // Parks, waterfront, etc. are free
        }
    }
    
    /**
     * Filter cached places by type.
     */
    private suspend fun getPlacesByType(vararg types: PlaceType): List<AppPlace> {
        val allPlaces = loadRemotePlaces()
        return allPlaces.filter { it.type in types }
    }
    
    /**
     * Apply type defaults only.
     * Neighborhood assignment is now handled by Supabase (single source of truth).
     * See: sync-places Edge Function for neighborhood assignment logic.
     */
    private fun AppPlace.withAllDefaults(): AppPlace {
        return this.withTypeDefaults()
    }
    
    private fun List<AppPlace>.applyAllDefaults(): List<AppPlace> = map { it.withAllDefaults() }
    
    /**
     * Apply sensible default operating hours and outdoor flag based on PlaceType.
     * Museums: 10 AM - 5 PM (indoor)
     * Parks: 6 AM - 10 PM (outdoor)
     * Restaurants: 7 AM - 10 PM (indoor) - varies for breakfast vs dinner spots
     * Nightlife: 4 PM - 2 AM (indoor)
     * Shopping: 10 AM - 9 PM (indoor)
     * Waterfront: 6 AM - 10 PM (outdoor)
     * Historic Sites: 9 AM - 6 PM (varies outdoor/indoor)
     * Entertainment: 7 PM - 11 PM (indoor, evening focused)
     * Games: 11 AM - 10 PM (indoor)
     * Outdoor: 8 AM - 6 PM (outdoor, daylight)
     * Wellness: 9 AM - 9 PM (indoor)
     * Brewery: 12 PM - 9 PM (indoor)
     * Class: 10 AM - 8 PM (indoor)
     * Market: 7 AM - 2 PM (outdoor, morning focused)
     * Sports: 7 AM - 10 PM (varies)
     */
    private fun AppPlace.withTypeDefaults(): AppPlace {
        return when (this.type) {
            PlaceType.MUSEUM -> this.copy(openHour = 10, closeHour = 17, isOutdoor = false)
            PlaceType.PARK -> this.copy(openHour = 6, closeHour = 22, isOutdoor = true)
            PlaceType.RESTAURANT -> this.copy(
                openHour = 7, 
                closeHour = 22, 
                isOutdoor = false,
                serviceStyle = getServiceStyle(this.id, this.priceLevel)
            )
            PlaceType.NIGHTLIFE -> this.copy(openHour = 16, closeHour = 2, isOutdoor = false)
            PlaceType.SHOPPING -> this.copy(openHour = 10, closeHour = 21, isOutdoor = false)
            PlaceType.WATERFRONT -> this.copy(openHour = 6, closeHour = 22, isOutdoor = true)
            PlaceType.HISTORIC_SITE -> {
                // Outdoor historic sites (trails, bridges, parks)
                val outdoorSites = listOf("golden_gate_bridge", "batteries_to_bluffs", 
                    "telegraph_hill_stairs", "sutro_baths", "dutch_windmill", "presidio",
                    "fort_point", "pet_cemetery", "huntington_park", "harvey_milk_plaza")
                if (this.id in outdoorSites) {
                    this.copy(openHour = 6, closeHour = 22, isOutdoor = true)
                } else {
                    this.copy(openHour = 9, closeHour = 18, isOutdoor = false)
                }
            }
            // New activity categories
            PlaceType.ENTERTAINMENT -> this.copy(openHour = 19, closeHour = 23, isOutdoor = false)
            PlaceType.GAMES -> this.copy(openHour = 11, closeHour = 22, isOutdoor = false)
            PlaceType.OUTDOOR -> this.copy(openHour = 8, closeHour = 18, isOutdoor = true)
            PlaceType.WELLNESS -> this.copy(openHour = 9, closeHour = 21, isOutdoor = false)
            PlaceType.BREWERY -> this.copy(openHour = 12, closeHour = 21, isOutdoor = false)
            PlaceType.CLASS -> this.copy(openHour = 10, closeHour = 20, isOutdoor = false)
            PlaceType.MARKET -> this.copy(openHour = 7, closeHour = 14, isOutdoor = true)
            PlaceType.SPORTS -> this.copy(openHour = 7, closeHour = 22, isOutdoor = true)
        }
    }
    
    /**
     * Determine service style for a restaurant based on ID or price level.
     * - QUICK: Food trucks, cafes, fast casual, bakeries, delis, grab-and-go
     * - CASUAL: Most neighborhood restaurants (default for price level 2)
     * - FORMAL: Fine dining, upscale (price level 3-4 or specific high-end spots)
     */
    private fun getServiceStyle(id: String, priceLevel: Int): ServiceStyle {
        // Explicit FORMAL restaurants (fine dining, tasting menus, upscale)
        val formalRestaurants = setOf(
            "atelier_crenn", "quince", "benu", "lazy_bear", "acquerello",
            "rich_table", "jardiniere", "boulevard", "aziza", "hakkasan",
            "spruce", "farallon", "waterbar", "scomas", "alioto",
            "franciscan", "frances", "foreign_cinema"
        )
        
        // Explicit QUICK restaurants (counter service, grab-and-go, fast casual)
        val quickRestaurants = setOf(
            // Taquerias and quick Mexican
            "la_taqueria", "el_farolito", "la_palma", "taqueria_cancun", 
            "pancho_villa", "lolos", "el_tonayense", "taqueria_guadalajara",
            "el_buen_comer", "taqueria_vallarta", "la_victoria", "panchitas",
            "los_panchos", "el_zocalo", "panchitas_noe", "el_rincon_yucateco", "gordo",
            // Pizza slices and quick Italian
            "golden_boy", "molinari", "liguria_bakery", "picas",
            // Quick Asian
            "house_nanking", "mensho_tokyo", "hinodeya", "benkyodo", 
            "ramen_yamadaya", "ramen_underground", "izakaya_sozai",
            // Cafes and bakeries
            "trouble_coffee", "devils_teeth", "arizmendi", "tartine_manufactory",
            "sentinel", "saigon_sandwich", "greens_to_go",
            // Fast casual
            "souvla", "souvla_hayes", "tacko", "papalote",
            "yamo", "loving_hut", "herbivore", "golden_era",
            "shangri_la", "vegan_picnic", "rosamunde"
        )
        
        return when {
            id in formalRestaurants -> ServiceStyle.FORMAL
            id in quickRestaurants -> ServiceStyle.QUICK
            priceLevel >= 3 -> ServiceStyle.FORMAL  // $$$ and $$$$ default to formal
            priceLevel == 1 -> ServiceStyle.QUICK   // $ default to quick
            else -> ServiceStyle.CASUAL             // $$ default to casual
        }
    }
    
    private fun List<AppPlace>.applyTypeDefaults(): List<AppPlace> = map { it.withAllDefaults() }

    suspend fun searchMuseums(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for museums (city=$currentCityId, remote=$USE_REMOTE_DATA)")
        
        if (USE_REMOTE_DATA) {
            val places = getPlacesByType(PlaceType.MUSEUM)
            Log.d("PlacesRepository", "Found ${places.size} museums from remote")
            return places // Don't fallback to SF mock data for other cities
        }
        
        return getMockMuseums()
    }
    
    private fun getMockMuseums(): List<AppPlace> {
        return listOf<AppPlace>(
            // North SF - Fisherman's Wharf / Marina
            AppPlace("maritime_museum", "Maritime Museum", PlaceType.MUSEUM, 37.8088, -122.4229, 4.5f, true, 2, 15),
            AppPlace("wax_museum", "Wax Museum at Fisherman's Wharf", PlaceType.MUSEUM, 37.8098, -122.4166, 4.2f, true, 2, 25),
            AppPlace("exploratorium", "Exploratorium", PlaceType.MUSEUM, 37.8014, -122.3975, 4.6f, true, 2, 30, reviewCount = 3500),
            AppPlace("palace_fine_arts", "Palace of Fine Arts", PlaceType.MUSEUM, 37.8033, -122.4477, 4.7f, true, 1, 0, reviewCount = 4000),
            AppPlace("fort_mason", "Fort Mason Center", PlaceType.MUSEUM, 37.8055, -122.4315, 4.4f, true, 1, 10),
            
            // Central SF - Downtown / SoMa
            AppPlace("sfmoma", "San Francisco Museum of Modern Art", PlaceType.MUSEUM, 37.7857, -122.4011, 4.6f, true, 2, 25, reviewCount = 4000),
            AppPlace("jewish_museum", "Contemporary Jewish Museum", PlaceType.MUSEUM, 37.7847, -122.4020, 4.3f, true, 2, 15),
            AppPlace("cartoon_art", "Cartoon Art Museum", PlaceType.MUSEUM, 37.7826, -122.4025, 4.4f, true, 2, 10),
            AppPlace("museum_craft", "Museum of Craft and Design", PlaceType.MUSEUM, 37.7751, -122.3985, 4.2f, true, 2, 12),
            AppPlace("asian_art", "Asian Art Museum", PlaceType.MUSEUM, 37.7803, -122.4158, 4.5f, true, 2, 20),
            AppPlace("afric_diaspora", "Museum of the African Diaspora", PlaceType.MUSEUM, 37.7858, -122.4012, 4.3f, true, 2, 15),
            
            // Central - Mission / Castro
            AppPlace("mission_cultural", "Mission Cultural Center", PlaceType.MUSEUM, 37.7485, -122.4192, 4.3f, true, 1, 5),
            AppPlace("womens_building", "Women's Building Mural", PlaceType.MUSEUM, 37.7564, -122.4202, 4.5f, true, 1, 0),
            AppPlace("glbt_history", "GLBT Historical Society Museum", PlaceType.MUSEUM, 37.7615, -122.4345, 4.6f, true, 1, 10),
            AppPlace("balmy_alley", "Balmy Alley Murals", PlaceType.MUSEUM, 37.7475, -122.4158, 4.7f, true, 1, 0),
            AppPlace("precita_eyes", "Precita Eyes Mural Arts Center", PlaceType.MUSEUM, 37.7478, -122.4148, 4.5f, true, 1, 15),
            
            // Golden Gate Park Area
            AppPlace("deyoung", "de Young Museum", PlaceType.MUSEUM, 37.7714, -122.4686, 4.6f, true, 2, 25, reviewCount = 3000),
            AppPlace("calacdemy", "California Academy of Sciences", PlaceType.MUSEUM, 37.7699, -122.4661, 4.7f, true, 2, 30, reviewCount = 5000),
            AppPlace("legion_honor", "Legion of Honor", PlaceType.MUSEUM, 37.7849, -122.5001, 4.6f, true, 2, 20, reviewCount = 1500),
            AppPlace("japanese_tea", "Japanese Tea Garden", PlaceType.MUSEUM, 37.7702, -122.4699, 4.6f, true, 1, 10),
            AppPlace("conservatory", "Conservatory of Flowers", PlaceType.MUSEUM, 37.7727, -122.4608, 4.6f, true, 1, 10),
            AppPlace("botanical_garden", "SF Botanical Garden", PlaceType.MUSEUM, 37.7677, -122.4736, 4.7f, true, 1, 10),
            
            // Richmond / Sunset
            AppPlace("musee_mecanique", "Musée Mécanique", PlaceType.MUSEUM, 37.8090, -122.4185, 4.5f, true, 1, 5),
            AppPlace("cliff_house", "Cliff House Visitor Center", PlaceType.MUSEUM, 37.7783, -122.5139, 4.2f, true, 1, 0),
            AppPlace("sutro_baths", "Sutro Baths Museum", PlaceType.MUSEUM, 37.7805, -122.5135, 4.4f, true, 1, 0),
            
            // Financial District / Embarcadero
            AppPlace("wells_fargo", "Wells Fargo History Museum", PlaceType.MUSEUM, 37.7933, -122.4012, 4.3f, true, 1, 0),
            AppPlace("museum_money", "Museum of Money", PlaceType.MUSEUM, 37.7888, -122.4034, 4.1f, true, 1, 0),
            AppPlace("railway_museum", "SF Railway Museum", PlaceType.MUSEUM, 37.7918, -122.3941, 4.4f, true, 1, 0),
            
            // Various Neighborhoods
            AppPlace("chinese_historical", "Chinese Historical Society", PlaceType.MUSEUM, 37.7942, -122.4061, 4.4f, true, 1, 10),
            AppPlace("beat_museum", "Beat Museum", PlaceType.MUSEUM, 37.7974, -122.4082, 4.3f, true, 1, 10),
            AppPlace("haas_lilienthal", "Haas-Lilienthal House", PlaceType.MUSEUM, 37.7912, -122.4253, 4.5f, true, 1, 15),
            AppPlace("octagon_house", "Octagon House Museum", PlaceType.MUSEUM, 37.8004, -122.4309, 4.3f, true, 1, 10),
            AppPlace("diego_rivera", "Diego Rivera Gallery", PlaceType.MUSEUM, 37.7218, -122.4714, 4.4f, true, 1, 5),
            AppPlace("randall_museum", "Randall Museum", PlaceType.MUSEUM, 37.7624, -122.4389, 4.5f, true, 1, 5),
            AppPlace("sf_fire", "SF Fire Department Museum", PlaceType.MUSEUM, 37.7845, -122.4217, 4.6f, true, 1, 0),
            AppPlace("society_pioneer", "Society of California Pioneers", PlaceType.MUSEUM, 37.7803, -122.4027, 4.3f, true, 1, 5),
            AppPlace("mexican_museum", "Mexican Museum", PlaceType.MUSEUM, 37.8056, -122.4322, 4.2f, true, 2, 15),
            AppPlace("sf_city_hall", "SF City Hall Tours", PlaceType.MUSEUM, 37.7793, -122.4193, 4.7f, true, 1, 10),
            AppPlace("sf_public_lib", "SF Main Library History", PlaceType.MUSEUM, 37.7799, -122.4158, 4.5f, true, 1, 0),
            AppPlace("childrens_creativity", "Children's Creativity Museum", PlaceType.MUSEUM, 37.7847, -122.4007, 4.4f, true, 2, 15),
            AppPlace("zeum", "Zeum Theater", PlaceType.MUSEUM, 37.7848, -122.4009, 4.3f, true, 2, 12),
            AppPlace("yerba_buena", "Yerba Buena Center for Arts", PlaceType.MUSEUM, 37.7854, -122.4020, 4.4f, true, 2, 15),
            AppPlace("museum_performance", "Museum of Performance + Design", PlaceType.MUSEUM, 37.7863, -122.4015, 4.2f, true, 1, 10),
            AppPlace("sf_arts_commission", "SF Arts Commission Gallery", PlaceType.MUSEUM, 37.7798, -122.4191, 4.3f, true, 1, 0),
            AppPlace("luggage_store", "Luggage Store Gallery", PlaceType.MUSEUM, 37.7851, -122.4080, 4.2f, true, 1, 0),
            AppPlace("catharine_clark", "Catharine Clark Gallery", PlaceType.MUSEUM, 37.7694, -122.4020, 4.4f, true, 1, 0),
            AppPlace("fraenkel_gallery", "Fraenkel Gallery", PlaceType.MUSEUM, 37.7887, -122.4018, 4.5f, true, 1, 0),
            
            // Additional Neighborhood Galleries & Cultural Sites
            // Mission
            AppPlace("clarion_alley", "Clarion Alley Murals", PlaceType.MUSEUM, 37.7629, -122.4200, 4.7f, true, 1, 0),
            AppPlace("gallery_16", "Gallery 16", PlaceType.MUSEUM, 37.7603, -122.4105, 4.3f, true, 1, 0),
            AppPlace("mission_comics", "Mission: Comics & Art", PlaceType.MUSEUM, 37.7550, -122.4195, 4.2f, true, 1, 5),
            
            // Dogpatch
            AppPlace("museum_3d", "Museum of 3D Illusions", PlaceType.MUSEUM, 37.7600, -122.3915, 4.4f, true, 2, 20),
            AppPlace("dogpatch_studios", "Dogpatch Studios", PlaceType.MUSEUM, 37.7588, -122.3880, 4.3f, true, 1, 0),
            
            // Potrero Hill
            AppPlace("anchor_brewing", "Anchor Brewing Museum", PlaceType.MUSEUM, 37.7620, -122.4003, 4.5f, true, 2, 15),
            
            // Hayes Valley
            AppPlace("sf_jazz", "SF JAZZ Center", PlaceType.MUSEUM, 37.7762, -122.4208, 4.7f, true, 2, 20),
            
            // Lower Haight
            AppPlace("bound_together", "Bound Together Bookstore", PlaceType.MUSEUM, 37.7702, -122.4485, 4.4f, true, 1, 0),
            
            // North Beach
            AppPlace("city_lights", "City Lights Bookstore", PlaceType.MUSEUM, 37.7976, -122.4066, 4.7f, true, 1, 0),
            AppPlace("kerouac_alley", "Jack Kerouac Alley", PlaceType.MUSEUM, 37.7976, -122.4070, 4.5f, true, 1, 0),
            
            // Inner Sunset
            AppPlace("sf_botanical_east", "SF Botanical Garden - East Meadow", PlaceType.MUSEUM, 37.7685, -122.4705, 4.6f, true, 1, 5),
            
            // Embarcadero
            AppPlace("ferry_artisan", "Ferry Building Artisan Showcase", PlaceType.MUSEUM, 37.7956, -122.3935, 4.5f, true, 1, 0),
            
            // Marina
            AppPlace("palace_legion", "Palace of Fine Arts Theatre", PlaceType.MUSEUM, 37.8033, -122.4480, 4.6f, true, 2, 15)
        ).applyTypeDefaults()
    }

    suspend fun searchParks(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for parks (city=$currentCityId, remote=$USE_REMOTE_DATA)")
        
        if (USE_REMOTE_DATA) {
            val places = getPlacesByType(PlaceType.PARK)
            Log.d("PlacesRepository", "Found ${places.size} parks from remote")
            return places
        }
        
        return getMockParks()
    }
    
    private fun getMockParks(): List<AppPlace> {
        return listOf<AppPlace>(
            // Golden Gate Park Area - Famous spots with high review counts
            AppPlace("ggpark", "Golden Gate Park", PlaceType.PARK, 37.7694, -122.4862, 4.8f, true, 0, 0, reviewCount = 5000),
            AppPlace("stow_lake", "Stow Lake", PlaceType.PARK, 37.7694, -122.4780, 4.7f, true, 0, 0, reviewCount = 1500),
            AppPlace("ggpark_polo", "Polo Fields", PlaceType.PARK, 37.7705, -122.4925, 4.5f, true, 0, 0),
            AppPlace("windmills", "Dutch Windmill Area", PlaceType.PARK, 37.7711, -122.5093, 4.6f, true, 0, 0),
            AppPlace("music_concourse", "Music Concourse", PlaceType.PARK, 37.7708, -122.4683, 4.5f, true, 0, 0),
            
            // Northern Waterfront
            AppPlace("crissy", "Crissy Field", PlaceType.PARK, 37.8050, -122.4650, 4.7f, true, 0, 0, reviewCount = 2500),
            AppPlace("presidio", "Presidio National Park", PlaceType.PARK, 37.7989, -122.4662, 4.8f, true, 0, 0, reviewCount = 3000),
            AppPlace("fort_point", "Fort Point Area", PlaceType.PARK, 37.8108, -122.4764, 4.7f, true, 0, 0),
            AppPlace("bakers_beach", "Baker Beach", PlaceType.PARK, 37.7930, -122.4836, 4.7f, true, 0, 0),
            AppPlace("marina_green", "Marina Green", PlaceType.PARK, 37.8038, -122.4388, 4.6f, true, 0, 0),
            AppPlace("fort_mason_park", "Fort Mason Gardens", PlaceType.PARK, 37.8062, -122.4313, 4.5f, true, 0, 0),
            
            // Mission / Castro
            AppPlace("dolores", "Dolores Park", PlaceType.PARK, 37.7596, -122.4269, 4.7f, true, 0, 0, reviewCount = 3500),
            AppPlace("mission_playground", "Mission Playground", PlaceType.PARK, 37.7543, -122.4152, 4.4f, true, 0, 0),
            AppPlace("balboa_park", "Balboa Park", PlaceType.PARK, 37.7211, -122.4450, 4.5f, true, 0, 0),
            AppPlace("glen_canyon", "Glen Canyon Park", PlaceType.PARK, 37.7419, -122.4418, 4.6f, true, 0, 0),
            AppPlace("bernal_heights", "Bernal Heights Park", PlaceType.PARK, 37.7417, -122.4197, 4.7f, true, 0, 0),
            AppPlace("holly_park", "Holly Park", PlaceType.PARK, 37.7409, -122.4221, 4.4f, true, 0, 0),
            
            // Central / Downtown
            AppPlace("alamo", "Alamo Square", PlaceType.PARK, 37.7766, -122.4345, 4.6f, true, 0, 0),
            AppPlace("buena_vista", "Buena Vista Park", PlaceType.PARK, 37.7676, -122.4403, 4.6f, true, 0, 0),
            AppPlace("corona_heights", "Corona Heights Park", PlaceType.PARK, 37.7637, -122.4380, 4.7f, true, 0, 0),
            AppPlace("lafayette_park", "Lafayette Park", PlaceType.PARK, 37.7916, -122.4285, 4.6f, true, 0, 0),
            AppPlace("alta_plaza", "Alta Plaza Park", PlaceType.PARK, 37.7919, -122.4350, 4.6f, true, 0, 0),
            AppPlace("jefferson_square", "Jefferson Square Park", PlaceType.PARK, 37.7769, -122.4237, 4.4f, true, 0, 0),
            AppPlace("civic_center", "Civic Center Plaza", PlaceType.PARK, 37.7799, -122.4193, 4.3f, true, 0, 0),
            AppPlace("un_plaza", "UN Plaza", PlaceType.PARK, 37.7802, -122.4137, 4.2f, true, 0, 0),
            AppPlace("south_park", "South Park", PlaceType.PARK, 37.7799, -122.3926, 4.5f, true, 0, 0),
            
            // Western SF
            AppPlace("landsend", "Lands End", PlaceType.PARK, 37.7849, -122.5080, 4.8f, true, 0, 0),
            AppPlace("sutro_heights", "Sutro Heights Park", PlaceType.PARK, 37.7794, -122.5126, 4.7f, true, 0, 0),
            AppPlace("ocean_beach", "Ocean Beach", PlaceType.PARK, 37.7602, -122.5110, 4.6f, true, 0, 0),
            AppPlace("lincoln_park", "Lincoln Park", PlaceType.PARK, 37.7837, -122.4980, 4.7f, true, 0, 0),
            AppPlace("fort_funston", "Fort Funston", PlaceType.PARK, 37.7134, -122.5012, 4.7f, true, 0, 0),
            
            // Bayview / Southeast
            AppPlace("candlestick", "Candlestick Point", PlaceType.PARK, 37.7098, -122.3860, 4.4f, true, 0, 0),
            AppPlace("mclaren_park", "McLaren Park", PlaceType.PARK, 37.7192, -122.4181, 4.6f, true, 0, 0),
            AppPlace("portola", "John McLaren Park", PlaceType.PARK, 37.7194, -122.4236, 4.5f, true, 0, 0),
            
            // North Beach / Telegraph Hill
            AppPlace("coit_tower", "Coit Tower Park", PlaceType.PARK, 37.8024, -122.4058, 4.7f, true, 0, 5),
            AppPlace("washington_square", "Washington Square", PlaceType.PARK, 37.8001, -122.4102, 4.5f, true, 0, 0),
            AppPlace("telegraph_hill", "Telegraph Hill Park", PlaceType.PARK, 37.8015, -122.4065, 4.6f, true, 0, 0),
            
            // Various
            AppPlace("twin_peaks", "Twin Peaks", PlaceType.PARK, 37.7544, -122.4477, 4.8f, true, 0, 0, reviewCount = 2800),
            AppPlace("mt_davidson", "Mount Davidson Park", PlaceType.PARK, 37.7382, -122.4550, 4.6f, true, 0, 0),
            AppPlace("lake_merced", "Lake Merced Park", PlaceType.PARK, 37.7167, -122.4871, 4.6f, true, 0, 0),
            AppPlace("stern_grove", "Stern Grove", PlaceType.PARK, 37.7290, -122.4743, 4.7f, true, 0, 0),
            AppPlace("mount_sutro", "Mount Sutro Open Space", PlaceType.PARK, 37.7538, -122.4518, 4.5f, true, 0, 0),
            
            // Additional Neighborhood Parks
            // Hayes Valley / Lower Haight
            AppPlace("patricia_green", "Patricia's Green", PlaceType.PARK, 37.7760, -122.4235, 4.5f, true, 0, 0),
            AppPlace("duboce_park", "Duboce Park", PlaceType.PARK, 37.7692, -122.4331, 4.5f, true, 0, 0),
            
            // Noe Valley / Glen Park
            AppPlace("douglass_park", "Douglass Playground", PlaceType.PARK, 37.7498, -122.4384, 4.4f, true, 0, 0),
            AppPlace("upper_noe_rec", "Upper Noe Recreation Center", PlaceType.PARK, 37.7492, -122.4363, 4.4f, true, 0, 0),
            AppPlace("billy_goat_hill", "Billy Goat Hill", PlaceType.PARK, 37.7420, -122.4355, 4.6f, true, 0, 0),
            
            // Potrero Hill
            AppPlace("mckinley_square", "McKinley Square", PlaceType.PARK, 37.7616, -122.4032, 4.5f, true, 0, 0),
            AppPlace("connecticut_friendship", "Connecticut Friendship Garden", PlaceType.PARK, 37.7578, -122.3971, 4.3f, true, 0, 0),
            
            // Inner Richmond
            AppPlace("rossi_playground", "Rossi Playground", PlaceType.PARK, 37.7810, -122.4555, 4.3f, true, 0, 0),
            AppPlace("mountain_lake_park", "Mountain Lake Park", PlaceType.PARK, 37.7876, -122.4690, 4.6f, true, 0, 0),
            
            // Russian Hill / North Beach
            AppPlace("ina_coolbrith", "Ina Coolbrith Park", PlaceType.PARK, 37.7989, -122.4157, 4.5f, true, 0, 0),
            AppPlace("pioneer_park", "Pioneer Park", PlaceType.PARK, 37.8024, -122.4061, 4.6f, true, 0, 0),
            
            // Dogpatch
            AppPlace("esprit_park", "Esprit Park", PlaceType.PARK, 37.7614, -122.3893, 4.4f, true, 0, 0),
            AppPlace("crane_cove", "Crane Cove Park", PlaceType.PARK, 37.7608, -122.3830, 4.5f, true, 0, 0)
        ).applyTypeDefaults()
    }

    suspend fun searchRestaurants(cuisineTypes: List<String>): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for restaurants (city=$currentCityId, cuisines=${if (cuisineTypes.isEmpty()) "ALL" else cuisineTypes}, remote=$USE_REMOTE_DATA)")
        
        if (USE_REMOTE_DATA) {
            // Remote data doesn't have cuisine types - return all restaurants
            // The app's quality scoring will handle selection
            val places = getPlacesByType(PlaceType.RESTAURANT)
            Log.d("PlacesRepository", "Found ${places.size} restaurants from remote")
            return places
        }
        
        return getMockRestaurants(cuisineTypes)
    }
    
    private fun getMockRestaurants(cuisineTypes: List<String>): List<AppPlace> {
        val allRestaurants = mutableListOf<AppPlace>()
        
        // If no cuisine types specified, include all cuisines
        val effectiveCuisines = if (cuisineTypes.isEmpty()) {
            listOf("italian", "mexican", "american", "asian", "seafood", "vegetarian")
        } else {
            cuisineTypes
        }

        if (effectiveCuisines.contains("italian")) {
            allRestaurants.addAll(
                listOf(
                    // North Beach (Italian Hub) - Famous spots with high review counts
                    AppPlace("flour_water", "Flour + Water", PlaceType.RESTAURANT, 37.7616, -122.4094, 4.5f, true, 2, 30, reviewCount = 3500),
                    AppPlace("tony_pizza", "Tony's Pizza Napoletana", PlaceType.RESTAURANT, 37.7980, -122.4094, 4.6f, true, 2, 25, reviewCount = 4500),
                    AppPlace("sotto_mare", "Sotto Mare", PlaceType.RESTAURANT, 37.8008, -122.4102, 4.5f, true, 2, 35, reviewCount = 2200),
                    AppPlace("golden_boy", "Golden Boy Pizza", PlaceType.RESTAURANT, 37.7990, -122.4080, 4.5f, true, 1, 15, reviewCount = 1800),
                    AppPlace("original_joes", "Original Joe's", PlaceType.RESTAURANT, 37.7991, -122.4096, 4.4f, true, 2, 30, reviewCount = 1500),
                    AppPlace("caffe_sport", "Caffe Sport", PlaceType.RESTAURANT, 37.8007, -122.4101, 4.3f, true, 2, 28),
                    AppPlace("mama_sf", "Mama's on Washington Square", PlaceType.RESTAURANT, 37.7999, -122.4105, 4.6f, true, 2, 20, reviewCount = 3000),
                    AppPlace("calzone", "Calzone's", PlaceType.RESTAURANT, 37.8004, -122.4084, 4.4f, true, 2, 22),
                    AppPlace("molinari", "Molinari Delicatessen", PlaceType.RESTAURANT, 37.7998, -122.4089, 4.7f, true, 1, 18, reviewCount = 2500),
                    AppPlace("liguria_bakery", "Liguria Bakery", PlaceType.RESTAURANT, 37.7987, -122.4093, 4.6f, true, 1, 12, reviewCount = 1200),
                    // Marina / Cow Hollow
                    AppPlace("delarosa", "Delarosa", PlaceType.RESTAURANT, 37.7989, -122.4354, 4.4f, true, 2, 25),
                    AppPlace("a16", "A16", PlaceType.RESTAURANT, 37.7999, -122.4358, 4.5f, true, 2, 32),
                    AppPlace("gaspare", "Gaspare's Pizza House", PlaceType.RESTAURANT, 37.8038, -122.4378, 4.4f, true, 2, 24),
                    // Inner Richmond
                    AppPlace("pazzia", "Pazzia Restaurant & Pizzeria", PlaceType.RESTAURANT, 37.7715, -122.4700, 4.3f, true, 2, 25),
                    AppPlace("picas", "Pica's", PlaceType.RESTAURANT, 37.7791, -122.4629, 4.4f, true, 1, 20),
                    // Mission
                    AppPlace("beretta", "Beretta", PlaceType.RESTAURANT, 37.7589, -122.4213, 4.4f, true, 2, 28, reviewCount = 1200),
                    AppPlace("delfina", "Delfina", PlaceType.RESTAURANT, 37.7612, -122.4179, 4.6f, true, 2, 35, reviewCount = 1800),
                    AppPlace("pizzeria_delfina", "Pizzeria Delfina", PlaceType.RESTAURANT, 37.7594, -122.4261, 4.5f, true, 2, 22, reviewCount = 1500),
                    AppPlace("locanda", "Locanda", PlaceType.RESTAURANT, 37.7590, -122.4212, 4.5f, true, 2, 30, reviewCount = 800),
                    // SoMa / Downtown
                    AppPlace("perbacco", "Perbacco", PlaceType.RESTAURANT, 37.7906, -122.4024, 4.5f, true, 2, 32, reviewCount = 900),
                    AppPlace("cotogna", "Cotogna", PlaceType.RESTAURANT, 37.7970, -122.4032, 4.6f, true, 2, 35, reviewCount = 1500),
                    AppPlace("ideale", "Ideale", PlaceType.RESTAURANT, 37.7826, -122.4098, 4.3f, true, 2, 28),
                    // Nob Hill
                    AppPlace("acquerello", "Acquerello", PlaceType.RESTAURANT, 37.7925, -122.4166, 4.7f, true, 3, 45),
                    // Russian Hill
                    AppPlace("da_flora", "Da Flora", PlaceType.RESTAURANT, 37.8036, -122.4189, 4.5f, true, 2, 30),
                    AppPlace("venticello", "Venticello", PlaceType.RESTAURANT, 37.8043, -122.4184, 4.4f, true, 2, 28),
                    // Potrero Hill
                    AppPlace("cento_osteria", "Cento Osteria", PlaceType.RESTAURANT, 37.7601, -122.4018, 4.3f, true, 2, 26),
                    // Castro
                    AppPlace("postrio", "Postrio", PlaceType.RESTAURANT, 37.7615, -122.4345, 4.3f, true, 2, 30),
                    // Hayes Valley
                    AppPlace("starbelly", "Starbelly", PlaceType.RESTAURANT, 37.7650, -122.4295, 4.4f, true, 2, 27),
                    // Outer Sunset
                    AppPlace("palinode", "Palinode", PlaceType.RESTAURANT, 37.7551, -122.4863, 4.2f, true, 2, 24),
                    AppPlace("mozzeria", "Mozzeria", PlaceType.RESTAURANT, 37.7603, -122.4643, 4.6f, true, 2, 22),
                    // Additional Italian - Dogpatch/Potrero
                    AppPlace("flour_water_pasta", "Flour + Water Pasta Shop", PlaceType.RESTAURANT, 37.7618, -122.4089, 4.5f, true, 1, 18),
                    AppPlace("plow", "Plow", PlaceType.RESTAURANT, 37.7590, -122.4011, 4.6f, true, 2, 24),
                    // Additional Italian - Bernal Heights
                    AppPlace("ragazza", "Ragazza", PlaceType.RESTAURANT, 37.7393, -122.4090, 4.4f, true, 2, 22),
                    // Additional Italian - Glen Park
                    AppPlace("glen_park_station", "Glen Park Station", PlaceType.RESTAURANT, 37.7348, -122.4336, 4.3f, true, 2, 20),
                    // Additional Italian - Lower Haight
                    AppPlace("uva_enoteca", "Uva Enoteca", PlaceType.RESTAURANT, 37.7717, -122.4298, 4.4f, true, 2, 26),
                    // Additional Italian - Nob Hill
                    AppPlace("frascati", "Frascati", PlaceType.RESTAURANT, 37.7930, -122.4180, 4.5f, true, 2, 28),
                    // Additional Italian - Inner Richmond
                    AppPlace("fiorella", "Fiorella", PlaceType.RESTAURANT, 37.7802, -122.4649, 4.5f, true, 2, 24)
                )
            )
        }

        if (effectiveCuisines.contains("mexican")) {
            allRestaurants.addAll(
                listOf(
                    // Mission District (Mexican Hub) - Famous spots with high review counts
                    AppPlace("la_taqueria", "La Taqueria", PlaceType.RESTAURANT, 37.7508, -122.4183, 4.6f, true, 1, 15, reviewCount = 5000),
                    AppPlace("el_farolito", "El Farolito", PlaceType.RESTAURANT, 37.7479, -122.4176, 4.5f, true, 1, 12, reviewCount = 3000),
                    AppPlace("la_palma", "La Palma Mexicatessen", PlaceType.RESTAURANT, 37.7528, -122.4172, 4.5f, true, 1, 14, reviewCount = 1200),
                    AppPlace("taqueria_cancun", "Taqueria Cancun", PlaceType.RESTAURANT, 37.7498, -122.4192, 4.4f, true, 1, 13, reviewCount = 2000),
                    AppPlace("pancho_villa", "Pancho Villa Taqueria", PlaceType.RESTAURANT, 37.7514, -122.4171, 4.3f, true, 1, 14, reviewCount = 1800),
                    AppPlace("papalote", "Papalote Mexican Grill", PlaceType.RESTAURANT, 37.7616, -122.4252, 4.4f, true, 2, 18, reviewCount = 1500),
                    AppPlace("gracias_madre", "Gracias Madre", PlaceType.RESTAURANT, 37.7622, -122.4245, 4.4f, true, 2, 28, reviewCount = 2000),
                    AppPlace("nopalito", "Nopalito", PlaceType.RESTAURANT, 37.7695, -122.4887, 4.4f, true, 2, 25, reviewCount = 1600),
                    AppPlace("tacolicious", "Tacolicious Mission", PlaceType.RESTAURANT, 37.7499, -122.4191, 4.3f, true, 2, 20, reviewCount = 1100),
                    AppPlace("lolos", "Lolo's", PlaceType.RESTAURANT, 37.7530, -122.4179, 4.5f, true, 1, 15),
                    AppPlace("el_tonayense", "El Tonayense", PlaceType.RESTAURANT, 37.7521, -122.4173, 4.4f, true, 1, 11),
                    AppPlace("taqueria_guadalajara", "Taqueria Guadalajara", PlaceType.RESTAURANT, 37.7526, -122.4188, 4.3f, true, 1, 12),
                    AppPlace("el_buen_comer", "El Buen Comer", PlaceType.RESTAURANT, 37.7474, -122.4172, 4.5f, true, 1, 13),
                    AppPlace("taqueria_vallarta", "Taqueria Vallarta", PlaceType.RESTAURANT, 37.7502, -122.4180, 4.4f, true, 1, 12),
                    AppPlace("la_victoria", "La Victoria", PlaceType.RESTAURANT, 37.7515, -122.4178, 4.3f, true, 1, 11),
                    // Marina
                    AppPlace("tacolicious_marina", "Tacolicious Marina", PlaceType.RESTAURANT, 37.8025, -122.4352, 4.4f, true, 2, 22),
                    AppPlace("tacko", "Tacko", PlaceType.RESTAURANT, 37.7999, -122.4355, 4.3f, true, 2, 18),
                    // Castro
                    AppPlace("la_mediterranee", "La Mediterranee", PlaceType.RESTAURANT, 37.7615, -122.4347, 4.4f, true, 2, 20),
                    // Inner Sunset
                    AppPlace("nopalito_9th", "Nopalito 9th Avenue", PlaceType.RESTAURANT, 37.7639, -122.4660, 4.4f, true, 2, 24),
                    // Hayes Valley
                    AppPlace("panchitas", "Panchita's", PlaceType.RESTAURANT, 37.7756, -122.4244, 4.3f, true, 1, 16),
                    // North Beach
                    AppPlace("mamacitas", "Mamacita's", PlaceType.RESTAURANT, 37.8012, -122.4345, 4.3f, true, 2, 22),
                    // SoMa
                    AppPlace("tropisueno", "Tropisueno", PlaceType.RESTAURANT, 37.7786, -122.4179, 4.4f, true, 2, 19),
                    // Potrero Hill
                    AppPlace("chez_maman", "Chez Maman", PlaceType.RESTAURANT, 37.7618, -122.4009, 4.5f, true, 2, 20),
                    // Outer Mission
                    AppPlace("los_panchos", "Los Panchos", PlaceType.RESTAURANT, 37.7265, -122.4221, 4.4f, true, 1, 13),
                    // Bernal Heights
                    AppPlace("el_zocalo", "El Zocalo", PlaceType.RESTAURANT, 37.7390, -122.4214, 4.3f, true, 1, 15),
                    // Financial District
                    AppPlace("colibri", "Colibri Mexican Bistro", PlaceType.RESTAURANT, 37.7902, -122.4022, 4.4f, true, 2, 22),
                    // Dogpatch
                    AppPlace("mosto", "Mosto", PlaceType.RESTAURANT, 37.7589, -122.3914, 4.3f, true, 2, 24),
                    // Noe Valley
                    AppPlace("panchitas_noe", "Panchita's Noe", PlaceType.RESTAURANT, 37.7508, -122.4314, 4.3f, true, 1, 14),
                    // Excelsior
                    AppPlace("el_rincon_yucateco", "El Rincon Yucateco", PlaceType.RESTAURANT, 37.7248, -122.4289, 4.5f, true, 1, 14),
                    // Inner Richmond
                    AppPlace("gordo", "Gordo Taqueria", PlaceType.RESTAURANT, 37.7814, -122.4614, 4.3f, true, 1, 13),
                    // Additional Mexican - Dogpatch
                    AppPlace("papito_dogpatch", "Papito", PlaceType.RESTAURANT, 37.7585, -122.3895, 4.4f, true, 1, 15),
                    // Additional Mexican - Glen Park
                    AppPlace("sunflower_cafe", "Sunflower Cafe", PlaceType.RESTAURANT, 37.7347, -122.4338, 4.3f, true, 1, 14),
                    // Additional Mexican - Lower Haight
                    AppPlace("poc_chuc", "Poc Chuc", PlaceType.RESTAURANT, 37.7715, -122.4306, 4.4f, true, 2, 18),
                    // Additional Mexican - Inner Sunset
                    AppPlace("playa_azul", "Playa Azul", PlaceType.RESTAURANT, 37.7638, -122.4669, 4.3f, true, 1, 15),
                    // Additional Mexican - Outer Mission
                    AppPlace("el_metate", "El Metate", PlaceType.RESTAURANT, 37.7302, -122.4217, 4.5f, true, 1, 13),
                    // Additional Mexican - Portola
                    AppPlace("el_toreador", "El Toreador", PlaceType.RESTAURANT, 37.7220, -122.4065, 4.4f, true, 1, 14),
                    // Additional Mexican - Haight
                    AppPlace("taqueria_haight", "Taqueria Haight", PlaceType.RESTAURANT, 37.7698, -122.4482, 4.2f, true, 1, 12)
                )
            )
        }

        if (effectiveCuisines.contains("american")) {
            allRestaurants.addAll(
                listOf(
                    // Downtown / Hayes Valley - Famous spots with high review counts
                    AppPlace("zuni", "Zuni Café", PlaceType.RESTAURANT, 37.7750, -122.4223, 4.5f, true, 2, 30, reviewCount = 2500),
                    AppPlace("nopa", "NOPA", PlaceType.RESTAURANT, 37.7749, -122.4375, 4.4f, true, 2, 28, reviewCount = 1800),
                    AppPlace("jardiniere", "Jardiniere", PlaceType.RESTAURANT, 37.7773, -122.4221, 4.5f, true, 3, 40, reviewCount = 900),
                    AppPlace("absinthe", "Absinthe Brasserie", PlaceType.RESTAURANT, 37.7760, -122.4237, 4.4f, true, 2, 32, reviewCount = 1400),
                    AppPlace("rich_table", "Rich Table", PlaceType.RESTAURANT, 37.7765, -122.4229, 4.6f, true, 3, 45, reviewCount = 1200),
                    // Mission / Castro
                    AppPlace("foreign_cinema", "Foreign Cinema", PlaceType.RESTAURANT, 37.7540, -122.4191, 4.5f, true, 2, 35, reviewCount = 2000),
                    AppPlace("lazy_bear", "Lazy Bear", PlaceType.RESTAURANT, 37.7574, -122.4211, 4.6f, true, 3, 50, reviewCount = 1200),
                    AppPlace("bar_tartine", "Bar Tartine", PlaceType.RESTAURANT, 37.7562, -122.4217, 4.4f, true, 2, 30, reviewCount = 1100),
                    AppPlace("tartine_manufactory", "Tartine Manufactory", PlaceType.RESTAURANT, 37.7597, -122.4117, 4.5f, true, 2, 25, reviewCount = 3500),
                    AppPlace("frances", "Frances", PlaceType.RESTAURANT, 37.7610, -122.4350, 4.6f, true, 2, 32, reviewCount = 1400),
                    // Outer Sunset
                    AppPlace("outerlands", "Outerlands", PlaceType.RESTAURANT, 37.7609, -122.5096, 4.5f, true, 2, 25, reviewCount = 1800),
                    AppPlace("trouble_coffee", "Trouble Coffee", PlaceType.RESTAURANT, 37.7604, -122.5110, 4.6f, true, 1, 12, reviewCount = 900),
                    AppPlace("devils_teeth", "Devil's Teeth Baking", PlaceType.RESTAURANT, 37.7606, -122.5094, 4.7f, true, 1, 15, reviewCount = 1600),
                    // Marina / Cow Hollow
                    AppPlace("rose_pistola", "Rose's Cafe", PlaceType.RESTAURANT, 37.7995, -122.4308, 4.5f, true, 2, 28),
                    AppPlace("atelier_crenn", "Atelier Crenn", PlaceType.RESTAURANT, 37.7998, -122.4363, 4.8f, true, 4, 80),
                    AppPlace("greens", "Greens Restaurant", PlaceType.RESTAURANT, 37.8055, -122.4323, 4.3f, true, 2, 25),
                    // North Beach
                    AppPlace("north_beach_restaurant", "North Beach Restaurant", PlaceType.RESTAURANT, 37.8011, -122.4091, 4.4f, true, 2, 30),
                    // Financial District
                    AppPlace("boulevard", "Boulevard", PlaceType.RESTAURANT, 37.7952, -122.3939, 4.5f, true, 3, 42),
                    AppPlace("quince", "Quince", PlaceType.RESTAURANT, 37.7942, -122.4021, 4.7f, true, 4, 75),
                    AppPlace("benu", "Benu", PlaceType.RESTAURANT, 37.7783, -122.3954, 4.6f, true, 4, 70),
                    // SoMa
                    AppPlace("marlowe", "Marlowe", PlaceType.RESTAURANT, 37.7792, -122.4008, 4.4f, true, 2, 28),
                    AppPlace("farmtable", "Farmtable", PlaceType.RESTAURANT, 37.7821, -122.4053, 4.3f, true, 2, 24),
                    AppPlace("sentinel", "The Sentinel", PlaceType.RESTAURANT, 37.7875, -122.3995, 4.5f, true, 1, 16),
                    // Potrero Hill
                    AppPlace("chez_papa_bistrot", "Chez Papa Bistrot", PlaceType.RESTAURANT, 37.7596, -122.4011, 4.4f, true, 2, 26),
                    // Richmond
                    AppPlace("aziza", "Aziza", PlaceType.RESTAURANT, 37.7793, -122.4696, 4.6f, true, 3, 38, reviewCount = 800),
                    AppPlace("burma_superstar", "Burma Superstar", PlaceType.RESTAURANT, 37.7808, -122.4619, 4.5f, true, 2, 22, reviewCount = 3500),
                    // Nob Hill
                    AppPlace("swan_oyster", "Swan Oyster Depot", PlaceType.RESTAURANT, 37.7921, -122.4202, 4.6f, true, 2, 30),
                    // Russian Hill
                    AppPlace("seven_hills", "Seven Hills", PlaceType.RESTAURANT, 37.8042, -122.4180, 4.4f, true, 2, 32),
                    // Dogpatch
                    AppPlace("piccino", "Piccino", PlaceType.RESTAURANT, 37.7602, -122.3921, 4.5f, true, 2, 22),
                    AppPlace("serpentine", "Serpentine", PlaceType.RESTAURANT, 37.7607, -122.3937, 4.3f, true, 2, 24),
                    // Additional American - Bernal Heights
                    AppPlace("emmy_sausage", "Emmy's Spaghetti Shack", PlaceType.RESTAURANT, 37.7390, -122.4190, 4.4f, true, 2, 22),
                    AppPlace("mish_mish", "Mish Mish", PlaceType.RESTAURANT, 37.7395, -122.4095, 4.3f, true, 2, 20),
                    // Additional American - Glen Park
                    AppPlace("glen_park_cantina", "Gialina Pizzeria", PlaceType.RESTAURANT, 37.7348, -122.4338, 4.5f, true, 2, 24),
                    AppPlace("higher_ground", "Higher Ground Coffee", PlaceType.RESTAURANT, 37.7346, -122.4334, 4.4f, true, 1, 12),
                    // Additional American - Lower Haight
                    AppPlace("memphis_minnie", "Memphis Minnie's BBQ", PlaceType.RESTAURANT, 37.7720, -122.4301, 4.5f, true, 2, 22),
                    AppPlace("maven", "Maven", PlaceType.RESTAURANT, 37.7713, -122.4295, 4.4f, true, 2, 26),
                    // Additional American - Inner Sunset
                    AppPlace("park_chow", "Park Chow", PlaceType.RESTAURANT, 37.7650, -122.4657, 4.4f, true, 2, 22),
                    AppPlace("art_coffee", "Arizmendi 9th Ave", PlaceType.RESTAURANT, 37.7642, -122.4660, 4.6f, true, 1, 10),
                    // Additional American - Cole Valley
                    AppPlace("zazie_cole", "Zazie", PlaceType.RESTAURANT, 37.7650, -122.4479, 4.5f, true, 2, 24),
                    // Additional American - Noe Valley
                    AppPlace("contigo", "Contigo", PlaceType.RESTAURANT, 37.7510, -122.4318, 4.5f, true, 2, 28),
                    AppPlace("fresca_noe", "Fresca", PlaceType.RESTAURANT, 37.7502, -122.4322, 4.4f, true, 2, 24),
                    // Additional American - Potrero Hill
                    AppPlace("just_for_you", "Just For You Cafe", PlaceType.RESTAURANT, 37.7588, -122.3880, 4.5f, true, 1, 18),
                    AppPlace("farley_sf", "Farley's", PlaceType.RESTAURANT, 37.7605, -122.4015, 4.4f, true, 1, 12)
                )
            )
        }

        if (effectiveCuisines.contains("asian")) {
            allRestaurants.addAll(
                listOf(
                    // Chinatown
                    AppPlace("dragon_beaux", "Dragon Beaux", PlaceType.RESTAURANT, 37.7943, -122.4078, 4.4f, true, 2, 25),
                    AppPlace("r&g_lounge", "R&G Lounge", PlaceType.RESTAURANT, 37.7949, -122.4061, 4.3f, true, 2, 28),
                    AppPlace("z&y", "Z & Y", PlaceType.RESTAURANT, 37.7964, -122.4069, 4.5f, true, 2, 24),
                    AppPlace("koi_palace", "Koi Palace", PlaceType.RESTAURANT, 37.7944, -122.4072, 4.4f, true, 2, 26),
                    AppPlace("house_nanking", "House of Nanking", PlaceType.RESTAURANT, 37.7978, -122.4059, 4.3f, true, 1, 18, reviewCount = 2200),
                    AppPlace("yank_sing", "Yank Sing", PlaceType.RESTAURANT, 37.7901, -122.3972, 4.5f, true, 2, 30, reviewCount = 2000),
                    AppPlace("lai_hong_lounge", "Lai Hong Lounge", PlaceType.RESTAURANT, 37.7955, -122.4067, 4.3f, true, 2, 22),
                    AppPlace("great_eastern", "Great Eastern", PlaceType.RESTAURANT, 37.7952, -122.4076, 4.3f, true, 2, 24),
                    AppPlace("hakkasan", "Hakkasan", PlaceType.RESTAURANT, 37.7887, -122.3998, 4.4f, true, 3, 45),
                    AppPlace("golden_flower", "Golden Flower", PlaceType.RESTAURANT, 37.7956, -122.4068, 4.2f, true, 2, 23),
                    // Japantown
                    AppPlace("mensho_tokyo", "Mensho Tokyo", PlaceType.RESTAURANT, 37.7848, -122.4305, 4.5f, true, 1, 18),
                    AppPlace("hinodeya", "Hinodeya", PlaceType.RESTAURANT, 37.7850, -122.4303, 4.4f, true, 1, 16),
                    AppPlace("waraku", "Waraku", PlaceType.RESTAURANT, 37.7853, -122.4307, 4.3f, true, 2, 22),
                    AppPlace("benkyodo", "Benkyodo", PlaceType.RESTAURANT, 37.7851, -122.4304, 4.5f, true, 1, 12),
                    AppPlace("izakaya_yuzuki", "Izakaya Yuzuki", PlaceType.RESTAURANT, 37.7854, -122.4309, 4.4f, true, 2, 24),
                    // Mission
                    AppPlace("rintaro", "Rintaro", PlaceType.RESTAURANT, 37.7600, -122.4194, 4.5f, true, 2, 30),
                    AppPlace("burma_love", "Burma Love", PlaceType.RESTAURANT, 37.7602, -122.4199, 4.4f, true, 2, 24),
                    AppPlace("ramen_yamadaya", "Ramen Yamadaya", PlaceType.RESTAURANT, 37.7495, -122.4187, 4.4f, true, 1, 16),
                    // Richmond
                    AppPlace("ton_kiang", "Ton Kiang", PlaceType.RESTAURANT, 37.7815, -122.4606, 4.4f, true, 2, 26),
                    AppPlace("thanh_long", "Thanh Long", PlaceType.RESTAURANT, 37.7760, -122.4937, 4.5f, true, 2, 35),
                    AppPlace("chapeau", "Chapeau!", PlaceType.RESTAURANT, 37.7802, -122.4632, 4.6f, true, 2, 32),
                    AppPlace("dragon_well", "Dragon Well", PlaceType.RESTAURANT, 37.7811, -122.4620, 4.3f, true, 2, 22),
                    AppPlace("koo", "Koo", PlaceType.RESTAURANT, 37.7778, -122.4678, 4.4f, true, 2, 24),
                    // Inner Sunset
                    AppPlace("ebisu", "Ebisu", PlaceType.RESTAURANT, 37.7640, -122.4681, 4.5f, true, 2, 28),
                    AppPlace("izakaya_sozai", "Izakaya Sozai", PlaceType.RESTAURANT, 37.7634, -122.4674, 4.4f, true, 1, 20),
                    AppPlace("sushi_toni", "Sushi Toni", PlaceType.RESTAURANT, 37.7623, -122.4653, 4.3f, true, 2, 25),
                    // SoMa
                    AppPlace("okaeri", "Okaeri", PlaceType.RESTAURANT, 37.7793, -122.4012, 4.4f, true, 2, 26),
                    AppPlace("ramen_underground", "Ramen Underground", PlaceType.RESTAURANT, 37.7848, -122.4009, 4.3f, true, 1, 14),
                    // Hayes Valley
                    AppPlace("souvla", "Souvla", PlaceType.RESTAURANT, 37.7756, -122.4248, 4.5f, true, 1, 16),
                    AppPlace("namu_gaji", "Namu Gaji", PlaceType.RESTAURANT, 37.7763, -122.4244, 4.4f, true, 2, 22),
                    // Additional Asian - Outer Richmond
                    AppPlace("good_luck_dim", "Good Luck Dim Sum", PlaceType.RESTAURANT, 37.7820, -122.4715, 4.5f, true, 1, 12),
                    AppPlace("kingdom_dumpling", "Kingdom of Dumpling", PlaceType.RESTAURANT, 37.7818, -122.4695, 4.4f, true, 1, 14),
                    AppPlace("spices_richmond", "Spices!", PlaceType.RESTAURANT, 37.7802, -122.4632, 4.5f, true, 1, 16),
                    // Additional Asian - Outer Sunset
                    AppPlace("san_tung", "San Tung", PlaceType.RESTAURANT, 37.7644, -122.4689, 4.6f, true, 1, 18),
                    AppPlace("old_mandarin", "Old Mandarin Islamic", PlaceType.RESTAURANT, 37.7605, -122.4700, 4.5f, true, 1, 16),
                    AppPlace("hook_fish", "Hook Fish Co", PlaceType.RESTAURANT, 37.7608, -122.5092, 4.6f, true, 2, 20),
                    // Additional Asian - Portola
                    AppPlace("lers_ros", "Lers Ros Thai", PlaceType.RESTAURANT, 37.7290, -122.4060, 4.5f, true, 1, 16),
                    // Additional Asian - Lower Haight
                    AppPlace("thep_phanom_haight", "Thep Phanom", PlaceType.RESTAURANT, 37.7732, -122.4296, 4.4f, true, 2, 22),
                    // Additional Asian - Castro
                    AppPlace("sushi_zone", "Sushi Zone", PlaceType.RESTAURANT, 37.7612, -122.4356, 4.3f, true, 2, 24),
                    // Additional Asian - Nob Hill
                    AppPlace("oriental_pearl", "Oriental Pearl", PlaceType.RESTAURANT, 37.7915, -122.4105, 4.3f, true, 2, 22),
                    // Additional Asian - Tenderloin
                    AppPlace("bodega_sf", "Bodega", PlaceType.RESTAURANT, 37.7843, -122.4105, 4.4f, true, 2, 24),
                    AppPlace("tu_lan", "Tu Lan", PlaceType.RESTAURANT, 37.7812, -122.4105, 4.3f, true, 1, 12)
                )
            )
        }

        if (effectiveCuisines.contains("seafood")) {
            allRestaurants.addAll(
                listOf(
                    // Fisherman's Wharf
                    AppPlace("scomas", "Scoma's", PlaceType.RESTAURANT, 37.8095, -122.4185, 4.3f, true, 3, 45),
                    AppPlace("alioto", "Alioto's", PlaceType.RESTAURANT, 37.8087, -122.4180, 4.2f, true, 3, 42),
                    AppPlace("franciscan", "Franciscan Crab Restaurant", PlaceType.RESTAURANT, 37.8082, -122.4174, 4.3f, true, 3, 40),
                    AppPlace("crab_house", "Crab House at Pier 39", PlaceType.RESTAURANT, 37.8087, -122.4098, 4.2f, true, 3, 38),
                    AppPlace("fog_harbor", "Fog Harbor Fish House", PlaceType.RESTAURANT, 37.8089, -122.4103, 4.3f, true, 3, 35),
                    AppPlace("pier_market", "Pier Market Seafood", PlaceType.RESTAURANT, 37.8084, -122.4175, 4.2f, true, 3, 36),
                    // Nob Hill / Russian Hill
                    AppPlace("swan_oyster", "Swan Oyster Depot", PlaceType.RESTAURANT, 37.7921, -122.4202, 4.6f, true, 2, 30),
                    AppPlace("seven_seas", "Seven Seas", PlaceType.RESTAURANT, 37.8011, -122.4172, 4.3f, true, 2, 28),
                    // Financial District / Embarcadero
                    AppPlace("tadich_grill", "Tadich Grill", PlaceType.RESTAURANT, 37.7941, -122.3988, 4.4f, true, 2, 32),
                    AppPlace("waterbar", "Waterbar", PlaceType.RESTAURANT, 37.7927, -122.3897, 4.4f, true, 3, 42),
                    AppPlace("hog_island", "Hog Island Oyster Co", PlaceType.RESTAURANT, 37.7956, -122.3935, 4.5f, true, 2, 30),
                    AppPlace("anchor_oyster", "Anchor Oyster Bar", PlaceType.RESTAURANT, 37.7610, -122.4353, 4.5f, true, 2, 28),
                    AppPlace("farallon", "Farallon", PlaceType.RESTAURANT, 37.7894, -122.4065, 4.4f, true, 3, 45),
                    AppPlace("bar_crudo", "Bar Crudo", PlaceType.RESTAURANT, 37.7738, -122.4373, 4.4f, true, 2, 32),
                    // Marina
                    AppPlace("blue_mermaid", "Blue Mermaid", PlaceType.RESTAURANT, 37.8084, -122.4162, 4.2f, true, 2, 26),
                    AppPlace("a16_marina", "A16", PlaceType.RESTAURANT, 37.7999, -122.4358, 4.5f, true, 2, 32),
                    // Outer Richmond
                    AppPlace("thanh_long_richmond", "Thanh Long", PlaceType.RESTAURANT, 37.7760, -122.4937, 4.5f, true, 2, 35),
                    AppPlace("ton_kiang_richmond", "Ton Kiang", PlaceType.RESTAURANT, 37.7815, -122.4606, 4.4f, true, 2, 26),
                    // Potrero Hill
                    AppPlace("chez_maman_potrero", "Chez Maman", PlaceType.RESTAURANT, 37.7618, -122.4009, 4.5f, true, 2, 24),
                    // Dogpatch
                    AppPlace("serpentine_dogpatch", "Serpentine", PlaceType.RESTAURANT, 37.7607, -122.3937, 4.3f, true, 2, 24),
                    AppPlace("the_ramp", "The Ramp", PlaceType.RESTAURANT, 37.7539, -122.3878, 4.3f, true, 2, 22),
                    // SoMa
                    AppPlace("yank_sing_soma", "Yank Sing Rincon", PlaceType.RESTAURANT, 37.7901, -122.3972, 4.5f, true, 2, 30),
                    // Hayes Valley
                    AppPlace("absinthe_hayes", "Absinthe Brasserie", PlaceType.RESTAURANT, 37.7760, -122.4237, 4.4f, true, 2, 32),
                    // Castro
                    AppPlace("anchor_castro", "Anchor Oyster Bar Castro", PlaceType.RESTAURANT, 37.7610, -122.4353, 4.5f, true, 2, 28),
                    // Inner Sunset
                    AppPlace("ebisu_sunset", "Ebisu", PlaceType.RESTAURANT, 37.7640, -122.4681, 4.5f, true, 2, 28),
                    // Mission
                    AppPlace("locanda_mission", "Locanda", PlaceType.RESTAURANT, 37.7590, -122.4212, 4.5f, true, 2, 30),
                    // Pacific Heights
                    AppPlace("spruce", "Spruce", PlaceType.RESTAURANT, 37.7886, -122.4287, 4.5f, true, 3, 40),
                    // North Beach
                    AppPlace("sotto_mare_nb", "Sotto Mare", PlaceType.RESTAURANT, 37.8008, -122.4102, 4.5f, true, 2, 35),
                    // Noe Valley
                    AppPlace("incanto", "Incanto", PlaceType.RESTAURANT, 37.7408, -122.4291, 4.4f, true, 2, 32),
                    // Lower Haight
                    AppPlace("thep_phanom", "Thep Phanom", PlaceType.RESTAURANT, 37.7732, -122.4296, 4.4f, true, 2, 26)
                )
            )
        }

        if (effectiveCuisines.contains("vegetarian")) {
            allRestaurants.addAll(
                listOf(
                    // Marina / Fort Mason
                    AppPlace("greens", "Greens Restaurant", PlaceType.RESTAURANT, 37.8055, -122.4323, 4.3f, true, 2, 25),
                    // Mission
                    AppPlace("gracias_madre", "Gracias Madre", PlaceType.RESTAURANT, 37.7622, -122.4245, 4.4f, true, 2, 28),
                    AppPlace("millennium", "Millennium", PlaceType.RESTAURANT, 37.7878, -122.4097, 4.5f, true, 2, 30),
                    AppPlace("shizen", "Shizen Vegan Sushi", PlaceType.RESTAURANT, 37.7596, -122.4202, 4.6f, true, 2, 26),
                    AppPlace("encuentro", "Encuentro", PlaceType.RESTAURANT, 37.7610, -122.4249, 4.3f, true, 2, 22),
                    // Hayes Valley
                    AppPlace("namu_gaji_hayes", "Namu Gaji", PlaceType.RESTAURANT, 37.7763, -122.4244, 4.4f, true, 2, 22),
                    AppPlace("souvla_hayes", "Souvla", PlaceType.RESTAURANT, 37.7756, -122.4248, 4.5f, true, 1, 16),
                    // Inner Sunset
                    AppPlace("outerlands_sunset", "Outerlands", PlaceType.RESTAURANT, 37.7609, -122.5096, 4.5f, true, 2, 25),
                    AppPlace("zazie", "Zazie", PlaceType.RESTAURANT, 37.7650, -122.4479, 4.5f, true, 2, 24),
                    AppPlace("arizmendi", "Arizmendi Bakery", PlaceType.RESTAURANT, 37.7642, -122.4660, 4.6f, true, 1, 12),
                    // Richmond
                    AppPlace("burma_superstar_richmond", "Burma Superstar", PlaceType.RESTAURANT, 37.7808, -122.4619, 4.5f, true, 2, 22),
                    AppPlace("yamo", "Yamo", PlaceType.RESTAURANT, 37.7816, -122.4612, 4.4f, true, 1, 14),
                    // Financial District
                    AppPlace("source", "The Source", PlaceType.RESTAURANT, 37.7878, -122.4097, 4.3f, true, 2, 20),
                    // SoMa
                    AppPlace("loving_hut", "Loving Hut", PlaceType.RESTAURANT, 37.7815, -122.4103, 4.2f, true, 1, 15),
                    AppPlace("plant", "PLANT Cafe Organic", PlaceType.RESTAURANT, 37.7896, -122.3959, 4.3f, true, 2, 18),
                    // Castro
                    AppPlace("herbivore", "Herbivore", PlaceType.RESTAURANT, 37.7615, -122.4347, 4.3f, true, 1, 16),
                    // Potrero Hill
                    AppPlace("chez_maman_potrero_veg", "Chez Maman", PlaceType.RESTAURANT, 37.7618, -122.4009, 4.5f, true, 2, 24),
                    // Noe Valley
                    AppPlace("firefly", "Firefly", PlaceType.RESTAURANT, 37.7483, -122.4314, 4.4f, true, 2, 26),
                    // Haight-Ashbury
                    AppPlace("cha_ya", "Cha-Ya Vegetarian", PlaceType.RESTAURANT, 37.7695, -122.4478, 4.5f, true, 2, 20),
                    AppPlace("golden_era", "Golden Era Vegan", PlaceType.RESTAURANT, 37.7718, -122.4341, 4.3f, true, 1, 14),
                    // Tenderloin
                    AppPlace("saigon_sandwich", "Saigon Sandwich", PlaceType.RESTAURANT, 37.7841, -122.4112, 4.6f, true, 1, 10),
                    // Inner Richmond
                    AppPlace("shangri_la", "Shangri-La Vegetarian", PlaceType.RESTAURANT, 37.7804, -122.4639, 4.4f, true, 1, 16),
                    // Japantown
                    AppPlace("waraku_veg", "Waraku", PlaceType.RESTAURANT, 37.7853, -122.4307, 4.3f, true, 2, 22),
                    // Excelsior
                    AppPlace("vegan_picnic", "Vegan Picnic", PlaceType.RESTAURANT, 37.7243, -122.4300, 4.5f, true, 1, 14),
                    // Dogpatch
                    AppPlace("piccino_veg", "Piccino", PlaceType.RESTAURANT, 37.7602, -122.3921, 4.5f, true, 2, 22),
                    // Lower Haight
                    AppPlace("rosamunde", "Rosamunde Sausage Grill", PlaceType.RESTAURANT, 37.7723, -122.4287, 4.4f, true, 1, 14),
                    // North Beach
                    AppPlace("mama_sf_veg", "Mama's on Washington Square", PlaceType.RESTAURANT, 37.7999, -122.4105, 4.6f, true, 2, 20),
                    // Marina
                    AppPlace("greens_to_go", "Greens To Go", PlaceType.RESTAURANT, 37.8056, -122.4322, 4.4f, true, 1, 15),
                    // Bernal Heights
                    AppPlace("liberty_cafe", "Liberty Cafe", PlaceType.RESTAURANT, 37.7397, -122.4186, 4.5f, true, 2, 22),
                    // Glen Park
                    AppPlace("gialina", "Gialina", PlaceType.RESTAURANT, 37.7380, -122.4338, 4.5f, true, 2, 24)
                )
            )
        }

        return allRestaurants.applyTypeDefaults()
    }

    suspend fun searchWaterfront(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for waterfront (city=$currentCityId, remote=$USE_REMOTE_DATA)")
        
        if (USE_REMOTE_DATA) {
            val places = getPlacesByType(PlaceType.WATERFRONT)
            Log.d("PlacesRepository", "Found ${places.size} waterfront locations from remote")
            return places
        }
        
        return getMockWaterfront()
    }
    
    private fun getMockWaterfront(): List<AppPlace> {
        return listOf<AppPlace>(
            // Northern Waterfront
            AppPlace("fishermans_wharf", "Fisherman's Wharf", PlaceType.WATERFRONT, 37.8080, -122.4177, 4.4f, true, 0, 0),
            AppPlace("pier39", "Pier 39", PlaceType.WATERFRONT, 37.8087, -122.4098, 4.3f, true, 0, 0),
            AppPlace("ghirardelli_square", "Ghirardelli Square", PlaceType.WATERFRONT, 37.8058, -122.4227, 4.4f, true, 0, 0),
            AppPlace("aquatic_park", "Aquatic Park", PlaceType.WATERFRONT, 37.8086, -122.4237, 4.6f, true, 0, 0),
            AppPlace("maritime_historic", "SF Maritime National Historic Park", PlaceType.WATERFRONT, 37.8087, -122.4230, 4.5f, true, 0, 0),
            AppPlace("hyde_street_pier", "Hyde Street Pier", PlaceType.WATERFRONT, 37.8089, -122.4217, 4.5f, true, 0, 5),
            AppPlace("fort_mason_pier", "Fort Mason Piers", PlaceType.WATERFRONT, 37.8067, -122.4304, 4.4f, true, 0, 0),
            AppPlace("marina_harbor", "Marina Harbor", PlaceType.WATERFRONT, 37.8049, -122.4397, 4.5f, true, 0, 0),
            AppPlace("wave_organ", "Wave Organ", PlaceType.WATERFRONT, 37.8068, -122.4366, 4.6f, true, 0, 0),
            AppPlace("yacht_harbor", "SF Yacht Harbor", PlaceType.WATERFRONT, 37.8051, -122.4385, 4.4f, true, 0, 0),
            // Eastern Waterfront
            AppPlace("embarcadero", "Embarcadero", PlaceType.WATERFRONT, 37.7955, -122.3937, 4.5f, true, 0, 0),
            AppPlace("ferry_building", "Ferry Building", PlaceType.WATERFRONT, 37.7956, -122.3935, 4.6f, true, 0, 0),
            AppPlace("rincon_park", "Rincon Park", PlaceType.WATERFRONT, 37.7917, -122.3886, 4.5f, true, 0, 0),
            AppPlace("cupids_span", "Cupid's Span", PlaceType.WATERFRONT, 37.7917, -122.3898, 4.3f, true, 0, 0),
            AppPlace("pier7", "Pier 7", PlaceType.WATERFRONT, 37.8004, -122.3972, 4.5f, true, 0, 0),
            AppPlace("pier14", "Pier 14", PlaceType.WATERFRONT, 37.7944, -122.3914, 4.6f, true, 0, 0),
            AppPlace("pier15", "Pier 15 (Exploratorium)", PlaceType.WATERFRONT, 37.8014, -122.3975, 4.5f, true, 0, 0),
            AppPlace("pier24", "Pier 24 Photography", PlaceType.WATERFRONT, 37.7887, -122.3897, 4.4f, true, 0, 0),
            // Southern Waterfront
            AppPlace("mission_bay", "Mission Bay Park", PlaceType.WATERFRONT, 37.7706, -122.3911, 4.4f, true, 0, 0),
            AppPlace("oracle_park_waterfront", "Oracle Park Waterfront", PlaceType.WATERFRONT, 37.7785, -122.3893, 4.6f, true, 0, 0),
            AppPlace("mission_creek", "Mission Creek Park", PlaceType.WATERFRONT, 37.7674, -122.3937, 4.3f, true, 0, 0),
            AppPlace("warm_water_cove", "Warm Water Cove", PlaceType.WATERFRONT, 37.7445, -122.3741, 4.4f, true, 0, 0),
            AppPlace("heron_head", "Heron's Head Park", PlaceType.WATERFRONT, 37.7276, -122.3744, 4.5f, true, 0, 0),
            AppPlace("india_basin", "India Basin Shoreline Park", PlaceType.WATERFRONT, 37.7261, -122.3767, 4.3f, true, 0, 0),
            AppPlace("hunters_point", "Hunters Point Shoreline", PlaceType.WATERFRONT, 37.7298, -122.3698, 4.2f, true, 0, 0),
            // Western Waterfront
            AppPlace("china_beach", "China Beach", PlaceType.WATERFRONT, 37.7900, -122.4902, 4.6f, true, 0, 0),
            AppPlace("lands_end_trail", "Lands End Coastal Trail", PlaceType.WATERFRONT, 37.7849, -122.5080, 4.8f, true, 0, 0),
            AppPlace("cliff_house_view", "Cliff House Viewpoint", PlaceType.WATERFRONT, 37.7783, -122.5139, 4.5f, true, 0, 0),
            AppPlace("ocean_beach_north", "Ocean Beach North", PlaceType.WATERFRONT, 37.7651, -122.5104, 4.6f, true, 0, 0),
            AppPlace("ocean_beach_south", "Ocean Beach South", PlaceType.WATERFRONT, 37.7350, -122.5110, 4.5f, true, 0, 0)
        ).applyTypeDefaults()
    }

    suspend fun searchHistoricSites(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for historic sites (city=$currentCityId, remote=$USE_REMOTE_DATA)")
        
        if (USE_REMOTE_DATA) {
            val places = getPlacesByType(PlaceType.HISTORIC_SITE)
            Log.d("PlacesRepository", "Found ${places.size} historic sites from remote")
            return places
        }
        
        return getMockHistoricSites()
    }
    
    private fun getMockHistoricSites(): List<AppPlace> {
        return listOf<AppPlace>(
            // Mission District
            AppPlace("mission_dolores", "Mission Dolores", PlaceType.HISTORIC_SITE, 37.7637, -122.4268, 4.4f, true, 1, 10),
            AppPlace("mission_dolores_park", "Mission Dolores Basilica", PlaceType.HISTORIC_SITE, 37.7639, -122.4267, 4.5f, true, 1, 5),
            // Nob Hill / Downtown
            AppPlace("cable_car_museum", "Cable Car Museum", PlaceType.HISTORIC_SITE, 37.7947, -122.4114, 4.6f, true, 1, 15),
            AppPlace("grace_cathedral", "Grace Cathedral", PlaceType.HISTORIC_SITE, 37.7918, -122.4125, 4.6f, true, 1, 5),
            AppPlace("fairmont", "Fairmont Hotel (Historic)", PlaceType.HISTORIC_SITE, 37.7923, -122.4104, 4.5f, true, 1, 0),
            AppPlace("huntington_park", "Huntington Park", PlaceType.HISTORIC_SITE, 37.7922, -122.4120, 4.5f, true, 0, 0),
            // Presidio
            AppPlace("presidio", "Presidio of San Francisco", PlaceType.HISTORIC_SITE, 37.7989, -122.4662, 4.7f, true, 0, 0),
            AppPlace("fort_point", "Fort Point National Historic Site", PlaceType.HISTORIC_SITE, 37.8108, -122.4764, 4.7f, true, 0, 5),
            AppPlace("presidio_officers_club", "Presidio Officers' Club", PlaceType.HISTORIC_SITE, 37.7991, -122.4665, 4.4f, true, 1, 5),
            AppPlace("pet_cemetery", "Presidio Pet Cemetery", PlaceType.HISTORIC_SITE, 37.7983, -122.4671, 4.5f, true, 0, 0),
            AppPlace("batteries_to_bluffs", "Batteries to Bluffs Trail", PlaceType.HISTORIC_SITE, 37.7975, -122.4814, 4.6f, true, 0, 0),
            // Telegraph Hill / North Beach
            AppPlace("coit_tower", "Coit Tower", PlaceType.HISTORIC_SITE, 37.8024, -122.4058, 4.7f, true, 1, 10),
            AppPlace("telegraph_hill_stairs", "Filbert Steps", PlaceType.HISTORIC_SITE, 37.8018, -122.4064, 4.7f, true, 0, 0),
            AppPlace("saints_peter_paul", "Sts Peter & Paul Church", PlaceType.HISTORIC_SITE, 37.8003, -122.4107, 4.6f, true, 0, 0),
            // Financial District
            AppPlace("transamerica", "Transamerica Pyramid", PlaceType.HISTORIC_SITE, 37.7952, -122.4028, 4.5f, true, 0, 0),
            AppPlace("old_mint", "Old San Francisco Mint", PlaceType.HISTORIC_SITE, 37.7791, -122.4063, 4.3f, true, 1, 10),
            AppPlace("ferry_building_historic", "Ferry Building (Historic)", PlaceType.HISTORIC_SITE, 37.7956, -122.3935, 4.6f, true, 0, 0),
            AppPlace("jackson_square", "Jackson Square Historic District", PlaceType.HISTORIC_SITE, 37.7965, -122.4019, 4.4f, true, 0, 0),
            // Civic Center
            AppPlace("city_hall", "San Francisco City Hall", PlaceType.HISTORIC_SITE, 37.7793, -122.4193, 4.7f, true, 1, 10),
            AppPlace("war_memorial", "War Memorial Opera House", PlaceType.HISTORIC_SITE, 37.7788, -122.4213, 4.5f, true, 1, 15),
            AppPlace("sf_main_library", "SF Main Library", PlaceType.HISTORIC_SITE, 37.7799, -122.4158, 4.5f, true, 0, 0),
            AppPlace("asian_art_museum_building", "Asian Art Museum Building", PlaceType.HISTORIC_SITE, 37.7803, -122.4158, 4.5f, true, 0, 0),
            // Pacific Heights / Marina
            AppPlace("palace_fine_arts", "Palace of Fine Arts", PlaceType.HISTORIC_SITE, 37.8033, -122.4477, 4.7f, true, 0, 0),
            AppPlace("haas_lilienthal", "Haas-Lilienthal House", PlaceType.HISTORIC_SITE, 37.7912, -122.4253, 4.5f, true, 1, 15),
            AppPlace("flood_mansion", "Flood Mansion", PlaceType.HISTORIC_SITE, 37.7926, -122.4280, 4.3f, true, 0, 0),
            AppPlace("octagon_house", "Octagon House", PlaceType.HISTORIC_SITE, 37.8004, -122.4309, 4.3f, true, 1, 10),
            // Richmond / Sunset
            AppPlace("sutro_baths", "Sutro Baths Ruins", PlaceType.HISTORIC_SITE, 37.7805, -122.5135, 4.6f, true, 0, 0),
            AppPlace("cliff_house", "Cliff House", PlaceType.HISTORIC_SITE, 37.7783, -122.5139, 4.4f, true, 0, 0),
            AppPlace("legion_honor_building", "Legion of Honor Building", PlaceType.HISTORIC_SITE, 37.7849, -122.5001, 4.6f, true, 0, 0),
            // Castro
            AppPlace("castro_theatre", "Castro Theatre", PlaceType.HISTORIC_SITE, 37.7621, -122.4349, 4.6f, true, 1, 12),
            AppPlace("harvey_milk_plaza", "Harvey Milk Plaza", PlaceType.HISTORIC_SITE, 37.7620, -122.4348, 4.5f, true, 0, 0),
            // Golden Gate Park
            AppPlace("conservatory_flowers", "Conservatory of Flowers", PlaceType.HISTORIC_SITE, 37.7727, -122.4608, 4.6f, true, 1, 10),
            AppPlace("dutch_windmill", "Dutch Windmill", PlaceType.HISTORIC_SITE, 37.7711, -122.5093, 4.5f, true, 0, 0),
            AppPlace("golden_gate_park_carousel", "Golden Gate Park Carousel", PlaceType.HISTORIC_SITE, 37.7700, -122.4734, 4.5f, true, 1, 5),
            // Chinatown
            AppPlace("chinatown_gate", "Chinatown Gate", PlaceType.HISTORIC_SITE, 37.7901, -122.4056, 4.5f, true, 0, 0),
            AppPlace("tin_how_temple", "Tin How Temple", PlaceType.HISTORIC_SITE, 37.7956, -122.4066, 4.4f, true, 0, 0),
            // Various
            AppPlace("golden_gate_bridge", "Golden Gate Bridge", PlaceType.HISTORIC_SITE, 37.8199, -122.4783, 4.8f, true, 0, 0),
            AppPlace("alcatraz", "Alcatraz Island", PlaceType.HISTORIC_SITE, 37.8270, -122.4230, 4.7f, true, 3, 40),
            AppPlace("angel_island", "Angel Island State Park", PlaceType.HISTORIC_SITE, 37.8619, -122.4326, 4.7f, true, 2, 20)
        ).applyTypeDefaults()
    }

    suspend fun searchShopping(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for shopping (city=$currentCityId, remote=$USE_REMOTE_DATA)")
        
        if (USE_REMOTE_DATA) {
            val places = getPlacesByType(PlaceType.SHOPPING)
            Log.d("PlacesRepository", "Found ${places.size} shopping locations from remote")
            return places
        }
        
        return getMockShopping()
    }
    
    private fun getMockShopping(): List<AppPlace> {
        return listOf<AppPlace>(
            // Downtown / Union Square
            AppPlace("union_square", "Union Square", PlaceType.SHOPPING, 37.7879, -122.4075, 4.3f, true, 0, 0),
            AppPlace("westfield", "Westfield San Francisco Centre", PlaceType.SHOPPING, 37.7845, -122.4062, 4.3f, true, 0, 0),
            AppPlace("macys_sf", "Macy's Union Square", PlaceType.SHOPPING, 37.7875, -122.4073, 4.2f, true, 0, 0),
            AppPlace("saks_fifth", "Saks Fifth Avenue", PlaceType.SHOPPING, 37.7888, -122.4069, 4.3f, true, 0, 0),
            AppPlace("neiman_marcus", "Neiman Marcus", PlaceType.SHOPPING, 37.7883, -122.4076, 4.2f, true, 0, 0),
            // Embarcadero / Financial District
            AppPlace("ferry_building", "Ferry Building Marketplace", PlaceType.SHOPPING, 37.7956, -122.3935, 4.6f, true, 0, 0),
            AppPlace("embarcadero_center", "Embarcadero Center", PlaceType.SHOPPING, 37.7950, -122.3985, 4.3f, true, 0, 0),
            AppPlace("crocker_galleria", "Crocker Galleria", PlaceType.SHOPPING, 37.7896, -122.4023, 4.2f, true, 0, 0),
            // Fisherman's Wharf
            AppPlace("ghirardelli", "Ghirardelli Square", PlaceType.SHOPPING, 37.8058, -122.4227, 4.4f, true, 0, 0),
            AppPlace("pier39_shops", "Pier 39 Shops", PlaceType.SHOPPING, 37.8087, -122.4098, 4.2f, true, 0, 0),
            AppPlace("anchorage", "The Anchorage", PlaceType.SHOPPING, 37.8074, -122.4190, 4.1f, true, 0, 0),
            AppPlace("cannery", "The Cannery", PlaceType.SHOPPING, 37.8064, -122.4205, 4.2f, true, 0, 0),
            // Hayes Valley
            AppPlace("hayes_valley", "Hayes Valley Shopping District", PlaceType.SHOPPING, 37.7760, -122.4240, 4.5f, true, 0, 0),
            AppPlace("hayes_street_shops", "Hayes Street Boutiques", PlaceType.SHOPPING, 37.7755, -122.4245, 4.5f, true, 0, 0),
            // Mission District
            AppPlace("valencia_street", "Valencia Street Shops", PlaceType.SHOPPING, 37.7600, -122.4216, 4.4f, true, 0, 0),
            AppPlace("mission_street_shops", "Mission Street Shopping", PlaceType.SHOPPING, 37.7520, -122.4180, 4.2f, true, 0, 0),
            // Castro
            AppPlace("castro_street", "Castro Street Shopping", PlaceType.SHOPPING, 37.7615, -122.4350, 4.4f, true, 0, 0),
            AppPlace("market_castro", "Market & Castro Shops", PlaceType.SHOPPING, 37.7620, -122.4348, 4.3f, true, 0, 0),
            // Haight-Ashbury
            AppPlace("haight_street", "Haight Street Shopping", PlaceType.SHOPPING, 37.7700, -122.4485, 4.5f, true, 0, 0),
            AppPlace("upper_haight", "Upper Haight Boutiques", PlaceType.SHOPPING, 37.7710, -122.4460, 4.4f, true, 0, 0),
            // Fillmore
            AppPlace("fillmore_street", "Fillmore Street Shopping", PlaceType.SHOPPING, 37.7865, -122.4331, 4.4f, true, 0, 0),
            AppPlace("pacific_heights_shops", "Pacific Heights Shops", PlaceType.SHOPPING, 37.7930, -122.4310, 4.3f, true, 0, 0),
            // Chestnut Street (Marina)
            AppPlace("chestnut_street", "Chestnut Street Shopping", PlaceType.SHOPPING, 37.8020, -122.4340, 4.4f, true, 0, 0),
            AppPlace("marina_district_shops", "Marina District Boutiques", PlaceType.SHOPPING, 37.8010, -122.4365, 4.3f, true, 0, 0),
            // Union Street (Cow Hollow)
            AppPlace("union_street", "Union Street Shopping", PlaceType.SHOPPING, 37.7980, -122.4295, 4.4f, true, 0, 0),
            // Polk Street
            AppPlace("polk_street", "Polk Street Shopping", PlaceType.SHOPPING, 37.7960, -122.4200, 4.3f, true, 0, 0),
            // Clement Street (Inner Richmond)
            AppPlace("clement_street", "Clement Street Shopping", PlaceType.SHOPPING, 37.7825, -122.4620, 4.4f, true, 0, 0),
            AppPlace("new_chinatown", "New Chinatown Clement", PlaceType.SHOPPING, 37.7815, -122.4630, 4.3f, true, 0, 0),
            // Japantown
            AppPlace("japan_center", "Japan Center", PlaceType.SHOPPING, 37.7850, -122.4305, 4.4f, true, 0, 0),
            AppPlace("japantown_shops", "Japantown Shopping", PlaceType.SHOPPING, 37.7853, -122.4307, 4.3f, true, 0, 0),
            // Chinatown
            AppPlace("chinatown_shops", "Chinatown Shopping District", PlaceType.SHOPPING, 37.7955, -122.4066, 4.3f, true, 0, 0),
            AppPlace("grant_avenue", "Grant Avenue Chinatown", PlaceType.SHOPPING, 37.7945, -122.4072, 4.2f, true, 0, 0),
            // Noe Valley
            AppPlace("24th_street_noe", "24th Street Noe Valley", PlaceType.SHOPPING, 37.7510, -122.4320, 4.5f, true, 0, 0)
        ).applyTypeDefaults()
    }

    suspend fun searchNightlife(nightlifeTypes: List<String>): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for nightlife (city=$currentCityId, types=$nightlifeTypes, remote=$USE_REMOTE_DATA)")
        
        if (USE_REMOTE_DATA) {
            val allNightlife = getPlacesByType(PlaceType.NIGHTLIFE)
            
            // If no specific types requested, return all nightlife
            if (nightlifeTypes.isEmpty()) {
                Log.d("PlacesRepository", "Found ${allNightlife.size} nightlife venues from remote (no filter)")
                return allNightlife
            }
            
            // Map user-facing types to database category names
            val categoryMap = mapOf(
                "bars" to listOf("bar"),
                "cocktail_bars" to listOf("cocktail_bar"),
                "clubs" to listOf("club", "night_club"),
                "live_music" to listOf("live_music"),
                "dive_bars" to listOf("dive_bar"),
                "rooftop_bars" to listOf("rooftop_bar"),
                "wine_bars" to listOf("wine_bar"),
                "sports_bars" to listOf("sports_bar"),
                "breweries" to listOf("brewery"),
                "karaoke" to listOf("karaoke")
            )
            
            // Get all matching categories
            val matchingCategories = nightlifeTypes.flatMap { type ->
                categoryMap[type] ?: listOf(type)
            }.toSet()
            
            // Filter by category
            val filtered = allNightlife.filter { place ->
                // Include if no category (legacy data) or if category matches
                place.nightlifeCategory == null || 
                place.nightlifeCategory in matchingCategories
            }
            
            Log.d("PlacesRepository", "Found ${filtered.size}/${allNightlife.size} nightlife venues matching categories: $matchingCategories")
            return filtered
        }
        
        return getMockNightlife(nightlifeTypes)
    }
    
    private fun getMockNightlife(nightlifeTypes: List<String>): List<AppPlace> {
        val allNightlife = mutableListOf<AppPlace>()
        
        if (nightlifeTypes.contains("bars")) {
            allNightlife.addAll(listOf(
                // Mission
                AppPlace("zeitgeist", "Zeitgeist", PlaceType.NIGHTLIFE, 37.7658, -122.4209, 4.5f, true, 2, 25),
                AppPlace("the_page", "The Page", PlaceType.NIGHTLIFE, 37.7692, -122.4340, 4.4f, true, 2, 20),
                AppPlace("trick_dog", "Trick Dog", PlaceType.NIGHTLIFE, 37.7537, -122.4175, 4.6f, true, 2, 30),
                AppPlace("el_rio", "El Rio", PlaceType.NIGHTLIFE, 37.7480, -122.4096, 4.5f, true, 2, 25),
                AppPlace("lone_palm", "The Lone Palm", PlaceType.NIGHTLIFE, 37.7434, -122.4262, 4.4f, true, 2, 20),
                // SoMa
                AppPlace("mr_tipples", "Mr. Tipple's Recording Studio", PlaceType.NIGHTLIFE, 37.7747, -122.4100, 4.5f, true, 2, 25),
                AppPlace("city_beer", "City Beer Store", PlaceType.NIGHTLIFE, 37.7805, -122.4100, 4.4f, true, 2, 20),
                AppPlace("the_view", "The View Lounge", PlaceType.NIGHTLIFE, 37.7855, -122.4088, 4.6f, true, 3, 35),
                // Castro
                AppPlace("the_castro", "The Castro Bar", PlaceType.NIGHTLIFE, 37.7615, -122.4350, 4.3f, true, 2, 25),
                AppPlace("lookout", "The Lookout", PlaceType.NIGHTLIFE, 37.7620, -122.4355, 4.4f, true, 2, 20),
                // Hayes Valley
                AppPlace("smugglers", "Smuggler's Cove Lite", PlaceType.NIGHTLIFE, 37.7763, -122.4235, 4.5f, true, 2, 30)
            ))
        }
        
        if (nightlifeTypes.contains("cocktail_bars")) {
            allNightlife.addAll(listOf(
                // Downtown / Tenderloin
                AppPlace("bourbon_branch", "Bourbon & Branch", PlaceType.NIGHTLIFE, 37.7835, -122.4184, 4.7f, true, 3, 40),
                AppPlace("smugglers_cove", "Smuggler's Cove", PlaceType.NIGHTLIFE, 37.7763, -122.4235, 4.8f, true, 3, 45),
                AppPlace("rickhouse", "Rickhouse", PlaceType.NIGHTLIFE, 37.7918, -122.3984, 4.6f, true, 3, 40),
                AppPlace("alchemist", "The Alchemist Bar", PlaceType.NIGHTLIFE, 37.7716, -122.4210, 4.5f, true, 3, 35),
                AppPlace("blackbird", "Blackbird Bar", PlaceType.NIGHTLIFE, 37.7831, -122.4126, 4.6f, true, 3, 40),
                // SoMa
                AppPlace("local_edition", "Local Edition", PlaceType.NIGHTLIFE, 37.7881, -122.4030, 4.7f, true, 3, 45),
                AppPlace("whitechapel", "Whitechapel", PlaceType.NIGHTLIFE, 37.7795, -122.4118, 4.6f, true, 3, 40),
                AppPlace("holy_water", "Holy Water", PlaceType.NIGHTLIFE, 37.7808, -122.4096, 4.5f, true, 3, 35),
                // North Beach
                AppPlace("comstock", "Comstock Saloon", PlaceType.NIGHTLIFE, 37.7983, -122.4078, 4.6f, true, 3, 40),
                AppPlace("tosca", "Tosca Cafe Bar", PlaceType.NIGHTLIFE, 37.7981, -122.4082, 4.5f, true, 3, 35),
                // Mission
                AppPlace("abv", "ABV", PlaceType.NIGHTLIFE, 37.7605, -122.4175, 4.7f, true, 3, 40),
                AppPlace("bergerac", "Bergerac", PlaceType.NIGHTLIFE, 37.7680, -122.4240, 4.5f, true, 3, 35)
            ))
        }
        
        if (nightlifeTypes.contains("clubs")) {
            allNightlife.addAll(listOf(
                // SoMa
                AppPlace("1015_folsom", "1015 Folsom", PlaceType.NIGHTLIFE, 37.7755, -122.4100, 4.3f, true, 3, 40),
                AppPlace("the_grand", "The Grand Nightclub", PlaceType.NIGHTLIFE, 37.7853, -122.4066, 4.4f, true, 3, 50),
                AppPlace("temple_sf", "Temple Nightclub", PlaceType.NIGHTLIFE, 37.7850, -122.4070, 4.5f, true, 3, 45),
                AppPlace("audio_sf", "Audio Discotech", PlaceType.NIGHTLIFE, 37.7835, -122.4095, 4.3f, true, 3, 40),
                AppPlace("monarch", "Monarch", PlaceType.NIGHTLIFE, 37.7790, -122.4132, 4.4f, true, 3, 35),
                AppPlace("mezzanine", "The Mezzanine", PlaceType.NIGHTLIFE, 37.7802, -122.4120, 4.5f, true, 3, 40),
                // Castro
                AppPlace("lookout_club", "Lookout Dance Club", PlaceType.NIGHTLIFE, 37.7618, -122.4352, 4.2f, true, 3, 35),
                // Mission
                AppPlace("elbo_room", "Elbo Room", PlaceType.NIGHTLIFE, 37.7595, -122.4220, 4.3f, true, 2, 30)
            ))
        }
        
        if (nightlifeTypes.contains("live_music")) {
            allNightlife.addAll(listOf(
                // Fillmore / Western Addition
                AppPlace("fillmore", "The Fillmore", PlaceType.NIGHTLIFE, 37.7832, -122.4333, 4.8f, true, 3, 50),
                AppPlace("boom_boom", "Boom Boom Room", PlaceType.NIGHTLIFE, 37.7834, -122.4337, 4.6f, true, 2, 35),
                // SoMa / Mission Bay
                AppPlace("great_american", "Great American Music Hall", PlaceType.NIGHTLIFE, 37.7839, -122.4209, 4.7f, true, 3, 45),
                AppPlace("the_independent", "The Independent", PlaceType.NIGHTLIFE, 37.7839, -122.4335, 4.6f, true, 3, 40),
                AppPlace("slims", "Slim's", PlaceType.NIGHTLIFE, 37.7808, -122.4120, 4.5f, true, 3, 35),
                // Mission
                AppPlace("bottom_hill", "Bottom of the Hill", PlaceType.NIGHTLIFE, 37.7512, -122.3983, 4.6f, true, 2, 30),
                AppPlace("chapel", "The Chapel", PlaceType.NIGHTLIFE, 37.7535, -122.4220, 4.7f, true, 2, 35),
                AppPlace("make_out_room", "Make-Out Room", PlaceType.NIGHTLIFE, 37.7555, -122.4210, 4.4f, true, 2, 25),
                // North Beach
                AppPlace("biscuits_blues", "Biscuits and Blues", PlaceType.NIGHTLIFE, 37.7897, -122.4015, 4.5f, true, 3, 40),
                // Haight
                AppPlace("independent_haight", "The Independent Haight", PlaceType.NIGHTLIFE, 37.7695, -122.4485, 4.4f, true, 2, 30)
            ))
        }
        
        if (nightlifeTypes.contains("dive_bars")) {
            allNightlife.addAll(listOf(
                // North Beach
                AppPlace("vesuvio", "Vesuvio Cafe", PlaceType.NIGHTLIFE, 37.7978, -122.4079, 4.6f, true, 2, 20),
                AppPlace("specs", "Spec's Twelve Adler Museum Cafe", PlaceType.NIGHTLIFE, 37.7981, -122.4064, 4.5f, true, 2, 15),
                AppPlace("gino_carlo", "Gino & Carlo", PlaceType.NIGHTLIFE, 37.7990, -122.4083, 4.4f, true, 2, 20),
                AppPlace("Li Po", "Li Po Cocktail Lounge", PlaceType.NIGHTLIFE, 37.7960, -122.4075, 4.3f, true, 2, 15),
                // Tenderloin
                AppPlace("phonebooth", "The Phonebooth", PlaceType.NIGHTLIFE, 37.7850, -122.4120, 4.2f, true, 2, 20),
                AppPlace("ha_ra", "Ha-Ra Club", PlaceType.NIGHTLIFE, 37.7845, -122.4130, 4.1f, true, 2, 15),
                // Mission
                AppPlace("wild_side", "Wild Side West", PlaceType.NIGHTLIFE, 37.7425, -122.4285, 4.5f, true, 2, 20),
                AppPlace("latin_american", "Latin American Club", PlaceType.NIGHTLIFE, 37.7575, -122.4235, 4.4f, true, 2, 15),
                // SoMa
                AppPlace("zeitgeist_soma", "Zeitgeist (SoMa location)", PlaceType.NIGHTLIFE, 37.7660, -122.4210, 4.4f, true, 2, 25),
                // Sunset
                AppPlace("trad_sam", "Trad'r Sam", PlaceType.NIGHTLIFE, 37.7705, -122.4685, 4.3f, true, 2, 20)
            ))
        }
        
        if (nightlifeTypes.contains("rooftop_bars")) {
            allNightlife.addAll(listOf(
                // Downtown
                AppPlace("charmaines", "Charmaine's Rooftop Bar", PlaceType.NIGHTLIFE, 37.7855, -122.4066, 4.6f, true, 3, 45),
                AppPlace("cityscape", "Cityscape Lounge", PlaceType.NIGHTLIFE, 37.7860, -122.4070, 4.5f, true, 3, 40),
                AppPlace("top_mark", "Top of the Mark", PlaceType.NIGHTLIFE, 37.7918, -122.4102, 4.7f, true, 3, 50),
                // Mission
                AppPlace("el_techo", "El Techo", PlaceType.NIGHTLIFE, 37.7616, -122.4245, 4.6f, true, 3, 35),
                AppPlace("rooftop_25", "Rooftop 25", PlaceType.NIGHTLIFE, 37.7840, -122.4110, 4.4f, true, 3, 40),
                // SoMa
                AppPlace("jones_sf", "Jones SF", PlaceType.NIGHTLIFE, 37.7835, -122.4080, 4.5f, true, 3, 40),
                // Marina
                AppPlace("pershing_hall", "Pershing Hall Rooftop", PlaceType.NIGHTLIFE, 37.8010, -122.4365, 4.3f, true, 3, 35)
            ))
        }
        
        Log.d("PlacesRepository", "Found ${allNightlife.size} nightlife venues")
        return allNightlife.applyTypeDefaults()
    }

    suspend fun searchEntertainment(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for entertainment (city=$currentCityId, remote=$USE_REMOTE_DATA)")
        
        if (USE_REMOTE_DATA) {
            val places = getPlacesByType(PlaceType.ENTERTAINMENT)
            Log.d("PlacesRepository", "Found ${places.size} entertainment venues from remote")
            return places
        }
        
        return getMockEntertainment()
    }
    
    private fun getMockEntertainment(): List<AppPlace> {
        return listOf<AppPlace>(
            // Comedy Clubs
            AppPlace("cobbs_comedy", "Cobb's Comedy Club", PlaceType.ENTERTAINMENT, 37.8058, -122.4214, 4.6f, true, 2, 30),
            AppPlace("punch_line", "Punch Line Comedy Club", PlaceType.ENTERTAINMENT, 37.7944, -122.3972, 4.5f, true, 2, 25),
            AppPlace("setup_comedy", "Setup SF", PlaceType.ENTERTAINMENT, 37.7802, -122.4100, 4.4f, true, 2, 20),
            AppPlace("doc_ricketts", "Doc Ricketts", PlaceType.ENTERTAINMENT, 37.7810, -122.4095, 4.3f, true, 2, 18),
            // Theaters
            AppPlace("curran_theatre", "Curran Theatre", PlaceType.ENTERTAINMENT, 37.7864, -122.4119, 4.7f, true, 3, 80),
            AppPlace("orpheum_theatre", "Orpheum Theatre", PlaceType.ENTERTAINMENT, 37.7803, -122.4175, 4.6f, true, 3, 75),
            AppPlace("ggolden_gate_theatre", "Golden Gate Theatre", PlaceType.ENTERTAINMENT, 37.7827, -122.4115, 4.6f, true, 3, 70),
            AppPlace("act_theatre", "American Conservatory Theater", PlaceType.ENTERTAINMENT, 37.7867, -122.4074, 4.7f, true, 3, 60),
            AppPlace("marines_memorial", "Marines Memorial Theatre", PlaceType.ENTERTAINMENT, 37.7888, -122.4080, 4.5f, true, 2, 45),
            // Live Shows / Cabaret
            AppPlace("beach_blanket", "Beach Blanket Babylon", PlaceType.ENTERTAINMENT, 37.7978, -122.4083, 4.8f, true, 3, 65),
            AppPlace("the_marsh", "The Marsh", PlaceType.ENTERTAINMENT, 37.7619, -122.4218, 4.5f, true, 2, 30),
            AppPlace("brava_theater", "Brava Theater", PlaceType.ENTERTAINMENT, 37.7555, -122.4208, 4.4f, true, 2, 35),
            AppPlace("magic_theatre", "Magic Theatre", PlaceType.ENTERTAINMENT, 37.8056, -122.4320, 4.5f, true, 2, 40),
            AppPlace("exit_theatre", "Exit Theatre", PlaceType.ENTERTAINMENT, 37.7857, -122.4130, 4.3f, true, 2, 25),
            // Dinner Theater
            AppPlace("teatro_zinzanni", "Teatro ZinZanni", PlaceType.ENTERTAINMENT, 37.7926, -122.3963, 4.6f, true, 3, 120)
        ).applyTypeDefaults()
    }

    suspend fun searchGames(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for games (city=$currentCityId, remote=$USE_REMOTE_DATA)")
        
        if (USE_REMOTE_DATA) {
            val places = getPlacesByType(PlaceType.GAMES)
            Log.d("PlacesRepository", "Found ${places.size} game venues from remote")
            return places
        }
        
        return getMockGames()
    }
    
    private fun getMockGames(): List<AppPlace> {
        return listOf<AppPlace>(
            // Escape Rooms
            AppPlace("escape_sf", "Escape SF", PlaceType.GAMES, 37.7869, -122.4064, 4.5f, true, 2, 35),
            AppPlace("reason", "Reason", PlaceType.GAMES, 37.7892, -122.4012, 4.6f, true, 2, 40),
            AppPlace("palace_games", "Palace Games", PlaceType.GAMES, 37.8035, -122.4478, 4.7f, true, 2, 45),
            AppPlace("escape_room_sf", "The Escape Room SF", PlaceType.GAMES, 37.7755, -122.4186, 4.4f, true, 2, 35),
            // Bowling
            AppPlace("presidio_bowl", "Presidio Bowl", PlaceType.GAMES, 37.7887, -122.4587, 4.4f, true, 2, 30),
            AppPlace("yerba_buena_bowl", "Yerba Buena Bowling", PlaceType.GAMES, 37.7850, -122.4040, 4.3f, true, 2, 25),
            AppPlace("lucky_strike", "Lucky Strike SF", PlaceType.GAMES, 37.7856, -122.4066, 4.4f, true, 2, 35),
            // Arcades & Games
            AppPlace("musee_mecanique", "Musee Mecanique", PlaceType.GAMES, 37.8090, -122.4185, 4.6f, true, 1, 10),
            AppPlace("urban_putt", "Urban Putt", PlaceType.GAMES, 37.7575, -122.4215, 4.5f, true, 2, 25),
            AppPlace("coin_op", "Coin-Op Game Room", PlaceType.GAMES, 37.7605, -122.4195, 4.4f, true, 2, 20),
            AppPlace("emporium_sf", "Emporium SF", PlaceType.GAMES, 37.7697, -122.4334, 4.5f, true, 2, 25),
            // Other Fun
            AppPlace("spin_sf", "SPiN San Francisco", PlaceType.GAMES, 37.7794, -122.4098, 4.4f, true, 2, 25),
            AppPlace("axe_throwing", "Urban Axes SF", PlaceType.GAMES, 37.7752, -122.4175, 4.3f, true, 2, 30)
        ).applyTypeDefaults()
    }

    suspend fun searchOutdoor(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for outdoor activities (city=$currentCityId, remote=$USE_REMOTE_DATA)")
        
        if (USE_REMOTE_DATA) {
            val places = getPlacesByType(PlaceType.OUTDOOR)
            Log.d("PlacesRepository", "Found ${places.size} outdoor activities from remote")
            return places
        }
        
        return getMockOutdoor()
    }
    
    private fun getMockOutdoor(): List<AppPlace> {
        return listOf<AppPlace>(
            // Kayaking / Water
            AppPlace("city_kayak", "City Kayak", PlaceType.OUTDOOR, 37.7785, -122.3880, 4.5f, true, 2, 60),
            AppPlace("sea_trek", "Sea Trek Kayak", PlaceType.OUTDOOR, 37.8588, -122.4897, 4.6f, true, 2, 75),
            AppPlace("bay_voyager", "Bay Voyager", PlaceType.OUTDOOR, 37.7956, -122.3935, 4.4f, true, 2, 50),
            // Bike Rentals / Tours
            AppPlace("blazing_saddles", "Blazing Saddles", PlaceType.OUTDOOR, 37.8082, -122.4181, 4.5f, true, 2, 45),
            AppPlace("bay_city_bike", "Bay City Bike", PlaceType.OUTDOOR, 37.8079, -122.4195, 4.4f, true, 2, 40),
            AppPlace("golden_gate_park_bikes", "Golden Gate Park Bike Rental", PlaceType.OUTDOOR, 37.7694, -122.4862, 4.5f, true, 2, 35),
            AppPlace("wheel_fun_rentals", "Wheel Fun Rentals", PlaceType.OUTDOOR, 37.7694, -122.4720, 4.3f, true, 2, 30),
            // Segway / Electric
            AppPlace("sf_segway", "SF Electric Tour", PlaceType.OUTDOOR, 37.8076, -122.4172, 4.4f, true, 2, 65),
            // Hiking / Walking
            AppPlace("lands_end_trails", "Lands End Trails", PlaceType.OUTDOOR, 37.7849, -122.5080, 4.8f, true, 0, 0),
            AppPlace("batteries_bluffs", "Batteries to Bluffs Trail", PlaceType.OUTDOOR, 37.7975, -122.4814, 4.7f, true, 0, 0),
            AppPlace("glen_park_greenway", "Glen Park Greenway", PlaceType.OUTDOOR, 37.7348, -122.4336, 4.5f, true, 0, 0),
            // Sailing
            AppPlace("sf_sailing", "SF Sailing Company", PlaceType.OUTDOOR, 37.8084, -122.4098, 4.6f, true, 3, 100),
            AppPlace("adventure_cat", "Adventure Cat Sailing", PlaceType.OUTDOOR, 37.8084, -122.4098, 4.5f, true, 2, 75)
        ).applyTypeDefaults()
    }

    suspend fun searchWellness(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for wellness (city=$currentCityId, remote=$USE_REMOTE_DATA)")
        
        if (USE_REMOTE_DATA) {
            val places = getPlacesByType(PlaceType.WELLNESS)
            Log.d("PlacesRepository", "Found ${places.size} wellness venues from remote")
            return places
        }
        
        return getMockWellness()
    }
    
    private fun getMockWellness(): List<AppPlace> {
        return listOf<AppPlace>(
            // Spas / Bathhouses
            AppPlace("kabuki_springs", "Kabuki Springs & Spa", PlaceType.WELLNESS, 37.7851, -122.4307, 4.7f, true, 2, 45),
            AppPlace("archimedes_banya", "Archimedes Banya", PlaceType.WELLNESS, 37.7785, -122.4000, 4.6f, true, 2, 50),
            AppPlace("imperial_day_spa", "Imperial Day Spa", PlaceType.WELLNESS, 37.7856, -122.4083, 4.4f, true, 2, 60),
            AppPlace("nob_hill_spa", "Nob Hill Spa", PlaceType.WELLNESS, 37.7918, -122.4102, 4.5f, true, 3, 120),
            AppPlace("remede_spa", "Remede Spa", PlaceType.WELLNESS, 37.7863, -122.4092, 4.6f, true, 3, 150),
            // Yoga Studios
            AppPlace("yoga_tree", "Yoga Tree Castro", PlaceType.WELLNESS, 37.7612, -122.4348, 4.5f, true, 1, 25),
            AppPlace("yoga_flow", "Yoga Flow SF", PlaceType.WELLNESS, 37.7600, -122.4200, 4.4f, true, 1, 25),
            AppPlace("lovingkindness", "LovingKindness Yoga", PlaceType.WELLNESS, 37.7550, -122.4170, 4.5f, true, 1, 22),
            // Float / Meditation
            AppPlace("reboot_float", "Reboot Float Spa", PlaceType.WELLNESS, 37.7925, -122.4047, 4.6f, true, 2, 80),
            AppPlace("float_matrix", "Float Matrix", PlaceType.WELLNESS, 37.7700, -122.4340, 4.4f, true, 2, 70)
        ).applyTypeDefaults()
    }

    suspend fun searchBreweries(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for breweries (city=$currentCityId, remote=$USE_REMOTE_DATA)")
        
        if (USE_REMOTE_DATA) {
            val places = getPlacesByType(PlaceType.BREWERY)
            Log.d("PlacesRepository", "Found ${places.size} breweries from remote")
            return places
        }
        
        return getMockBreweries()
    }
    
    private fun getMockBreweries(): List<AppPlace> {
        return listOf<AppPlace>(
            // Breweries
            AppPlace("anchor_brewing", "Anchor Brewing Company", PlaceType.BREWERY, 37.7645, -122.4003, 4.7f, true, 2, 25),
            AppPlace("fort_point_beer", "Fort Point Beer Company", PlaceType.BREWERY, 37.8033, -122.4477, 4.6f, true, 2, 20),
            AppPlace("almanac_beer", "Almanac Beer Co", PlaceType.BREWERY, 37.7518, -122.4083, 4.5f, true, 2, 22),
            AppPlace("harmonic_brewing", "Harmonic Brewing", PlaceType.BREWERY, 37.7693, -122.4019, 4.4f, true, 2, 18),
            AppPlace("standard_deviant", "Standard Deviant Brewing", PlaceType.BREWERY, 37.7595, -122.4210, 4.5f, true, 2, 20),
            AppPlace("southern_pacific", "Southern Pacific Brewing", PlaceType.BREWERY, 37.7580, -122.4180, 4.4f, true, 2, 18),
            AppPlace("barebottle", "Barebottle Brewing", PlaceType.BREWERY, 37.7400, -122.4070, 4.5f, true, 2, 18),
            // Wine Bars / Tasting
            AppPlace("press_club", "Press Club", PlaceType.BREWERY, 37.7852, -122.4007, 4.4f, true, 2, 35),
            AppPlace("wine_down", "Wine Down SF", PlaceType.BREWERY, 37.7605, -122.4185, 4.3f, true, 2, 28),
            AppPlace("arlequin_wine", "Arlequin Wine Merchant", PlaceType.BREWERY, 37.7760, -122.4240, 4.5f, true, 2, 25),
            // Distilleries
            AppPlace("seven_stills", "Seven Stills", PlaceType.BREWERY, 37.7560, -122.4070, 4.4f, true, 2, 22),
            AppPlace("hotaling_co", "Hotaling & Co", PlaceType.BREWERY, 37.7970, -122.4020, 4.5f, true, 2, 30)
        ).applyTypeDefaults()
    }

    suspend fun searchClasses(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for classes (city=$currentCityId, remote=$USE_REMOTE_DATA)")
        
        if (USE_REMOTE_DATA) {
            val places = getPlacesByType(PlaceType.CLASS)
            Log.d("PlacesRepository", "Found ${places.size} class venues from remote")
            return places
        }
        
        return getMockClasses()
    }
    
    private fun getMockClasses(): List<AppPlace> {
        return listOf<AppPlace>(
            // Cooking Classes
            AppPlace("sur_la_table", "Sur La Table Cooking Class", PlaceType.CLASS, 37.7956, -122.3935, 4.5f, true, 2, 85),
            AppPlace("sf_cooking_school", "San Francisco Cooking School", PlaceType.CLASS, 37.7755, -122.4186, 4.6f, true, 2, 95),
            AppPlace("draeger_culinary", "Draeger's Cooking School", PlaceType.CLASS, 37.7850, -122.4060, 4.4f, true, 2, 80),
            AppPlace("18_reasons", "18 Reasons", PlaceType.CLASS, 37.7550, -122.4170, 4.5f, true, 2, 65),
            // Art / Pottery
            AppPlace("color_me_mine", "Color Me Mine", PlaceType.CLASS, 37.8020, -122.4340, 4.3f, true, 2, 35),
            AppPlace("clay_by_bay", "Clay by the Bay", PlaceType.CLASS, 37.7945, -122.4072, 4.5f, true, 2, 55),
            AppPlace("paint_nite", "Paint Nite SF", PlaceType.CLASS, 37.7760, -122.4240, 4.4f, true, 2, 45),
            AppPlace("workshop_sf", "Workshop SF", PlaceType.CLASS, 37.7585, -122.4120, 4.5f, true, 2, 75),
            // Craft / DIY
            AppPlace("the_workshop", "The Workshop Residence", PlaceType.CLASS, 37.7545, -122.4188, 4.4f, true, 2, 60),
            AppPlace("general_assembly", "General Assembly", PlaceType.CLASS, 37.7878, -122.4007, 4.3f, true, 2, 50)
        ).applyTypeDefaults()
    }

    suspend fun searchMarkets(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for markets (city=$currentCityId, remote=$USE_REMOTE_DATA)")
        
        if (USE_REMOTE_DATA) {
            val places = getPlacesByType(PlaceType.MARKET)
            Log.d("PlacesRepository", "Found ${places.size} markets from remote")
            return places
        }
        
        return getMockMarkets()
    }
    
    private fun getMockMarkets(): List<AppPlace> {
        return listOf<AppPlace>(
            // Farmers Markets
            AppPlace("ferry_farmers", "Ferry Plaza Farmers Market", PlaceType.MARKET, 37.7956, -122.3935, 4.8f, true, 0, 0),
            AppPlace("alemany_farmers", "Alemany Farmers Market", PlaceType.MARKET, 37.7329, -122.4127, 4.6f, true, 0, 0),
            AppPlace("castro_farmers", "Castro Farmers Market", PlaceType.MARKET, 37.7620, -122.4350, 4.4f, true, 0, 0),
            AppPlace("mission_community", "Mission Community Market", PlaceType.MARKET, 37.7525, -122.4178, 4.5f, true, 0, 0),
            AppPlace("noe_valley_farmers", "Noe Valley Farmers Market", PlaceType.MARKET, 37.7510, -122.4320, 4.5f, true, 0, 0),
            // Flea Markets / Vintage
            AppPlace("alameda_flea", "Alameda Point Antiques Faire", PlaceType.MARKET, 37.7870, -122.3020, 4.7f, true, 1, 5),
            AppPlace("treasure_island_flea", "Treasure Island Flea", PlaceType.MARKET, 37.8235, -122.3705, 4.5f, true, 1, 5),
            // Food Halls
            AppPlace("ferry_building_market", "Ferry Building Marketplace", PlaceType.MARKET, 37.7956, -122.3935, 4.7f, true, 0, 0)
        ).applyTypeDefaults()
    }

    suspend fun searchSports(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for sports (city=$currentCityId, remote=$USE_REMOTE_DATA)")
        
        if (USE_REMOTE_DATA) {
            val places = getPlacesByType(PlaceType.SPORTS)
            Log.d("PlacesRepository", "Found ${places.size} sports venues from remote")
            return places
        }
        
        return getMockSports()
    }
    
    private fun getMockSports(): List<AppPlace> {
        return listOf<AppPlace>(
            // Golf
            AppPlace("presidio_golf", "Presidio Golf Course", PlaceType.SPORTS, 37.7880, -122.4612, 4.5f, true, 2, 80),
            AppPlace("harding_park", "TPC Harding Park", PlaceType.SPORTS, 37.7250, -122.4920, 4.6f, true, 3, 150),
            AppPlace("lincoln_park_golf", "Lincoln Park Golf Course", PlaceType.SPORTS, 37.7837, -122.4940, 4.4f, true, 2, 50),
            AppPlace("golden_gate_golf", "Golden Gate Park Golf Course", PlaceType.SPORTS, 37.7680, -122.4900, 4.3f, true, 1, 25),
            // Climbing
            AppPlace("mission_cliffs", "Mission Cliffs", PlaceType.SPORTS, 37.7592, -122.4150, 4.6f, true, 2, 30),
            AppPlace("dogpatch_boulders", "Dogpatch Boulders", PlaceType.SPORTS, 37.7565, -122.3880, 4.5f, true, 2, 28),
            AppPlace("planet_granite", "Planet Granite SF", PlaceType.SPORTS, 37.8050, -122.4330, 4.5f, true, 2, 28),
            // Tennis / Pickleball
            AppPlace("dolores_tennis", "Dolores Park Tennis", PlaceType.SPORTS, 37.7596, -122.4269, 4.4f, true, 1, 15),
            AppPlace("golden_gate_tennis", "Golden Gate Park Tennis", PlaceType.SPORTS, 37.7700, -122.4680, 4.5f, true, 1, 20),
            // Batting Cages
            AppPlace("south_sf_batting", "South SF Batting Range", PlaceType.SPORTS, 37.6410, -122.4100, 4.3f, true, 1, 20)
        ).applyTypeDefaults()
    }
}
