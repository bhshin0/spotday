// sync-places Edge Function
// Fetches places from Google Places API and caches them in Supabase
// Runs weekly or on-demand per neighborhood

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const GOOGLE_PLACES_API_KEY = Deno.env.get("GOOGLE_PLACES_API_KEY")!;
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const PLACES_API_BASE = "https://maps.googleapis.com/maps/api/place";

// Place types to fetch for each category
const PLACE_TYPE_MAPPING: Record<string, string[]> = {
  RESTAURANT: ["restaurant", "cafe", "bakery"],
  MUSEUM: ["museum", "art_gallery"],
  PARK: ["park"],
  BAR: ["bar", "night_club"],
  SHOPPING: ["shopping_mall", "clothing_store", "book_store"],
  ENTERTAINMENT: ["movie_theater", "bowling_alley", "amusement_park"],
  WATERFRONT: ["tourist_attraction"], // Filter by location
  HISTORIC_SITE: ["tourist_attraction", "church", "hindu_temple"],
};

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

// Determine place type from Google types
function determinePlaceType(types: string[]): string {
  if (types.includes("restaurant") || types.includes("food")) return "RESTAURANT";
  if (types.includes("cafe") || types.includes("bakery")) return "RESTAURANT";
  if (types.includes("bar") || types.includes("night_club")) return "NIGHTLIFE";
  if (types.includes("museum") || types.includes("art_gallery")) return "MUSEUM";
  if (types.includes("park")) return "PARK";
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
  neighborhoodId: string | null
): CachedPlace {
  return {
    id: place.place_id,
    city_id: cityId,
    neighborhood_id: neighborhoodId,
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

    const results: Array<{
      neighborhood_id: string;
      places_synced: number;
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

        // Fetch places for each type
        const typesToFetch = ["restaurant", "bar", "museum", "park", "cafe"];

        for (const placeType of typesToFetch) {
          const places = await fetchPlacesNearby(
            neighborhood.center_lat,
            neighborhood.center_lng,
            neighborhood.radius_meters || 800,
            placeType
          );

          for (const place of places) {
            // Skip if already added (dedup by place_id)
            if (!allPlaces.some((p) => p.id === place.place_id)) {
              allPlaces.push(
                convertPlace(place, neighborhood.city_id, neighborhood.id)
              );
            }
          }

          // Rate limiting - 1 second between requests
          await new Promise((resolve) => setTimeout(resolve, 1000));
        }

        // Upsert places
        if (allPlaces.length > 0) {
          const { error: upsertError } = await supabase
            .from("cached_places")
            .upsert(allPlaces, { onConflict: "id" });

          if (upsertError) {
            throw new Error(`Failed to upsert places: ${upsertError.message}`);
          }
        }

        // Update sync log
        await supabase
          .from("sync_log")
          .update({
            completed_at: new Date().toISOString(),
            records_synced: allPlaces.length,
            status: "success",
          })
          .eq("id", syncLog?.id);

        results.push({
          neighborhood_id: neighborhood.id,
          places_synced: allPlaces.length,
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
