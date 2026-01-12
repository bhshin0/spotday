package com.spotday.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.spotday.app.ui.theme.SpotDayTheme
import com.spotday.app.util.PreferencesManager

class RestaurantSelectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val startHour = intent.getIntExtra("startHour", 9)
        val endHour = intent.getIntExtra("endHour", 17)
        val totalBudget = intent.getIntExtra("totalBudget", 100)
        val isHungryNow = intent.getBooleanExtra("isHungryNow", false)
        val isSpontaneousMode = intent.getBooleanExtra("isSpontaneousMode", false)
        val activityTypes = intent.getStringArrayExtra("activityTypes") ?: arrayOf()
        val selectedEventIds = intent.getStringArrayExtra("selectedEventIds") ?: arrayOf()
        val startLat = intent.getDoubleExtra("startLatitude", 0.0).takeIf { it != 0.0 }
        val startLng = intent.getDoubleExtra("startLongitude", 0.0).takeIf { it != 0.0 }
        val explorationMode = intent.getStringExtra("explorationMode") ?: "ONE_AREA"
        
        setContent {
            SpotDayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RestaurantSelectionScreen(
                        startHour = startHour,
                        endHour = endHour,
                        totalBudget = totalBudget,
                        isHungryNow = isHungryNow,
                        isSpontaneousMode = isSpontaneousMode,
                        activityTypes = activityTypes.toList(),
                        selectedEventIds = selectedEventIds.toList(),
                        startLatitude = startLat,
                        startLongitude = startLng,
                        explorationMode = explorationMode
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantSelectionScreen(
    startHour: Int,
    endHour: Int,
    totalBudget: Int,
    isHungryNow: Boolean,
    isSpontaneousMode: Boolean = false,
    activityTypes: List<String>,
    selectedEventIds: List<String> = emptyList(),
    startLatitude: Double? = null,
    startLongitude: Double? = null,
    explorationMode: String = "ONE_AREA"
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    
    // Load saved cuisine selections
    val savedCuisines = remember { preferencesManager.getCuisineSelections() }
    var italianChecked by remember { mutableStateOf(savedCuisines.contains("italian")) }
    var mexicanChecked by remember { mutableStateOf(savedCuisines.contains("mexican")) }
    var americanChecked by remember { mutableStateOf(savedCuisines.contains("american")) }
    var asianChecked by remember { mutableStateOf(savedCuisines.contains("asian")) }
    var seafoodChecked by remember { mutableStateOf(savedCuisines.contains("seafood")) }
    var vegetarianChecked by remember { mutableStateOf(savedCuisines.contains("vegetarian")) }
    
    // Load saved service style selections
    val savedServiceStyles = remember { preferencesManager.getServiceStyleSelections() }
    var quickServiceChecked by remember { mutableStateOf(savedServiceStyles.contains("quick")) }
    var casualChecked by remember { mutableStateOf(savedServiceStyles.contains("casual")) }
    var formalChecked by remember { mutableStateOf(savedServiceStyles.contains("formal")) }
    
    // Load saved nightlife selections
    val savedNightlife = remember { preferencesManager.getNightlifeSelections() }
    var barsChecked by remember { mutableStateOf(savedNightlife.contains("bars")) }
    var cocktailBarsChecked by remember { mutableStateOf(savedNightlife.contains("cocktail_bars")) }
    var clubsChecked by remember { mutableStateOf(savedNightlife.contains("clubs")) }
    var liveMusicChecked by remember { mutableStateOf(savedNightlife.contains("live_music")) }
    var diveBarsChecked by remember { mutableStateOf(savedNightlife.contains("dive_bars")) }
    var rooftopBarsChecked by remember { mutableStateOf(savedNightlife.contains("rooftop_bars")) }
    
    // Save preferences whenever they change
    LaunchedEffect(
        italianChecked, mexicanChecked, americanChecked, 
        asianChecked, seafoodChecked, vegetarianChecked
    ) {
        val cuisines = mutableSetOf<String>()
        if (italianChecked) cuisines.add("italian")
        if (mexicanChecked) cuisines.add("mexican")
        if (americanChecked) cuisines.add("american")
        if (asianChecked) cuisines.add("asian")
        if (seafoodChecked) cuisines.add("seafood")
        if (vegetarianChecked) cuisines.add("vegetarian")
        preferencesManager.saveCuisineSelections(cuisines)
    }
    
    LaunchedEffect(quickServiceChecked, casualChecked, formalChecked) {
        val styles = mutableSetOf<String>()
        if (quickServiceChecked) styles.add("quick")
        if (casualChecked) styles.add("casual")
        if (formalChecked) styles.add("formal")
        preferencesManager.saveServiceStyleSelections(styles)
    }
    
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Select Food Preferences",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Dining Style - Segmented Button (moved to top for prominence)
        Text(
            text = "Dining Style",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        MultiChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            SegmentedButton(
                checked = quickServiceChecked,
                onCheckedChange = { quickServiceChecked = it },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
            ) { Text("Quick 30m") }
            
            SegmentedButton(
                checked = casualChecked,
                onCheckedChange = { casualChecked = it },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
            ) { Text("Casual 60m") }
            
            SegmentedButton(
                checked = formalChecked,
                onCheckedChange = { formalChecked = it },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
            ) { Text("Formal 90m") }
        }
        
        Text(
            text = "(Skip for all styles)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        Divider(modifier = Modifier.padding(bottom = 16.dp))

        Text(
            text = "Cuisine",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "(Leave empty for all cuisines)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
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

        // Nightlife Section
        Divider(modifier = Modifier.padding(vertical = 16.dp))

        Text(
            text = "Nightlife (Optional)",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Going out late? Add bars and clubs to your night",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Bars
        Row(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = barsChecked,
                onCheckedChange = { barsChecked = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Bars",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // Cocktail Bars / Lounges
        Row(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = cocktailBarsChecked,
                onCheckedChange = { cocktailBarsChecked = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Cocktail Bars / Lounges",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // Dance Clubs / Nightclubs
        Row(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = clubsChecked,
                onCheckedChange = { clubsChecked = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Dance Clubs / Nightclubs",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // Live Music Venues
        Row(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = liveMusicChecked,
                onCheckedChange = { liveMusicChecked = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Live Music Venues",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // Dive Bars / Casual Spots
        Row(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = diveBarsChecked,
                onCheckedChange = { diveBarsChecked = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Dive Bars / Casual Spots",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // Rooftop Bars / Views
        Row(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = rooftopBarsChecked,
                onCheckedChange = { rooftopBarsChecked = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Rooftop Bars / Views",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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

                    val selectedNightlifeTypes = mutableListOf<String>()
                    if (barsChecked) selectedNightlifeTypes.add("bars")
                    if (cocktailBarsChecked) selectedNightlifeTypes.add("cocktail_bars")
                    if (clubsChecked) selectedNightlifeTypes.add("clubs")
                    if (liveMusicChecked) selectedNightlifeTypes.add("live_music")
                    if (diveBarsChecked) selectedNightlifeTypes.add("dive_bars")
                    if (rooftopBarsChecked) selectedNightlifeTypes.add("rooftop_bars")

                    // Service styles (empty = all allowed)
                    val selectedServiceStyles = mutableListOf<String>()
                    if (quickServiceChecked) selectedServiceStyles.add("quick")
                    if (casualChecked) selectedServiceStyles.add("casual")
                    if (formalChecked) selectedServiceStyles.add("formal")

                    // Empty selections = all (cuisines, styles)
                    Log.d("RestaurantSelection", "Starting ItineraryDisplayActivity")
                    Log.d("RestaurantSelection", "Time range: $startHour to $endHour, Budget: $$totalBudget, HungryNow: $isHungryNow, Spontaneous: $isSpontaneousMode")
                    Log.d("RestaurantSelection", "Activity types: $activityTypes")
                    Log.d("RestaurantSelection", "Food types: ${if (selectedFoodTypes.isEmpty()) "ALL" else selectedFoodTypes}")
                    Log.d("RestaurantSelection", "Service styles: ${if (selectedServiceStyles.isEmpty()) "ALL" else selectedServiceStyles}")
                    Log.d("RestaurantSelection", "Nightlife types: $selectedNightlifeTypes")
                    Log.d("RestaurantSelection", "Selected events: $selectedEventIds")
                    if (startLatitude != null && startLongitude != null) {
                        Log.d("RestaurantSelection", "Starting location: ($startLatitude, $startLongitude)")
                    }
                    
                    val intent = Intent(context, ItineraryDisplayActivity::class.java).apply {
                        putExtra("startHour", startHour)
                        putExtra("endHour", endHour)
                        putExtra("totalBudget", totalBudget)
                        putExtra("isHungryNow", isHungryNow)
                        putExtra("isSpontaneousMode", isSpontaneousMode)
                        putExtra("activityTypes", activityTypes.toTypedArray())
                        putExtra("foodTypes", selectedFoodTypes.toTypedArray())
                        putExtra("serviceStyles", selectedServiceStyles.toTypedArray())
                        putExtra("nightlifeTypes", selectedNightlifeTypes.toTypedArray())
                        putExtra("selectedEventIds", selectedEventIds.toTypedArray())
                        putExtra("explorationMode", explorationMode)
                        if (startLatitude != null) putExtra("startLatitude", startLatitude)
                        if (startLongitude != null) putExtra("startLongitude", startLongitude)
                    }
                    context.startActivity(intent)
                    (context as? ComponentActivity)?.finish()
                } catch (e: Exception) {
                    Log.e("RestaurantSelection", "Error starting activity", e)
                }
            },
            modifier = Modifier.fillMaxWidth()
            // Always enabled - empty cuisine = all cuisines, empty style = all styles
        ) {
            Text("Generate Itinerary")
        }
    }
}

