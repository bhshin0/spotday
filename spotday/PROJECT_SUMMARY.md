# SpotDay - Project Summary

## Project Overview
**SpotDay** is an automated itinerary builder Android application currently focused on San Francisco. It allows users to input their time availability, budget, and preferences (activities and food) to generate a geographically optimized, budget-aware day plan displayed on an interactive map.

## Technical Stack
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose with Material 3
- **Maps:** Google Maps SDK for Android & Maps Compose
- **Architecture:** MVVM (Model-View-ViewModel)
- **Build System:** Gradle (Kotlin DSL)

## Current Features & Implementation Status

### 1. Navigation Flow
- **WelcomeActivity:** User selects total hours (slider) and total budget (slider).
- **ActivityPreferencesActivity:** User chooses activity types (Museums, Parks, Waterfront, etc.).
- **RestaurantSelectionActivity:** User chooses food cuisines (Italian, Mexican, Asian, etc.).
- **ItineraryDisplayActivity:** Displays the final plan with a Google Map and a chronological timeline.

### 2. Planning Algorithm (`ItineraryGenerator.kt`)
- **Randomization:** Uses `.shuffled()` on candidate pools to provide variety for returning users.
- **Smart Routing:** Uses the Haversine formula to calculate distances and selects restaurants/activities that are geographically clustered to minimize travel time.
- **Budget Awareness:** Filters and selects places based on `priceLevel` and `estimatedCost`, allowing a 20% buffer over the target budget for quality.
- **Time Management:** Allocates specific durations for different place types (e.g., 2h for museums, 1h for food) and accounts for travel time.

### 3. Data Layer (`PlacesRepository.kt`)
- **Status:** Currently uses high-quality hardcoded data for San Francisco for POC/MVP.
- **Infrastructure:** Ready for Google Places API integration (Retrofit/Client setup initialized but commented out to prioritize planning logic).

### 4. Build & Security
- **API Key Management:** API keys are stored in `local.properties` (gitignored). `build.gradle.kts` is configured to inject these into `BuildConfig` and `Manifest` placeholders.
- **Gradle Performance:** JVM heap size increased to 4GB to prevent `OutOfMemoryError` during dex merging.

## Key Bug Fixes & Improvements
- **ViewModel Factory:** Fixed a crash in `ItineraryDisplayActivity` by implementing a proper `ViewModelProvider.Factory` for passing parameters.
- **Navigation Loop:** Added `finish()` calls to intermediate activities to ensure a clean back stack.
- **API Key Loading:** Fixed a critical bug where `project.findProperty` failed to read `local.properties`, by explicitly loading the properties file in `build.gradle.kts`.
- **Regeneration Logic:** Added a "Show Different Options" button that triggers a fresh randomized generation using the same preferences.

## Future Roadmap (Next Steps)
1. **Live Data:** Replace hardcoded lists in `PlacesRepository` with live calls to the Google Places API.
2. **Persistence:** Add the ability to "Save" itineraries to a local database (Room).
3. **Sharing:** Implement sharing of itineraries via deep links or text.
4. **Expansion:** Expand beyond San Francisco (requires dynamic location detection).
5. **Backend:** Consider a server-side component for caching points of interest and more complex optimization.

---
*Summary generated on December 28, 2025.*

