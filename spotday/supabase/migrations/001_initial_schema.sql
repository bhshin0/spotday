-- SpotDay Database Schema
-- Migration: 001_initial_schema
-- Description: Creates tables for caching API data and LLM-seeded city/neighborhood data

-- ============================================
-- CITIES TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS cities (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    country TEXT NOT NULL DEFAULT 'USA',
    state_code TEXT,
    size TEXT NOT NULL CHECK (size IN ('large', 'medium', 'small')),
    density TEXT NOT NULL CHECK (density IN ('very_dense', 'moderate', 'spread_out')),
    center_lat DOUBLE PRECISION NOT NULL,
    center_lng DOUBLE PRECISION NOT NULL,
    estimated_areas INTEGER NOT NULL DEFAULT 8,
    data_source TEXT NOT NULL DEFAULT 'LLM' CHECK (data_source IN ('CURATED', 'LLM', 'ALGORITHM')),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================
-- NEIGHBORHOODS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS neighborhoods (
    id TEXT PRIMARY KEY,
    city_id TEXT NOT NULL REFERENCES cities(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    tier TEXT NOT NULL CHECK (tier IN ('ESSENTIAL', 'CLASSIC', 'LOCAL')),
    center_lat DOUBLE PRECISION NOT NULL,
    center_lng DOUBLE PRECISION NOT NULL,
    radius_meters INTEGER NOT NULL DEFAULT 800,
    vibes TEXT[] NOT NULL DEFAULT '{}',
    description TEXT,
    adjacent_neighborhoods TEXT[] NOT NULL DEFAULT '{}',
    data_source TEXT NOT NULL DEFAULT 'LLM' CHECK (data_source IN ('CURATED', 'LLM', 'ALGORITHM')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_neighborhoods_city_id ON neighborhoods(city_id);
CREATE INDEX IF NOT EXISTS idx_neighborhoods_tier ON neighborhoods(tier);

-- ============================================
-- CACHED EVENTS TABLE (Ticketmaster)
-- ============================================
CREATE TABLE IF NOT EXISTS cached_events (
    id TEXT PRIMARY KEY,
    city_id TEXT NOT NULL REFERENCES cities(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    description TEXT,
    event_type TEXT NOT NULL CHECK (event_type IN ('CONCERT', 'SPORTS', 'THEATER', 'COMEDY', 'FOOD_FESTIVAL', 'STREET_FAIR', 'CLASS_WORKSHOP')),
    venue_name TEXT NOT NULL,
    venue_lat DOUBLE PRECISION NOT NULL,
    venue_lng DOUBLE PRECISION NOT NULL,
    start_date DATE NOT NULL,
    start_hour INTEGER NOT NULL CHECK (start_hour >= 0 AND start_hour <= 23),
    start_minute INTEGER NOT NULL DEFAULT 0 CHECK (start_minute >= 0 AND start_minute <= 59),
    duration_minutes INTEGER NOT NULL DEFAULT 120,
    price_min DOUBLE PRECISION,
    price_max DOUBLE PRECISION,
    is_sold_out BOOLEAN NOT NULL DEFAULT false,
    ticket_url TEXT,
    popularity INTEGER NOT NULL DEFAULT 3 CHECK (popularity >= 1 AND popularity <= 5),
    source TEXT NOT NULL DEFAULT 'TICKETMASTER' CHECK (source IN ('TICKETMASTER', 'EVENTBRITE')),
    raw_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cached_events_city_id ON cached_events(city_id);
CREATE INDEX IF NOT EXISTS idx_cached_events_start_date ON cached_events(start_date);
CREATE INDEX IF NOT EXISTS idx_cached_events_event_type ON cached_events(event_type);
CREATE INDEX IF NOT EXISTS idx_cached_events_popularity ON cached_events(popularity);

-- ============================================
-- CACHED PLACES TABLE (Google Places)
-- ============================================
CREATE TABLE IF NOT EXISTS cached_places (
    id TEXT PRIMARY KEY,
    city_id TEXT NOT NULL REFERENCES cities(id) ON DELETE CASCADE,
    neighborhood_id TEXT REFERENCES neighborhoods(id) ON DELETE SET NULL,
    name TEXT NOT NULL,
    place_type TEXT NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    rating DOUBLE PRECISION,
    review_count INTEGER DEFAULT 0,
    price_level INTEGER CHECK (price_level >= 1 AND price_level <= 4),
    is_outdoor BOOLEAN DEFAULT false,
    open_hour INTEGER DEFAULT 6,
    close_hour INTEGER DEFAULT 22,
    raw_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cached_places_city_id ON cached_places(city_id);
CREATE INDEX IF NOT EXISTS idx_cached_places_neighborhood_id ON cached_places(neighborhood_id);
CREATE INDEX IF NOT EXISTS idx_cached_places_place_type ON cached_places(place_type);

-- ============================================
-- SYNC LOG TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS sync_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sync_type TEXT NOT NULL CHECK (sync_type IN ('events', 'places', 'city_seed')),
    city_id TEXT REFERENCES cities(id) ON DELETE SET NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    records_synced INTEGER DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'running' CHECK (status IN ('running', 'success', 'failed')),
    error_message TEXT,
    metadata JSONB
);

CREATE INDEX IF NOT EXISTS idx_sync_log_sync_type ON sync_log(sync_type);
CREATE INDEX IF NOT EXISTS idx_sync_log_city_id ON sync_log(city_id);
CREATE INDEX IF NOT EXISTS idx_sync_log_started_at ON sync_log(started_at DESC);

-- ============================================
-- HELPER FUNCTIONS
-- ============================================

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for updated_at
CREATE TRIGGER update_cities_updated_at
    BEFORE UPDATE ON cities
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_neighborhoods_updated_at
    BEFORE UPDATE ON neighborhoods
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_cached_events_updated_at
    BEFORE UPDATE ON cached_events
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_cached_places_updated_at
    BEFORE UPDATE ON cached_places
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- ROW LEVEL SECURITY (RLS)
-- ============================================

-- Enable RLS on all tables
ALTER TABLE cities ENABLE ROW LEVEL SECURITY;
ALTER TABLE neighborhoods ENABLE ROW LEVEL SECURITY;
ALTER TABLE cached_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE cached_places ENABLE ROW LEVEL SECURITY;
ALTER TABLE sync_log ENABLE ROW LEVEL SECURITY;

-- Public read access for active cities and their data
CREATE POLICY "Public read access for active cities"
    ON cities FOR SELECT
    USING (is_active = true);

CREATE POLICY "Public read access for neighborhoods"
    ON neighborhoods FOR SELECT
    USING (EXISTS (SELECT 1 FROM cities WHERE cities.id = neighborhoods.city_id AND cities.is_active = true));

CREATE POLICY "Public read access for cached events"
    ON cached_events FOR SELECT
    USING (EXISTS (SELECT 1 FROM cities WHERE cities.id = cached_events.city_id AND cities.is_active = true));

CREATE POLICY "Public read access for cached places"
    ON cached_places FOR SELECT
    USING (EXISTS (SELECT 1 FROM cities WHERE cities.id = cached_places.city_id AND cities.is_active = true));

-- Service role has full access (for Edge Functions)
CREATE POLICY "Service role full access to cities"
    ON cities FOR ALL
    USING (auth.role() = 'service_role');

CREATE POLICY "Service role full access to neighborhoods"
    ON neighborhoods FOR ALL
    USING (auth.role() = 'service_role');

CREATE POLICY "Service role full access to cached_events"
    ON cached_events FOR ALL
    USING (auth.role() = 'service_role');

CREATE POLICY "Service role full access to cached_places"
    ON cached_places FOR ALL
    USING (auth.role() = 'service_role');

CREATE POLICY "Service role full access to sync_log"
    ON sync_log FOR ALL
    USING (auth.role() = 'service_role');

-- ============================================
-- SEED DATA: San Francisco (CURATED)
-- ============================================

INSERT INTO cities (id, name, country, state_code, size, density, center_lat, center_lng, estimated_areas, data_source, is_active)
VALUES ('san_francisco', 'San Francisco', 'USA', 'CA', 'medium', 'very_dense', 37.7749, -122.4194, 12, 'CURATED', true)
ON CONFLICT (id) DO NOTHING;

-- Essential neighborhoods
INSERT INTO neighborhoods (id, city_id, name, tier, center_lat, center_lng, radius_meters, vibes, description, adjacent_neighborhoods, data_source)
VALUES 
    ('mission', 'san_francisco', 'Mission District', 'ESSENTIAL', 37.7599, -122.4148, 1000, 
     ARRAY['foodie', 'nightlife', 'artsy', 'latin'], 
     'SF''s most vibrant neighborhood - tacos, murals, dive bars, and the best nightlife',
     ARRAY['castro', 'bernal_heights', 'potrero_hill', 'soma'], 'CURATED'),
    ('castro', 'san_francisco', 'Castro', 'ESSENTIAL', 37.7609, -122.4350, 600,
     ARRAY['lgbtq', 'nightlife', 'historic', 'brunch'],
     'Historic LGBTQ+ hub with great bars, brunch spots, and iconic streetscape',
     ARRAY['mission', 'hayes_valley', 'noe_valley'], 'CURATED'),
    ('north_beach', 'san_francisco', 'North Beach', 'ESSENTIAL', 37.8005, -122.4091, 700,
     ARRAY['italian', 'historic', 'nightlife', 'literary'],
     'Little Italy meets Beat Generation - classic restaurants, City Lights bookstore',
     ARRAY['chinatown', 'fishermans_wharf', 'russian_hill'], 'CURATED'),
    ('hayes_valley', 'san_francisco', 'Hayes Valley', 'ESSENTIAL', 37.7759, -122.4245, 500,
     ARRAY['trendy', 'shopping', 'foodie', 'upscale'],
     'Trendy boutiques, excellent restaurants, perfect for afternoon strolling',
     ARRAY['castro', 'soma', 'civic_center'], 'CURATED'),
-- Classic neighborhoods
    ('chinatown', 'san_francisco', 'Chinatown', 'CLASSIC', 37.7941, -122.4078, 500,
     ARRAY['chinese', 'historic', 'foodie', 'cultural'],
     'Oldest Chinatown in North America - dim sum, tea shops, historic temples',
     ARRAY['north_beach', 'financial_district', 'union_square'], 'CURATED'),
    ('marina', 'san_francisco', 'Marina', 'CLASSIC', 37.8025, -122.4382, 800,
     ARRAY['brunch', 'upscale', 'waterfront', 'fitness'],
     'Upscale brunch spots, waterfront views, young professional scene',
     ARRAY['cow_hollow', 'pacific_heights', 'presidio'], 'CURATED'),
    ('soma', 'san_francisco', 'SoMa', 'CLASSIC', 37.7785, -122.4056, 1200,
     ARRAY['museums', 'tech', 'nightlife', 'industrial'],
     'SFMOMA, tech offices, nightclubs - sprawling and varied',
     ARRAY['mission', 'hayes_valley', 'financial_district'], 'CURATED'),
    ('haight', 'san_francisco', 'Haight-Ashbury', 'CLASSIC', 37.7692, -122.4481, 600,
     ARRAY['vintage', 'counterculture', 'parks'],
     'Summer of Love history, vintage shops, near Golden Gate Park',
     ARRAY['cole_valley', 'panhandle'], 'CURATED'),
    ('embarcadero', 'san_francisco', 'Embarcadero', 'CLASSIC', 37.7936, -122.3930, 1000,
     ARRAY['waterfront', 'foodie', 'scenic'],
     'Ferry Building marketplace, waterfront promenade, bay views',
     ARRAY['financial_district', 'soma'], 'CURATED'),
-- Local neighborhoods  
    ('potrero_hill', 'san_francisco', 'Potrero Hill', 'LOCAL', 37.7601, -122.4018, 800,
     ARRAY['views', 'brunch', 'local'],
     'Sunny hilltop with great restaurants and city views',
     ARRAY['mission', 'dogpatch', 'soma'], 'CURATED'),
    ('dogpatch', 'san_francisco', 'Dogpatch', 'LOCAL', 37.7580, -122.3870, 600,
     ARRAY['breweries', 'artsy', 'emerging'],
     'Industrial-chic breweries and restaurants, up-and-coming',
     ARRAY['potrero_hill'], 'CURATED'),
    ('bernal_heights', 'san_francisco', 'Bernal Heights', 'LOCAL', 37.7396, -122.4156, 700,
     ARRAY['family', 'local', 'views'],
     'Quiet neighborhood with hilltop park and local favorites',
     ARRAY['mission'], 'CURATED')
ON CONFLICT (id) DO NOTHING;
