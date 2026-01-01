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
import com.spotday.app.model.ItineraryStop
import com.spotday.app.model.PlaceType
import com.spotday.app.service.ItineraryGenerator
import com.spotday.app.ui.theme.SpotDayTheme
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
        val activityTypes = intent.getStringArrayExtra("activityTypes")?.toList() ?: emptyList()
        val foodTypes = intent.getStringArrayExtra("foodTypes")?.toList() ?: emptyList()
        
        Log.d("ItineraryDisplay", "Received: time range=$startHour-$endHour, budget=$totalBudget, hungryNow=$isHungryNow, activities=$activityTypes, food=$foodTypes")
        
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
                        activityTypes = activityTypes,
                        foodTypes = foodTypes
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
    private val activityTypes: List<String>,
    private val foodTypes: List<String>,
    placesRepository: PlacesRepository
) : ViewModel() {
    
    private val itineraryGenerator = ItineraryGenerator(placesRepository)
    
    var itinerary by mutableStateOf<List<ItineraryStop>>(emptyList())
        private set
    
    var isLoading by mutableStateOf(true)
        private set
    
    var error by mutableStateOf<String?>(null)
        private set
    
    init {
        generateItinerary()
    }
    
    fun regenerateItinerary() {
        Log.d("ItineraryViewModel", "Regenerating itinerary...")
        generateItinerary()
    }
    
    private fun generateItinerary() {
        viewModelScope.launch {
            try {
                isLoading = true
                error = null
                Log.d("ItineraryViewModel", "Generating itinerary...")
                // Run on background thread to avoid ANR
                itinerary = withContext(Dispatchers.IO) {
                    itineraryGenerator.generateItinerary(
                        startHour = startHour,
                        endHour = endHour,
                        totalBudget = totalBudget,
                        activityTypes = activityTypes,
                        foodTypes = foodTypes,
                        isHungryNow = isHungryNow
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
    activityTypes: List<String>,
    foodTypes: List<String>
) {
    val context = LocalContext.current
    val placesRepository = remember { PlacesRepository(context) }
    
    val viewModel: ItineraryViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ItineraryViewModel(startHour, endHour, totalBudget, isHungryNow, activityTypes, foodTypes, placesRepository) as T
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
    
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isMyLocationEnabled = false,
            // Reduce map complexity for better performance
            isTrafficEnabled = false,
            isBuildingEnabled = false
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = true,
            // Disable expensive gestures on slow emulators
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = false
        )
    ) {
        // Add markers for each stop
        itinerary.forEach { stop ->
            val position = LatLng(stop.place.lat, stop.place.lng)
            Marker(
                state = MarkerState(position = position),
                title = stop.place.name,
                snippet = "${stop.startTime} - ${stop.endTime}"
            )
        }
        
        // Draw polyline connecting stops
        if (itinerary.size > 1) {
            val points = itinerary.map { LatLng(it.place.lat, it.place.lng) }
            Polyline(
                points = points,
                color = Color(0xFF6650A4),
                width = 10f
            )
        }
    }
}

@Composable
fun ItineraryTimeline(
    itinerary: List<ItineraryStop>,
    totalBudget: Int,
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
                    TravelTimeIndicator(distanceKm = itinerary[index + 1].distanceFromPreviousKm)
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stop.place.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
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
                    text = when (stop.place.type) {
                        PlaceType.MUSEUM -> "Museum"
                        PlaceType.PARK -> "Park"
                        PlaceType.RESTAURANT -> "Restaurant"
                        PlaceType.WATERFRONT -> "Waterfront"
                        PlaceType.HISTORIC_SITE -> "Historic Site"
                        PlaceType.SHOPPING -> "Shopping"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⭐ ${stop.place.rating}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text(
                    text = "${stop.durationMinutes} minutes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text(
                    text = if (stop.place.estimatedCost > 0) "$$${stop.place.estimatedCost}" else "Free",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (stop.place.estimatedCost > 0)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TravelTimeIndicator(distanceKm: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Divider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text(
            text = "  🚗 ${formatDistance(distanceKm)} • ~${estimateTravelTime(distanceKm)} min  ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Divider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

private fun formatDistance(km: Double): String {
    return if (km < 1.0) {
        "${(km * 1000).toInt()}m"
    } else {
        "${"%.1f".format(km)}km"
    }
}

private fun estimateTravelTime(km: Double): Int {
    // Assume 20 km/h average in SF (0.33 km/min)
    // Time = distance / speed = km / (km/min) = minutes
    return maxOf(5, (km / 0.33).toInt())
} 