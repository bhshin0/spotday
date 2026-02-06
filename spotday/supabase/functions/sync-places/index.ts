// sync-places Edge Function
// Fetches places from Google Places API and caches them in Supabase
// ALWAYS fetches opening hours from Place Details API
//
// Usage:
//   POST /sync-places
//   Body: { "city_id": "phoenix" }               - sync all neighborhoods in city
//   Body: { "neighborhood_id": "downtown" }      - sync single neighborhood
//   Body: { "city_id": "phoenix", "types": ["restaurant", "bar"] }  - specific types only

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const GOOGLE_PLACES_API_KEY = Deno.env.get("GOOGLE_PLACES_API_KEY")!;
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const NEARBY_SEARCH_URL = "https://places.googleapis.com/v1/places:searchNearby";
const PLACE_DETAILS_URL = "https://places.googleapis.com/v1/places";

// ============================================
// TYPES
// ============================================

interface GooglePlace {
  place_id: string;
  name: string;
  geometry: { location: { lat: number; lng: number } };
  rating?: number;
  user_ratings_total?: number;
  price_level?: number;
  types?: string[];
}

interface OpeningPeriod {
  open: { day: number; time: string };
  close?: { day: number; time: string };
}

interface DayHours {
  open: string;
  close: string;
}

interface WeeklyHours {
  monday: DayHours | null;
  tuesday: DayHours | null;
  wednesday: DayHours | null;
  thursday: DayHours | null;
  friday: DayHours | null;
  saturday: DayHours | null;
  sunday: DayHours | null;
}

const DAY_NAMES = ["sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"] as const;

// Google Place types to fetch
const PLACE_TYPES = [
  // Food & Drink
  "restaurant", "cafe", "bar", "night_club",
  // Culture
  "museum", "art_gallery",
  // Nature & Outdoors
  "park", "garden", "beach", "hiking_area", "campground",
  // Waterfront
  "marina",
  // Entertainment
  "performing_arts_theater", "concert_hall", "amusement_park",
  "movie_theater", "karaoke",
  // Activities
  "shopping_mall", "zoo", "aquarium", "tourist_attraction",
  "historical_landmark", "stadium", "bowling_alley", "video_arcade",
  // Wellness
  "spa"
];

// Fast food chains to exclude
const FAST_FOOD_BLOCKLIST = [
  "mcdonald's", "burger king", "wendy's", "five guys", "in-n-out",
  "jack in the box", "carl's jr", "hardee's", "sonic", "whataburger",
  "kfc", "chick-fil-a", "popeyes", "raising cane's", "wingstop",
  "taco bell", "chipotle", "del taco", "qdoba",
  "pizza hut", "domino's", "papa john's", "little caesars",
  "subway", "jimmy john's", "jersey mike's",
  "starbucks", "dunkin'", "peet's coffee",
  "panda express", "chili's", "denny's", "ihop",
  "panera", "dairy queen", "baskin-robbins",
  "7-eleven", "circle k", "arby's"
];

// ============================================
// HELPER FUNCTIONS
// ============================================

function isFastFood(name: string): boolean {
  const lower = name.toLowerCase();
  return FAST_FOOD_BLOCKLIST.some(chain => lower.includes(chain));
}

// Wellness sub-type detection by name keywords
function getWellnessSubType(name: string): string | null {
  const lower = name.toLowerCase();

  // Blocklist - not day-trip appropriate
  const blocklist = [
    "nail", "laser", "clinic", "surgery", "medical", "aesthet",
    "threading", "wax", "fitness", "gym", "barber", "hair",
    "colonic", "acne", "cosmetic", "plastic", "lash", "brow",
    "salon", "skincare", "skin care", "dermat", "beauty"
  ];
  if (blocklist.some(kw => lower.includes(kw))) return null;

  // Massage detection
  if (lower.includes("massage") || lower.includes("bodywork") ||
      lower.includes("reflexology")) {
    return "MASSAGE";
  }

  // Sauna/Spa detection (includes float, cryo, bathhouse, day spas)
  if (lower.includes("sauna") || lower.includes("bathhouse") ||
      lower.includes("bath house") || lower.includes("onsen") ||
      lower.includes("float") || lower.includes("cryo") ||
      lower.includes("day spa") || lower.includes("thai spa") ||
      lower.includes("korean spa")) {
    return "SAUNA";
  }

  // Generic "spa" in name - only if it passed the blocklist
  if (lower.includes("spa")) {
    return "SAUNA";
  }

  return null;
}

function formatTime(time: string): string {
  return time.length === 4 ? `${time.slice(0, 2)}:${time.slice(2)}` : time;
}

function determinePlaceType(types: string[]): string {
  // Existing mappings
  if (types.includes("restaurant") || types.includes("food") || types.includes("cafe")) return "RESTAURANT";
  if (types.includes("bar") || types.includes("night_club")) return "NIGHTLIFE";
  if (types.includes("beach")) return "BEACH";
  if (types.includes("marina")) return "WATERFRONT";
  if (types.includes("museum") || types.includes("art_gallery")) return "MUSEUM";
  if (types.includes("park") || types.includes("garden")) return "PARK";
  if (types.includes("karaoke")) return "NIGHTLIFE";
  // Spa handling is done separately in the sync loop via getWellnessSubType()
  if (types.includes("shopping_mall") || types.includes("store")) return "SHOPPING";
  if (types.includes("zoo") || types.includes("aquarium")) return "ZOO";
  if (types.includes("movie_theater")) return "CINEMA";
  if (types.includes("tourist_attraction")) return "ATTRACTION";
  if (types.includes("amusement_park")) return "ENTERTAINMENT";

  // NEW mappings
  if (types.includes("historical_landmark") || types.includes("monument")) return "HISTORIC_SITE";
  if (types.includes("performing_arts_theater") || types.includes("concert_hall") ||
      types.includes("opera_house") || types.includes("comedy_club")) return "ENTERTAINMENT";
  if (types.includes("hiking_area") || types.includes("campground") ||
      types.includes("national_park")) return "OUTDOOR";
  if (types.includes("gym") || types.includes("fitness_center") ||
      types.includes("stadium") || types.includes("sports_complex")) return "SPORTS";
  if (types.includes("bowling_alley") || types.includes("video_arcade")) return "GAMES";

  return "OTHER";
}

function getDefaultHours(placeType: string): WeeklyHours {
  const defaults: Record<string, WeeklyHours> = {
    NIGHTLIFE: {
      monday: { open: "16:00", close: "02:00" },
      tuesday: { open: "16:00", close: "02:00" },
      wednesday: { open: "16:00", close: "02:00" },
      thursday: { open: "16:00", close: "02:00" },
      friday: { open: "16:00", close: "02:00" },
      saturday: { open: "16:00", close: "02:00" },
      sunday: { open: "16:00", close: "00:00" },
    },
    RESTAURANT: {
      monday: { open: "11:00", close: "22:00" },
      tuesday: { open: "11:00", close: "22:00" },
      wednesday: { open: "11:00", close: "22:00" },
      thursday: { open: "11:00", close: "22:00" },
      friday: { open: "11:00", close: "23:00" },
      saturday: { open: "10:00", close: "23:00" },
      sunday: { open: "10:00", close: "21:00" },
    },
    MUSEUM: {
      monday: null,
      tuesday: { open: "10:00", close: "17:00" },
      wednesday: { open: "10:00", close: "17:00" },
      thursday: { open: "10:00", close: "21:00" },
      friday: { open: "10:00", close: "17:00" },
      saturday: { open: "10:00", close: "17:00" },
      sunday: { open: "11:00", close: "17:00" },
    },
    PARK: {
      monday: { open: "06:00", close: "22:00" },
      tuesday: { open: "06:00", close: "22:00" },
      wednesday: { open: "06:00", close: "22:00" },
      thursday: { open: "06:00", close: "22:00" },
      friday: { open: "06:00", close: "22:00" },
      saturday: { open: "06:00", close: "22:00" },
      sunday: { open: "06:00", close: "22:00" },
    },
    MASSAGE: {
      monday: { open: "10:00", close: "21:00" },
      tuesday: { open: "10:00", close: "21:00" },
      wednesday: { open: "10:00", close: "21:00" },
      thursday: { open: "10:00", close: "21:00" },
      friday: { open: "10:00", close: "21:00" },
      saturday: { open: "10:00", close: "20:00" },
      sunday: { open: "11:00", close: "19:00" },
    },
    SAUNA: {
      monday: { open: "09:00", close: "22:00" },
      tuesday: { open: "09:00", close: "22:00" },
      wednesday: { open: "09:00", close: "22:00" },
      thursday: { open: "09:00", close: "22:00" },
      friday: { open: "09:00", close: "23:00" },
      saturday: { open: "09:00", close: "23:00" },
      sunday: { open: "09:00", close: "21:00" },
    },
    BEACH: {
      monday: { open: "06:00", close: "20:00" },
      tuesday: { open: "06:00", close: "20:00" },
      wednesday: { open: "06:00", close: "20:00" },
      thursday: { open: "06:00", close: "20:00" },
      friday: { open: "06:00", close: "20:00" },
      saturday: { open: "06:00", close: "20:00" },
      sunday: { open: "06:00", close: "20:00" },
    },
    WATERFRONT: {
      monday: { open: "08:00", close: "18:00" },
      tuesday: { open: "08:00", close: "18:00" },
      wednesday: { open: "08:00", close: "18:00" },
      thursday: { open: "08:00", close: "18:00" },
      friday: { open: "08:00", close: "18:00" },
      saturday: { open: "08:00", close: "18:00" },
      sunday: { open: "08:00", close: "18:00" },
    },
    BREWERY: {
      monday: { open: "12:00", close: "21:00" },
      tuesday: { open: "12:00", close: "21:00" },
      wednesday: { open: "12:00", close: "21:00" },
      thursday: { open: "12:00", close: "21:00" },
      friday: { open: "12:00", close: "21:00" },
      saturday: { open: "12:00", close: "21:00" },
      sunday: { open: "12:00", close: "21:00" },
    },
    SHOPPING: {
      monday: { open: "10:00", close: "21:00" },
      tuesday: { open: "10:00", close: "21:00" },
      wednesday: { open: "10:00", close: "21:00" },
      thursday: { open: "10:00", close: "21:00" },
      friday: { open: "10:00", close: "21:00" },
      saturday: { open: "10:00", close: "21:00" },
      sunday: { open: "11:00", close: "18:00" },
    },
    ZOO: {
      monday: { open: "09:00", close: "17:00" },
      tuesday: { open: "09:00", close: "17:00" },
      wednesday: { open: "09:00", close: "17:00" },
      thursday: { open: "09:00", close: "17:00" },
      friday: { open: "09:00", close: "17:00" },
      saturday: { open: "09:00", close: "18:00" },
      sunday: { open: "09:00", close: "18:00" },
    },
    CINEMA: {
      monday: { open: "11:00", close: "23:00" },
      tuesday: { open: "11:00", close: "23:00" },
      wednesday: { open: "11:00", close: "23:00" },
      thursday: { open: "11:00", close: "23:00" },
      friday: { open: "11:00", close: "00:00" },
      saturday: { open: "10:00", close: "00:00" },
      sunday: { open: "10:00", close: "23:00" },
    },
    ATTRACTION: {
      monday: { open: "08:00", close: "20:00" },
      tuesday: { open: "08:00", close: "20:00" },
      wednesday: { open: "08:00", close: "20:00" },
      thursday: { open: "08:00", close: "20:00" },
      friday: { open: "08:00", close: "20:00" },
      saturday: { open: "08:00", close: "20:00" },
      sunday: { open: "08:00", close: "20:00" },
    },
    HISTORIC_SITE: {
      monday: { open: "09:00", close: "17:00" },
      tuesday: { open: "09:00", close: "17:00" },
      wednesday: { open: "09:00", close: "17:00" },
      thursday: { open: "09:00", close: "17:00" },
      friday: { open: "09:00", close: "17:00" },
      saturday: { open: "10:00", close: "18:00" },
      sunday: { open: "10:00", close: "18:00" },
    },
    ENTERTAINMENT: {
      monday: { open: "11:00", close: "23:00" },
      tuesday: { open: "11:00", close: "23:00" },
      wednesday: { open: "11:00", close: "23:00" },
      thursday: { open: "11:00", close: "23:00" },
      friday: { open: "11:00", close: "00:00" },
      saturday: { open: "10:00", close: "00:00" },
      sunday: { open: "10:00", close: "23:00" },
    },
    OUTDOOR: {
      monday: { open: "06:00", close: "20:00" },
      tuesday: { open: "06:00", close: "20:00" },
      wednesday: { open: "06:00", close: "20:00" },
      thursday: { open: "06:00", close: "20:00" },
      friday: { open: "06:00", close: "20:00" },
      saturday: { open: "06:00", close: "20:00" },
      sunday: { open: "06:00", close: "20:00" },
    },
    SPORTS: {
      monday: { open: "05:00", close: "23:00" },
      tuesday: { open: "05:00", close: "23:00" },
      wednesday: { open: "05:00", close: "23:00" },
      thursday: { open: "05:00", close: "23:00" },
      friday: { open: "05:00", close: "23:00" },
      saturday: { open: "06:00", close: "22:00" },
      sunday: { open: "06:00", close: "22:00" },
    },
    GAMES: {
      monday: { open: "12:00", close: "23:00" },
      tuesday: { open: "12:00", close: "23:00" },
      wednesday: { open: "12:00", close: "23:00" },
      thursday: { open: "12:00", close: "23:00" },
      friday: { open: "12:00", close: "00:00" },
      saturday: { open: "10:00", close: "00:00" },
      sunday: { open: "10:00", close: "22:00" },
    },
  };

  return defaults[placeType] || defaults.RESTAURANT;
}

// ============================================
// GOOGLE API FUNCTIONS
// ============================================

async function fetchNearbyPlaces(lat: number, lng: number, radius: number, type: string): Promise<GooglePlace[]> {
  const response = await fetch(NEARBY_SEARCH_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Goog-Api-Key": GOOGLE_PLACES_API_KEY,
      "X-Goog-FieldMask": "places.id,places.displayName,places.location,places.rating,places.userRatingCount,places.priceLevel,places.types"
    },
    body: JSON.stringify({
      includedTypes: [type],
      locationRestriction: {
        circle: {
          center: { latitude: lat, longitude: lng },
          radius: radius
        }
      },
      maxResultCount: 20
    })
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Nearby Search API error: ${response.status} - ${errorText}`);
  }

  const data = await response.json();
  return (data.places || []).map(mapNewApiPlace);
}

// Map new API response to our existing interface
function mapNewApiPlace(place: any): GooglePlace {
  return {
    place_id: place.id,
    name: place.displayName?.text || "",
    geometry: {
      location: {
        lat: place.location?.latitude || 0,
        lng: place.location?.longitude || 0
      }
    },
    rating: place.rating,
    user_ratings_total: place.userRatingCount,
    price_level: mapPriceLevel(place.priceLevel),
    types: place.types || []
  };
}

function mapPriceLevel(priceLevel?: string): number | undefined {
  const mapping: Record<string, number> = {
    "PRICE_LEVEL_FREE": 0,
    "PRICE_LEVEL_INEXPENSIVE": 1,
    "PRICE_LEVEL_MODERATE": 2,
    "PRICE_LEVEL_EXPENSIVE": 3,
    "PRICE_LEVEL_VERY_EXPENSIVE": 4
  };
  return priceLevel ? mapping[priceLevel] : undefined;
}

async function fetchPlaceHours(placeId: string): Promise<WeeklyHours | null> {
  try {
    const response = await fetch(`${PLACE_DETAILS_URL}/${placeId}`, {
      headers: {
        "X-Goog-Api-Key": GOOGLE_PLACES_API_KEY,
        "X-Goog-FieldMask": "regularOpeningHours"
      }
    });

    if (!response.ok) return null;

    const data = await response.json();
    if (!data.regularOpeningHours?.periods) return null;

    return parseOpeningHours(data.regularOpeningHours.periods);
  } catch {
    return null;
  }
}

// Parse opening hours from new API format
function parseOpeningHours(periods: any[]): WeeklyHours {
  const hours: WeeklyHours = {
    monday: null, tuesday: null, wednesday: null, thursday: null,
    friday: null, saturday: null, sunday: null
  };

  // Check for 24/7 (single period with open at day 0, hour 0, no close)
  if (periods.length === 1 && !periods[0].close && periods[0].open.day === 0 && periods[0].open.hour === 0) {
    const allDay: DayHours = { open: "00:00", close: "23:59" };
    return {
      sunday: allDay, monday: allDay, tuesday: allDay, wednesday: allDay,
      thursday: allDay, friday: allDay, saturday: allDay
    };
  }

  for (const period of periods) {
    const dayName = DAY_NAMES[period.open.day];
    hours[dayName as keyof WeeklyHours] = {
      open: `${String(period.open.hour).padStart(2, '0')}:${String(period.open.minute || 0).padStart(2, '0')}`,
      close: period.close
        ? `${String(period.close.hour).padStart(2, '0')}:${String(period.close.minute || 0).padStart(2, '0')}`
        : "23:59"
    };
  }

  return hours;
}

// ============================================
// MAIN HANDLER
// ============================================

serve(async (req) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: { "Content-Type": "application/json" },
    });
  }

  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

  // Parse request
  let cityId: string | null = null;
  let neighborhoodId: string | null = null;
  let typesToFetch = PLACE_TYPES;

  try {
    const body = await req.json();
    cityId = body.city_id || null;
    neighborhoodId = body.neighborhood_id || null;
    if (body.types) typesToFetch = body.types;
  } catch { /* empty body */ }

  console.log(`Sync: city=${cityId}, neighborhood=${neighborhoodId}, types=${typesToFetch.length}`);

  // Get neighborhoods to sync
  let query = supabase
    .from("neighborhoods")
    .select("*, cities!inner(id, name, is_active)")
    .eq("cities.is_active", true);

  if (cityId) query = query.eq("city_id", cityId);
  if (neighborhoodId) query = query.eq("id", neighborhoodId);

  const { data: neighborhoods, error: fetchError } = await query;
  if (fetchError) {
    return new Response(JSON.stringify({ error: fetchError.message }), { status: 500 });
  }

  const results: Array<{ neighborhood: string; synced: number; status: string }> = [];

  // Process each neighborhood
  for (const neighborhood of neighborhoods || []) {
    try {
      const places: Array<{
        id: string;
        city_id: string;
        neighborhood_id: string;
        name: string;
        place_type: string;
        lat: number;
        lng: number;
        rating: number | null;
        review_count: number;
        price_level: number | null;
        is_outdoor: boolean;
        weekly_hours: WeeklyHours;
      }> = [];

      const seenIds = new Set<string>();

      // Fetch places for each type
      for (const placeType of typesToFetch) {
        let googlePlaces: GooglePlace[];
        try {
          googlePlaces = await fetchNearbyPlaces(
            neighborhood.center_lat,
            neighborhood.center_lng,
            neighborhood.radius_meters || 800,
            placeType
          );
        } catch (e) {
          console.warn(`⚠ Skipping type ${placeType}: ${e instanceof Error ? e.message : e}`);
          continue;
        }

        for (const gp of googlePlaces) {
          if (isFastFood(gp.name)) continue;
          if (seenIds.has(gp.place_id)) continue;
          seenIds.add(gp.place_id);

          // Determine place type - special handling for spa
          let appType: string;
          if ((gp.types || []).includes("spa")) {
            const wellnessType = getWellnessSubType(gp.name);
            if (!wellnessType) continue;  // Skip invalid wellness places
            appType = wellnessType;
          } else {
            appType = determinePlaceType(gp.types || []);
          }

          // Fetch hours (rate limited)
          await new Promise(r => setTimeout(r, 50));
          const hours = await fetchPlaceHours(gp.place_id);
          
          places.push({
            id: gp.place_id,
            city_id: neighborhood.city_id,
            neighborhood_id: neighborhood.id,
            name: gp.name,
            place_type: appType,
            lat: gp.geometry.location.lat,
            lng: gp.geometry.location.lng,
            rating: gp.rating || null,
            review_count: gp.user_ratings_total || 0,
            price_level: gp.price_level || null,
            is_outdoor: (gp.types || []).includes("park"),
            weekly_hours: hours || getDefaultHours(appType),
          });
        }

        // Rate limit between types
        await new Promise(r => setTimeout(r, 500));
      }

      // Upsert places
      if (places.length > 0) {
        const { error: upsertError } = await supabase
          .from("cached_places")
          .upsert(places, { onConflict: "id" });

        if (upsertError) throw upsertError;
      }

      results.push({
        neighborhood: neighborhood.id,
        synced: places.length,
        status: "success",
      });

      console.log(`✓ ${neighborhood.id}: ${places.length} places`);
    } catch (error) {
      results.push({
        neighborhood: neighborhood.id,
        synced: 0,
        status: `error: ${error instanceof Error ? error.message : "Unknown"}`,
      });
    }
  }

  return new Response(
    JSON.stringify({
      success: true,
      neighborhoods: results.length,
      results,
    }),
    { status: 200, headers: { "Content-Type": "application/json" } }
  );
});
