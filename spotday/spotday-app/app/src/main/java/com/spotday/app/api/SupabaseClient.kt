package com.spotday.app.api

import com.spotday.app.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable

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
