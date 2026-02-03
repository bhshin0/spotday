package com.spotday.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotday.app.ui.theme.BarCrawlBackground
import com.spotday.app.ui.theme.BarCrawlPrimary
import com.spotday.app.ui.theme.BarCrawlSecondary
import com.spotday.app.ui.theme.BarCrawlTertiary
import com.spotday.app.ui.theme.BarCrawlTheme
import com.spotday.app.util.PreferencesManager

class BarCrawlSelectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val totalBudget = intent.getIntExtra("totalBudget", 150)
        val cityId = intent.getStringExtra("cityId") ?: "san_francisco"
        val isSpontaneousMode = intent.getBooleanExtra("isSpontaneousMode", false)
        val startLatitude = if (intent.hasExtra("startLatitude")) intent.getDoubleExtra("startLatitude", 0.0) else null
        val startLongitude = if (intent.hasExtra("startLongitude")) intent.getDoubleExtra("startLongitude", 0.0) else null

        setContent {
            BarCrawlTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BarCrawlSelectionScreen(
                        totalBudget = totalBudget,
                        cityId = cityId,
                        isSpontaneousMode = isSpontaneousMode,
                        startLatitude = startLatitude,
                        startLongitude = startLongitude
                    )
                }
            }
        }
    }
}

@Composable
fun BarCrawlSelectionScreen(
    totalBudget: Int,
    cityId: String,
    isSpontaneousMode: Boolean = false,
    startLatitude: Double? = null,
    startLongitude: Double? = null
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    
    // Animated glow effect for the title
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    // Load saved nightlife selections
    val savedNightlife = remember { preferencesManager.getNightlifeSelections() }
    var barsChecked by remember { mutableStateOf(savedNightlife.contains("bars") || savedNightlife.isEmpty()) }
    var cocktailBarsChecked by remember { mutableStateOf(savedNightlife.contains("cocktail_bars") || savedNightlife.isEmpty()) }
    var clubsChecked by remember { mutableStateOf(savedNightlife.contains("clubs")) }
    var liveMusicChecked by remember { mutableStateOf(savedNightlife.contains("live_music")) }
    var diveBarsChecked by remember { mutableStateOf(savedNightlife.contains("dive_bars")) }
    var rooftopBarsChecked by remember { mutableStateOf(savedNightlife.contains("rooftop_bars")) }
    
    // Save preferences whenever they change
    LaunchedEffect(
        barsChecked, cocktailBarsChecked, clubsChecked,
        liveMusicChecked, diveBarsChecked, rooftopBarsChecked
    ) {
        val nightlife = mutableSetOf<String>()
        if (barsChecked) nightlife.add("bars")
        if (cocktailBarsChecked) nightlife.add("cocktail_bars")
        if (clubsChecked) nightlife.add("clubs")
        if (liveMusicChecked) nightlife.add("live_music")
        if (diveBarsChecked) nightlife.add("dive_bars")
        if (rooftopBarsChecked) nightlife.add("rooftop_bars")
        preferencesManager.saveNightlifeSelections(nightlife)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BarCrawlBackground,
                        Color(0xFF1A0A2E),
                        Color(0xFF0D0221)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Glowing title
            Text(
                text = "🌙",
                fontSize = 48.sp,
                modifier = Modifier.alpha(glowAlpha)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "BAR CRAWL MODE",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                ),
                color = BarCrawlPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(glowAlpha)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Let's paint the town...",
                style = MaterialTheme.typography.titleMedium,
                color = BarCrawlTertiary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "10 PM - 2 AM",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = BarCrawlSecondary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Nightlife options
            Text(
                text = "What's your vibe tonight?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Nightlife checkboxes with neon styling
            NightlifeOption(
                checked = barsChecked,
                onCheckedChange = { barsChecked = it },
                label = "Bars",
                emoji = "🍺"
            )
            
            NightlifeOption(
                checked = cocktailBarsChecked,
                onCheckedChange = { cocktailBarsChecked = it },
                label = "Cocktail Bars / Lounges",
                emoji = "🍸"
            )
            
            NightlifeOption(
                checked = clubsChecked,
                onCheckedChange = { clubsChecked = it },
                label = "Dance Clubs / Nightclubs",
                emoji = "🪩"
            )
            
            NightlifeOption(
                checked = liveMusicChecked,
                onCheckedChange = { liveMusicChecked = it },
                label = "Live Music Venues",
                emoji = "🎸"
            )
            
            NightlifeOption(
                checked = diveBarsChecked,
                onCheckedChange = { diveBarsChecked = it },
                label = "Dive Bars / Casual Spots",
                emoji = "🎱"
            )
            
            NightlifeOption(
                checked = rooftopBarsChecked,
                onCheckedChange = { rooftopBarsChecked = it },
                label = "Rooftop Bars / Views",
                emoji = "🌃"
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Warning if nothing selected
            val anySelected = barsChecked || cocktailBarsChecked || clubsChecked || 
                              liveMusicChecked || diveBarsChecked || rooftopBarsChecked
            
            if (!anySelected) {
                Text(
                    text = "Select at least one option",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Let's Go button with gradient
            Button(
                onClick = {
                    try {
                        val selectedNightlifeTypes = mutableListOf<String>()
                        if (barsChecked) selectedNightlifeTypes.add("bars")
                        if (cocktailBarsChecked) selectedNightlifeTypes.add("cocktail_bars")
                        if (clubsChecked) selectedNightlifeTypes.add("clubs")
                        if (liveMusicChecked) selectedNightlifeTypes.add("live_music")
                        if (diveBarsChecked) selectedNightlifeTypes.add("dive_bars")
                        if (rooftopBarsChecked) selectedNightlifeTypes.add("rooftop_bars")
                        
                        Log.d("BarCrawlSelection", "Starting bar crawl itinerary")
                        Log.d("BarCrawlSelection", "Time: 22:00 - 02:00 (10 PM - 2 AM)")
                        Log.d("BarCrawlSelection", "Budget: $$totalBudget")
                        Log.d("BarCrawlSelection", "Nightlife types: $selectedNightlifeTypes")
                        
                        val intent = Intent(context, ItineraryDisplayActivity::class.java).apply {
                            putExtra("startHour", 22)  // 10 PM
                            putExtra("endHour", 2)     // 2 AM (next day)
                            putExtra("totalBudget", totalBudget)
                            putExtra("isHungryNow", false)
                            putExtra("isSpontaneousMode", isSpontaneousMode)
                            putExtra("activityTypes", arrayOf<String>())  // No activities
                            putExtra("foodTypes", arrayOf<String>())      // No food
                            putExtra("serviceStyles", arrayOf<String>())
                            putExtra("nightlifeTypes", selectedNightlifeTypes.toTypedArray())
                            putExtra("selectedEventIds", arrayOf<String>())
                            putExtra("explorationMode", "CITY_WIDE")
                            putExtra("cityId", cityId)
                            putExtra("isBarCrawlMode", true)  // Flag for special handling
                            // Pass GPS coordinates if available
                            if (startLatitude != null && startLongitude != null) {
                                putExtra("startLatitude", startLatitude)
                                putExtra("startLongitude", startLongitude)
                            }
                        }
                        context.startActivity(intent)
                        (context as? ComponentActivity)?.finish()
                    } catch (e: Exception) {
                        Log.e("BarCrawlSelection", "Error starting activity", e)
                    }
                },
                enabled = anySelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BarCrawlPrimary,
                    contentColor = Color.White,
                    disabledContainerColor = BarCrawlPrimary.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = "Let's Go! 🚀",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NightlifeOption(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    emoji: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = BarCrawlPrimary,
                uncheckedColor = BarCrawlTertiary.copy(alpha = 0.6f),
                checkmarkColor = Color.White
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = emoji,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
