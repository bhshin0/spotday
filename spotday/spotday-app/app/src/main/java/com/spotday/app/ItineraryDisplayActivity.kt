package com.spotday.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class Venue(
    val name: String,
    val type: String, // "Museum", "Restaurant"
    val latitude: Double,
    val longitude: Double,
    val openingHour: Int, // 24-hour format
    val closingHour: Int, // 24-hour format
    val typicalDurationMinutes: Int
)

data class ItineraryItem(
    val venueName: String,
    val startTime: Calendar,
    val endTime: Calendar
)

class ItineraryDisplayActivity : AppCompatActivity() {

    // Simplified opening hours check (ignores day of week for this prototype)
    private fun isVenueOpen(venue: Venue, time: Calendar): Boolean {
        val hourOfDay = time.get(Calendar.HOUR_OF_DAY)
        return hourOfDay >= venue.openingHour && hourOfDay < venue.closingHour
    }

    private fun formatTime(calendar: Calendar): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.US)
        return sdf.format(calendar.time)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_itinerary_display)

        val tvSchedule = findViewById<TextView>(R.id.tvSchedule)
        val tvMapData = findViewById<TextView>(R.id.tvMapData)

        val totalDurationHours = intent.getIntExtra("totalDurationHours", 4)
        // For this slice, we ignore the passed totalDurationHours and use fixed values
        // to ensure the hardcoded itinerary works.

        // --- Hardcoded Data for the Thin Slice ---
        val museum = Venue(
            name = "SFMOMA",
            type = "Museum",
            latitude = 37.7857,
            longitude = -122.4011,
            openingHour = 10, // 10 AM
            closingHour = 17, // 5 PM
            typicalDurationMinutes = 120 // 2 hours
        )

        val tacoPlace = Venue(
            name = "La Taqueria (Mission)",
            type = "Restaurant",
            latitude = 37.7508,
            longitude = -122.4183,
            openingHour = 11, // 11 AM
            closingHour = 21, // 9 PM
            typicalDurationMinutes = 60 // 1 hour
        )

        val travelTimeMuseumToTacosMinutes = 30
        val overallWindowHours = 4 // The user's desired total time

        // --- Scheduling Logic ---
        val scheduleText = StringBuilder()
        val mapDataText = StringBuilder()
        val itineraryItems = mutableListOf<ItineraryItem>()

        // Assume starting the day at 10:00 AM for this prototype
        val currentTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val itineraryEndTimeLimit = (currentTime.clone() as Calendar).apply{
            add(Calendar.HOUR, overallWindowHours)
        }

        scheduleText.append("Proposed Itinerary (Target Start: ${formatTime(currentTime)}, Window: $overallWindowHours hours):\n\n")

        // 1. Try to schedule Museum
        var canSchedule = false
        if (isVenueOpen(museum, currentTime)) {
            val museumVisitStartTime = currentTime.clone() as Calendar
            val museumVisitEndTime = (museumVisitStartTime.clone() as Calendar).apply {
                add(Calendar.MINUTE, museum.typicalDurationMinutes)
            }
            if (!museumVisitEndTime.after(itineraryEndTimeLimit)) {
                itineraryItems.add(ItineraryItem(museum.name, museumVisitStartTime, museumVisitEndTime))
                scheduleText.append("${formatTime(museumVisitStartTime)} - ${formatTime(museumVisitEndTime)}: ${museum.name}\n")
                mapDataText.append("${museum.name}: (${museum.latitude}, ${museum.longitude})\n")
                currentTime.time = museumVisitEndTime.time // Update current time
                canSchedule = true
            } else {
                 scheduleText.append("${museum.name} visit exceeds the ${overallWindowHours}hr window.\n")
            }
        } else {
            scheduleText.append("${museum.name} is closed at ${formatTime(currentTime)}.\n")
        }

        // 2. Travel to Taco Place
        if(canSchedule) {
            val travelStartTime = currentTime.clone() as Calendar
            val travelEndTime = (travelStartTime.clone() as Calendar).apply {
                add(Calendar.MINUTE, travelTimeMuseumToTacosMinutes)
            }
             if (!travelEndTime.after(itineraryEndTimeLimit)) {
                scheduleText.append("${formatTime(travelStartTime)} - ${formatTime(travelEndTime)}: Travel to ${tacoPlace.name}\n")
                currentTime.time = travelEndTime.time
            } else {
                scheduleText.append("Travel to ${tacoPlace.name} exceeds the ${overallWindowHours}hr window.\n")
                canSchedule = false // Cannot continue if travel exceeds limit
            }
        }

        // 3. Try to schedule Taco Place
        if (canSchedule) {
            if (isVenueOpen(tacoPlace, currentTime)) {
                val tacoVisitStartTime = currentTime.clone() as Calendar
                val tacoVisitEndTime = (tacoVisitStartTime.clone() as Calendar).apply {
                    add(Calendar.MINUTE, tacoPlace.typicalDurationMinutes)
                }
                if (!tacoVisitEndTime.after(itineraryEndTimeLimit)) {
                    itineraryItems.add(ItineraryItem(tacoPlace.name, tacoVisitStartTime, tacoVisitEndTime))
                    scheduleText.append("${formatTime(tacoVisitStartTime)} - ${formatTime(tacoVisitEndTime)}: ${tacoPlace.name}\n")
                    mapDataText.append("${tacoPlace.name}: (${tacoPlace.latitude}, ${tacoPlace.longitude})\n")
                    currentTime.time = tacoVisitEndTime.time

                    // Check if total duration fits
                     val firstItemStart = itineraryItems.first().startTime
                     val lastItemEnd = itineraryItems.last().endTime
                     val totalScheduledDurationMillis = lastItemEnd.timeInMillis - firstItemStart.timeInMillis
                     val totalScheduledDurationHours = totalScheduledDurationMillis / (1000.0 * 60 * 60)

                    scheduleText.append("\nTotal estimated time: %.1f hours.".format(totalScheduledDurationHours))
                    if (totalScheduledDurationHours > overallWindowHours) {
                        scheduleText.append(" (Exceeds ${overallWindowHours}hr window!)")
                    }
                } else {
                    scheduleText.append("${tacoPlace.name} visit exceeds the ${overallWindowHours}hr window.\n")
                }
            } else {
                scheduleText.append("${tacoPlace.name} is closed at ${formatTime(currentTime)}.\n")
            }
        }

        if (itineraryItems.isEmpty()) {
            scheduleText.append("Could not schedule any activities within the given constraints.")
        }

        tvSchedule.text = scheduleText.toString()
        tvMapData.text = mapDataText.toString()

        val apiKey = BuildConfig.MAPS_API_KEY
    }
} 