package com.spotday.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotday.app.ui.theme.SpotDayTheme

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

@Composable
fun WelcomeScreen() {
    val context = LocalContext.current
    var time by remember { mutableStateOf(4) }
    var budget by remember { mutableStateOf(50) }
    var familiarity by remember { mutableStateOf("Tourist") }
    var foodChecked by remember { mutableStateOf(false) }
    var activitiesChecked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Welcome to SpotDay!",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Let's build your perfect day in San Francisco.",
            style = MaterialTheme.typography.bodyLarge
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("How much time do you have?")
        Slider(
            value = time.toFloat(),
            onValueChange = { time = it.toInt() },
            valueRange = 1f..8f,
            steps = 7
        )
        Text("$time hours")
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("What's your total budget?")
        Slider(
            value = budget.toFloat(),
            onValueChange = { budget = it.toInt() },
            valueRange = 0f..200f,
            steps = 40
        )
        Text("$$budget")
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Your familiarity:")
        Row {
            RadioButton(
                selected = familiarity == "Tourist",
                onClick = { familiarity = "Tourist" }
            )
            Text("Tourist")
            Spacer(modifier = Modifier.width(8.dp))
            RadioButton(
                selected = familiarity == "New",
                onClick = { familiarity = "New" }
            )
            Text("New")
            Spacer(modifier = Modifier.width(8.dp))
            RadioButton(
                selected = familiarity == "Local",
                onClick = { familiarity = "Local" }
            )
            Text("Local")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Must-haves:")
        Row {
            Checkbox(
                checked = foodChecked,
                onCheckedChange = { foodChecked = it }
            )
            Text("Food")
            Spacer(modifier = Modifier.width(16.dp))
            Checkbox(
                checked = activitiesChecked,
                onCheckedChange = { activitiesChecked = it }
            )
            Text("Activities")
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                try {
                    val intent = Intent(context, ActivityPreferencesActivity::class.java).apply {
                        putExtra("totalDurationHours", 4) // Pass the 4-hour duration
                        // We can also pass other parameters if needed
                        // putExtra("budget", budget)
                        // putExtra("familiarity", familiarity)
                    }
                    Log.d("WelcomeActivity", "Starting ActivityPreferencesActivity")
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("WelcomeActivity", "Error starting activity", e)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Apply")
        }
    }
} 