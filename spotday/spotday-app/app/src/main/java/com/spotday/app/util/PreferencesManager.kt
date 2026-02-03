package com.spotday.app.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user preference persistence using SharedPreferences.
 * Remembers activity, cuisine, and nightlife selections across app sessions.
 */
class PreferencesManager(context: Context) {
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("spotday_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_ACTIVITIES = "selected_activities"
        private const val KEY_CUISINES = "selected_cuisines"
        private const val KEY_NIGHTLIFE = "selected_nightlife"
        private const val KEY_SERVICE_STYLES = "selected_service_styles"
        private const val KEY_EXPLORATION_MODE = "exploration_mode"
        private const val KEY_DEFAULT_BUDGET = "default_budget"
        private const val KEY_SELECTED_CITY = "selected_city"
        
        // WelcomeActivity preferences
        private const val KEY_IS_SPONTANEOUS_MODE = "is_spontaneous_mode"
        private const val KEY_TIME_RANGE_START = "time_range_start"
        private const val KEY_TIME_RANGE_END = "time_range_end"
        private const val KEY_DURATION_HOURS = "duration_hours"
        private const val KEY_BUDGET = "budget"
        private const val KEY_IS_HUNGRY_NOW = "is_hungry_now"
        private const val KEY_USE_MY_LOCATION = "use_my_location"
        
        // Default city ID
        const val DEFAULT_CITY_ID = "san_francisco"
    }
    
    // Activity selections
    fun saveActivitySelections(activities: Set<String>) {
        prefs.edit().putStringSet(KEY_ACTIVITIES, activities).apply()
    }
    
    fun getActivitySelections(): Set<String> {
        return prefs.getStringSet(KEY_ACTIVITIES, emptySet()) ?: emptySet()
    }
    
    // Cuisine selections
    fun saveCuisineSelections(cuisines: Set<String>) {
        prefs.edit().putStringSet(KEY_CUISINES, cuisines).apply()
    }
    
    fun getCuisineSelections(): Set<String> {
        return prefs.getStringSet(KEY_CUISINES, emptySet()) ?: emptySet()
    }
    
    // Nightlife selections
    fun saveNightlifeSelections(nightlife: Set<String>) {
        prefs.edit().putStringSet(KEY_NIGHTLIFE, nightlife).apply()
    }
    
    fun getNightlifeSelections(): Set<String> {
        return prefs.getStringSet(KEY_NIGHTLIFE, emptySet()) ?: emptySet()
    }
    
    // Service style selections
    fun saveServiceStyleSelections(styles: Set<String>) {
        prefs.edit().putStringSet(KEY_SERVICE_STYLES, styles).apply()
    }
    
    fun getServiceStyleSelections(): Set<String> {
        return prefs.getStringSet(KEY_SERVICE_STYLES, emptySet()) ?: emptySet()
    }
    
    // Exploration mode
    fun saveExplorationMode(mode: String) {
        prefs.edit().putString(KEY_EXPLORATION_MODE, mode).apply()
    }
    
    fun getExplorationMode(): String {
        return prefs.getString(KEY_EXPLORATION_MODE, "ONE_AREA") ?: "ONE_AREA"
    }
    
    // Default budget
    fun saveDefaultBudget(budget: Int) {
        prefs.edit().putInt(KEY_DEFAULT_BUDGET, budget).apply()
    }
    
    fun getDefaultBudget(): Int {
        return prefs.getInt(KEY_DEFAULT_BUDGET, 100)
    }
    
    // Welcome screen preferences
    fun saveSpontaneousMode(isSpontaneous: Boolean) {
        prefs.edit().putBoolean(KEY_IS_SPONTANEOUS_MODE, isSpontaneous).apply()
    }
    
    fun getSpontaneousMode(): Boolean {
        return prefs.getBoolean(KEY_IS_SPONTANEOUS_MODE, false)
    }
    
    fun saveTimeRange(start: Float, end: Float) {
        prefs.edit()
            .putFloat(KEY_TIME_RANGE_START, start)
            .putFloat(KEY_TIME_RANGE_END, end)
            .apply()
    }
    
    fun getTimeRangeStart(): Float {
        return prefs.getFloat(KEY_TIME_RANGE_START, 9f)
    }
    
    fun getTimeRangeEnd(): Float {
        return prefs.getFloat(KEY_TIME_RANGE_END, 17f)
    }
    
    fun saveDurationHours(hours: Float) {
        prefs.edit().putFloat(KEY_DURATION_HOURS, hours).apply()
    }
    
    fun getDurationHours(): Float {
        return prefs.getFloat(KEY_DURATION_HOURS, 4f)
    }
    
    fun saveBudget(budget: Int) {
        prefs.edit().putInt(KEY_BUDGET, budget).apply()
    }
    
    fun getBudget(): Int {
        return prefs.getInt(KEY_BUDGET, 100)
    }
    
    fun saveHungryNow(isHungry: Boolean) {
        prefs.edit().putBoolean(KEY_IS_HUNGRY_NOW, isHungry).apply()
    }
    
    fun getHungryNow(): Boolean {
        return prefs.getBoolean(KEY_IS_HUNGRY_NOW, false)
    }
    
    // Selected city
    fun saveSelectedCity(cityId: String) {
        prefs.edit().putString(KEY_SELECTED_CITY, cityId).apply()
    }

    fun getSelectedCity(): String {
        return prefs.getString(KEY_SELECTED_CITY, DEFAULT_CITY_ID) ?: DEFAULT_CITY_ID
    }

    // Use My Location preference
    fun saveUseMyLocation(useLocation: Boolean) {
        prefs.edit().putBoolean(KEY_USE_MY_LOCATION, useLocation).apply()
    }

    fun getUseMyLocation(): Boolean {
        return prefs.getBoolean(KEY_USE_MY_LOCATION, false)
    }

    // Clear all preferences
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
