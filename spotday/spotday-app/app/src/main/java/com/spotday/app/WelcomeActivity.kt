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
import androidx.compose.ui.unit.dp
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
    var timeRange by remember { mutableStateOf(9f..17f) } // 9 AM to 5 PM default
    var budget by remember { mutableStateOf(100) }
    var isHungryNow by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
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
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "What time will you be out?",
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        RangeSlider(
            value = timeRange,
            onValueChange = { timeRange = it },
            valueRange = 6f..23f, // 6 AM to 11 PM
            steps = 16 // Every hour
        )
        
        Text(
            text = "${formatTime(timeRange.start.toInt())} - ${formatTime(timeRange.endInclusive.toInt())}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        
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
                    val startHour = timeRange.start.toInt()
                    val endHour = timeRange.endInclusive.toInt()
                    val intent = Intent(context, ActivityPreferencesActivity::class.java).apply {
                        putExtra("startHour", startHour)
                        putExtra("endHour", endHour)
                        putExtra("totalBudget", budget)
                        putExtra("isHungryNow", isHungryNow)
                    }
                    Log.d("WelcomeActivity", "Starting ActivityPreferencesActivity from $startHour to $endHour with $$budget budget, hungryNow=$isHungryNow")
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
    val period = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "$displayHour:00 $period"
} 