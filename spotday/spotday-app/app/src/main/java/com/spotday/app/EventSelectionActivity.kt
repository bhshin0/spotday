package com.spotday.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spotday.app.api.EventsRepository
import com.spotday.app.model.Event
import com.spotday.app.model.EventType
import com.spotday.app.ui.theme.SpotDayTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EventSelectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val startHour = intent.getIntExtra("startHour", 9)
        val endHour = intent.getIntExtra("endHour", 17)
        val totalBudget = intent.getIntExtra("totalBudget", 100)
        val isHungryNow = intent.getBooleanExtra("isHungryNow", false)
        val isSpontaneousMode = intent.getBooleanExtra("isSpontaneousMode", false)
        val startLat = intent.getDoubleExtra("startLatitude", 0.0).takeIf { it != 0.0 }
        val startLng = intent.getDoubleExtra("startLongitude", 0.0).takeIf { it != 0.0 }
        val activityTypes = intent.getStringArrayExtra("activityTypes")?.toList() ?: emptyList()
        val explorationMode = intent.getStringExtra("explorationMode") ?: "ONE_AREA"
        val cityId = intent.getStringExtra("cityId") ?: "san_francisco"
        
        setContent {
            SpotDayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EventSelectionScreen(
                        startHour = startHour,
                        endHour = endHour,
                        totalBudget = totalBudget,
                        isHungryNow = isHungryNow,
                        isSpontaneousMode = isSpontaneousMode,
                        startLatitude = startLat,
                        startLongitude = startLng,
                        activityTypes = activityTypes,
                        explorationMode = explorationMode,
                        cityId = cityId
                    )
                }
            }
        }
    }
}

/**
 * Check if two events overlap in time.
 */
private fun eventsOverlap(event1: Event, event2: Event): Boolean {
    val start1 = event1.startHour * 60 + event1.startMinute
    val end1 = start1 + event1.durationMinutes
    val start2 = event2.startHour * 60 + event2.startMinute
    val end2 = start2 + event2.durationMinutes
    
    // Events overlap if one starts during the other
    return (start1 < end2) && (start2 < end1)
}

/**
 * Find which event conflicts with the given event among selected events.
 */
private fun findConflictingEvent(newEvent: Event, selectedEvents: List<Event>): Event? {
    return selectedEvents.find { eventsOverlap(it, newEvent) }
}

@Composable
fun EventSelectionScreen(
    startHour: Int,
    endHour: Int,
    totalBudget: Int,
    isHungryNow: Boolean,
    isSpontaneousMode: Boolean,
    startLatitude: Double?,
    startLongitude: Double?,
    activityTypes: List<String>,
    explorationMode: String = "ONE_AREA",
    cityId: String = "san_francisco"
) {
    val context = LocalContext.current
    var events by remember { mutableStateOf<List<Event>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedEventIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var overlapError by remember { mutableStateOf<String?>(null) }
    
    // Load events for the time window
    LaunchedEffect(startHour, endHour, cityId) {
        withContext(Dispatchers.IO) {
            val repository = EventsRepository(cityId)
            events = repository.getEventsForTimeWindow(startHour, endHour)
            isLoading = false
        }
    }
    
    // Helper to get selected events as list
    val selectedEvents = events.filter { it.id in selectedEventIds }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Events Happening Today",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Select any events you'd like to attend",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Text(
            text = "(Optional - these become fixed anchors in your itinerary)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Show overlap error if present
        if (overlapError != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚠️",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = overlapError!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (events.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "😔",
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        text = "No Events Available",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "We couldn't load live events right now. Don't worry - we'll still build you an amazing itinerary with places to explore!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(events) { event ->
                    EventCard(
                        event = event,
                        isSelected = event.id in selectedEventIds,
                        onToggle = {
                            if (event.id in selectedEventIds) {
                                // Deselecting - always allowed
                                selectedEventIds = selectedEventIds - event.id
                                overlapError = null
                            } else {
                                // Selecting - check for overlaps
                                val conflict = findConflictingEvent(event, selectedEvents)
                                if (conflict != null) {
                                    // Show error - don't allow selection
                                    overlapError = "\"${event.name}\" overlaps with \"${conflict.name}\""
                                } else {
                                    // No conflict - allow selection
                                    selectedEventIds = selectedEventIds + event.id
                                    overlapError = null
                                }
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                try {
                    Log.d("EventSelection", "Selected events: $selectedEventIds")
                    
                    val intent = Intent(context, RestaurantSelectionActivity::class.java).apply {
                        putExtra("startHour", startHour)
                        putExtra("endHour", endHour)
                        putExtra("totalBudget", totalBudget)
                        putExtra("isHungryNow", isHungryNow)
                        putExtra("isSpontaneousMode", isSpontaneousMode)
                        putExtra("activityTypes", activityTypes.toTypedArray())
                        putExtra("selectedEventIds", selectedEventIds.toTypedArray())
                        putExtra("explorationMode", explorationMode)
                        putExtra("cityId", cityId)
                        if (startLatitude != null) putExtra("startLatitude", startLatitude)
                        if (startLongitude != null) putExtra("startLongitude", startLongitude)
                    }
                    context.startActivity(intent)
                    (context as? ComponentActivity)?.finish()
                } catch (e: Exception) {
                    Log.e("EventSelection", "Error starting activity", e)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (selectedEventIds.isEmpty()) "Skip Events" else "Continue with ${selectedEventIds.size} Event${if (selectedEventIds.size != 1) "s" else ""}")
        }
    }
}

@Composable
fun EventCard(
    event: Event,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = { if (!event.isSoldOut) onToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = getEventTypeEmoji(event.eventType),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = event.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = event.venueName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = formatEventTime(event.startHour, event.startMinute, event.durationMinutes),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                if (event.priceMin != null || event.priceMax != null) {
                    Text(
                        text = formatPriceRange(event.priceMin, event.priceMax),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                if (event.isSoldOut) {
                    Text(
                        text = "SOLD OUT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            if (!event.isSoldOut) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() }
                )
            }
        }
    }
}

private fun getEventTypeEmoji(eventType: EventType): String {
    return when (eventType) {
        EventType.CONCERT -> "🎵"
        EventType.SPORTS -> "⚾"
        EventType.THEATER -> "🎭"
        EventType.COMEDY -> "😂"
        EventType.FOOD_FESTIVAL -> "🍔"
        EventType.STREET_FAIR -> "🎪"
        EventType.CLASS_WORKSHOP -> "🎨"
    }
}

private fun formatEventTime(startHour: Int, startMinute: Int, durationMinutes: Int): String {
    val startFormatted = formatHour(startHour, startMinute)
    val endHour = startHour + (startMinute + durationMinutes) / 60
    val endMinute = (startMinute + durationMinutes) % 60
    val endFormatted = formatHour(endHour % 24, endMinute)
    val durationHours = durationMinutes / 60
    val durationMins = durationMinutes % 60
    val durationStr = if (durationHours > 0 && durationMins > 0) {
        "${durationHours}h ${durationMins}m"
    } else if (durationHours > 0) {
        "${durationHours}h"
    } else {
        "${durationMins}m"
    }
    return "$startFormatted - $endFormatted ($durationStr)"
}

private fun formatHour(hour: Int, minute: Int): String {
    val adjustedHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val amPm = if (hour < 12 || hour == 24) "AM" else "PM"
    return if (minute == 0) {
        "$adjustedHour $amPm"
    } else {
        "$adjustedHour:${minute.toString().padStart(2, '0')} $amPm"
    }
}

private fun formatPriceRange(priceMin: Double?, priceMax: Double?): String {
    return when {
        priceMin != null && priceMax != null -> "$${priceMin.toInt()} - $${priceMax.toInt()}"
        priceMin != null -> "From $${priceMin.toInt()}"
        priceMax != null -> "Up to $${priceMax.toInt()}"
        else -> "Free"
    }
}
