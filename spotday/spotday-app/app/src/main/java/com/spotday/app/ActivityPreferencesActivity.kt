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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

// Activity options with their display labels
private val activityOptions = listOf(
    "museums" to "Museums",
    "parks" to "Parks",
    "waterfront" to "Waterfront",
    "historic_sites" to "Historic",
    "shopping" to "Shopping",
    "entertainment" to "Shows",
    "games" to "Games",
    "outdoor" to "Outdoors",
    "wellness" to "Wellness",
    "breweries" to "Breweries",
    "classes" to "Classes",
    "markets" to "Markets",
    "sports" to "Sports"
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
    
    // Load saved selections on init
    var selectedActivities by remember { 
        mutableStateOf(preferencesManager.getActivitySelections()) 
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

        // Filter chips in a flow layout
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            activityOptions.forEach { (type, label) ->
                val isSelected = selectedActivities.contains(type)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedActivities = if (isSelected) {
                            selectedActivities - type
                        } else {
                            selectedActivities + type
                        }
                    },
                    label = { Text(label) },
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
