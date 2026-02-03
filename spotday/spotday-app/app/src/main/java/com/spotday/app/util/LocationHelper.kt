package com.spotday.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Helper for managing user location using GPS
 */
object LocationHelper {
    private const val TAG = "LocationHelper"

    // City center fallbacks when GPS unavailable
    private val CITY_CENTERS = mapOf(
        "san_francisco" to Pair(37.7749, -122.4194),
        "charlotte" to Pair(35.2271, -80.8431),
        "phoenix" to Pair(33.4484, -112.0740),
        "tucson" to Pair(32.2226, -110.9747)
    )

    /**
     * Check if location permission is granted
     */
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get current GPS location asynchronously
     * Returns (latitude, longitude) or null if unavailable
     */
    suspend fun getCurrentLocation(context: Context): Pair<Double, Double>? {
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "Location permission not granted")
            return null
        }

        val fusedLocationClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)

        return try {
            // First try to get last known location (fast)
            val lastLocation = getLastLocation(fusedLocationClient)
            if (lastLocation != null) {
                Log.d(TAG, "Using last known location: ${lastLocation.first}, ${lastLocation.second}")
                return lastLocation
            }

            // If no last location, request current location (slower but accurate)
            Log.d(TAG, "No last location, requesting current location...")
            getCurrentLocationFresh(fusedLocationClient)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting location", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            null
        }
    }

    @Suppress("MissingPermission")
    private suspend fun getLastLocation(
        fusedLocationClient: FusedLocationProviderClient
    ): Pair<Double, Double>? = suspendCancellableCoroutine { continuation ->
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    continuation.resume(Pair(location.latitude, location.longitude))
                } else {
                    continuation.resume(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get last location", e)
                continuation.resume(null)
            }
    }

    @Suppress("MissingPermission")
    private suspend fun getCurrentLocationFresh(
        fusedLocationClient: FusedLocationProviderClient
    ): Pair<Double, Double>? = suspendCancellableCoroutine { continuation ->
        val cancellationTokenSource = CancellationTokenSource()

        continuation.invokeOnCancellation {
            cancellationTokenSource.cancel()
        }

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location: Location? ->
            if (location != null) {
                Log.d(TAG, "Got fresh location: ${location.latitude}, ${location.longitude}")
                continuation.resume(Pair(location.latitude, location.longitude))
            } else {
                Log.w(TAG, "getCurrentLocation returned null")
                continuation.resume(null)
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to get current location", e)
            continuation.resume(null)
        }
    }

    /**
     * Get city center coordinates as fallback
     */
    fun getCityCenter(cityId: String): Pair<Double, Double> {
        return CITY_CENTERS[cityId] ?: CITY_CENTERS["san_francisco"]!!
    }

    /**
     * Get location with fallback to city center
     */
    suspend fun getLocationOrFallback(context: Context, cityId: String): Pair<Double, Double> {
        return getCurrentLocation(context) ?: getCityCenter(cityId)
    }
}
