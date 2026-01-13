package com.spotday.app.api

import com.spotday.app.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Supabase client for accessing cached API data.
 * Reads from cached_events, cached_weather, neighborhoods, and cities tables.
 */
object SupabaseClient {
    
    private val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
        // Ignore unknown keys like raw_json that we don't need in the app
        defaultSerializer = KotlinXSerializer(Json {
            ignoreUnknownKeys = true
        })
    }
    
    val postgrest get() = client.postgrest
    
    /**
     * Get cached weather for a city at a specific date/hour.
     * Returns null if not found in cache.
     */
    suspend fun getWeather(cityId: String, date: String, hour: Int): SupabaseCachedWeather? {
        return try {
            postgrest.from("cached_weather")
                .select {
                    filter {
                        eq("city_id", cityId)
                        eq("forecast_date", date)
                        eq("forecast_hour", hour)
                    }
                }
                .decodeSingleOrNull<SupabaseCachedWeather>()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get all cached places for a city.
     * Returns all places at once for local filtering (faster than multiple queries).
     * Returns empty list if not found or on error.
     */
    suspend fun getAllPlaces(cityId: String): List<SupabaseCachedPlace> {
        return try {
            postgrest.from("cached_places")
                .select {
                    filter {
                        eq("city_id", cityId)
                    }
                }
                .decodeList<SupabaseCachedPlace>()
        } catch (e: Exception) {
            android.util.Log.e("SupabaseClient", "Failed to fetch places for $cityId", e)
            emptyList()
        }
    }
    
    /**
     * Get all neighborhoods for a city.
     * Returns empty list if not found or on error.
     */
    suspend fun getNeighborhoods(cityId: String): List<SupabaseNeighborhood> {
        return try {
            postgrest.from("neighborhoods")
                .select {
                    filter {
                        eq("city_id", cityId)
                    }
                }
                .decodeList<SupabaseNeighborhood>()
        } catch (e: Exception) {
            android.util.Log.e("SupabaseClient", "Failed to fetch neighborhoods for $cityId", e)
            emptyList()
        }
    }
    
    /**
     * Get all quick stops (viewpoints, photo spots, street art) for a city.
     * Coffee stops come from cached_places, not this table.
     * Returns empty list if not found or on error.
     */
    suspend fun getQuickStops(cityId: String): List<SupabaseQuickStop> {
        return try {
            postgrest.from("cached_quick_stops")
                .select {
                    filter {
                        eq("city_id", cityId)
                    }
                }
                .decodeList<SupabaseQuickStop>()
        } catch (e: Exception) {
            android.util.Log.e("SupabaseClient", "Failed to fetch quick stops for $cityId", e)
            emptyList()
        }
    }
}

// ============================================
// Supabase Data Models (matching database schema)
// ============================================

@Serializable
data class SupabaseCity(
    val id: String,
    val name: String,
    val country: String,
    val state_code: String?,
    val size: String,
    val density: String,
    val center_lat: Double,
    val center_lng: Double,
    val estimated_areas: Int,
    val data_source: String,
    val is_active: Boolean
)

@Serializable
data class SupabaseNeighborhood(
    val id: String,
    val city_id: String,
    val name: String,
    val tier: String,
    val center_lat: Double,
    val center_lng: Double,
    val radius_meters: Int,
    val vibes: List<String>,
    val description: String?,
    val adjacent_neighborhoods: List<String>,
    val data_source: String
)

@Serializable
data class SupabaseCachedEvent(
    val id: String,
    val city_id: String,
    val name: String,
    val description: String?,
    val event_type: String,
    val venue_name: String,
    val venue_lat: Double,
    val venue_lng: Double,
    val start_date: String,
    val start_hour: Int,
    val start_minute: Int,
    val duration_minutes: Int,
    val price_min: Double?,
    val price_max: Double?,
    val is_sold_out: Boolean,
    val ticket_url: String?,
    val popularity: Int,
    val source: String
)

@Serializable
data class SupabaseCachedWeather(
    val id: String,
    val city_id: String,
    val forecast_date: String,
    val forecast_hour: Int,
    val condition: String,
    val temperature_f: Int = 65,
    val feels_like_f: Int = 65,
    val humidity_percent: Int? = null,
    val wind_mph: Int? = null,
    val description: String? = null,
    val icon_code: String? = null
) {
    // Convenience accessors for the app's camelCase convention
    val temperatureF: Int get() = temperature_f
    val feelsLikeF: Int get() = feels_like_f
    val humidityPercent: Int? get() = humidity_percent
    val windMph: Int? get() = wind_mph
    val iconCode: String? get() = icon_code
}

@Serializable
data class SupabaseDayHours(
    val open: String,
    val close: String
)

@Serializable
data class SupabaseWeeklyHours(
    val monday: SupabaseDayHours? = null,
    val tuesday: SupabaseDayHours? = null,
    val wednesday: SupabaseDayHours? = null,
    val thursday: SupabaseDayHours? = null,
    val friday: SupabaseDayHours? = null,
    val saturday: SupabaseDayHours? = null,
    val sunday: SupabaseDayHours? = null
)

@Serializable
data class SupabaseCachedPlace(
    val id: String,
    val city_id: String,
    val neighborhood_id: String? = null,
    val name: String,
    val place_type: String,           // "RESTAURANT", "MUSEUM", "PARK", etc.
    val lat: Double,
    val lng: Double,
    val rating: Double? = null,
    val review_count: Int = 0,
    val price_level: Int? = null,     // 1-4
    val is_outdoor: Boolean = false,
    // Nightlife-specific fields
    val nightlife_category: String? = null,  // cocktail_bar, dive_bar, rooftop_bar, etc.
    val last_verified_at: String? = null,    // ISO timestamp for staleness filtering
    val is_permanently_closed: Boolean = false,
    // Weekly hours from Google Places API (required after migration 012)
    val weekly_hours: SupabaseWeeklyHours? = null
) {
    // Convenience accessors for camelCase
    val cityId: String get() = city_id
    val neighborhoodId: String? get() = neighborhood_id
    val placeType: String get() = place_type
    val reviewCount: Int get() = review_count
    val priceLevel: Int? get() = price_level
    val isOutdoor: Boolean get() = is_outdoor
    val nightlifeCategory: String? get() = nightlife_category
    val lastVerifiedAt: String? get() = last_verified_at
    val isPermanentlyClosed: Boolean get() = is_permanently_closed
    val weeklyHours: SupabaseWeeklyHours? get() = weekly_hours
}

@Serializable
data class SupabaseQuickStop(
    val id: String,
    val city_id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val stop_type: String,            // "VIEWPOINT", "PHOTO_SPOT", "STREET_ART"
    val description: String? = null,
    val duration_minutes: Int = 20,
    val neighborhood_id: String? = null
) {
    // Convenience accessors for camelCase
    val cityId: String get() = city_id
    val stopType: String get() = stop_type
    val durationMinutes: Int get() = duration_minutes
    val neighborhoodId: String? get() = neighborhood_id
}
