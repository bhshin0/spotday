package com.spotday.app.api

import com.spotday.app.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object PlacesApiClient {
    private const val BASE_URL = "https://maps.googleapis.com/maps/api/place/"
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // Add your API interface here
    // interface PlacesApi {
    //     @GET("nearbysearch/json")
    //     suspend fun getNearbyPlaces(
    //         @Query("location") location: String,
    //         @Query("radius") radius: Int,
    //         @Query("type") type: String,
    //         @Query("key") key: String = BuildConfig.PLACES_API_KEY
    //     ): PlacesResponse
    // }

    // val placesApi: PlacesApi = retrofit.create(PlacesApi::class.java)
} 