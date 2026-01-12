// sync-events Edge Function
// Fetches events from Ticketmaster API and caches them in Supabase
// Runs daily at 6 AM PT via cron

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const TICKETMASTER_API_KEY = Deno.env.get("TICKETMASTER_API_KEY")!;
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const TICKETMASTER_BASE_URL = "https://app.ticketmaster.com/discovery/v2";

interface TicketmasterEvent {
  id: string;
  name: string;
  info?: string;
  url?: string;
  dates?: {
    start?: {
      localDate?: string;
      localTime?: string;
      dateTime?: string;
    };
    status?: {
      code?: string;
    };
  };
  classifications?: Array<{
    primary?: boolean;
    segment?: { name?: string };
    genre?: { name?: string };
  }>;
  _embedded?: {
    venues?: Array<{
      name?: string;
      location?: {
        latitude?: string;
        longitude?: string;
      };
    }>;
  };
  priceRanges?: Array<{
    type?: string;
    min?: number;
    max?: number;
  }>;
}

interface CachedEvent {
  id: string;
  city_id: string;
  name: string;
  description: string | null;
  event_type: string;
  venue_name: string;
  venue_lat: number;
  venue_lng: number;
  start_date: string;
  start_hour: number;
  start_minute: number;
  duration_minutes: number;
  price_min: number | null;
  price_max: number | null;
  is_sold_out: boolean;
  ticket_url: string | null;
  popularity: number;
  source: string;
  raw_json: unknown;
}

// Map Ticketmaster classification to our event types
function determineEventType(classifications?: TicketmasterEvent["classifications"]): string {
  const primary = classifications?.find((c) => c.primary) || classifications?.[0];
  const segmentName = primary?.segment?.name?.toLowerCase() || "";
  const genreName = primary?.genre?.name?.toLowerCase() || "";

  if (segmentName.includes("music")) return "CONCERT";
  if (segmentName.includes("sport")) return "SPORTS";
  if (segmentName.includes("arts") || segmentName.includes("theatre")) {
    if (genreName.includes("comedy")) return "COMEDY";
    return "THEATER";
  }
  if (genreName.includes("comedy")) return "COMEDY";
  return "CONCERT"; // Default
}

// Estimate duration based on event type
function estimateDuration(eventType: string): number {
  switch (eventType) {
    case "CONCERT": return 150;
    case "SPORTS": return 180;
    case "THEATER": return 165;
    case "COMEDY": return 90;
    default: return 120;
  }
}

// Convert Ticketmaster event to our cached format
function convertEvent(tmEvent: TicketmasterEvent, cityId: string, index: number): CachedEvent | null {
  const startTime = tmEvent.dates?.start;
  if (!startTime?.localTime && !startTime?.localDate) return null;

  // Parse time (format: "19:30:00")
  const timeParts = startTime.localTime?.split(":") || ["12", "00"];
  const startHour = parseInt(timeParts[0]) || 12;
  const startMinute = parseInt(timeParts[1]) || 0;

  // Get venue info
  const venue = tmEvent._embedded?.venues?.[0];
  const venueName = venue?.name || "TBD";
  const latitude = parseFloat(venue?.location?.latitude || "0");
  const longitude = parseFloat(venue?.location?.longitude || "0");

  if (latitude === 0 || longitude === 0) return null;

  // Get price info
  const priceRange = tmEvent.priceRanges?.find((p) => p.type === "standard") || tmEvent.priceRanges?.[0];

  // Determine event type
  const eventType = determineEventType(tmEvent.classifications);

  // Check if sold out
  const isSoldOut = tmEvent.dates?.status?.code === "offsale" || tmEvent.dates?.status?.code === "cancelled";

  // Calculate popularity based on position (API returns by relevance)
  // First 10 = 5, next 10 = 4, next 10 = 3, rest = 3
  const popularity = index < 10 ? 5 : index < 20 ? 4 : 3;

  return {
    id: `tm_${tmEvent.id}`,
    city_id: cityId,
    name: tmEvent.name,
    description: tmEvent.info || `Live ${eventType.toLowerCase()} at ${venueName}`,
    event_type: eventType,
    venue_name: venueName,
    venue_lat: latitude,
    venue_lng: longitude,
    start_date: startTime.localDate || new Date().toISOString().split("T")[0],
    start_hour: startHour,
    start_minute: startMinute,
    duration_minutes: estimateDuration(eventType),
    price_min: priceRange?.min || null,
    price_max: priceRange?.max || null,
    is_sold_out: isSoldOut,
    ticket_url: tmEvent.url || null,
    popularity,
    source: "TICKETMASTER",
    raw_json: tmEvent,
  };
}

// Fetch events from Ticketmaster for a city
async function fetchTicketmasterEvents(
  city: string,
  stateCode: string,
  daysAhead: number = 7
): Promise<TicketmasterEvent[]> {
  const now = new Date();
  const startDateTime = now.toISOString().replace(/\.\d{3}Z$/, "Z");
  
  const endDate = new Date(now);
  endDate.setDate(endDate.getDate() + daysAhead);
  const endDateTime = endDate.toISOString().replace(/\.\d{3}Z$/, "Z");

  const params = new URLSearchParams({
    apikey: TICKETMASTER_API_KEY,
    city: city,
    stateCode: stateCode,
    startDateTime: startDateTime,
    endDateTime: endDateTime,
    sort: "relevance,desc",
    size: "50",
  });

  const response = await fetch(`${TICKETMASTER_BASE_URL}/events.json?${params}`);
  
  if (!response.ok) {
    throw new Error(`Ticketmaster API error: ${response.status} ${response.statusText}`);
  }

  const data = await response.json();
  return data._embedded?.events || [];
}

serve(async (req) => {
  try {
    // Initialize Supabase client with service role
    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

    // Parse request body for optional city filter
    let targetCityId: string | null = null;
    try {
      const body = await req.json();
      targetCityId = body.city_id || null;
    } catch {
      // No body or invalid JSON, sync all cities
    }

    // Get active cities to sync
    let citiesQuery = supabase.from("cities").select("*").eq("is_active", true);
    if (targetCityId) {
      citiesQuery = citiesQuery.eq("id", targetCityId);
    }
    
    const { data: cities, error: citiesError } = await citiesQuery;
    
    if (citiesError) {
      throw new Error(`Failed to fetch cities: ${citiesError.message}`);
    }

    const results: Array<{ city_id: string; events_synced: number; status: string }> = [];

    for (const city of cities || []) {
      // Start sync log
      const { data: syncLog } = await supabase
        .from("sync_log")
        .insert({
          sync_type: "events",
          city_id: city.id,
          status: "running",
        })
        .select()
        .single();

      try {
        // Fetch events from Ticketmaster
        const tmEvents = await fetchTicketmasterEvents(city.name, city.state_code || "CA");
        
        // Convert to our format
        const cachedEvents = tmEvents
          .map((event, index) => convertEvent(event, city.id, index))
          .filter((e): e is CachedEvent => e !== null);

        // Delete old events for this city (older than today)
        const today = new Date().toISOString().split("T")[0];
        await supabase
          .from("cached_events")
          .delete()
          .eq("city_id", city.id)
          .lt("start_date", today);

        // Upsert new events
        if (cachedEvents.length > 0) {
          const { error: upsertError } = await supabase
            .from("cached_events")
            .upsert(cachedEvents, { onConflict: "id" });

          if (upsertError) {
            throw new Error(`Failed to upsert events: ${upsertError.message}`);
          }
        }

        // Update sync log
        await supabase
          .from("sync_log")
          .update({
            completed_at: new Date().toISOString(),
            records_synced: cachedEvents.length,
            status: "success",
          })
          .eq("id", syncLog?.id);

        results.push({
          city_id: city.id,
          events_synced: cachedEvents.length,
          status: "success",
        });
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

        results.push({
          city_id: city.id,
          events_synced: 0,
          status: `failed: ${error instanceof Error ? error.message : "Unknown error"}`,
        });
      }
    }

    return new Response(JSON.stringify({ success: true, results }), {
      headers: { "Content-Type": "application/json" },
      status: 200,
    });
  } catch (error) {
    return new Response(
      JSON.stringify({
        success: false,
        error: error instanceof Error ? error.message : "Unknown error",
      }),
      {
        headers: { "Content-Type": "application/json" },
        status: 500,
      }
    );
  }
});
