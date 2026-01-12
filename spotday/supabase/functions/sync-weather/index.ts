// sync-weather Edge Function
// Fetches weather forecasts from OpenWeatherMap and caches them in Supabase
// Runs every 3 hours via cron

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const OPENWEATHERMAP_API_KEY = Deno.env.get("OPENWEATHERMAP_API_KEY")!;
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const OWM_BASE_URL = "https://api.openweathermap.org/data/2.5";

interface OWMForecastItem {
  dt: number;  // Unix timestamp
  main: {
    temp: number;
    feels_like: number;
    humidity: number;
  };
  weather: Array<{
    id: number;
    main: string;  // "Clear", "Clouds", "Rain", etc.
    description: string;
    icon: string;  // "01d", "10n", etc.
  }>;
  wind: {
    speed: number;  // m/s
  };
  dt_txt: string;  // "2024-01-15 12:00:00"
}

interface OWMForecastResponse {
  cod: string;
  list: OWMForecastItem[];
  city: {
    name: string;
    coord: {
      lat: number;
      lon: number;
    };
  };
}

interface CachedWeather {
  city_id: string;
  forecast_date: string;
  forecast_hour: number;
  condition: string;
  temperature_f: number;
  feels_like_f: number;
  humidity_percent: number;
  wind_mph: number;
  description: string;
  icon_code: string;
  raw_json: unknown;
}

// Map OpenWeatherMap condition to our simplified conditions
function mapCondition(owmMain: string, owmId: number): string {
  // OWM weather condition codes: https://openweathermap.org/weather-conditions
  if (owmId >= 200 && owmId < 300) return "STORMY";  // Thunderstorm
  if (owmId >= 300 && owmId < 400) return "RAINY";   // Drizzle
  if (owmId >= 500 && owmId < 600) return "RAINY";   // Rain
  if (owmId >= 600 && owmId < 700) return "SNOWY";   // Snow
  if (owmId >= 700 && owmId < 800) return "FOGGY";   // Atmosphere (fog, mist)
  if (owmId === 800) return "SUNNY";                  // Clear
  if (owmId > 800) return "CLOUDY";                   // Clouds
  return "CLOUDY";  // Default
}

// Convert Celsius to Fahrenheit
function celsiusToFahrenheit(celsius: number): number {
  return Math.round(celsius * 9/5 + 32);
}

// Convert m/s to mph
function msToMph(ms: number): number {
  return Math.round(ms * 2.237);
}

// Fetch 5-day forecast from OpenWeatherMap
async function fetchForecast(lat: number, lng: number): Promise<OWMForecastResponse> {
  const params = new URLSearchParams({
    lat: lat.toString(),
    lon: lng.toString(),
    appid: OPENWEATHERMAP_API_KEY,
    units: "metric",  // Get Celsius, convert to F ourselves
  });

  const response = await fetch(`${OWM_BASE_URL}/forecast?${params}`);

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`OpenWeatherMap API error: ${response.status} - ${errorText}`);
  }

  return await response.json();
}

// Convert OWM forecast item to our cached format
function convertForecast(item: OWMForecastItem, cityId: string): CachedWeather {
  const weather = item.weather[0];
  const date = new Date(item.dt * 1000);
  
  return {
    city_id: cityId,
    forecast_date: date.toISOString().split("T")[0],
    forecast_hour: date.getUTCHours(),
    condition: mapCondition(weather.main, weather.id),
    temperature_f: celsiusToFahrenheit(item.main.temp),
    feels_like_f: celsiusToFahrenheit(item.main.feels_like),
    humidity_percent: item.main.humidity,
    wind_mph: msToMph(item.wind.speed),
    description: weather.description,
    icon_code: weather.icon,
    raw_json: item,
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

    // Parse request body for optional city filter
    let targetCityId: string | null = null;
    try {
      const body = await req.json();
      targetCityId = body.city_id || null;
    } catch {
      // No body, sync all active cities
    }

    // Get active cities
    let citiesQuery = supabase.from("cities").select("*").eq("is_active", true);
    if (targetCityId) {
      citiesQuery = citiesQuery.eq("id", targetCityId);
    }

    const { data: cities, error: citiesError } = await citiesQuery;

    if (citiesError) {
      throw new Error(`Failed to fetch cities: ${citiesError.message}`);
    }

    const results: Array<{
      city_id: string;
      forecasts_synced: number;
      status: string;
    }> = [];

    for (const city of cities || []) {
      try {
        // Fetch forecast from OpenWeatherMap
        const forecast = await fetchForecast(city.center_lat, city.center_lng);

        // Convert to our format
        const cachedWeather = forecast.list.map((item) =>
          convertForecast(item, city.id)
        );

        // Upsert weather forecasts
        if (cachedWeather.length > 0) {
          const { error: upsertError } = await supabase
            .from("cached_weather")
            .upsert(cachedWeather, {
              onConflict: "city_id,forecast_date,forecast_hour",
            });

          if (upsertError) {
            throw new Error(`Failed to upsert weather: ${upsertError.message}`);
          }
        }

        // Clean up old weather data for this city
        const twoDaysAgo = new Date();
        twoDaysAgo.setDate(twoDaysAgo.getDate() - 2);
        await supabase
          .from("cached_weather")
          .delete()
          .eq("city_id", city.id)
          .lt("forecast_date", twoDaysAgo.toISOString().split("T")[0]);

        results.push({
          city_id: city.id,
          forecasts_synced: cachedWeather.length,
          status: "success",
        });

        // Rate limiting between cities
        await new Promise((resolve) => setTimeout(resolve, 500));
      } catch (error) {
        results.push({
          city_id: city.id,
          forecasts_synced: 0,
          status: `failed: ${error instanceof Error ? error.message : "Unknown error"}`,
        });
      }
    }

    return new Response(
      JSON.stringify({
        success: true,
        cities_processed: results.length,
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
