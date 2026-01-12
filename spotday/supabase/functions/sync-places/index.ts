// sync-places Edge Function
// Fetches places from Google Places API and caches them in Supabase
// Runs weekly or on-demand per neighborhood
//
// Strategy: Minimal types, first page only (20 per type), prominence sort
// Cost: ~$1.60 per full SF sync (12 neighborhoods × 8 types × 1 request each)
//
// Neighborhood Assignment:
// - Places are assigned to the neighborhood that syncs them
// - If a place is synced by multiple neighborhoods (overlapping radii),
//   we keep the assignment to the NEAREST neighborhood center
// - neighborhood_source tracks how the assignment was made

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const GOOGLE_PLACES_API_KEY = Deno.env.get("GOOGLE_PLACES_API_KEY")!;
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const PLACES_API_BASE = "https://maps.googleapis.com/maps/api/place";

// Minimal set of Google Place types to fetch (8 types)
// First page only (20 results per type) - most prominent places
const TYPES_TO_FETCH = [
  "restaurant",     // → RESTAURANT
  "cafe",           // → RESTAURANT  
  "bar",            // → NIGHTLIFE
  "night_club",     // → NIGHTLIFE
  "museum",         // → MUSEUM
  "park",           // → PARK
  "spa",            // → WELLNESS
  "shopping_mall",  // → SHOPPING
];

// Fast food chains to exclude from sync (case-insensitive matching)
const FAST_FOOD_BLOCKLIST = [
  // Burgers
  "mcdonald's", "burger king", "wendy's", "five guys", "in-n-out",
  "jack in the box", "carl's jr", "hardee's", "sonic", "whataburger",
  "shake shack", "smashburger", "checkers", "rally's", "white castle",
  "culver's", "steak 'n shake",
  // Chicken
  "kfc", "chick-fil-a", "popeyes", "raising cane's", "wingstop",
  "zaxby's", "bojangles", "church's chicken", "el pollo loco",
  // Mexican
  "taco bell", "chipotle", "del taco", "qdoba", "moe's southwest",
  "rubio's",
  // Pizza
  "pizza hut", "domino's", "papa john's", "little caesars", "papa murphy's",
  // Subs/Sandwiches
  "subway", "jimmy john's", "jersey mike's", "firehouse subs",
  "potbelly", "quiznos", "which wich", "blimpie",
  // Coffee chains
  "starbucks", "dunkin'", "peet's coffee",
  // Asian fast food
  "panda express", "pei wei", "cava",
  // Casual dining chains
  "chili's", "denny's", "ihop", "cheesecake factory", "bubba gump",
  "first watch",
  // Bakery/Bagel chains
  "panera", "einstein bros", "bruegger's", "noah's ny bagels",
  "krispy kreme",
  // Ice cream chains
  "dairy queen", "baskin-robbins", "cold stone", "ben & jerry's",
  // Juice/Smoothie chains
  "jamba", "smoothie king", "nekter juice",
  // Fast-casual
  "salad and go",
  // Convenience stores
  "7-eleven", "circle k", "quiktrip",
  // Other
  "arby's", "auntie anne's", "cinnabon",
];

// Check if a place name matches any fast food chain (case-insensitive)
function isFastFood(name: string): boolean {
  const lowerName = name.toLowerCase();
  return FAST_FOOD_BLOCKLIST.some((chain) => lowerName.includes(chain));
}

interface GooglePlace {
  place_id: string;
  name: string;
  geometry: {
    location: {
      lat: number;
      lng: number;
    };
  };
  rating?: number;
  user_ratings_total?: number;
  price_level?: number;
  types?: string[];
  opening_hours?: {
    open_now?: boolean;
  };
  vicinity?: string;
}

interface GooglePlacesResponse {
  results: GooglePlace[];
  next_page_token?: string;
  status: string;
}

interface CachedPlace {
  id: string;
  city_id: string;
  neighborhood_id: string | null;
  neighborhood_source: "SYNC" | "DISTANCE_TIE" | "MANUAL";
  name: string;
  place_type: string;
  lat: number;
  lng: number;
  rating: number | null;
  review_count: number;
  price_level: number | null;
  is_outdoor: boolean;
  raw_json: unknown;
}

interface Neighborhood {
  id: string;
  city_id: string;
  center_lat: number;
  center_lng: number;
  radius_meters: number;
}

// Calculate squared distance between two points (no need for sqrt for comparison)
function distanceSquared(
  lat1: number,
  lng1: number,
  lat2: number,
  lng2: number
): number {
  const latDiff = lat1 - lat2;
  const lngDiff = lng1 - lng2;
  return latDiff * latDiff + lngDiff * lngDiff;
}

// Determine place type from Google types
// Maps to app's PlaceType enum values
function determinePlaceType(types: string[]): string {
  if (types.includes("restaurant") || types.includes("food")) return "RESTAURANT";
  if (types.includes("cafe") || types.includes("bakery")) return "RESTAURANT";
  if (types.includes("bar") || types.includes("night_club")) return "NIGHTLIFE";
  if (types.includes("museum") || types.includes("art_gallery")) return "MUSEUM";
  if (types.includes("park")) return "PARK";
  if (types.includes("spa") || types.includes("beauty_salon")) return "WELLNESS";
  if (types.includes("shopping_mall") || types.includes("store")) return "SHOPPING";
  if (types.includes("movie_theater") || types.includes("amusement_park")) return "ENTERTAINMENT";
  if (types.includes("church") || types.includes("hindu_temple")) return "HISTORIC_SITE";
  return "OTHER";
}

// Check if place is likely outdoor
function isOutdoorPlace(types: string[]): boolean {
  const outdoorTypes = ["park", "zoo", "campground", "natural_feature"];
  return types.some((t) => outdoorTypes.includes(t));
}

// Fetch places from Google Places API
async function fetchPlacesNearby(
  lat: number,
  lng: number,
  radius: number,
  type: string
): Promise<GooglePlace[]> {
  const params = new URLSearchParams({
    location: `${lat},${lng}`,
    radius: radius.toString(),
    type: type,
    key: GOOGLE_PLACES_API_KEY,
  });

  const response = await fetch(`${PLACES_API_BASE}/nearbysearch/json?${params}`);

  if (!response.ok) {
    throw new Error(`Google Places API error: ${response.status}`);
  }

  const data: GooglePlacesResponse = await response.json();

  if (data.status !== "OK" && data.status !== "ZERO_RESULTS") {
    throw new Error(`Google Places API status: ${data.status}`);
  }

  return data.results || [];
}

// Convert Google Place to our cached format
function convertPlace(
  place: GooglePlace,
  cityId: string,
  neighborhoodId: string | null,
  neighborhoodSource: "SYNC" | "DISTANCE_TIE" | "MANUAL" = "SYNC"
): CachedPlace {
  return {
    id: place.place_id,
    city_id: cityId,
    neighborhood_id: neighborhoodId,
    neighborhood_source: neighborhoodSource,
    name: place.name,
    place_type: determinePlaceType(place.types || []),
    lat: place.geometry.location.lat,
    lng: place.geometry.location.lng,
    rating: place.rating || null,
    review_count: place.user_ratings_total || 0,
    price_level: place.price_level || null,
    is_outdoor: isOutdoorPlace(place.types || []),
    raw_json: place,
  };
}

serve(async (req) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: { "Content-Type": "application/json" },
    });
  }

  try {
    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

    // Parse request body
    let targetCityId: string | null = null;
    let targetNeighborhoodId: string | null = null;
    try {
      const body = await req.json();
      targetCityId = body.city_id || null;
      targetNeighborhoodId = body.neighborhood_id || null;
    } catch {
      // No body, sync all
    }

    // Get neighborhoods to sync
    let query = supabase
      .from("neighborhoods")
      .select("*, cities!inner(id, name, state_code, is_active)")
      .eq("cities.is_active", true);

    if (targetCityId) {
      query = query.eq("city_id", targetCityId);
    }
    if (targetNeighborhoodId) {
      query = query.eq("id", targetNeighborhoodId);
    }

    const { data: neighborhoods, error: neighborhoodsError } = await query;

    if (neighborhoodsError) {
      throw new Error(`Failed to fetch neighborhoods: ${neighborhoodsError.message}`);
    }

    // Build a map of all neighborhoods for distance comparison
    const neighborhoodMap = new Map<string, Neighborhood>();
    for (const n of neighborhoods || []) {
      neighborhoodMap.set(n.id, {
        id: n.id,
        city_id: n.city_id,
        center_lat: n.center_lat,
        center_lng: n.center_lng,
        radius_meters: n.radius_meters || 800,
      });
    }

    const results: Array<{
      neighborhood_id: string;
      places_synced: number;
      duplicates_resolved: number;
      status: string;
    }> = [];

    // Process each neighborhood
    for (const neighborhood of neighborhoods || []) {
      const { data: syncLog } = await supabase
        .from("sync_log")
        .insert({
          sync_type: "places",
          city_id: neighborhood.city_id,
          status: "running",
          metadata: { neighborhood_id: neighborhood.id },
        })
        .select()
        .single();

      try {
        const allPlaces: CachedPlace[] = [];
        let duplicatesResolved = 0;

        // Fetch places for each type (first page only - 20 most prominent per type)
        for (const placeType of TYPES_TO_FETCH) {
          const places = await fetchPlacesNearby(
            neighborhood.center_lat,
            neighborhood.center_lng,
            neighborhood.radius_meters || 800,
            placeType
          );

          for (const place of places) {
            // Skip fast food chains
            if (isFastFood(place.name)) {
              continue;
            }
            // Skip if already added in this batch (dedup by place_id)
            if (!allPlaces.some((p) => p.id === place.place_id)) {
              allPlaces.push(
                convertPlace(place, neighborhood.city_id, neighborhood.id, "SYNC")
              );
            }
          }

          // Rate limiting - 1 second between requests
          await new Promise((resolve) => setTimeout(resolve, 1000));
        }

        // Check for existing places and handle overlapping neighborhoods
        // Fetch all place IDs we're about to upsert
        const placeIds = allPlaces.map((p) => p.id);
        
        const { data: existingPlaces } = await supabase
          .from("cached_places")
          .select("id, neighborhood_id, lat, lng")
          .in("id", placeIds);

        const existingMap = new Map(
          (existingPlaces || []).map((p) => [p.id, p])
        );

        // Filter places: only upsert if this neighborhood is closer (or place is new)
        const placesToUpsert: CachedPlace[] = [];

        for (const place of allPlaces) {
          const existing = existingMap.get(place.id);

          if (!existing) {
            // New place - always add
            placesToUpsert.push(place);
          } else if (existing.neighborhood_id === neighborhood.id) {
            // Same neighborhood - update (refresh data)
            placesToUpsert.push(place);
          } else if (existing.neighborhood_id) {
            // Different neighborhood - compare distances
            const existingNeighborhood = neighborhoodMap.get(existing.neighborhood_id);
            const currentNeighborhood = neighborhoodMap.get(neighborhood.id);

            if (existingNeighborhood && currentNeighborhood) {
              const distToExisting = distanceSquared(
                place.lat,
                place.lng,
                existingNeighborhood.center_lat,
                existingNeighborhood.center_lng
              );
              const distToCurrent = distanceSquared(
                place.lat,
                place.lng,
                currentNeighborhood.center_lat,
                currentNeighborhood.center_lng
              );

              if (distToCurrent < distToExisting) {
                // Current neighborhood is closer - update with DISTANCE_TIE source
                place.neighborhood_source = "DISTANCE_TIE";
                placesToUpsert.push(place);
                duplicatesResolved++;
                console.log(
                  `Reassigned ${place.name} from ${existing.neighborhood_id} to ${neighborhood.id} (closer)`
                );
              }
              // else: existing neighborhood is closer, skip this place
            } else {
              // Can't find neighborhood data, keep existing assignment
            }
          } else {
            // Existing has null neighborhood - assign to current
            placesToUpsert.push(place);
          }
        }

        // Upsert places
        if (placesToUpsert.length > 0) {
          const { error: upsertError } = await supabase
            .from("cached_places")
            .upsert(placesToUpsert, { onConflict: "id" });

          if (upsertError) {
            throw new Error(`Failed to upsert places: ${upsertError.message}`);
          }
        }

        // Update sync log
        await supabase
          .from("sync_log")
          .update({
            completed_at: new Date().toISOString(),
            records_synced: placesToUpsert.length,
            status: "success",
            metadata: {
              neighborhood_id: neighborhood.id,
              fetched: allPlaces.length,
              upserted: placesToUpsert.length,
              duplicates_resolved: duplicatesResolved,
            },
          })
          .eq("id", syncLog?.id);

        results.push({
          neighborhood_id: neighborhood.id,
          places_synced: placesToUpsert.length,
          duplicates_resolved: duplicatesResolved,
          status: "success",
        });
      } catch (error) {
        await supabase
          .from("sync_log")
          .update({
            completed_at: new Date().toISOString(),
            status: "failed",
            error_message: error instanceof Error ? error.message : "Unknown error",
          })
          .eq("id", syncLog?.id);

        results.push({
          neighborhood_id: neighborhood.id,
          places_synced: 0,
          duplicates_resolved: 0,
          status: `failed: ${error instanceof Error ? error.message : "Unknown error"}`,
        });
      }
    }

    return new Response(
      JSON.stringify({
        success: true,
        neighborhoods_processed: results.length,
        results,
      }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );
  } catch (error) {
    return new Response(
      JSON.stringify({
        success: false,
        error: error instanceof Error ? error.message : "Unknown error",
      }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }
});
