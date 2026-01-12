// seed-nightlife Edge Function
// Uses LLM to generate curated nightlife venues by category,
// then enriches each venue with Google Places data (ratings, reviews, coordinates).
//
// Flow:
// 1. LLM generates venue names + categories
// 2. Google Find Place API looks up each venue
// 3. Validate location is within city bounds
// 4. Store enriched data in cached_places with nightlife_category
// 5. Log unmatched venues for manual review

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const OPENAI_API_KEY = Deno.env.get("OPENAI_API_KEY")!;
const GOOGLE_PLACES_API_KEY = Deno.env.get("GOOGLE_PLACES_API_KEY")!;
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
const FIND_PLACE_API_URL = "https://maps.googleapis.com/maps/api/place/findplacefromtext/json";

// Maximum distance from city center (in km) for valid venues
const MAX_DISTANCE_FROM_CITY_KM = 50;

interface LLMVenue {
  name: string;
  category: string;
  neighborhood_hint: string;
  why_notable: string;
}

interface LLMResponse {
  venues: LLMVenue[];
}

interface GooglePlaceCandidate {
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
  business_status?: string;
  opening_hours?: {
    open_now?: boolean;
  };
}

interface GoogleFindPlaceResponse {
  candidates: GooglePlaceCandidate[];
  status: string;
}

interface CityData {
  id: string;
  name: string;
  state_code: string;
  center_lat: number;
  center_lng: number;
}

interface Neighborhood {
  id: string;
  center_lat: number;
  center_lng: number;
}

// Calculate distance between two points in km using Haversine formula
function calculateDistance(
  lat1: number,
  lng1: number,
  lat2: number,
  lng2: number
): number {
  const R = 6371; // Earth's radius in km
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLng = ((lng2 - lng1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLng / 2) *
      Math.sin(dLng / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

// Find nearest neighborhood by coordinates
function findNearestNeighborhood(
  lat: number,
  lng: number,
  neighborhoods: Neighborhood[]
): string | null {
  if (neighborhoods.length === 0) return null;

  let nearest = neighborhoods[0];
  let minDist = Infinity;

  for (const n of neighborhoods) {
    const dist =
      (n.center_lat - lat) ** 2 + (n.center_lng - lng) ** 2;
    if (dist < minDist) {
      minDist = dist;
      nearest = n;
    }
  }

  return nearest.id;
}

// Build the LLM prompt for nightlife venues
function buildPrompt(cityName: string, stateCode: string): string {
  return `Generate the BEST nightlife venues in ${cityName}, ${stateCode} for a day-trip planning app.

QUALITY OVER QUANTITY:
- Only include well-known, highly-rated, or locally beloved spots
- Avoid generic chain bars (no Applebee's, Buffalo Wild Wings, Hooters, etc.)
- Prioritize venues that locals recommend and visitors seek out
- Some categories may have many options, others may have few - that's fine
- Skip categories entirely if no quality options exist in this city

CATEGORIES (include as many quality venues as exist):
- COCKTAIL_BAR: Craft cocktail lounges, speakeasy-style bars, mixology-focused
- DIVE_BAR: Classic dive bars, no-frills neighborhood bars with character
- ROOFTOP_BAR: Rooftop venues with views, outdoor elevated bars
- WINE_BAR: Wine-focused bars, natural wine spots, tasting rooms
- SPORTS_BAR: Sports viewing bars with good atmosphere (not chains)
- LIVE_MUSIC: Venues with regular live music (jazz clubs, rock venues, not concert halls)
- BREWERY: Craft breweries with taprooms, brewpubs
- KARAOKE: Karaoke bars, private room karaoke

TARGET: 40-80 total venues across all categories.
Prioritize neighborhoods with active nightlife scenes.

Return ONLY valid JSON with this exact structure:
{
  "venues": [
    {
      "name": "Exact venue name as it appears on Google Maps",
      "category": "COCKTAIL_BAR",
      "neighborhood_hint": "Mission District",
      "why_notable": "One sentence on what makes it special"
    }
  ]
}

REQUIREMENTS:
- Use EXACT venue names as they appear on Google Maps (critical for lookup)
- Category must be one of: COCKTAIL_BAR, DIVE_BAR, ROOFTOP_BAR, WINE_BAR, SPORTS_BAR, LIVE_MUSIC, BREWERY, KARAOKE
- Focus on quality over quantity
- Include a mix of famous spots and local favorites

Return ONLY the JSON, no additional text.`;
}

// Call OpenAI API
async function callOpenAI(prompt: string): Promise<LLMResponse> {
  const response = await fetch(OPENAI_API_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${OPENAI_API_KEY}`,
    },
    body: JSON.stringify({
      model: "gpt-4o",
      max_tokens: 8192,
      response_format: { type: "json_object" },
      messages: [
        {
          role: "system",
          content:
            "You are a helpful assistant that generates structured JSON data about nightlife venues. Always respond with valid JSON only.",
        },
        {
          role: "user",
          content: prompt,
        },
      ],
    }),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`OpenAI API error: ${response.status} - ${errorText}`);
  }

  const data = await response.json();
  const content = data.choices?.[0]?.message?.content;

  if (!content) {
    throw new Error("No content in OpenAI response");
  }

  // Parse JSON from response
  let jsonStr = content;
  const jsonMatch = content.match(/```(?:json)?\s*([\s\S]*?)\s*```/);
  if (jsonMatch) {
    jsonStr = jsonMatch[1];
  }

  try {
    return JSON.parse(jsonStr);
  } catch {
    throw new Error(
      `Failed to parse OpenAI response as JSON: ${content.substring(0, 500)}`
    );
  }
}

// Call Google Find Place API
async function findPlace(
  venueName: string,
  cityName: string
): Promise<GooglePlaceCandidate | null> {
  const input = `${venueName} ${cityName}`;
  const fields =
    "place_id,name,geometry,rating,user_ratings_total,price_level,business_status,opening_hours";

  const params = new URLSearchParams({
    input,
    inputtype: "textquery",
    fields,
    key: GOOGLE_PLACES_API_KEY,
  });

  const response = await fetch(`${FIND_PLACE_API_URL}?${params}`);

  if (!response.ok) {
    console.error(`Google API error for ${venueName}: ${response.status}`);
    return null;
  }

  const data: GoogleFindPlaceResponse = await response.json();

  if (data.status !== "OK" || !data.candidates || data.candidates.length === 0) {
    return null;
  }

  // Return the first (most relevant) candidate
  return data.candidates[0];
}

// Validate LLM response
function validateResponse(data: LLMResponse): string[] {
  const errors: string[] = [];
  const validCategories = [
    "COCKTAIL_BAR",
    "DIVE_BAR",
    "ROOFTOP_BAR",
    "WINE_BAR",
    "SPORTS_BAR",
    "LIVE_MUSIC",
    "BREWERY",
    "KARAOKE",
  ];

  if (!data.venues || data.venues.length === 0) {
    errors.push("No venues provided");
  } else {
    for (const venue of data.venues) {
      if (!venue.name) errors.push("Venue missing name");
      if (!validCategories.includes(venue.category)) {
        errors.push(`Invalid category: ${venue.category}`);
      }
    }
  }

  return errors;
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
    const body = await req.json();
    const { city_id } = body;

    if (!city_id) {
      return new Response(
        JSON.stringify({ error: "city_id is required" }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      );
    }

    // Get city data
    const { data: city, error: cityError } = await supabase
      .from("cities")
      .select("id, name, state_code, center_lat, center_lng")
      .eq("id", city_id)
      .single();

    if (cityError || !city) {
      throw new Error(`City not found: ${city_id}`);
    }

    // Get neighborhoods for this city
    const { data: neighborhoods } = await supabase
      .from("neighborhoods")
      .select("id, center_lat, center_lng")
      .eq("city_id", city_id);

    // Start sync log
    const { data: syncLog } = await supabase
      .from("sync_log")
      .insert({
        sync_type: "places",
        city_id: city.id,
        status: "running",
        metadata: { type: "nightlife_seed" },
      })
      .select()
      .single();

    try {
      console.log(`Generating nightlife venues for ${city.name}, ${city.state_code}...`);

      // Step 1: Call LLM to generate venues
      const prompt = buildPrompt(city.name, city.state_code);
      const llmResponse = await callOpenAI(prompt);

      // Validate response
      const validationErrors = validateResponse(llmResponse);
      if (validationErrors.length > 0) {
        throw new Error(`LLM validation errors: ${validationErrors.join(", ")}`);
      }

      console.log(`LLM generated ${llmResponse.venues.length} venues`);

      // Step 2: Enrich each venue with Google data
      let matched = 0;
      let unmatched = 0;
      let closed = 0;
      let wrongLocation = 0;

      const placesToUpsert: Array<{
        id: string;
        city_id: string;
        neighborhood_id: string | null;
        name: string;
        place_type: string;
        nightlife_category: string;
        lat: number;
        lng: number;
        rating: number | null;
        review_count: number;
        price_level: number | null;
        is_outdoor: boolean;
        last_verified_at: string;
        is_permanently_closed: boolean;
      }> = [];

      for (const venue of llmResponse.venues) {
        // Rate limiting - 100ms between requests
        await new Promise((resolve) => setTimeout(resolve, 100));

        const googleResult = await findPlace(venue.name, city.name);

        if (!googleResult) {
          // Not found on Google
          await supabase.from("unmatched_venues").insert({
            llm_name: venue.name,
            city_id: city.id,
            category: venue.category,
            neighborhood_hint: venue.neighborhood_hint,
            why_notable: venue.why_notable,
            reason: "NOT_FOUND",
          });
          unmatched++;
          console.log(`NOT_FOUND: ${venue.name}`);
          continue;
        }

        // Validate location is within city bounds
        const distFromCity = calculateDistance(
          googleResult.geometry.location.lat,
          googleResult.geometry.location.lng,
          city.center_lat,
          city.center_lng
        );

        if (distFromCity > MAX_DISTANCE_FROM_CITY_KM) {
          await supabase.from("unmatched_venues").insert({
            llm_name: venue.name,
            city_id: city.id,
            category: venue.category,
            neighborhood_hint: venue.neighborhood_hint,
            why_notable: venue.why_notable,
            reason: "WRONG_LOCATION",
          });
          wrongLocation++;
          console.log(
            `WRONG_LOCATION: ${venue.name} is ${distFromCity.toFixed(1)}km from ${city.name}`
          );
          continue;
        }

        // Check if permanently closed
        if (googleResult.business_status === "CLOSED_PERMANENTLY") {
          // Check if exists in DB and mark as closed
          const { data: existing } = await supabase
            .from("cached_places")
            .select("id")
            .eq("id", googleResult.place_id)
            .single();

          if (existing) {
            await supabase
              .from("cached_places")
              .update({ is_permanently_closed: true })
              .eq("id", googleResult.place_id);
          }
          closed++;
          console.log(`CLOSED: ${venue.name}`);
          continue;
        }

        // Find nearest neighborhood
        const neighborhoodId = findNearestNeighborhood(
          googleResult.geometry.location.lat,
          googleResult.geometry.location.lng,
          neighborhoods || []
        );

        // Prepare place for upsert
        placesToUpsert.push({
          id: googleResult.place_id,
          city_id: city.id,
          neighborhood_id: neighborhoodId,
          name: googleResult.name,
          place_type: "NIGHTLIFE",
          nightlife_category: venue.category.toLowerCase(),
          lat: googleResult.geometry.location.lat,
          lng: googleResult.geometry.location.lng,
          rating: googleResult.rating || null,
          review_count: googleResult.user_ratings_total || 0,
          price_level: googleResult.price_level || null,
          is_outdoor: false,
          last_verified_at: new Date().toISOString(),
          is_permanently_closed: false,
        });

        matched++;
        console.log(
          `MATCHED: ${venue.name} → ${googleResult.name} (${venue.category}, rating: ${googleResult.rating || "N/A"})`
        );
      }

      // Step 3: Upsert all matched places
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
          records_synced: matched,
          status: "success",
          metadata: {
            type: "nightlife_seed",
            llm_generated: llmResponse.venues.length,
            matched,
            unmatched,
            closed,
            wrong_location: wrongLocation,
          },
        })
        .eq("id", syncLog?.id);

      // Return summary
      return new Response(
        JSON.stringify({
          success: true,
          city: city.name,
          llm_generated: llmResponse.venues.length,
          matched,
          unmatched,
          closed,
          wrong_location: wrongLocation,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    } catch (error) {
      // Update sync log with error
      await supabase
        .from("sync_log")
        .update({
          completed_at: new Date().toISOString(),
          status: "failed",
          error_message: error instanceof Error ? error.message : "Unknown error",
        })
        .eq("id", syncLog?.id);

      throw error;
    }
  } catch (error) {
    console.error("seed-nightlife error:", error);
    return new Response(
      JSON.stringify({
        success: false,
        error: error instanceof Error ? error.message : "Unknown error",
      }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }
});
