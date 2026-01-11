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
    
    // Clear all preferences
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
