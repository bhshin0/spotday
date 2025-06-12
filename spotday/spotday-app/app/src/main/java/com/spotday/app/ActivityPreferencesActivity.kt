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
        setContent {
            SpotDayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ActivityPreferencesScreen()
                }
            }
        }
    }
}

@Composable
fun ActivityPreferencesScreen() {
    var museumsChecked by remember { mutableStateOf(false) }
    var natureChecked by remember { mutableStateOf(false) }
    var entertainmentChecked by remember { mutableStateOf(false) }
    var comedyChecked by remember { mutableStateOf(false) }
    var musicChecked by remember { mutableStateOf(false) }
    var sportsChecked by remember { mutableStateOf(false) }
    var shoppingChecked by remember { mutableStateOf(false) }
    var foodDrinkChecked by remember { mutableStateOf(false) }
    var historyChecked by remember { mutableStateOf(false) }
    var nightlifeChecked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Select Your Interests",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Activity checkboxes
        Row {
            Checkbox(
                checked = museumsChecked,
                onCheckedChange = { museumsChecked = it }
            )
            Text("Museums")
        }
        Row {
            Checkbox(
                checked = natureChecked,
                onCheckedChange = { natureChecked = it }
            )
            Text("Nature")
        }
        Row {
            Checkbox(
                checked = entertainmentChecked,
                onCheckedChange = { entertainmentChecked = it }
            )
            Text("Entertainment")
        }
        Row {
            Checkbox(
                checked = comedyChecked,
                onCheckedChange = { comedyChecked = it }
            )
            Text("Comedy")
        }
        Row {
            Checkbox(
                checked = musicChecked,
                onCheckedChange = { musicChecked = it }
            )
            Text("Music")
        }
        Row {
            Checkbox(
                checked = sportsChecked,
                onCheckedChange = { sportsChecked = it }
            )
            Text("Sports")
        }
        Row {
            Checkbox(
                checked = shoppingChecked,
                onCheckedChange = { shoppingChecked = it }
            )
            Text("Shopping")
        }
        Row {
            Checkbox(
                checked = foodDrinkChecked,
                onCheckedChange = { foodDrinkChecked = it }
            )
            Text("Food & Drink")
        }
        Row {
            Checkbox(
                checked = historyChecked,
                onCheckedChange = { historyChecked = it }
            )
            Text("History")
        }
        Row {
            Checkbox(
                checked = nightlifeChecked,
                onCheckedChange = { nightlifeChecked = it }
            )
            Text("Nightlife")
        }

        Spacer(modifier = Modifier.weight(1f))

        NavigationButton(
            museumsChecked = museumsChecked,
            natureChecked = natureChecked,
            entertainmentChecked = entertainmentChecked,
            comedyChecked = comedyChecked,
            musicChecked = musicChecked,
            sportsChecked = sportsChecked,
            shoppingChecked = shoppingChecked,
            foodDrinkChecked = foodDrinkChecked,
            historyChecked = historyChecked,
            nightlifeChecked = nightlifeChecked
        )
    }
}

@Composable
private fun NavigationButton(
    museumsChecked: Boolean,
    natureChecked: Boolean,
    entertainmentChecked: Boolean,
    comedyChecked: Boolean,
    musicChecked: Boolean,
    sportsChecked: Boolean,
    shoppingChecked: Boolean,
    foodDrinkChecked: Boolean,
    historyChecked: Boolean,
    nightlifeChecked: Boolean
) {
    val context = LocalContext.current
    
    Button(
        onClick = {
            try {
                val selectedPreferences = mutableListOf<String>()
                if (museumsChecked) selectedPreferences.add("museums")
                if (natureChecked) selectedPreferences.add("nature")
                if (entertainmentChecked) selectedPreferences.add("entertainment")
                if (comedyChecked) selectedPreferences.add("comedy")
                if (musicChecked) selectedPreferences.add("music")
                if (sportsChecked) selectedPreferences.add("sports")
                if (shoppingChecked) selectedPreferences.add("shopping")
                if (foodDrinkChecked) selectedPreferences.add("food_drink")
                if (historyChecked) selectedPreferences.add("history")
                if (nightlifeChecked) selectedPreferences.add("nightlife")

                if (selectedPreferences.isNotEmpty()) {
                    Log.d("ActivityPreferences", "Starting ItineraryDisplayActivity with ${selectedPreferences.size} preferences")
                    val intent = Intent(context, ItineraryDisplayActivity::class.java).apply {
                        putExtra("totalDurationHours", 4)
                        putExtra("selectedPreferences", selectedPreferences.toTypedArray())
                    }
                    context.startActivity(intent)
                } else {
                    Log.d("ActivityPreferences", "No preferences selected")
                }
            } catch (e: Exception) {
                Log.e("ActivityPreferences", "Error starting activity", e)
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Continue")
    }
} 