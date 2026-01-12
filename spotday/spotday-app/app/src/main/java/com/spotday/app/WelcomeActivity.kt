package com.spotday.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.spotday.app.api.PlacesRepository
import com.spotday.app.model.ExplorationMode
import com.spotday.app.ui.theme.SpotDayTheme
import com.spotday.app.util.PreferencesManager
import kotlinx.coroutines.launch

// Available cities for testing
data class CityOption(val id: String, val displayName: String)

val availableCities = listOf(
    CityOption("san_francisco", "San Francisco"),
    CityOption("charlotte", "Charlotte, NC"),
    CityOption("phoenix", "Phoenix, AZ"),
    CityOption("tucson", "Tucson, AZ")
)

class WelcomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpotDayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WelcomeScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen() {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val placesRepository = remember { PlacesRepository(context) }
    val scope = rememberCoroutineScope()
    
    // Load saved preferences
    var selectedCity by remember { 
        mutableStateOf(availableCities.find { it.id == preferencesManager.getSelectedCity() } ?: availableCities.first())
    }
    var cityDropdownExpanded by remember { mutableStateOf(false) }
    var isPrefetching by remember { mutableStateOf(false) }
    
    var isSpontaneousMode by remember { mutableStateOf(preferencesManager.getSpontaneousMode()) }
    var timeRange by remember { 
        mutableStateOf(preferencesManager.getTimeRangeStart()..preferencesManager.getTimeRangeEnd()) 
    }
    var durationHours by remember { mutableStateOf(preferencesManager.getDurationHours()) }
    var budget by remember { mutableStateOf(preferencesManager.getBudget()) }
    var isHungryNow by remember { mutableStateOf(preferencesManager.getHungryNow()) }
    var explorationMode by remember { 
        mutableStateOf(
            try {
                ExplorationMode.valueOf(preferencesManager.getExplorationMode())
            } catch (e: Exception) {
                ExplorationMode.ONE_AREA
            }
        )
    }
    
    // Prefetch data for selected city on selection change
    LaunchedEffect(selectedCity) {
        preferencesManager.saveSelectedCity(selectedCity.id)
        isPrefetching = true
        try {
            placesRepository.prefetchForCity(selectedCity.id)
        } catch (e: Exception) {
            Log.e("WelcomeScreen", "Prefetch failed for ${selectedCity.id}", e)
        }
        isPrefetching = false
    }
    
    // Save preferences whenever they change
    LaunchedEffect(isSpontaneousMode) {
        preferencesManager.saveSpontaneousMode(isSpontaneousMode)
    }
    
    LaunchedEffect(timeRange) {
        preferencesManager.saveTimeRange(timeRange.start, timeRange.endInclusive)
    }
    
    LaunchedEffect(durationHours) {
        preferencesManager.saveDurationHours(durationHours)
    }
    
    LaunchedEffect(budget) {
        preferencesManager.saveBudget(budget)
    }
    
    LaunchedEffect(isHungryNow) {
        preferencesManager.saveHungryNow(isHungryNow)
    }
    
    LaunchedEffect(explorationMode) {
        preferencesManager.saveExplorationMode(explorationMode.name)
    }
    
    // Calculate total hours for current time selection
    val totalHours = if (isSpontaneousMode) {
        durationHours.toInt()
    } else {
        (timeRange.endInclusive - timeRange.start).toInt()
    }
    
    // Show exploration toggle when time window >= 6 hours
    val showExplorationToggle = totalHours >= 6

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to SpotDay!",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Let's build your perfect day in ${selectedCity.displayName}.",
            style = MaterialTheme.typography.bodyLarge
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // City Selector Dropdown
        ExposedDropdownMenuBox(
            expanded = cityDropdownExpanded,
            onExpandedChange = { cityDropdownExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedCity.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("City") },
                trailingIcon = {
                    if (isPrefetching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityDropdownExpanded)
                    }
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            
            ExposedDropdownMenu(
                expanded = cityDropdownExpanded,
                onDismissRequest = { cityDropdownExpanded = false }
            ) {
                availableCities.forEach { city ->
                    DropdownMenuItem(
                        text = { Text(city.displayName) },
                        onClick = {
                            selectedCity = city
                            cityDropdownExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Mode selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Spontaneous (Start Now)",
                style = MaterialTheme.typography.titleMedium
            )
            Switch(
                checked = isSpontaneousMode,
                onCheckedChange = { isSpontaneousMode = it }
            )
        }
        
        Text(
            text = if (isSpontaneousMode) "Starts immediately from your location" else "Plan for a specific time",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Conditional time selection based on mode
        if (isSpontaneousMode) {
            Text(
                text = "How long from now?",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Slider(
                value = durationHours,
                onValueChange = { durationHours = it },
                valueRange = 1f..8f,
                steps = 6
            )
            
            Text(
                text = "${durationHours.toInt()} hours",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Text(
                text = "What time will you be out?",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            RangeSlider(
                value = timeRange,
                onValueChange = { timeRange = it },
                valueRange = 6f..24f, // 6 AM to Midnight
                steps = 17 // Every hour
            )
            
            Text(
                text = "${formatTime(timeRange.start.toInt())} - ${formatTime(timeRange.endInclusive.toInt())}",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        
        // Exploration mode toggle - only shown for 6+ hour windows
        AnimatedVisibility(visible = showExplorationToggle) {
            Column(modifier = Modifier.padding(top = 24.dp)) {
                ExplorationModeToggle(
                    selectedMode = explorationMode,
                    onModeSelected = { explorationMode = it }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Are you hungry now?",
                style = MaterialTheme.typography.titleMedium
            )
            Switch(
                checked = isHungryNow,
                onCheckedChange = { isHungryNow = it }
            )
        }
        
        Text(
            text = if (isHungryNow) "We'll start with a meal" else "Meals at traditional times",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "What's your budget?",
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Slider(
            value = budget.toFloat(),
            onValueChange = { budget = it.toInt() },
            valueRange = 50f..250f,
            steps = 19
        )
        
        Text(
            text = "$$budget",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = {
                try {
                    // Calculate start and end hours based on mode
                    val (startHour, endHour) = if (isSpontaneousMode) {
                        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                        val spontaneousStart = currentHour
                        val spontaneousEnd = (currentHour + durationHours.toInt()).coerceAtMost(23)
                        Pair(spontaneousStart, spontaneousEnd)
                    } else {
                        Pair(timeRange.start.toInt(), timeRange.endInclusive.toInt())
                    }
                    
                    // Get mocked location for spontaneous mode
                    val (startLat, startLng) = if (isSpontaneousMode) {
                        com.spotday.app.util.LocationHelper.getRandomSFLocation()
                    } else {
                        Pair(0.0, 0.0) // Will be ignored in planned mode
                    }
                    
                    val intent = Intent(context, ActivityPreferencesActivity::class.java).apply {
                        putExtra("startHour", startHour)
                        putExtra("endHour", endHour)
                        putExtra("totalBudget", budget)
                        putExtra("isHungryNow", isHungryNow)
                        putExtra("isSpontaneousMode", isSpontaneousMode)
                        putExtra("explorationMode", explorationMode.name)
                        putExtra("cityId", selectedCity.id)
                        if (isSpontaneousMode) {
                            putExtra("startLatitude", startLat)
                            putExtra("startLongitude", startLng)
                        }
                    }
                    Log.d("WelcomeActivity", "Starting ActivityPreferencesActivity from $startHour to $endHour with $$budget budget, hungryNow=$isHungryNow, spontaneous=$isSpontaneousMode")
                    if (isSpontaneousMode) {
                        Log.d("WelcomeActivity", "Spontaneous mode starting location: ($startLat, $startLng)")
                    }
                    context.startActivity(intent)
                    (context as? ComponentActivity)?.finish()
                } catch (e: Exception) {
                    Log.e("WelcomeActivity", "Error starting activity", e)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

// Helper function to format hour as time (e.g., 9 -> "9:00 AM", 14 -> "2:00 PM")
fun formatTime(hour: Int): String {
    // Handle midnight (24 or 0) specially
    if (hour == 24 || hour == 0) return "12:00 AM"
    
    val period = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour > 12 -> hour - 12
        else -> hour
    }
    return "$displayHour:00 $period"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorationModeToggle(
    selectedMode: ExplorationMode,
    onModeSelected: (ExplorationMode) -> Unit
) {
    Column {
        Text(
            text = "How do you want to explore?",
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedMode == ExplorationMode.ONE_AREA,
                onClick = { onModeSelected(ExplorationMode.ONE_AREA) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text("One area")
            }
            SegmentedButton(
                selected = selectedMode == ExplorationMode.CITY_WIDE,
                onClick = { onModeSelected(ExplorationMode.CITY_WIDE) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text("See the city")
            }
        }
        
        Text(
            text = if (selectedMode == ExplorationMode.ONE_AREA)
                "Walkable day in one neighborhood"
            else
                "Visit highlights across town",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
} 