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
                        isHungryNow = isHungryNow
                    )
                }
            }
        }
    }
}

@Composable
fun ActivityPreferencesScreen(startHour: Int, endHour: Int, totalBudget: Int, isHungryNow: Boolean) {
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
            modifier = Modifier.padding(bottom = 24.dp)
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

                    if (selectedActivities.isNotEmpty()) {
                        Log.d("ActivityPreferences", "Starting RestaurantSelectionActivity")
                        Log.d("ActivityPreferences", "Time range: $startHour to $endHour, Budget: $$totalBudget, HungryNow: $isHungryNow")
                        Log.d("ActivityPreferences", "Activities: $selectedActivities")
                        
                        val intent = Intent(context, RestaurantSelectionActivity::class.java).apply {
                            putExtra("startHour", startHour)
                            putExtra("endHour", endHour)
                            putExtra("totalBudget", totalBudget)
                            putExtra("isHungryNow", isHungryNow)
                            putExtra("activityTypes", selectedActivities.toTypedArray())
                        }
                        context.startActivity(intent)
                        (context as? ComponentActivity)?.finish()
                    } else {
                        Log.d("ActivityPreferences", "No activities selected")
                    }
                } catch (e: Exception) {
                    Log.e("ActivityPreferences", "Error starting activity", e)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = museumsChecked || parksChecked || waterfrontChecked || historicSitesChecked || shoppingChecked
        ) {
            Text("Continue")
        }
    }
}
