package com.spotday.app.util

/**
 * Helper for weather-aware itinerary recommendations.
 * Currently uses mock data for demo purposes.
 * Future: Integrate with OpenWeatherMap or similar API.
 */
object WeatherHelper {
    
    enum class WeatherCondition {
        SUNNY,
        CLOUDY,
        RAINY,
        STORMY
    }
    
    data class WeatherForecast(
        val condition: WeatherCondition,
        val temperatureF: Int,
        val description: String
    )
    
    // Mock weather forecasts for demo
    private val mockForecasts = listOf(
        WeatherForecast(WeatherCondition.SUNNY, 72, "Sunny and clear"),
        WeatherForecast(WeatherCondition.SUNNY, 68, "Beautiful day"),
        WeatherForecast(WeatherCondition.SUNNY, 75, "Warm and sunny"),
        WeatherForecast(WeatherCondition.CLOUDY, 65, "Partly cloudy"),
        WeatherForecast(WeatherCondition.CLOUDY, 62, "Overcast"),
        WeatherForecast(WeatherCondition.RAINY, 58, "Rain expected"),
        WeatherForecast(WeatherCondition.RAINY, 55, "Showers likely"),
        WeatherForecast(WeatherCondition.STORMY, 52, "Thunderstorms possible")
    )
    
    /**
     * Get a mock weather forecast for demo purposes.
     * Weighted to favor good weather (70% sunny/cloudy, 30% rain/storm)
     */
    fun getMockForecast(): WeatherForecast {
        val rand = (0..100).random()
        return when {
            rand < 40 -> mockForecasts.filter { it.condition == WeatherCondition.SUNNY }.random()
            rand < 70 -> mockForecasts.filter { it.condition == WeatherCondition.CLOUDY }.random()
            rand < 90 -> mockForecasts.filter { it.condition == WeatherCondition.RAINY }.random()
            else -> mockForecasts.filter { it.condition == WeatherCondition.STORMY }.random()
        }
    }
    
    /**
     * Determine if outdoor activities should be avoided based on weather.
     * Avoid outdoor when:
     * - Raining or storming
     * - Temperature below 45°F (too cold)
     * - Temperature above 95°F (too hot)
     */
    fun shouldAvoidOutdoor(forecast: WeatherForecast): Boolean {
        return forecast.condition == WeatherCondition.RAINY ||
               forecast.condition == WeatherCondition.STORMY ||
               forecast.temperatureF < 45 ||
               forecast.temperatureF > 95
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
}
