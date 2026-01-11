package com.spotday.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.spotday.app.ui.theme.SpotDayTheme

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
                        explorationMode = explorationMode
                    )
                }
            }
        }
    }
}

@Composable
fun ActivityPreferencesScreen(
    startHour: Int, 
    endHour: Int, 
    totalBudget: Int, 
    isHungryNow: Boolean,
    isSpontaneousMode: Boolean = false,
    startLatitude: Double? = null,
    startLongitude: Double? = null,
    explorationMode: String = "ONE_AREA"
) {
    val context = LocalContext.current
    var museumsChecked by remember { mutableStateOf(false) }
    var parksChecked by remember { mutableStateOf(false) }
    var waterfrontChecked by remember { mutableStateOf(false) }
    var historicSitesChecked by remember { mutableStateOf(false) }
    var shoppingChecked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
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

        // Museums
        Row(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = museumsChecked,
                onCheckedChange = { museumsChecked = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Museums",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // Parks
        Row(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = parksChecked,
                onCheckedChange = { parksChecked = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Parks",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // Waterfront
        Row(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = waterfrontChecked,
                onCheckedChange = { waterfrontChecked = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Waterfront",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // Historic Sites
        Row(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = historicSitesChecked,
                onCheckedChange = { historicSitesChecked = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Historic Sites",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // Shopping
        Row(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = shoppingChecked,
                onCheckedChange = { shoppingChecked = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Shopping",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                try {
                    val selectedActivities = mutableListOf<String>()
                    if (museumsChecked) selectedActivities.add("museums")
                    if (parksChecked) selectedActivities.add("parks")
                    if (waterfrontChecked) selectedActivities.add("waterfront")
                    if (historicSitesChecked) selectedActivities.add("historic_sites")
                    if (shoppingChecked) selectedActivities.add("shopping")

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
