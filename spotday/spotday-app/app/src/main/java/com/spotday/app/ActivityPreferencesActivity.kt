package com.spotday.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.spotday.app.api.PlacesRepository
import com.spotday.app.model.PlaceType
import com.spotday.app.model.PopularityInfo
import com.spotday.app.ui.theme.SpotDayTheme
import com.spotday.app.util.PreferencesManager
class ActivityPreferencesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val startHour = intent.getIntExtra("startHour", 9)
        val endHour = intent.getIntExtra("endHour", 17)
        val totalBudget = intent.getIntExtra("totalBudget", 100)
        val isHungryNow = intent.getBooleanExtra("isHungryNow", false)
        val isSpontaneousMode = intent.getBooleanExtra("isSpontaneousMode", false)
        val startLat = intent.getDoubleExtra("startLatitude", 0.0).takeIf { it != 0.0 }
        val startLng = intent.getDoubleExtra("startLongitude", 0.0).takeIf { it != 0.0 }
        val explorationMode = intent.getStringExtra("explorationMode") ?: "ONE_AREA"
        val cityId = intent.getStringExtra("cityId") ?: "san_francisco"

        setContent {
            SpotDayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ActivityPreferencesScreen(
                        startHour = startHour,
                        endHour = endHour,
                        totalBudget = totalBudget,
                        isHungryNow = isHungryNow,
                        isSpontaneousMode = isSpontaneousMode,
                        startLatitude = startLat,
                        startLongitude = startLng,
                        explorationMode = explorationMode,
                        cityId = cityId
                    )
                }
            }
        }
    }
}

// Activity options with their display labels and PlaceType mapping
private val activityOptions = listOf(
    Triple("museums", "Museums", PlaceType.MUSEUM),
    Triple("parks", "Parks", PlaceType.PARK),
    Triple("waterfront", "Waterfront", PlaceType.WATERFRONT),
    Triple("historic_sites", "Historic", PlaceType.HISTORIC_SITE),
    Triple("shopping", "Shopping", PlaceType.SHOPPING),
    Triple("entertainment", "Shows", PlaceType.ENTERTAINMENT),
    Triple("games", "Games", PlaceType.GAMES),
    Triple("outdoor", "Outdoors", PlaceType.OUTDOOR),
    Triple("massage", "Massage", PlaceType.MASSAGE),
    Triple("sauna", "Spa & Sauna", PlaceType.SAUNA),
    Triple("beach", "Beach", PlaceType.BEACH),
    Triple("breweries", "Breweries", PlaceType.BREWERY),
    Triple("classes", "Classes", PlaceType.CLASS),
    Triple("markets", "Markets", PlaceType.MARKET),
    Triple("sports", "Sports", PlaceType.SPORTS),
    Triple("zoos", "Zoos", PlaceType.ZOO),
    Triple("cinema", "Movies", PlaceType.CINEMA),
    Triple("attractions", "Sights", PlaceType.ATTRACTION)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActivityPreferencesScreen(
    startHour: Int,
    endHour: Int,
    totalBudget: Int,
    isHungryNow: Boolean,
    isSpontaneousMode: Boolean = false,
    startLatitude: Double? = null,
    startLongitude: Double? = null,
    explorationMode: String = "ONE_AREA",
    cityId: String = "san_francisco"
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val placesRepository = remember { PlacesRepository(context) }

    // Load saved selections on init
    var selectedActivities by remember {
        mutableStateOf(preferencesManager.getActivitySelections())
    }

    // Popularity data state
    var popularityData by remember { mutableStateOf<Map<PlaceType, PopularityInfo>>(emptyMap()) }
    var isLoadingPopularity by remember { mutableStateOf(true) }

    // Fetch popularity data on load
    LaunchedEffect(cityId) {
        isLoadingPopularity = true
        try {
            placesRepository.currentCityId = cityId
            popularityData = placesRepository.getActivityPopularity()
            Log.d("ActivityPreferences", "Loaded popularity data for $cityId: ${popularityData.size} types")
        } catch (e: Exception) {
            Log.e("ActivityPreferences", "Failed to load popularity data", e)
            // Continue without popularity data - show original order
        }
        isLoadingPopularity = false
    }

    // Sort activity options by popularity score (descending)
    val sortedActivityOptions = remember(popularityData) {
        if (popularityData.isEmpty()) {
            activityOptions
        } else {
            activityOptions.sortedByDescending { (_, _, placeType) ->
                popularityData[placeType]?.score ?: 0f
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Select Activities",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "What would you like to do?",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "(Optional - skip for food & nightlife only)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Loading indicator while fetching popularity
        if (isLoadingPopularity) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Loading popular activities...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Filter chips in a flow layout
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            sortedActivityOptions.forEach { (type, label, placeType) ->
                val isSelected = selectedActivities.contains(type)
                val popularity = popularityData[placeType]
                val count = popularity?.count ?: 0
                val isPopular = popularity?.isPopular == true

                // Build label with count if available
                val displayLabel = if (count > 0) "$label ($count)" else label

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedActivities = if (isSelected) {
                            selectedActivities - type
                        } else {
                            selectedActivities + type
                        }
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(displayLabel)
                            if (isPopular && !isLoadingPopularity) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Text(
                                        text = "Popular",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selection count hint
        Text(
            text = if (selectedActivities.isEmpty())
                "No activities selected - will focus on food & drinks"
            else
                "${selectedActivities.size} activit${if (selectedActivities.size == 1) "y" else "ies"} selected",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.weight(1f))

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                try {
                    // Save selections for next time
                    preferencesManager.saveActivitySelections(selectedActivities)

                    Log.d("ActivityPreferences", "Starting EventSelectionActivity")
                    Log.d("ActivityPreferences", "Time range: $startHour to $endHour, Budget: $$totalBudget, HungryNow: $isHungryNow, Spontaneous: $isSpontaneousMode")
                    Log.d("ActivityPreferences", "Activities: ${if (selectedActivities.isEmpty()) "NONE (food/nightlife only)" else selectedActivities.toString()}")

                    val intent = Intent(context, EventSelectionActivity::class.java).apply {
                        putExtra("startHour", startHour)
                        putExtra("endHour", endHour)
                        putExtra("totalBudget", totalBudget)
                        putExtra("isHungryNow", isHungryNow)
                        putExtra("isSpontaneousMode", isSpontaneousMode)
                        putExtra("activityTypes", selectedActivities.toTypedArray())
                        putExtra("explorationMode", explorationMode)
                        putExtra("cityId", cityId)
                        if (startLatitude != null) putExtra("startLatitude", startLatitude)
                        if (startLongitude != null) putExtra("startLongitude", startLongitude)
                    }
                    context.startActivity(intent)
                    (context as? ComponentActivity)?.finish()
                } catch (e: Exception) {
                    Log.e("ActivityPreferences", "Error starting activity", e)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}
