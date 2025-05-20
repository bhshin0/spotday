package com.spotday.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.spotday.app.ui.theme.SpotDayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpotDayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onStartClick = {
                            startActivity(Intent(this, WelcomeActivity::class.java))
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(onStartClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = onStartClick) {
            Text("Start SpotDay")
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Welcome to $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SpotDayTheme {
        Greeting("SpotDay")
    }
} 