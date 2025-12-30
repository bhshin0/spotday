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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PlacesRepository(private val context: Context) {
    private val placesClient: PlacesClient

    init {
        if (!Places.isInitialized()) {
            Places.initialize(context, BuildConfig.PLACES_API_KEY)
        }
        placesClient = Places.createClient(context)
    }

    // San Francisco center coordinates
    private val SF_CENTER = LatLng(37.7749, -122.4194)

    suspend fun searchMuseums(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for museums in SF")
        // For POC, return hardcoded highly-rated SF museums
        return listOf(
            AppPlace(
                id = "sfmoma",
                name = "San Francisco Museum of Modern Art",
                type = PlaceType.MUSEUM,
                lat = 37.7857,
                lng = -122.4011,
                rating = 4.6f,
                isOpen = true,
                priceLevel = 2,
                estimatedCost = 25
            ),
            AppPlace(
                id = "calacdemy",
                name = "California Academy of Sciences",
                type = PlaceType.MUSEUM,
                lat = 37.7699,
                lng = -122.4661,
                rating = 4.7f,
                isOpen = true,
                priceLevel = 2,
                estimatedCost = 30
            ),
            AppPlace(
                id = "exploratorium",
                name = "Exploratorium",
                type = PlaceType.MUSEUM,
                lat = 37.8014,
                lng = -122.3975,
                rating = 4.6f,
                isOpen = true,
                priceLevel = 2,
                estimatedCost = 30
            ),
            AppPlace(
                id = "deyoung",
                name = "de Young Museum",
                type = PlaceType.MUSEUM,
                lat = 37.7714,
                lng = -122.4686,
                rating = 4.6f,
                isOpen = true,
                priceLevel = 2,
                estimatedCost = 25
            ),
            AppPlace(
                id = "asianart",
                name = "Asian Art Museum",
                type = PlaceType.MUSEUM,
                lat = 37.7803,
                lng = -122.4158,
                rating = 4.5f,
                isOpen = true,
                priceLevel = 2,
                estimatedCost = 20
            )
        )
    }

    suspend fun searchParks(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for parks in SF")
        // For POC, return hardcoded highly-rated SF parks
        return listOf(
            AppPlace(
                id = "ggpark",
                name = "Golden Gate Park",
                type = PlaceType.PARK,
                lat = 37.7694,
                lng = -122.4862,
                rating = 4.8f,
                isOpen = true,
                priceLevel = 0,
                estimatedCost = 0
            ),
            AppPlace(
                id = "dolores",
                name = "Dolores Park",
                type = PlaceType.PARK,
                lat = 37.7596,
                lng = -122.4269,
                rating = 4.7f,
                isOpen = true,
                priceLevel = 0,
                estimatedCost = 0
            ),
            AppPlace(
                id = "crissy",
                name = "Crissy Field",
                type = PlaceType.PARK,
                lat = 37.8050,
                lng = -122.4650,
                rating = 4.7f,
                isOpen = true,
                priceLevel = 0,
                estimatedCost = 0
            ),
            AppPlace(
                id = "alamo",
                name = "Alamo Square",
                type = PlaceType.PARK,
                lat = 37.7766,
                lng = -122.4345,
                rating = 4.6f,
                isOpen = true,
                priceLevel = 0,
                estimatedCost = 0
            ),
            AppPlace(
                id = "landsend",
                name = "Lands End",
                type = PlaceType.PARK,
                lat = 37.7849,
                lng = -122.5080,
                rating = 4.8f,
                isOpen = true,
                priceLevel = 0,
                estimatedCost = 0
            )
        )
    }

    suspend fun searchRestaurants(cuisineTypes: List<String>): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for restaurants: $cuisineTypes")
        val allRestaurants = mutableListOf<AppPlace>()

        if (cuisineTypes.contains("italian")) {
            allRestaurants.addAll(
                listOf(
                    AppPlace(
                        id = "flour_water",
                        name = "Flour + Water",
                        type = PlaceType.RESTAURANT,
                        lat = 37.7616,
                        lng = -122.4094,
                        rating = 4.5f,
                        isOpen = true,
                        priceLevel = 2,
                        estimatedCost = 30
                    ),
                    AppPlace(
                        id = "delarosa",
                        name = "Delarosa",
                        type = PlaceType.RESTAURANT,
                        lat = 37.7989,
                        lng = -122.4354,
                        rating = 4.4f,
                        isOpen = true,
                        priceLevel = 2,
                        estimatedCost = 25
                    ),
                    AppPlace(
                        id = "pazzia",
                        name = "Pazzia Restaurant & Pizzeria",
                        type = PlaceType.RESTAURANT,
                        lat = 37.7715,
                        lng = -122.4700,
                        rating = 4.3f,
                        isOpen = true,
                        priceLevel = 2,
                        estimatedCost = 25
                    )
                )
            )
        }

        if (cuisineTypes.contains("mexican")) {
            allRestaurants.addAll(
                listOf(
                    AppPlace(
                        id = "la_taqueria",
                        name = "La Taqueria",
                        type = PlaceType.RESTAURANT,
                        lat = 37.7508,
                        lng = -122.4183,
                        rating = 4.6f,
                        isOpen = true,
                        priceLevel = 1,
                        estimatedCost = 15
                    ),
                    AppPlace(
                        id = "nopalito",
                        name = "Nopalito",
                        type = PlaceType.RESTAURANT,
                        lat = 37.7695,
                        lng = -122.4887,
                        rating = 4.4f,
                        isOpen = true,
                        priceLevel = 2,
                        estimatedCost = 25
                    )
                )
            )
        }

        if (cuisineTypes.contains("american")) {
            allRestaurants.addAll(
                listOf(
                    AppPlace(
                        id = "zuni",
                        name = "Zuni Café",
                        type = PlaceType.RESTAURANT,
                        lat = 37.7750,
                        lng = -122.4223,
                        rating = 4.5f,
                        isOpen = true,
                        priceLevel = 2,
                        estimatedCost = 30
                    ),
                    AppPlace(
                        id = "nopa",
                        name = "NOPA",
                        type = PlaceType.RESTAURANT,
                        lat = 37.7749,
                        lng = -122.4375,
                        rating = 4.4f,
                        isOpen = true,
                        priceLevel = 2,
                        estimatedCost = 28
                    ),
                    AppPlace(
                        id = "outerlands",
                        name = "Outerlands",
                        type = PlaceType.RESTAURANT,
                        lat = 37.7609,
                        lng = -122.5096,
                        rating = 4.5f,
                        isOpen = true,
                        priceLevel = 2,
                        estimatedCost = 25
                    )
                )
            )
        }

        if (cuisineTypes.contains("asian")) {
            allRestaurants.addAll(
                listOf(
                    AppPlace(
                        id = "dragon_beaux",
                        name = "Dragon Beaux",
                        type = PlaceType.RESTAURANT,
                        lat = 37.7943,
                        lng = -122.4078,
                        rating = 4.4f,
                        isOpen = true,
                        priceLevel = 2,
                        estimatedCost = 25
                    ),
                    AppPlace(
                        id = "rintaro",
                        name = "Rintaro",
                        type = PlaceType.RESTAURANT,
                        lat = 37.7600,
                        lng = -122.4194,
                        rating = 4.5f,
                        isOpen = true,
                        priceLevel = 2,
                        estimatedCost = 30
                    )
                )
            )
        }

        if (cuisineTypes.contains("seafood")) {
            allRestaurants.addAll(
                listOf(
                    AppPlace(
                        id = "scomas",
                        name = "Scoma's",
                        type = PlaceType.RESTAURANT,
                        lat = 37.8095,
                        lng = -122.4185,
                        rating = 4.3f,
                        isOpen = true,
                        priceLevel = 3,
                        estimatedCost = 45
                    ),
                    AppPlace(
                        id = "swan_oyster",
                        name = "Swan Oyster Depot",
                        type = PlaceType.RESTAURANT,
                        lat = 37.7921,
                        lng = -122.4202,
                        rating = 4.6f,
                        isOpen = true,
                        priceLevel = 2,
                        estimatedCost = 30
                    )
                )
            )
        }

        if (cuisineTypes.contains("vegetarian")) {
            allRestaurants.addAll(
                listOf(
                    AppPlace(
                        id = "greens",
                        name = "Greens Restaurant",
                        type = PlaceType.RESTAURANT,
                        lat = 37.8055,
                        lng = -122.4323,
                        rating = 4.3f,
                        isOpen = true,
                        priceLevel = 2,
                        estimatedCost = 25
                    ),
                    AppPlace(
                        id = "gracias_madre",
                        name = "Gracias Madre",
                        type = PlaceType.RESTAURANT,
                        lat = 37.7622,
                        lng = -122.4245,
                        rating = 4.4f,
                        isOpen = true,
                        priceLevel = 2,
                        estimatedCost = 28
                    )
                )
            )
        }

        return allRestaurants
    }

    suspend fun searchWaterfront(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for waterfront locations in SF")
        return listOf(
            AppPlace(
                id = "fishermans_wharf",
                name = "Fisherman's Wharf",
                type = PlaceType.WATERFRONT,
                lat = 37.8080,
                lng = -122.4177,
                rating = 4.4f,
                isOpen = true,
                priceLevel = 0,
                estimatedCost = 0
            ),
            AppPlace(
                id = "embarcadero",
                name = "Embarcadero",
                type = PlaceType.WATERFRONT,
                lat = 37.7955,
                lng = -122.3937,
                rating = 4.5f,
                isOpen = true,
                priceLevel = 0,
                estimatedCost = 0
            ),
            AppPlace(
                id = "pier39",
                name = "Pier 39",
                type = PlaceType.WATERFRONT,
                lat = 37.8087,
                lng = -122.4098,
                rating = 4.3f,
                isOpen = true,
                priceLevel = 0,
                estimatedCost = 0
            )
        )
    }

    suspend fun searchHistoricSites(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for historic sites in SF")
        return listOf(
            AppPlace(
                id = "mission_dolores",
                name = "Mission Dolores",
                type = PlaceType.HISTORIC_SITE,
                lat = 37.7637,
                lng = -122.4268,
                rating = 4.4f,
                isOpen = true,
                priceLevel = 1,
                estimatedCost = 10
            ),
            AppPlace(
                id = "cable_car_museum",
                name = "Cable Car Museum",
                type = PlaceType.HISTORIC_SITE,
                lat = 37.7947,
                lng = -122.4114,
                rating = 4.6f,
                isOpen = true,
                priceLevel = 1,
                estimatedCost = 15
            ),
            AppPlace(
                id = "presidio",
                name = "Presidio of San Francisco",
                type = PlaceType.HISTORIC_SITE,
                lat = 37.7989,
                lng = -122.4662,
                rating = 4.7f,
                isOpen = true,
                priceLevel = 0,
                estimatedCost = 0
            )
        )
    }

    suspend fun searchShopping(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for shopping areas in SF")
        return listOf(
            AppPlace(
                id = "union_square",
                name = "Union Square",
                type = PlaceType.SHOPPING,
                lat = 37.7879,
                lng = -122.4075,
                rating = 4.3f,
                isOpen = true,
                priceLevel = 0,
                estimatedCost = 0
            ),
            AppPlace(
                id = "ferry_building",
                name = "Ferry Building Marketplace",
                type = PlaceType.SHOPPING,
                lat = 37.7956,
                lng = -122.3935,
                rating = 4.6f,
                isOpen = true,
                priceLevel = 0,
                estimatedCost = 0
            ),
            AppPlace(
                id = "ghirardelli",
                name = "Ghirardelli Square",
                type = PlaceType.SHOPPING,
                lat = 37.8058,
                lng = -122.4227,
                rating = 4.4f,
                isOpen = true,
                priceLevel = 0,
                estimatedCost = 0
            )
        )
    }
}

