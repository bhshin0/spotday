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

const NEARBY_SEARCH_URL = "https://maps.googleapis.com/maps/api/place/nearbysearch/json";
const PLACE_DETAILS_URL = "https://maps.googleapis.com/maps/api/place/details/json";

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
  "restaurant", "cafe", "bar", "night_club", 
  "museum", "park", "spa", "shopping_mall"
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

function formatTime(time: string): string {
  return time.length === 4 ? `${time.slice(0, 2)}:${time.slice(2)}` : time;
}

function determinePlaceType(types: string[]): string {
  if (types.includes("restaurant") || types.includes("food") || types.includes("cafe")) return "RESTAURANT";
  if (types.includes("bar") || types.includes("night_club")) return "NIGHTLIFE";
  if (types.includes("museum") || types.includes("art_gallery")) return "MUSEUM";
  if (types.includes("park")) return "PARK";
  if (types.includes("spa") || types.includes("beauty_salon")) return "WELLNESS";
  if (types.includes("shopping_mall") || types.includes("store")) return "SHOPPING";
  if (types.includes("movie_theater") || types.includes("amusement_park")) return "ENTERTAINMENT";
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
    WELLNESS: {
      monday: { open: "09:00", close: "21:00" },
      tuesday: { open: "09:00", close: "21:00" },
      wednesday: { open: "09:00", close: "21:00" },
      thursday: { open: "09:00", close: "21:00" },
      friday: { open: "09:00", close: "21:00" },
      saturday: { open: "09:00", close: "18:00" },
      sunday: null,
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
  };
  
  return defaults[placeType] || defaults.RESTAURANT;
}

// ============================================
// GOOGLE API FUNCTIONS
// ============================================

async function fetchNearbyPlaces(lat: number, lng: number, radius: number, type: string): Promise<GooglePlace[]> {
  const params = new URLSearchParams({
    location: `${lat},${lng}`,
    radius: radius.toString(),
    type,
    key: GOOGLE_PLACES_API_KEY,
  });

  const response = await fetch(`${NEARBY_SEARCH_URL}?${params}`);
  if (!response.ok) throw new Error(`Nearby Search API error: ${response.status}`);

  const data = await response.json();
  if (data.status !== "OK" && data.status !== "ZERO_RESULTS") {
    throw new Error(`Nearby Search API status: ${data.status}`);
  }

  return data.results || [];
}

async function fetchPlaceHours(placeId: string): Promise<WeeklyHours | null> {
  const params = new URLSearchParams({
    place_id: placeId,
    fields: "opening_hours",
    key: GOOGLE_PLACES_API_KEY,
  });

  try {
    const response = await fetch(`${PLACE_DETAILS_URL}?${params}`);
    if (!response.ok) return null;

    const data = await response.json();
    if (data.status !== "OK" || !data.result?.opening_hours?.periods) return null;

    const periods: OpeningPeriod[] = data.result.opening_hours.periods;
    
    // Check for 24/7
    if (periods.length === 1 && !periods[0].close && periods[0].open.day === 0 && periods[0].open.time === "0000") {
      const allDay: DayHours = { open: "00:00", close: "23:59" };
      return {
        sunday: allDay, monday: allDay, tuesday: allDay, wednesday: allDay,
        thursday: allDay, friday: allDay, saturday: allDay,
      };
    }

    // Parse each period
    const hours: WeeklyHours = {
      monday: null, tuesday: null, wednesday: null, thursday: null,
      friday: null, saturday: null, sunday: null,
    };

    for (const period of periods) {
      const dayName = DAY_NAMES[period.open.day];
      hours[dayName as keyof WeeklyHours] = {
        open: formatTime(period.open.time),
        close: period.close ? formatTime(period.close.time) : "23:59",
      };
    }

    return hours;
  } catch {
    return null;
  }
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
        const googlePlaces = await fetchNearbyPlaces(
          neighborhood.center_lat,
          neighborhood.center_lng,
          neighborhood.radius_meters || 800,
          placeType
        );

        for (const gp of googlePlaces) {
          if (isFastFood(gp.name)) continue;
          if (seenIds.has(gp.place_id)) continue;
          seenIds.add(gp.place_id);

          // Fetch hours (rate limited)
          await new Promise(r => setTimeout(r, 50));
          const hours = await fetchPlaceHours(gp.place_id);
          
          const appType = determinePlaceType(gp.types || []);
          
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
