// seed-city Edge Function
// Uses Claude API to generate neighborhood data for a new city
// Triggered manually by admin when expanding to a new city

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const ANTHROPIC_API_KEY = Deno.env.get("ANTHROPIC_API_KEY")!;
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";

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

interface LLMResponse {
  city: CityData;
  neighborhoods: NeighborhoodData[];
}

// Build the prompt for Claude
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

// Call Claude API
async function callClaude(prompt: string): Promise<LLMResponse> {
  const response = await fetch(ANTHROPIC_API_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-api-key": ANTHROPIC_API_KEY,
      "anthropic-version": "2023-06-01",
    },
    body: JSON.stringify({
      model: "claude-sonnet-4-20250514",
      max_tokens: 4096,
      messages: [
        {
          role: "user",
          content: prompt,
        },
      ],
    }),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Claude API error: ${response.status} - ${errorText}`);
  }

  const data = await response.json();
  const content = data.content?.[0]?.text;

  if (!content) {
    throw new Error("No content in Claude response");
  }

  // Parse JSON from response (Claude might include markdown code blocks)
  let jsonStr = content;
  const jsonMatch = content.match(/```(?:json)?\s*([\s\S]*?)\s*```/);
  if (jsonMatch) {
    jsonStr = jsonMatch[1];
  }

  try {
    return JSON.parse(jsonStr);
  } catch {
    throw new Error(`Failed to parse Claude response as JSON: ${content.substring(0, 500)}`);
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
      // Build prompt and call Claude
      const prompt = buildPrompt(city_name, state_code, country);
      const llmResponse = await callClaude(prompt);

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

      // Update sync log
      await supabase
        .from("sync_log")
        .update({
          city_id: llmResponse.city.id,
          completed_at: new Date().toISOString(),
          records_synced: llmResponse.neighborhoods.length,
          status: "success",
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
