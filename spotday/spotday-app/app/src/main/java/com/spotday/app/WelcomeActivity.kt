package com.spotday.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import com.spotday.app.util.LocationHelper
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
    val coroutineScope = rememberCoroutineScope()

    // Load saved preferences
    var selectedCity by remember {
        mutableStateOf(availableCities.find { it.id == preferencesManager.getSelectedCity() } ?: availableCities.first())
    }
    var cityDropdownExpanded by remember { mutableStateOf(false) }
    var isPrefetching by remember { mutableStateOf(false) }
    var timeRange by remember {
        mutableStateOf(preferencesManager.getTimeRangeStart()..preferencesManager.getTimeRangeEnd())
    }
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

    // Location state
    var useMyLocation by remember { mutableStateOf(preferencesManager.getUseMyLocation()) }
    var hasLocationPermission by remember {
        mutableStateOf(LocationHelper.hasLocationPermission(context))
    }
    var isGettingLocation by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }

    // Permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        hasLocationPermission = fineLocationGranted || coarseLocationGranted

        if (hasLocationPermission) {
            Log.d("WelcomeActivity", "Location permission granted")
            locationError = null
            // Fetch location now that we have permission
            coroutineScope.launch {
                isGettingLocation = true
                currentLocation = LocationHelper.getCurrentLocation(context)
                isGettingLocation = false
                if (currentLocation == null) {
                    locationError = "Could not get location"
                }
            }
        } else {
            Log.d("WelcomeActivity", "Location permission denied")
            useMyLocation = false
            preferencesManager.saveUseMyLocation(false)
            locationError = "Location permission denied"
        }
    }

    // Fetch location when toggle enabled
    LaunchedEffect(useMyLocation) {
        preferencesManager.saveUseMyLocation(useMyLocation)
        if (useMyLocation) {
            if (!hasLocationPermission) {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } else {
                isGettingLocation = true
                currentLocation = LocationHelper.getCurrentLocation(context)
                isGettingLocation = false
                if (currentLocation == null) {
                    locationError = "Could not get location"
                } else {
                    locationError = null
                }
            }
        } else {
            currentLocation = null
            locationError = null
        }
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
    LaunchedEffect(timeRange) {
        preferencesManager.saveTimeRange(timeRange.start, timeRange.endInclusive)
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
    val totalHours = (timeRange.endInclusive - timeRange.start).toInt()

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

        // Use My Location toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Use My Location",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (useMyLocation && currentLocation != null)
                        "Starting from your current spot"
                    else if (isGettingLocation)
                        "Getting location..."
                    else if (locationError != null)
                        locationError!!
                    else
                        "Start itinerary from where you are",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (locationError != null)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isGettingLocation) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Switch(
                    checked = useMyLocation,
                    onCheckedChange = { useMyLocation = it }
                )
            }
        }

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

        // Time selection
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

        // Check for hidden bar crawl mode trigger
        // Triggered when: start >= 23 (11pm+) OR both handles at midnight (24-24)
        val isBarCrawlMode = timeRange.start >= 23f ||
            (timeRange.start >= 24f && timeRange.endInclusive >= 24f)

        Button(
            onClick = {
                try {
                    // Check for bar crawl mode first
                    if (isBarCrawlMode) {
                        Log.d("WelcomeActivity", "BAR CRAWL MODE ACTIVATED! Time range: ${timeRange.start}-${timeRange.endInclusive}")
                        val intent = Intent(context, BarCrawlSelectionActivity::class.java).apply {
                            putExtra("totalBudget", budget)
                            putExtra("cityId", selectedCity.id)
                            // Pass location for spontaneous bar crawl
                            if (useMyLocation && currentLocation != null) {
                                putExtra("startLatitude", currentLocation!!.first)
                                putExtra("startLongitude", currentLocation!!.second)
                                putExtra("isSpontaneousMode", true)
                            }
                        }
                        context.startActivity(intent)
                        (context as? ComponentActivity)?.finish()
                        return@Button
                    }

                    val startHour = timeRange.start.toInt()
                    val endHour = timeRange.endInclusive.toInt()

                    // Determine if spontaneous mode is enabled
                    val isSpontaneous = useMyLocation && currentLocation != null

                    val intent = Intent(context, ActivityPreferencesActivity::class.java).apply {
                        putExtra("startHour", startHour)
                        putExtra("endHour", endHour)
                        putExtra("totalBudget", budget)
                        putExtra("isHungryNow", isHungryNow)
                        putExtra("isSpontaneousMode", isSpontaneous)
                        putExtra("explorationMode", explorationMode.name)
                        putExtra("cityId", selectedCity.id)
                        // Pass GPS coordinates if available
                        if (isSpontaneous) {
                            putExtra("startLatitude", currentLocation!!.first)
                            putExtra("startLongitude", currentLocation!!.second)
                        }
                    }
                    Log.d("WelcomeActivity", "Starting ActivityPreferencesActivity from $startHour to $endHour with $$budget budget, hungryNow=$isHungryNow, spontaneous=$isSpontaneous")
                    context.startActivity(intent)
                    (context as? ComponentActivity)?.finish()
                } catch (e: Exception) {
                    Log.e("WelcomeActivity", "Error starting activity", e)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isBarCrawlMode) "Let's Go!" else "Continue")
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
