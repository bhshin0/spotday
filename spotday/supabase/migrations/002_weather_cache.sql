-- SpotDay Database Schema
-- Migration: 002_weather_cache
-- Description: Adds weather caching table for weather-aware itinerary recommendations

-- ============================================
-- CACHED WEATHER TABLE (OpenWeatherMap)
-- ============================================
CREATE TABLE IF NOT EXISTS cached_weather (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    city_id TEXT NOT NULL REFERENCES cities(id) ON DELETE CASCADE,
    forecast_date DATE NOT NULL,
    forecast_hour INTEGER NOT NULL CHECK (forecast_hour >= 0 AND forecast_hour <= 23),
    condition TEXT NOT NULL CHECK (condition IN ('SUNNY', 'CLOUDY', 'RAINY', 'STORMY', 'SNOWY', 'FOGGY')),
    temperature_f INTEGER NOT NULL,
    feels_like_f INTEGER NOT NULL,
    humidity_percent INTEGER,
    wind_mph INTEGER,
    description TEXT,
    icon_code TEXT,  -- OpenWeatherMap icon code (e.g., "01d", "10n")
    raw_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- Unique constraint: one forecast per city/date/hour
    UNIQUE(city_id, forecast_date, forecast_hour)
);

CREATE INDEX IF NOT EXISTS idx_cached_weather_city_id ON cached_weather(city_id);
CREATE INDEX IF NOT EXISTS idx_cached_weather_date ON cached_weather(forecast_date);
CREATE INDEX IF NOT EXISTS idx_cached_weather_city_date ON cached_weather(city_id, forecast_date);

-- Trigger for updated_at
CREATE TRIGGER update_cached_weather_updated_at
    BEFORE UPDATE ON cached_weather
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Enable RLS
ALTER TABLE cached_weather ENABLE ROW LEVEL SECURITY;

-- Public read access for active cities
CREATE POLICY "Public read access for cached weather"
    ON cached_weather FOR SELECT
    USING (EXISTS (SELECT 1 FROM cities WHERE cities.id = cached_weather.city_id AND cities.is_active = true));

-- Service role full access
CREATE POLICY "Service role full access to cached_weather"
    ON cached_weather FOR ALL
    USING (auth.role() = 'service_role');

-- ============================================
-- HELPER: Clean up old weather data
-- ============================================
CREATE OR REPLACE FUNCTION cleanup_old_weather()
RETURNS void AS $$
BEGIN
    -- Delete weather data older than 2 days
    DELETE FROM cached_weather 
    WHERE forecast_date < CURRENT_DATE - INTERVAL '2 days';
END;
$$ LANGUAGE plpgsql;

-- Optional: Schedule cleanup daily
-- SELECT cron.schedule('cleanup-weather-daily', '0 0 * * *', 'SELECT cleanup_old_weather()');
