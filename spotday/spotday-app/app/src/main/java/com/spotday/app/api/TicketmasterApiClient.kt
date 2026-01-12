package com.spotday.app.api

import com.spotday.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Ticketmaster Discovery API v2 client.
 * https://developer.ticketmaster.com/products-and-docs/apis/discovery-api/v2/
 */
object TicketmasterApiClient {
    
    private const val BASE_URL = "https://app.ticketmaster.com/discovery/v2/"
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val api: TicketmasterApi = retrofit.create(TicketmasterApi::class.java)
}

/**
 * Ticketmaster Discovery API endpoints.
 */
interface TicketmasterApi {
    
    /**
     * Search for events.
     * 
     * @param apikey API key (required)
     * @param city City name (e.g., "San Francisco")
     * @param stateCode State code (e.g., "CA")
     * @param startDateTime Start date/time in ISO 8601 format (e.g., "2025-01-11T00:00:00Z")
     * @param endDateTime End date/time in ISO 8601 format
     * @param classificationName Filter by classification (e.g., "music", "sports", "arts")
     * @param sort Sort order: "date,asc", "date,desc", "relevance,desc", "name,asc"
     * @param size Number of results (max 200)
     * @param page Page number (0-indexed)
     */
    @GET("events.json")
    suspend fun searchEvents(
        @Query("apikey") apikey: String = BuildConfig.TICKETMASTER_API_KEY,
        @Query("city") city: String? = null,
        @Query("stateCode") stateCode: String? = null,
        @Query("startDateTime") startDateTime: String? = null,
        @Query("endDateTime") endDateTime: String? = null,
        @Query("classificationName") classificationName: String? = null,
        @Query("sort") sort: String = "relevance,desc",
        @Query("size") size: Int = 50,
        @Query("page") page: Int = 0
    ): TicketmasterEventsResponse
    
    /**
     * Get events by specific segment (Music, Sports, Arts & Theatre, etc.)
     */
    @GET("events.json")
    suspend fun searchEventsBySegment(
        @Query("apikey") apikey: String = BuildConfig.TICKETMASTER_API_KEY,
        @Query("city") city: String? = null,
        @Query("stateCode") stateCode: String? = null,
        @Query("segmentName") segmentName: String,
        @Query("startDateTime") startDateTime: String? = null,
        @Query("endDateTime") endDateTime: String? = null,
        @Query("sort") sort: String = "relevance,desc",
        @Query("size") size: Int = 50
    ): TicketmasterEventsResponse
}
