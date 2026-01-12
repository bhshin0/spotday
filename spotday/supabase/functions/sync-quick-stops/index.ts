// sync-quick-stops Edge Function
// Generates quick stops (viewpoints, photo spots, street art) for a city using OpenAI
// For cities that need quick stops added after initial seeding

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const OPENAI_API_KEY = Deno.env.get("OPENAI_API_KEY")!;
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

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

interface QuickStopsLLMResponse {
  quick_stops: QuickStopData[];
}

// Build prompt for quick stops
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

// Call OpenAI API
async function callOpenAI(prompt: string): Promise<QuickStopsLLMResponse> {
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
    throw new Error(`OpenAI API error: ${response.status} - ${errorText}`);
  }

  const data = await response.json();
  const content = data.choices?.[0]?.message?.content;

  if (!content) {
    throw new Error("No content in OpenAI response");
  }

  let jsonStr = content;
  const jsonMatch = content.match(/```(?:json)?\s*([\s\S]*?)\s*```/);
  if (jsonMatch) {
    jsonStr = jsonMatch[1];
  }

  try {
    return JSON.parse(jsonStr);
  } catch {
    throw new Error(`Failed to parse response as JSON: ${content.substring(0, 500)}`);
  }
}

// Validate quick stops
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
    try {
      const body = await req.json();
      targetCityId = body.city_id || null;
    } catch {
      // No body, sync all cities that need quick stops
    }

    // Get cities to sync
    let citiesQuery = supabase.from("cities").select("*").eq("is_active", true);
    if (targetCityId) {
      citiesQuery = citiesQuery.eq("id", targetCityId);
    }

    const { data: cities, error: citiesError } = await citiesQuery;

    if (citiesError) {
      throw new Error(`Failed to fetch cities: ${citiesError.message}`);
    }

    const results: Array<{ city_id: string; quick_stops_synced: number; status: string }> = [];

    for (const city of cities || []) {
      // Check if city already has quick stops
      const { count: existingCount } = await supabase
        .from("cached_quick_stops")
        .select("*", { count: "exact", head: true })
        .eq("city_id", city.id);

      if ((existingCount || 0) > 0 && !targetCityId) {
        // Skip cities that already have quick stops unless explicitly requested
        results.push({
          city_id: city.id,
          quick_stops_synced: 0,
          status: `skipped: already has ${existingCount} quick stops`,
        });
        continue;
      }

      // Start sync log
      const { data: syncLog } = await supabase
        .from("sync_log")
        .insert({
          sync_type: "quick_stops",
          city_id: city.id,
          status: "running",
        })
        .select()
        .single();

      try {
        // Get neighborhoods for this city
        const { data: neighborhoods } = await supabase
          .from("neighborhoods")
          .select("id")
          .eq("city_id", city.id);

        const neighborhoodIds = (neighborhoods || []).map((n) => n.id);

        // Generate quick stops
        const prompt = buildQuickStopsPrompt(city.name, city.state_code || "", neighborhoodIds);
        const llmResponse = await callOpenAI(prompt);

        // Validate
        const errors = validateQuickStops(llmResponse);
        if (errors.length > 0) {
          throw new Error(`Validation errors: ${errors.join(", ")}`);
        }

        // Delete existing quick stops for this city if regenerating
        if (targetCityId) {
          await supabase
            .from("cached_quick_stops")
            .delete()
            .eq("city_id", city.id);
        }

        // Insert quick stops
        const quickStopsData = llmResponse.quick_stops.map((stop) => ({
          id: `${city.id}_${stop.id}`,
          city_id: city.id,
          name: stop.name,
          lat: stop.lat,
          lng: stop.lng,
          stop_type: stop.stop_type,
          description: stop.description,
          duration_minutes: stop.duration_minutes || 20,
          neighborhood_id: stop.neighborhood_id || null,
          data_source: "LLM",
        }));

        const { error: upsertError } = await supabase
          .from("cached_quick_stops")
          .upsert(quickStopsData, { onConflict: "id" });

        if (upsertError) {
          throw new Error(`Failed to insert quick stops: ${upsertError.message}`);
        }

        // Update sync log
        await supabase
          .from("sync_log")
          .update({
            completed_at: new Date().toISOString(),
            records_synced: quickStopsData.length,
            status: "success",
          })
          .eq("id", syncLog?.id);

        results.push({
          city_id: city.id,
          quick_stops_synced: quickStopsData.length,
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
          city_id: city.id,
          quick_stops_synced: 0,
          status: `failed: ${error instanceof Error ? error.message : "Unknown error"}`,
        });
      }
    }

    return new Response(JSON.stringify({ success: true, results }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
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
