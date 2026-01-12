package com.spotday.app.util

import com.spotday.app.api.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Helper for weather-aware itinerary recommendations.
 * Fetches cached weather from Supabase, falls back to mock data.
 */
object WeatherHelper {
    
    enum class WeatherCondition {
        SUNNY,
        CLOUDY,
        RAINY,
        STORMY,
        SNOWY,
        FOGGY
    }
    
    data class WeatherForecast(
        val condition: WeatherCondition,
        val temperatureF: Int,
        val feelsLikeF: Int = temperatureF,
        val humidityPercent: Int = 50,
        val windMph: Int = 5,
        val description: String,
        val iconCode: String? = null,
        val isFallback: Boolean = false  // Indicates weather data unavailable
    )
    
    /**
     * Get a fallback forecast when real data is unavailable.
     * Always returns pleasant sunny weather to avoid disrupting user experience.
     */
    fun getFallbackForecast(): WeatherForecast {
        return WeatherForecast(
            condition = WeatherCondition.SUNNY,
            temperatureF = 70,
            feelsLikeF = 70,
            humidityPercent = 50,
            windMph = 5,
            description = "Pleasant weather",
            iconCode = null,
            isFallback = true
        )
    }
    
    /**
     * Get weather forecast from Supabase cache for the given city and time.
     * Falls back to mock data if cache is unavailable.
     */
    suspend fun getForecast(
        cityId: String = "san_francisco",
        dateTime: LocalDateTime = LocalDateTime.now()
    ): WeatherForecast = withContext(Dispatchers.IO) {
        try {
            val date = dateTime.toLocalDate().toString()
            val hour = dateTime.hour
            
            // Round to nearest 3-hour block (OpenWeatherMap returns 3-hour forecasts)
            val roundedHour = (hour / 3) * 3
            
            val weather = SupabaseClient.getWeather(cityId, date, roundedHour)
            if (weather != null) {
                WeatherForecast(
                    condition = try {
                        WeatherCondition.valueOf(weather.condition)
                    } catch (e: Exception) {
                        WeatherCondition.CLOUDY
                    },
                    temperatureF = weather.temperatureF,
                    feelsLikeF = weather.feelsLikeF,
                    humidityPercent = weather.humidityPercent ?: 50,
                    windMph = weather.windMph ?: 5,
                    description = weather.description ?: "No description",
                    iconCode = weather.iconCode,
                    isFallback = false
                )
            } else {
                getFallbackForecast()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            getFallbackForecast()
        }
    }
    
    
    /**
     * Determine if outdoor activities should be avoided based on weather.
     * Avoid outdoor when:
     * - Raining, storming, or snowing
     * - Temperature below 45°F (too cold)
     * - Temperature above 95°F (too hot)
     * - Very high winds (> 25 mph)
     */
    fun shouldAvoidOutdoor(forecast: WeatherForecast): Boolean {
        return forecast.condition == WeatherCondition.RAINY ||
               forecast.condition == WeatherCondition.STORMY ||
               forecast.condition == WeatherCondition.SNOWY ||
               forecast.temperatureF < 45 ||
               forecast.temperatureF > 95 ||
               forecast.windMph > 25
    }
    
    /**
     * Get emoji for weather condition
     */
    fun getWeatherEmoji(condition: WeatherCondition): String {
        return when (condition) {
            WeatherCondition.SUNNY -> "☀️"
            WeatherCondition.CLOUDY -> "⛅"
            WeatherCondition.RAINY -> "🌧️"
            WeatherCondition.STORMY -> "⛈️"
            WeatherCondition.SNOWY -> "❄️"
            WeatherCondition.FOGGY -> "🌫️"
        }
    }
    
    /**
     * Get a user-friendly message about the weather impact
     */
    fun getWeatherAdvice(forecast: WeatherForecast): String {
        return when {
            forecast.condition == WeatherCondition.STORMY -> 
                "Storms expected - indoor activities recommended"
            forecast.condition == WeatherCondition.RAINY -> 
                "Rain likely - prioritizing indoor spots"
            forecast.condition == WeatherCondition.SNOWY -> 
                "Snow expected - indoor activities recommended"
            forecast.condition == WeatherCondition.FOGGY -> 
                "Foggy conditions - drive safe"
            forecast.windMph > 25 ->
                "Windy conditions - indoor activities preferred"
            forecast.temperatureF < 45 -> 
                "Chilly weather - indoor activities preferred"
            forecast.temperatureF > 95 -> 
                "Very hot - seeking indoor/shaded spots"
            forecast.condition == WeatherCondition.SUNNY && forecast.temperatureF in 65..80 -> 
                "Perfect weather for outdoor activities!"
            else -> 
                "Good conditions for your day out"
        }
    }
    
    /**
     * Get an icon URL from OpenWeatherMap icon code
     */
    fun getIconUrl(iconCode: String?): String? {
        return iconCode?.let { "https://openweathermap.org/img/wn/${it}@2x.png" }
    }
}
