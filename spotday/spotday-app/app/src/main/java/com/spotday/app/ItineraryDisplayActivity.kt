package com.spotday.app

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.spotday.app.api.PlacesRepository
import com.spotday.app.model.Event
import com.spotday.app.model.EventType
import com.spotday.app.model.ItineraryStop
import com.spotday.app.model.PlaceType
import com.spotday.app.model.StopType
import com.spotday.app.model.TransitEstimate
import com.spotday.app.service.ItineraryGenerator
import com.spotday.app.ui.theme.SpotDayTheme
import com.spotday.app.util.TransitHelper
import com.spotday.app.util.WeatherHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// San Francisco center
private val SF_CENTER = LatLng(37.7749, -122.4194)

class ItineraryDisplayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val startHour = intent.getIntExtra("startHour", 9)
        val endHour = intent.getIntExtra("endHour", 17)
        val totalBudget = intent.getIntExtra("totalBudget", 100)
        val isHungryNow = intent.getBooleanExtra("isHungryNow", false)
        val isSpontaneousMode = intent.getBooleanExtra("isSpontaneousMode", false)
        val activityTypes = intent.getStringArrayExtra("activityTypes")?.toList() ?: emptyList()
        val foodTypes = intent.getStringArrayExtra("foodTypes")?.toList() ?: emptyList()
        val serviceStyles = intent.getStringArrayExtra("serviceStyles")?.toList() ?: emptyList()
        val nightlifeTypes = intent.getStringArrayExtra("nightlifeTypes")?.toList() ?: emptyList()
        val selectedEventIds = intent.getStringArrayExtra("selectedEventIds")?.toList() ?: emptyList()
        val startLat = intent.getDoubleExtra("startLatitude", 0.0).takeIf { it != 0.0 }
        val startLng = intent.getDoubleExtra("startLongitude", 0.0).takeIf { it != 0.0 }
        
        Log.d("ItineraryDisplay", "Received: time range=$startHour-$endHour, budget=$totalBudget, hungryNow=$isHungryNow, spontaneous=$isSpontaneousMode, activities=$activityTypes, food=$foodTypes, styles=${if (serviceStyles.isEmpty()) "ALL" else serviceStyles}, nightlife=$nightlifeTypes, events=${selectedEventIds.size}")
        if (startLat != null && startLng != null) {
            Log.d("ItineraryDisplay", "Starting location: ($startLat, $startLng)")
        }
        
        setContent {
            SpotDayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ItineraryDisplayScreen(
                        startHour = startHour,
                        endHour = endHour,
                        totalBudget = totalBudget,
                        isHungryNow = isHungryNow,
                        isSpontaneousMode = isSpontaneousMode,
                        startLatitude = startLat,
                        startLongitude = startLng,
                        activityTypes = activityTypes,
                        foodTypes = foodTypes,
                        serviceStyles = serviceStyles,
                        nightlifeTypes = nightlifeTypes,
                        selectedEventIds = selectedEventIds
                    )
                }
            }
        }
    }
}

class ItineraryViewModel(
    private val startHour: Int,
    private val endHour: Int,
    private val totalBudget: Int,
    private val isHungryNow: Boolean,
    private val isSpontaneousMode: Boolean,
    private val startLatitude: Double?,
    private val startLongitude: Double?,
    private val activityTypes: List<String>,
    private val foodTypes: List<String>,
    private val serviceStyles: List<String>,
    private val nightlifeTypes: List<String>,
    private val selectedEventIds: List<String>,
    placesRepository: PlacesRepository
) : ViewModel() {
    
    private val itineraryGenerator = ItineraryGenerator(placesRepository)
    
    var itinerary by mutableStateOf<List<ItineraryStop>>(emptyList())
        private set
    
    var isLoading by mutableStateOf(true)
        private set
    
    var error by mutableStateOf<String?>(null)
        private set
    
    // Weather state - using regular vars to avoid Compose snapshot issues in coroutines
    // These will be passed explicitly to composables
    private var _weatherForecast = WeatherHelper.getMockForecast()
    val weatherForecast: WeatherHelper.WeatherForecast get() = _weatherForecast
    
    private var _avoidOutdoor = false
    val avoidOutdoor: Boolean get() = _avoidOutdoor
    
    init {
        _avoidOutdoor = WeatherHelper.shouldAvoidOutdoor(_weatherForecast)
        Log.d("ItineraryViewModel", "Weather: ${_weatherForecast.description}, avoidOutdoor: $_avoidOutdoor")
        generateItinerary(_avoidOutdoor)
    }
    
    fun regenerateItinerary() {
        Log.d("ItineraryViewModel", "Regenerating itinerary...")
        _weatherForecast = WeatherHelper.getMockForecast()
        _avoidOutdoor = WeatherHelper.shouldAvoidOutdoor(_weatherForecast)
        generateItinerary(_avoidOutdoor)
    }
    
    private fun generateItinerary(avoidOutdoorParam: Boolean) {
        viewModelScope.launch {
            try {
                isLoading = true
                error = null
                Log.d("ItineraryViewModel", "Generating itinerary...")
                itinerary = withContext(Dispatchers.IO) {
                    itineraryGenerator.generateItinerary(
                        startHour = startHour,
                        endHour = endHour,
                        totalBudget = totalBudget,
                        activityTypes = activityTypes,
                        foodTypes = foodTypes,
                        serviceStyles = serviceStyles,
                        isHungryNow = isHungryNow,
                        isSpontaneousMode = isSpontaneousMode,
                        userStartLat = startLatitude,
                        userStartLng = startLongitude,
                        nightlifeTypes = nightlifeTypes,
                        avoidOutdoor = avoidOutdoorParam,
                        selectedEventIds = selectedEventIds
                    )
                }
                Log.d("ItineraryViewModel", "Itinerary generated with ${itinerary.size} stops")
                isLoading = false
            } catch (e: Exception) {
                Log.e("ItineraryViewModel", "Error generating itinerary", e)
                error = "Failed to generate itinerary: ${e.message}"
                isLoading = false
            }
        }
    }
}

@Composable
fun ItineraryDisplayScreen(
    startHour: Int,
    endHour: Int,
    totalBudget: Int,
    isHungryNow: Boolean,
    isSpontaneousMode: Boolean = false,
    startLatitude: Double? = null,
    startLongitude: Double? = null,
    activityTypes: List<String>,
    foodTypes: List<String>,
    serviceStyles: List<String> = emptyList(),
    nightlifeTypes: List<String> = emptyList(),
    selectedEventIds: List<String> = emptyList()
) {
    val context = LocalContext.current
    val placesRepository = remember { PlacesRepository(context) }
    
    val viewModel: ItineraryViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ItineraryViewModel(
                    startHour, 
                    endHour, 
                    totalBudget, 
                    isHungryNow, 
                    isSpontaneousMode,
                    startLatitude,
                    startLongitude,
                    activityTypes, 
                    foodTypes,
                    serviceStyles,
                    nightlifeTypes,
                    selectedEventIds,
                    placesRepository
                ) as T
            }
        }
    )
    
    // Request location permission
    var hasLocationPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
    }
    
    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    
    when {
        viewModel.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Generating your itinerary...")
                }
            }
        }
        viewModel.error != null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = viewModel.error ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        else -> {
            ItineraryContent(
                itinerary = viewModel.itinerary,
                totalBudget = totalBudget,
                hasLocationPermission = hasLocationPermission,
                weatherForecast = viewModel.weatherForecast,
                avoidOutdoor = viewModel.avoidOutdoor,
                onRegenerate = { viewModel.regenerateItinerary() }
            )
        }
    }
}

@Composable
fun ItineraryContent(
    itinerary: List<ItineraryStop>,
    totalBudget: Int,
    hasLocationPermission: Boolean,
    weatherForecast: WeatherHelper.WeatherForecast,
    avoidOutdoor: Boolean,
    onRegenerate: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Map (top half)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            ItineraryMap(itinerary = itinerary)
        }
        
        // Timeline (bottom half)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.background)
        ) {
            ItineraryTimeline(
                itinerary = itinerary,
                totalBudget = totalBudget,
                weatherForecast = weatherForecast,
                avoidOutdoor = avoidOutdoor,
                onRegenerate = onRegenerate
            )
        }
    }
}

@Composable
fun ItineraryMap(itinerary: List<ItineraryStop>) {
    // Only load Google Maps if we have itinerary data
    if (itinerary.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Map will appear here",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(SF_CENTER, 12f)
    }
    
    // Separate main stops from waypoints/quick stops for different rendering
    val mainStops = itinerary.filter { it.stopType == StopType.MAIN }
    val waypointStops = itinerary.filter { it.stopType == StopType.WAYPOINT || it.stopType == StopType.QUICK_STOP }
    
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isMyLocationEnabled = false,
            isTrafficEnabled = false,
            isBuildingEnabled = false
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = true,
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = false
        )
    ) {
        // Add numbered markers for main stops
        var mainIndex = 1
        mainStops.forEach { stop ->
            val lat = stop.place?.lat ?: stop.event?.venueLatitude ?: return@forEach
            val lng = stop.place?.lng ?: stop.event?.venueLongitude ?: return@forEach
            val name = stop.place?.name ?: stop.event?.name ?: "Stop"
            val position = LatLng(lat, lng)
            
            Marker(
                state = MarkerState(position = position),
                title = "${mainIndex}. $name",
                snippet = "${stop.startTime} - ${stop.endTime}"
            )
            mainIndex++
        }
        
        // Add small circles for waypoints and quick stops
        waypointStops.forEach { stop ->
            val lat = stop.waypoint?.lat ?: return@forEach
            val lng = stop.waypoint?.lng ?: return@forEach
            val position = LatLng(lat, lng)
            
            Circle(
                center = position,
                radius = 40.0, // Small circle in meters
                fillColor = if (stop.stopType == StopType.QUICK_STOP) 
                    Color(0x99E8DEF8) else Color(0x99CCCCCC),
                strokeColor = Color.White,
                strokeWidth = 2f
            )
        }
        
        // Draw polylines connecting all stops
        if (itinerary.size > 1) {
            // Build list of all positions (main stops and waypoints)
            val allPositions = itinerary.mapNotNull { stop ->
                when {
                    stop.place != null -> LatLng(stop.place.lat, stop.place.lng)
                    stop.event != null -> LatLng(stop.event.venueLatitude, stop.event.venueLongitude)
                    stop.waypoint != null -> LatLng(stop.waypoint.lat, stop.waypoint.lng)
                    else -> null
                }
            }
            
            if (allPositions.size > 1) {
                Polyline(
                    points = allPositions,
                    color = Color(0xFF6650A4),
                    width = 8f
                )
            }
        }
    }
}

@Composable
fun ItineraryTimeline(
    itinerary: List<ItineraryStop>,
    totalBudget: Int,
    weatherForecast: WeatherHelper.WeatherForecast,
    avoidOutdoor: Boolean,
    onRegenerate: () -> Unit
) {
    val scrollState = rememberScrollState()
    val estimatedCost = itinerary.sumOf { it.place.estimatedCost }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Your Itinerary",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        if (itinerary.isEmpty()) {
            Text(
                text = "No itinerary available. Try selecting different preferences.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Weather banner
            WeatherBanner(
                forecast = weatherForecast,
                avoidOutdoor = avoidOutdoor
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Budget summary card
            BudgetSummaryCard(
                budget = totalBudget,
                estimatedCost = estimatedCost
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            itinerary.forEachIndexed { index, stop ->
                ItineraryStopCard(stop = stop)
                
                // Add travel time indicator between stops
                if (index < itinerary.size - 1) {
                    val nextStop = itinerary[index + 1]
                    TravelTimeIndicator(transitEstimate = nextStop.transitEstimate)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Regenerate button
            OutlinedButton(
                onClick = onRegenerate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show Different Options")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun BudgetSummaryCard(budget: Int, estimatedCost: Int) {
    val isOverBudget = estimatedCost > budget
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverBudget)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Budget:",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "$$budget",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Estimated:",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "$$estimatedCost",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isOverBudget) "Over Budget:" else "Remaining:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "$${kotlin.math.abs(budget - estimatedCost)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOverBudget)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ItineraryStopCard(stop: ItineraryStop) {
    // Handle different stop types
    when (stop.stopType) {
        StopType.MAIN -> MainStopCard(stop)
        StopType.WAYPOINT -> WaypointCard(stop)
        StopType.QUICK_STOP -> QuickStopCard(stop)
        StopType.FREE_TIME -> FreeTimeCard(stop)
    }
}

@Composable
fun MainStopCard(stop: ItineraryStop) {
    val isNightlife = stop.place?.type == PlaceType.NIGHTLIFE
    val isEvent = stop.event != null
    val placeName = stop.place?.name ?: stop.event?.name ?: "Unknown"
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isEvent) 4.dp else 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = when {
            isEvent -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
            isNightlife -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
            else -> CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isEvent -> "${getEventTypeEmoji(stop.event!!.eventType)} "
                        isNightlife -> "🍸 "
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = placeName,
                    style = MaterialTheme.typography.titleLarge,
                    color = when {
                        isEvent -> MaterialTheme.colorScheme.onSecondaryContainer
                        isNightlife -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
            
            if (isEvent && stop.event != null) {
                Text(
                    text = "@ ${stop.event.venueName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${stop.startTime} - ${stop.endTime}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = when {
                        isEvent -> getEventTypeLabel(stop.event!!.eventType)
                        else -> stop.place?.type?.let { type ->
                            when (type) {
                                PlaceType.MUSEUM -> "Museum"
                                PlaceType.PARK -> "Park"
                                PlaceType.RESTAURANT -> "Restaurant"
                                PlaceType.WATERFRONT -> "Waterfront"
                                PlaceType.HISTORIC_SITE -> "Historic Site"
                                PlaceType.SHOPPING -> "Shopping"
                                PlaceType.NIGHTLIFE -> "Nightlife"
                            }
                        } ?: ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        isEvent -> MaterialTheme.colorScheme.secondary
                        isNightlife -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.secondary
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isEvent && stop.place != null) {
                    Text(
                        text = "⭐ ${stop.place.rating}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }
                
                Text(
                    text = "${stop.durationMinutes} min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (stop.place != null) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (stop.place.estimatedCost > 0) "$${stop.place.estimatedCost}" else "Free",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (stop.place.estimatedCost > 0)
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (isEvent) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "📌 Fixed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun WaypointCard(stop: ItineraryStop) {
    // Smaller, muted card for scenic pass-through points
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "◇",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = stop.waypoint?.name ?: "Scenic Point",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            stop.waypoint?.description?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun QuickStopCard(stop: ItineraryStop) {
    // Compact card for coffee/photo breaks
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "☕",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stop.waypoint?.name ?: "Quick Break",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                stop.waypoint?.description?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Text(
                text = "${stop.durationMinutes} min",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun FreeTimeCard(stop: ItineraryStop) {
    // Card for exploration/free time periods
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🚶",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Free time to explore",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stop.neighborhoodName?.let { "Explore $it" } ?: "Wander and discover",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                text = "${stop.durationMinutes} min",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
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

private fun getEventTypeLabel(eventType: EventType): String {
    return when (eventType) {
        EventType.CONCERT -> "Concert"
        EventType.SPORTS -> "Sports"
        EventType.THEATER -> "Theater"
        EventType.COMEDY -> "Comedy"
        EventType.FOOD_FESTIVAL -> "Food Festival"
        EventType.STREET_FAIR -> "Street Fair"
        EventType.CLASS_WORKSHOP -> "Class/Workshop"
    }
}

@Composable
fun WeatherBanner(
    forecast: WeatherHelper.WeatherForecast,
    avoidOutdoor: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (avoidOutdoor) 
                MaterialTheme.colorScheme.errorContainer 
            else 
                MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = WeatherHelper.getWeatherEmoji(forecast.condition),
                style = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${forecast.temperatureF}°F - ${forecast.description}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (avoidOutdoor)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (avoidOutdoor) {
                    Text(
                        text = WeatherHelper.getWeatherAdvice(forecast),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun TravelTimeIndicator(transitEstimate: TransitEstimate?) {
    if (transitEstimate == null) return
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        
        Text(
            text = "  ${TransitHelper.formatDistance(transitEstimate.distanceKm)} • 🚶${transitEstimate.walkingMinutes}m 🚇${transitEstimate.transitMinutes}m 🚗${transitEstimate.drivingMinutes}m  ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
} 