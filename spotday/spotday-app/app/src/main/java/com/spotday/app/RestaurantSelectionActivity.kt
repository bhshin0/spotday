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

class RestaurantSelectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val totalHours = intent.getIntExtra("totalHours", 4)
        val totalBudget = intent.getIntExtra("totalBudget", 100)
        val activityTypes = intent.getStringArrayExtra("activityTypes") ?: arrayOf()
        
        setContent {
            SpotDayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RestaurantSelectionScreen(
                        totalHours = totalHours,
                        totalBudget = totalBudget,
                        activityTypes = activityTypes.toList()
                    )
                }
            }
        }
    }
}

@Composable
fun RestaurantSelectionScreen(
    totalHours: Int,
    totalBudget: Int,
    activityTypes: List<String>
) {
    val context = LocalContext.current
    var italianChecked by remember { mutableStateOf(false) }
    var mexicanChecked by remember { mutableStateOf(false) }
    var americanChecked by remember { mutableStateOf(false) }
    var asianChecked by remember { mutableStateOf(false) }
    var seafoodChecked by remember { mutableStateOf(false) }
    var vegetarianChecked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Select Food Preferences",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "What type of cuisine would you like?",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Italian
        Row(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = italianChecked,
                onCheckedChange = { italianChecked = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Italian",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // Mexican
        Row(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = mexicanChecked,
                onCheckedChange = { mexicanChecked = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Mexican",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // American
        Row(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = americanChecked,
                onCheckedChange = { americanChecked = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "American",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // Asian
        Row(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = asianChecked,
                onCheckedChange = { asianChecked = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Asian",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // Seafood
        Row(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = seafoodChecked,
                onCheckedChange = { seafoodChecked = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Seafood",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // Vegetarian
        Row(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = vegetarianChecked,
                onCheckedChange = { vegetarianChecked = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Vegetarian",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                try {
                    val selectedFoodTypes = mutableListOf<String>()
                    if (italianChecked) selectedFoodTypes.add("italian")
                    if (mexicanChecked) selectedFoodTypes.add("mexican")
                    if (americanChecked) selectedFoodTypes.add("american")
                    if (asianChecked) selectedFoodTypes.add("asian")
                    if (seafoodChecked) selectedFoodTypes.add("seafood")
                    if (vegetarianChecked) selectedFoodTypes.add("vegetarian")

                    if (selectedFoodTypes.isNotEmpty()) {
                        Log.d("RestaurantSelection", "Starting ItineraryDisplayActivity")
                        Log.d("RestaurantSelection", "Total hours: $totalHours, Budget: $$totalBudget")
                        Log.d("RestaurantSelection", "Activity types: $activityTypes")
                        Log.d("RestaurantSelection", "Food types: $selectedFoodTypes")
                        
                        val intent = Intent(context, ItineraryDisplayActivity::class.java).apply {
                            putExtra("totalHours", totalHours)
                            putExtra("totalBudget", totalBudget)
                            putExtra("activityTypes", activityTypes.toTypedArray())
                            putExtra("foodTypes", selectedFoodTypes.toTypedArray())
                        }
                        context.startActivity(intent)
                        (context as? ComponentActivity)?.finish()
                    } else {
                        Log.d("RestaurantSelection", "No food preferences selected")
                    }
                } catch (e: Exception) {
                    Log.e("RestaurantSelection", "Error starting activity", e)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = italianChecked || mexicanChecked || americanChecked || asianChecked || seafoodChecked || vegetarianChecked
        ) {
            Text("Generate Itinerary")
        }
    }
}

