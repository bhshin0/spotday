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
    var budget by remember { mutableStateOf(100) }

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
            text = "How much time do you have?",
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Slider(
            value = time.toFloat(),
            onValueChange = { time = it.toInt() },
            valueRange = 2f..8f,
            steps = 6
        )
        
        Text(
            text = "$time hours",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp)
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
                    val intent = Intent(context, ActivityPreferencesActivity::class.java).apply {
                        putExtra("totalHours", time)
                        putExtra("totalBudget", budget)
                    }
                    Log.d("WelcomeActivity", "Starting ActivityPreferencesActivity with $time hours and $$budget budget")
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