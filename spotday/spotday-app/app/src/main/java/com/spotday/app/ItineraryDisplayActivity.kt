package com.spotday.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spotday.app.ui.theme.SpotDayTheme
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

class ItineraryDisplayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpotDayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ItineraryDisplayScreen()
                }
            }
        }
    }
}

@Composable
fun ItineraryDisplayScreen() {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Your Itinerary",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Hardcoded data for testing
        val museum = Venue(
            name = "SFMOMA",
            type = "Museum",
            latitude = 37.7857,
            longitude = -122.4011,
            openingHour = 10,
            closingHour = 17,
            typicalDurationMinutes = 120
        )

        val tacoPlace = Venue(
            name = "La Taqueria (Mission)",
            type = "Restaurant",
            latitude = 37.7508,
            longitude = -122.4183,
            openingHour = 11,
            closingHour = 21,
            typicalDurationMinutes = 60
        )

        // Display the itinerary
        Text(
            text = "10:00 AM - 12:00 PM: ${museum.name}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        
        Text(
            text = "12:00 PM - 12:30 PM: Travel to ${tacoPlace.name}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        
        Text(
            text = "12:30 PM - 1:30 PM: ${tacoPlace.name}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Map Data:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        Text(
            text = "${museum.name}: (${museum.latitude}, ${museum.longitude})",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Text(
            text = "${tacoPlace.name}: (${tacoPlace.latitude}, ${tacoPlace.longitude})",
            style = MaterialTheme.typography.bodyMedium
        )
    }
} 