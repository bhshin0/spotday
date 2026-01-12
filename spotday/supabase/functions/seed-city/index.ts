// seed-city Edge Function
// Uses OpenAI API to generate neighborhood data for a new city
// Triggered manually by admin when expanding to a new city

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const OPENAI_API_KEY = Deno.env.get("OPENAI_API_KEY")!;
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

interface CityData {
  id: string;
  name: string;
  country: string;
  state_code: string;
  size: "large" | "medium" | "small";
  density: "very_dense" | "moderate" | "spread_out";
  center_lat: number;
  center_lng: number;
  estimated_areas: number;
}

interface NeighborhoodData {
  id: string;
  name: string;
  tier: "ESSENTIAL" | "CLASSIC" | "LOCAL";
  center_lat: number;
  center_lng: number;
  radius_meters: number;
  vibes: string[];
  description: string;
  adjacent_neighborhoods: string[];
}

interface QuickStopData {
  id: string;
  name: string;
  lat: number;
  lng: number;
  stop_type: "VIEWPOINT" | "PHOTO_SPOT" | "STREET_ART";
  description: string;
  duration_minutes: number;
  neighborhood_id?: string;
}

interface LLMResponse {
  city: CityData;
  neighborhoods: NeighborhoodData[];
}

interface QuickStopsLLMResponse {
  quick_stops: QuickStopData[];
}

// Build the prompt for OpenAI
function buildPrompt(cityName: string, stateCode: string, country: string): string {
  return `Generate neighborhood data for ${cityName}, ${stateCode}, ${country} for a day-trip planning app.

CITY SIZING GUIDELINES:
Consider both city size and urban density to determine neighborhood count.
Focus on WALKABLE areas with high concentration of restaurants, bars, and activities.
Spread-out cities have fewer walkable clusters; dense cities have more.

Target neighborhood counts by city profile:
- Large + very_dense (NYC, Chicago, Boston): 15-20 neighborhoods
- Large + moderate (Seattle, Miami): 10-14 neighborhoods
- Large + spread_out (Austin, Phoenix, Houston): 5-8 neighborhoods
- Medium + very_dense (SF, DC): 10-14 neighborhoods
- Medium + moderate (Portland, Denver, Nashville): 8-12 neighborhoods
- Medium + spread_out (San Diego): 5-8 neighborhoods
- Small + dense (Charleston, Savannah): 4-6 neighborhoods

TIER DISTRIBUTION:
- ESSENTIAL (30%): Must-see, highest density, first-timer priorities
- CLASSIC (40%): Worth visiting, iconic but less dense
- LOCAL (30%): Skip unless exploring, local favorites

Return ONLY valid JSON with this exact structure:
{
  "city": {
    "id": "lowercase_underscore_format",
    "name": "${cityName}",
    "country": "${country}",
    "state_code": "${stateCode}",
    "size": "large" | "medium" | "small",
    "density": "very_dense" | "moderate" | "spread_out",
    "center_lat": <number>,
    "center_lng": <number>,
    "estimated_areas": <number>
  },
  "neighborhoods": [
    {
      "id": "lowercase_underscore_format",
      "name": "Display Name",
      "tier": "ESSENTIAL" | "CLASSIC" | "LOCAL",
      "center_lat": <number>,
      "center_lng": <number>,
      "radius_meters": <400-1000>,
      "vibes": ["vibe1", "vibe2", "vibe3"],
      "description": "One sentence describing what makes this neighborhood special for visitors",
      "adjacent_neighborhoods": ["neighbor1_id", "neighbor2_id"]
    }
  ]
}

REQUIREMENTS:
- Use accurate center coordinates (verify lat/lng are correct for that neighborhood)
- Walkable radius in meters (typically 400-1000m based on neighborhood density)
- 3-5 vibes from: [foodie, nightlife, artsy, historic, trendy, waterfront, brunch, shopping, live_music, lgbtq, hipster, upscale, latin, italian, chinese, parks, museums, breweries, family, local, emerging, views, counterculture, tech, college, sports, beach]
- 1-sentence description highlighting what makes it special for visitors
- Adjacent neighborhoods must be real geographic adjacencies
- IDs must be lowercase with underscores (e.g., "pearl_district", "south_congress")

Return ONLY the JSON, no additional text.`;
}

// Build prompt for quick stops (viewpoints, photo spots, street art)
function buildQuickStopsPrompt(
  cityName: string,
  stateCode: string,
  neighborhoodIds: string[]
): string {
  return `Generate quick stops for ${cityName}, ${stateCode} for a day-trip planning app.

Quick stops are 15-25 minute stops for scenic views, photo opportunities, and street art.
Do NOT include restaurants, cafes, or coffee shops - those come from a different data source.

Generate 15-25 stops across these categories:
- VIEWPOINT (5-8): Scenic overlooks, hilltops, observation decks, panoramic views
- PHOTO_SPOT (5-8): Iconic landmarks, interesting architecture, Instagram-worthy spots, bridges
- STREET_ART (3-6): Murals, graffiti alleys, public art installations, sculpture gardens

Available neighborhood IDs for reference: ${JSON.stringify(neighborhoodIds)}

Return ONLY valid JSON with this exact structure:
{
  "quick_stops": [
    {
      "id": "lowercase_underscore_format",
      "name": "Display Name",
      "lat": <number>,
      "lng": <number>,
      "stop_type": "VIEWPOINT" | "PHOTO_SPOT" | "STREET_ART",
      "description": "One sentence about what makes this spot special",
      "duration_minutes": <15-25>,
      "neighborhood_id": "matching_neighborhood_id_or_null"
    }
  ]
}

REQUIREMENTS:
- Use accurate coordinates (verify lat/lng are correct for that location)
- IDs must be lowercase with underscores (e.g., "twin_peaks_overlook", "graffiti_alley")
- Duration should reflect typical visit time: viewpoints 15-20min, photo spots 10-15min, murals 15-20min
- neighborhood_id should match one of the provided IDs if the stop is within that area, otherwise omit it
- Focus on FREE or low-cost spots that visitors can walk to
- Include both famous tourist spots AND local hidden gems

Return ONLY the JSON, no additional text.`;
}

// Call OpenAI API for quick stops
async function callOpenAIForQuickStops(prompt: string): Promise<QuickStopsLLMResponse> {
  const response = await fetch(OPENAI_API_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${OPENAI_API_KEY}`,
    },
    body: JSON.stringify({
      model: "gpt-4o",
      max_tokens: 4096,
      response_format: { type: "json_object" },
      messages: [
        {
          role: "system",
          content: "You are a helpful assistant that generates structured JSON data about city attractions. Always respond with valid JSON only.",
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
    throw new Error(`OpenAI API error for quick stops: ${response.status} - ${errorText}`);
  }

  const data = await response.json();
  const content = data.choices?.[0]?.message?.content;

  if (!content) {
    throw new Error("No content in OpenAI response for quick stops");
  }

  let jsonStr = content;
  const jsonMatch = content.match(/```(?:json)?\s*([\s\S]*?)\s*```/);
  if (jsonMatch) {
    jsonStr = jsonMatch[1];
  }

  try {
    return JSON.parse(jsonStr);
  } catch {
    throw new Error(`Failed to parse quick stops response as JSON: ${content.substring(0, 500)}`);
  }
}

// Validate quick stops response
function validateQuickStops(data: QuickStopsLLMResponse): string[] {
  const errors: string[] = [];
  const validTypes = ["VIEWPOINT", "PHOTO_SPOT", "STREET_ART"];

  if (!data.quick_stops || data.quick_stops.length === 0) {
    errors.push("No quick stops provided");
  } else {
    for (const stop of data.quick_stops) {
      if (!stop.id) errors.push("Quick stop missing id");
      if (!stop.name) errors.push(`Quick stop ${stop.id} missing name`);
      if (!stop.lat || !stop.lng) errors.push(`Quick stop ${stop.id} missing coordinates`);
      if (!validTypes.includes(stop.stop_type)) {
        errors.push(`Quick stop ${stop.id} has invalid type: ${stop.stop_type}`);
      }
    }
  }

  return errors;
}

// Call OpenAI API
async function callOpenAI(prompt: string): Promise<LLMResponse> {
  const response = await fetch(OPENAI_API_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${OPENAI_API_KEY}`,
    },
    body: JSON.stringify({
      model: "gpt-4o",
      max_tokens: 4096,
      response_format: { type: "json_object" },
      messages: [
        {
          role: "system",
          content: "You are a helpful assistant that generates structured JSON data about city neighborhoods. Always respond with valid JSON only.",
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

  // Parse JSON from response (OpenAI with json_object mode should return clean JSON)
  let jsonStr = content;
  const jsonMatch = content.match(/```(?:json)?\s*([\s\S]*?)\s*```/);
  if (jsonMatch) {
    jsonStr = jsonMatch[1];
  }

  try {
    return JSON.parse(jsonStr);
  } catch {
    throw new Error(`Failed to parse OpenAI response as JSON: ${content.substring(0, 500)}`);
  }
}

// Validate the LLM response
function validateResponse(data: LLMResponse): string[] {
  const errors: string[] = [];

  // Validate city
  if (!data.city?.id) errors.push("Missing city.id");
  if (!data.city?.name) errors.push("Missing city.name");
  if (!data.city?.center_lat || !data.city?.center_lng) errors.push("Missing city coordinates");
  if (!["large", "medium", "small"].includes(data.city?.size)) errors.push("Invalid city.size");
  if (!["very_dense", "moderate", "spread_out"].includes(data.city?.density)) errors.push("Invalid city.density");

  // Validate neighborhoods
  if (!data.neighborhoods || data.neighborhoods.length === 0) {
    errors.push("No neighborhoods provided");
  } else {
    for (const n of data.neighborhoods) {
      if (!n.id) errors.push(`Neighborhood missing id`);
      if (!n.name) errors.push(`Neighborhood ${n.id} missing name`);
      if (!["ESSENTIAL", "CLASSIC", "LOCAL"].includes(n.tier)) {
        errors.push(`Neighborhood ${n.id} has invalid tier: ${n.tier}`);
      }
      if (!n.center_lat || !n.center_lng) {
        errors.push(`Neighborhood ${n.id} missing coordinates`);
      }
      if (!n.vibes || n.vibes.length === 0) {
        errors.push(`Neighborhood ${n.id} missing vibes`);
      }
    }
  }

  return errors;
}

serve(async (req) => {
  // Only allow POST
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: { "Content-Type": "application/json" },
    });
  }

  try {
    // Parse request body
    const body = await req.json();
    const { city_name, state_code, country = "USA" } = body;

    if (!city_name || !state_code) {
      return new Response(
        JSON.stringify({ error: "city_name and state_code are required" }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      );
    }

    // Initialize Supabase client
    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

    // Start sync log
    const { data: syncLog } = await supabase
      .from("sync_log")
      .insert({
        sync_type: "city_seed",
        status: "running",
        metadata: { city_name, state_code, country },
      })
      .select()
      .single();

    try {
      // Build prompt and call OpenAI
      const prompt = buildPrompt(city_name, state_code, country);
      const llmResponse = await callOpenAI(prompt);

      // Validate response
      const validationErrors = validateResponse(llmResponse);
      if (validationErrors.length > 0) {
        throw new Error(`Validation errors: ${validationErrors.join(", ")}`);
      }

      // Insert city
      const cityData = {
        ...llmResponse.city,
        data_source: "LLM",
        is_active: true,
      };

      const { error: cityError } = await supabase
        .from("cities")
        .upsert(cityData, { onConflict: "id" });

      if (cityError) {
        throw new Error(`Failed to insert city: ${cityError.message}`);
      }

      // Insert neighborhoods
      const neighborhoodData = llmResponse.neighborhoods.map((n) => ({
        ...n,
        city_id: llmResponse.city.id,
        data_source: "LLM",
      }));

      const { error: neighborhoodError } = await supabase
        .from("neighborhoods")
        .upsert(neighborhoodData, { onConflict: "id" });

      if (neighborhoodError) {
        throw new Error(`Failed to insert neighborhoods: ${neighborhoodError.message}`);
      }

      // Chain sync-places to populate Google Places data for the new city
      let placesResult = { success: false, places_synced: 0, error: null as string | null };
      try {
        console.log(`Triggering sync-places for ${llmResponse.city.id}...`);
        const syncPlacesResponse = await fetch(
          `${SUPABASE_URL}/functions/v1/sync-places`,
          {
            method: "POST",
            headers: {
              "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
              "Content-Type": "application/json",
            },
            body: JSON.stringify({ city_id: llmResponse.city.id }),
          }
        );
        
        if (syncPlacesResponse.ok) {
          const syncData = await syncPlacesResponse.json();
          placesResult = {
            success: true,
            places_synced: syncData.results?.reduce(
              (sum: number, r: { places_synced?: number }) => sum + (r.places_synced || 0),
              0
            ) || 0,
            error: null,
          };
          console.log(`sync-places completed: ${placesResult.places_synced} places synced`);
        } else {
          const errorText = await syncPlacesResponse.text();
          placesResult.error = `sync-places failed: ${errorText}`;
          console.error(placesResult.error);
        }
      } catch (e) {
        placesResult.error = `sync-places error: ${e instanceof Error ? e.message : "Unknown"}`;
        console.error(placesResult.error);
      }

      // Generate and insert quick stops (viewpoints, photo spots, street art)
      let quickStopsResult = { success: false, quick_stops_synced: 0, error: null as string | null };
      try {
        console.log(`Generating quick stops for ${llmResponse.city.id}...`);
        const neighborhoodIds = llmResponse.neighborhoods.map((n) => n.id);
        const quickStopsPrompt = buildQuickStopsPrompt(city_name, state_code, neighborhoodIds);
        const quickStopsResponse = await callOpenAIForQuickStops(quickStopsPrompt);

        // Validate quick stops
        const quickStopsErrors = validateQuickStops(quickStopsResponse);
        if (quickStopsErrors.length > 0) {
          throw new Error(`Quick stops validation errors: ${quickStopsErrors.join(", ")}`);
        }

        // Insert quick stops
        const quickStopsData = quickStopsResponse.quick_stops.map((stop) => ({
          id: `${llmResponse.city.id}_${stop.id}`,
          city_id: llmResponse.city.id,
          name: stop.name,
          lat: stop.lat,
          lng: stop.lng,
          stop_type: stop.stop_type,
          description: stop.description,
          duration_minutes: stop.duration_minutes || 20,
          neighborhood_id: stop.neighborhood_id || null,
          data_source: "LLM",
        }));

        const { error: quickStopsError } = await supabase
          .from("cached_quick_stops")
          .upsert(quickStopsData, { onConflict: "id" });

        if (quickStopsError) {
          throw new Error(`Failed to insert quick stops: ${quickStopsError.message}`);
        }

        quickStopsResult = {
          success: true,
          quick_stops_synced: quickStopsData.length,
          error: null,
        };
        console.log(`Quick stops completed: ${quickStopsResult.quick_stops_synced} stops synced`);
      } catch (e) {
        quickStopsResult.error = `quick-stops error: ${e instanceof Error ? e.message : "Unknown"}`;
        console.error(quickStopsResult.error);
      }

      // Chain sync-events to populate Ticketmaster events for the new city
      let eventsResult = { success: false, events_synced: 0, error: null as string | null };
      try {
        console.log(`Triggering sync-events for ${llmResponse.city.id}...`);
        const syncEventsResponse = await fetch(
          `${SUPABASE_URL}/functions/v1/sync-events`,
          {
            method: "POST",
            headers: {
              "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
              "Content-Type": "application/json",
            },
            body: JSON.stringify({ city_id: llmResponse.city.id }),
          }
        );
        
        if (syncEventsResponse.ok) {
          const syncData = await syncEventsResponse.json();
          eventsResult = {
            success: true,
            events_synced: syncData.results?.[0]?.events_synced || 0,
            error: null,
          };
          console.log(`sync-events completed: ${eventsResult.events_synced} events synced`);
        } else {
          const errorText = await syncEventsResponse.text();
          eventsResult.error = `sync-events failed: ${errorText}`;
          console.error(eventsResult.error);
        }
      } catch (e) {
        eventsResult.error = `sync-events error: ${e instanceof Error ? e.message : "Unknown"}`;
        console.error(eventsResult.error);
      }

      // Chain seed-nightlife to populate LLM-curated nightlife venues
      let nightlifeResult = { success: false, nightlife_matched: 0, nightlife_unmatched: 0, error: null as string | null };
      try {
        console.log(`Triggering seed-nightlife for ${llmResponse.city.id}...`);
        const seedNightlifeResponse = await fetch(
          `${SUPABASE_URL}/functions/v1/seed-nightlife`,
          {
            method: "POST",
            headers: {
              "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
              "Content-Type": "application/json",
            },
            body: JSON.stringify({ city_id: llmResponse.city.id }),
          }
        );
        
        if (seedNightlifeResponse.ok) {
          const syncData = await seedNightlifeResponse.json();
          nightlifeResult = {
            success: true,
            nightlife_matched: syncData.matched || 0,
            nightlife_unmatched: syncData.unmatched || 0,
            error: null,
          };
          console.log(`seed-nightlife completed: ${nightlifeResult.nightlife_matched} venues matched, ${nightlifeResult.nightlife_unmatched} unmatched`);
        } else {
          const errorText = await seedNightlifeResponse.text();
          nightlifeResult.error = `seed-nightlife failed: ${errorText}`;
          console.error(nightlifeResult.error);
        }
      } catch (e) {
        nightlifeResult.error = `seed-nightlife error: ${e instanceof Error ? e.message : "Unknown"}`;
        console.error(nightlifeResult.error);
      }

      // Update sync log
      await supabase
        .from("sync_log")
        .update({
          city_id: llmResponse.city.id,
          completed_at: new Date().toISOString(),
          records_synced: llmResponse.neighborhoods.length,
          status: "success",
          metadata: {
            city_name,
            state_code,
            country,
            places_synced: placesResult.places_synced,
            places_sync_error: placesResult.error,
            quick_stops_synced: quickStopsResult.quick_stops_synced,
            quick_stops_error: quickStopsResult.error,
            events_synced: eventsResult.events_synced,
            events_sync_error: eventsResult.error,
            nightlife_matched: nightlifeResult.nightlife_matched,
            nightlife_unmatched: nightlifeResult.nightlife_unmatched,
            nightlife_error: nightlifeResult.error,
          },
        })
        .eq("id", syncLog?.id);

      return new Response(
        JSON.stringify({
          success: true,
          city: llmResponse.city,
          neighborhoods_count: llmResponse.neighborhoods.length,
          neighborhoods: llmResponse.neighborhoods.map((n) => ({
            id: n.id,
            name: n.name,
            tier: n.tier,
          })),
          places_synced: placesResult.places_synced,
          places_sync_error: placesResult.error,
          quick_stops_synced: quickStopsResult.quick_stops_synced,
          quick_stops_error: quickStopsResult.error,
          events_synced: eventsResult.events_synced,
          events_sync_error: eventsResult.error,
          nightlife_matched: nightlifeResult.nightlife_matched,
          nightlife_unmatched: nightlifeResult.nightlife_unmatched,
          nightlife_error: nightlifeResult.error,
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
    return new Response(
      JSON.stringify({
        success: false,
        error: error instanceof Error ? error.message : "Unknown error",
      }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }
});
