# SpotDay Architecture & Scaling Plan

## Current State (Phase 1: San Francisco POC)

### Status: ✅ In Development
**Goal:** Prove core itinerary planning algorithms with San Francisco as test city

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     Android App (Kotlin)                     │
├─────────────────────────────────────────────────────────────┤
│  UI Layer (Jetpack Compose)                                 │
│  ├─ WelcomeActivity - Time range & budget input             │
│  ├─ ActivityPreferencesActivity - Activity selection        │
│  ├─ RestaurantSelectionActivity - Food preferences          │
│  └─ ItineraryDisplayActivity - Map + Timeline display       │
├─────────────────────────────────────────────────────────────┤
│  Business Logic Layer                                        │
│  ├─ ItineraryGenerator - Core planning algorithms           │
│  │   ├─ Time-based meal scheduling                          │
│  │   ├─ Budget-aware place selection                        │
│  │   ├─ Nearest-neighbor route optimization                 │
│  │   └─ Randomized starting locations                       │
│  └─ ItineraryViewModel - State management                   │
├─────────────────────────────────────────────────────────────┤
│  Data Layer                                                  │
│  └─ PlacesRepository (HARDCODED SF DATA)                    │
│      ├─ 50 museums                                           │
│      ├─ 45 parks                                             │
│      ├─ 180 restaurants (6 cuisines × 30 each)              │
│      ├─ 30 waterfront locations                             │
│      ├─ 40 historic sites                                    │
│      └─ 35 shopping areas                                    │
└─────────────────────────────────────────────────────────────┘
```

### What's Hardcoded to SF

**Files with SF-specific code:**
- `PlacesRepository.kt` - All 400+ places manually coded for SF
- `ItineraryGenerator.kt` - 8 SF neighborhood starting locations
- `WelcomeActivity.kt` - "San Francisco" in UI text
- `ItineraryDisplayActivity.kt` - SF center for map (37.7749, -122.4194)

**Why this is okay for now:**
- ✅ Fast development (no API setup needed)
- ✅ No API costs during testing
- ✅ Consistent test data
- ✅ Works offline
- ✅ Proves algorithms work

### Core Algorithms (Already Generalizable ✅)

These work for any city without modification:

1. **Nearest-Neighbor Route Optimization**
   - Uses Haversine distance formula (works globally)
   - Picks from top 3 closest to add variety
   - Minimizes backtracking across city

2. **Time-Based Meal Scheduling**
   - Breakfast: if start ≤ 9 AM
   - Lunch: if window includes 11 AM - 2 PM
   - Dinner: if end ≥ 6 PM
   - Universal across cultures

3. **Budget-Aware Planning**
   - 20% budget buffer allowed
   - Sorts by rating (DESC) then cost (ASC)
   - Works with any currency (just change symbol)

4. **Smart Activity Distribution**
   - Short day (< 6h): 2 activities
   - Medium day (6-10h): 3 activities
   - Long day (> 10h): 4 activities

## Phase 2: Multi-City Architecture (Future)

### Target: Global Scalability

**Supported City Types:**
- Tier 1: Major metros (NYC, LA, Tokyo, London, Paris) - ~50 cities
- Tier 2: Regional hubs (Austin, Portland, Barcelona) - ~200 cities
- Tier 3: Any city with Google Places data - unlimited

### Data Flow Architecture

```
┌──────────────┐
│  Mobile App  │
│              │
│ [UI + Logic] │
└──────┬───────┘
       │
       │ HTTP/REST
       │
       ▼
┌──────────────────────────────────────────┐
│         Your Backend Server              │
│                                          │
│  ┌────────────────────────────────┐    │
│  │  REST API Endpoints            │    │
│  │  • GET /api/cities             │    │
│  │  • GET /api/places/:city       │    │
│  │  • GET /api/city/:city/config  │    │
│  └────────────────────────────────┘    │
│                                          │
│  ┌────────────────────────────────┐    │
│  │  Cached Places Database        │    │
│  │  • Redis/PostgreSQL            │    │
│  │  • 30-day TTL                  │    │
│  │  • Curated/filtered results    │    │
│  └────────────────────────────────┘    │
│                                          │
│  ┌────────────────────────────────┐    │
│  │  Background Jobs               │    │
│  │  • Nightly refresh (cron)      │    │
│  │  • Places API queries          │    │
│  │  • Data validation/cleaning    │    │
│  └────────────────────────────────┘    │
└─────────────┬────────────────────────────┘
              │
              │ API calls (batched, rate-limited)
              │
              ▼
     ┌────────────────────┐
     │ Google Places API  │
     └────────────────────┘
```

### Why Server-Side Caching?

**Benefits:**
1. **Cost Control** - Places API: ~$0.032/search × 1000 users/day = $960/month
   - With caching: One-time cost, refresh weekly = ~$5/month
2. **Performance** - App response: < 100ms vs 500-1000ms direct API
3. **Reliability** - Works if Places API has outage
4. **Data Quality** - Filter closed businesses, validate ratings
5. **Enhanced Features** - Add trending data, user favorites, seasonal recommendations

### Server API Design

```
GET /api/cities
Response:
{
  "cities": [
    {
      "id": "san-francisco",
      "name": "San Francisco",
      "country": "USA",
      "center": { "lat": 37.7749, "lng": -122.4194 },
      "bounds": { "north": 37.81, "south": 37.70, ... },
      "timezone": "America/Los_Angeles"
    },
    ...
  ]
}

GET /api/places/san-francisco/museums?limit=50
Response:
{
  "places": [
    {
      "id": "sfmoma",
      "name": "SFMOMA",
      "type": "museum",
      "coordinates": { "lat": 37.7857, "lng": -122.4011 },
      "rating": 4.6,
      "priceLevel": 2,
      "estimatedCost": 25,
      "isOpen": true,
      "lastUpdated": "2026-01-01T00:00:00Z"
    },
    ...
  ]
}

GET /api/city/san-francisco/config
Response:
{
  "startingLocations": [
    { "name": "Fisherman's Wharf", "lat": 37.8080, "lng": -122.4177 },
    { "name": "Mission District", "lat": 37.7599, "lng": -122.4148 },
    ...
  ],
  "popularCuisines": ["italian", "mexican", "asian", "seafood", "american", "vegetarian"],
  "topActivities": ["museums", "parks", "waterfront", "historic_sites", "shopping"]
}
```

### Mobile App Data Source Abstraction

```kotlin
// Interface for all data sources
interface PlacesDataSource {
    suspend fun searchMuseums(): List<AppPlace>
    suspend fun searchParks(): List<AppPlace>
    suspend fun searchRestaurants(cuisineTypes: List<String>): List<AppPlace>
    suspend fun searchWaterfront(): List<AppPlace>
    suspend fun searchHistoricSites(): List<AppPlace>
    suspend fun searchShopping(): List<AppPlace>
    fun getCityConfig(): CityConfig
}

// Implementation 1: Current (for SF testing)
class SanFranciscoPlacesDataSource : PlacesDataSource {
    // Hardcoded 400+ SF places
}

// Implementation 2: Future (production)
class ServerPlacesDataSource(
    private val apiClient: ApiClient,
    private val cityId: String
) : PlacesDataSource {
    override suspend fun searchMuseums(): List<AppPlace> {
        return apiClient.get("/api/places/$cityId/museums")
    }
    // Caches responses locally with Room DB
}

// Implementation 3: Fallback (if server down)
class GooglePlacesDataSource(
    private val placesClient: PlacesClient,
    private val cityBounds: LatLngBounds
) : PlacesDataSource {
    // Direct Places API calls (expensive, last resort)
}

// Usage in app:
val dataSource = when {
    BuildConfig.DEBUG -> SanFranciscoPlacesDataSource() // Testing
    else -> ServerPlacesDataSource(apiClient, selectedCity) // Production
}
```

## Phase 3: Advanced Features (Long-term)

### 1. Personalization Engine
- Learn from user's past itineraries
- Prefer certain activity types
- Remember budget preferences
- Adapt to walking speed

### 2. Real-Time Optimization
- Check live traffic for travel times
- Monitor place closures/capacity
- Suggest alternatives if restaurant full
- Weather-aware recommendations

### 3. Social Features
- Share itineraries with friends
- Collaborative planning
- See what locals recommend
- Photo spots and tips

### 4. Booking Integration
- Reserve restaurant tables (OpenTable API)
- Buy museum tickets in-app
- Book tours/activities
- One-click itinerary execution

## Implementation Phases

### Phase 1: SF POC (Current - Q1 2026)
- ✅ Core UI flow complete
- ✅ Time range selection
- ✅ Multi-meal scheduling
- ✅ Budget tracking
- ✅ Route optimization
- ✅ 400+ hardcoded SF places
- 🚧 Testing & refinement

**Success Criteria:**
- Generate sensible 5-stop itinerary in < 2 seconds
- Routes minimize travel (< 10km total for full day)
- Budget accuracy within 10%
- 95% of generated itineraries are logically sound

### Phase 2: Backend Development (Q2 2026)
- Build REST API server (Node.js/Python/Go)
- Implement Places API integration
- Set up caching layer (Redis + PostgreSQL)
- Create data refresh jobs
- Add 5 major cities (NYC, LA, Chicago, London, Tokyo)

**Success Criteria:**
- API response time < 100ms (cached)
- 99.9% uptime
- Places data freshness < 30 days
- Cost < $50/month for 1000 DAU

### Phase 3: Mobile Multi-City (Q3 2026)
- Add city selection UI
- Implement ServerPlacesDataSource
- Add offline caching (Room DB)
- Localization (i18n) for 5 languages
- Launch with 10 cities

**Success Criteria:**
- Same quality itineraries as SF POC
- Works offline with cached data
- Supports non-USD currencies
- < 50MB app size

### Phase 4: Scale & Monetization (Q4 2026)
- Expand to 50 cities
- Premium features (longer itineraries, booking integration)
- Social features
- iOS app
- Marketing & user acquisition

## Technical Debt to Address

### Before Multi-City Launch:
1. **Extract city config** from hardcoded values
2. **Create PlacesDataSource interface**
3. **Remove "San Francisco" from UI** (make dynamic)
4. **Add city selection screen**
5. **Implement server API client**

### Performance Optimizations:
1. **Cache parsed itineraries** (don't regenerate identical inputs)
2. **Precompute distance matrices** for common routes
3. **Add analytics** to track generation time
4. **Implement request batching** for server calls

### Code Quality:
1. **Add unit tests** for ItineraryGenerator algorithms
2. **Add integration tests** for full flow
3. **Document API contracts**
4. **Set up CI/CD pipeline**

## Cost Projections

### Current (SF POC):
- Google Maps SDK: Free (< 28,000 loads/month)
- Development: Time only
- **Total: $0/month**

### Phase 2 (Server + 5 cities):
- Server hosting (DigitalOcean): $12/month
- Places API (5 cities × 6 categories × 50 places × $0.032 × 4 refreshes/month): ~$20/month
- Database (managed PostgreSQL): $15/month
- **Total: ~$50/month**

### Phase 3 (Production, 10 cities, 1000 DAU):
- Server scaling: $50/month
- Places API: $40/month
- Maps SDK (slight overage): $10/month
- CDN/assets: $10/month
- **Total: ~$110/month**

### Phase 4 (50 cities, 10,000 DAU):
- Server cluster: $200/month
- Places API: $150/month
- Maps SDK: $50/month
- CDN: $30/month
- Monitoring/analytics: $30/month
- **Total: ~$460/month**

**Revenue targets:** $5/month premium × 1000 users = $5000/month (10x cost coverage)

## Decision Log

### Why start with hardcoded data?
- Faster iteration during algorithm development
- No API costs during testing
- Consistent test dataset
- Offline development

### Why server-side caching vs direct API?
- 95% cost reduction
- Better performance
- Reliability/uptime
- Data curation opportunity

### Why Android-first?
- Kotlin + Compose = fast development
- Team expertise
- Larger global market share
- iOS port straightforward once validated

### Why not use existing APIs (TripAdvisor, Yelp)?
- Want full control over UX
- Avoid competitor dependencies
- Can optimize for itinerary use case
- Own the data relationships

## Repository Structure

```
spotday/
├── spotday-app/              # Android app (current)
│   ├── app/
│   │   └── src/main/java/com/spotday/app/
│   │       ├── model/        # Data models
│   │       ├── api/          # PlacesRepository (to be abstracted)
│   │       ├── service/      # ItineraryGenerator
│   │       └── ui/           # Activities + Composables
│   └── build.gradle.kts
├── spotday-server/           # Backend API (future)
│   ├── src/
│   │   ├── api/              # REST endpoints
│   │   ├── jobs/             # Background refresh jobs
│   │   └── db/               # Database models
│   └── package.json
├── ARCHITECTURE.md           # This file
├── PROJECT_SUMMARY.md        # Implementation details
└── README.md                 # Getting started
```

## Success Metrics

### POC Phase (Current):
- [ ] Generate 100 test itineraries without crashes
- [ ] Average route distance < 12km for full day
- [ ] Budget variance < 15%
- [ ] User can regenerate and see different neighborhoods

### Multi-City Phase:
- [ ] 10 cities supported
- [ ] Same quality metrics as SF
- [ ] API response time < 150ms p95
- [ ] Server uptime > 99.5%

### Scale Phase:
- [ ] 50 cities
- [ ] 10,000+ monthly active users
- [ ] < 1% error rate
- [ ] 4.5+ star rating

## Open Questions

1. **Data freshness:** How often to refresh each city? (Weekly? Monthly?)
2. **Pricing model:** Freemium? One-time purchase? Subscription?
3. **Offline mode:** How much data to cache locally? (Full city? Popular only?)
4. **User accounts:** Required or optional? (Needed for cross-device sync)
5. **Attribution:** How to credit data sources? (Google Places TOS compliance)

## Next Steps

**Immediate (This Week):**
1. Continue testing SF POC with real scenarios
2. Gather feedback on itinerary quality
3. Fine-tune route optimization parameters
4. Document edge cases

**Short-term (This Month):**
1. Achieve 95% quality threshold for SF
2. Plan backend architecture details
3. Choose server tech stack
4. Design database schema

**Medium-term (Q2 2026):**
1. Build MVP backend server
2. Integrate 3 test cities
3. Refactor app to use ServerPlacesDataSource
4. Prepare for beta launch

---

**Last Updated:** January 1, 2026  
**Status:** Phase 1 (SF POC) - Active Development  
**Next Review:** End of Q1 2026

