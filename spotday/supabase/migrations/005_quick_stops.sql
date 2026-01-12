-- SpotDay Database Schema
-- Migration: 003_quick_stops
-- Description: Creates table for LLM-generated quick stops (viewpoints, photo spots, street art)
-- Note: Coffee stops come from cached_places at runtime, not stored here

-- ============================================
-- CACHED QUICK STOPS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS cached_quick_stops (
    id TEXT PRIMARY KEY,
    city_id TEXT NOT NULL REFERENCES cities(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    stop_type TEXT NOT NULL CHECK (stop_type IN ('VIEWPOINT', 'PHOTO_SPOT', 'STREET_ART')),
    description TEXT,
    duration_minutes INTEGER NOT NULL DEFAULT 20,
    neighborhood_id TEXT REFERENCES neighborhoods(id) ON DELETE SET NULL,
    data_source TEXT NOT NULL DEFAULT 'LLM' CHECK (data_source IN ('CURATED', 'LLM')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for efficient querying
CREATE INDEX IF NOT EXISTS idx_quick_stops_city_id ON cached_quick_stops(city_id);
CREATE INDEX IF NOT EXISTS idx_quick_stops_stop_type ON cached_quick_stops(stop_type);
CREATE INDEX IF NOT EXISTS idx_quick_stops_neighborhood ON cached_quick_stops(neighborhood_id);

-- Trigger for updated_at
CREATE TRIGGER update_cached_quick_stops_updated_at
    BEFORE UPDATE ON cached_quick_stops
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- ROW LEVEL SECURITY (RLS)
-- ============================================

ALTER TABLE cached_quick_stops ENABLE ROW LEVEL SECURITY;

-- Public read access for quick stops in active cities
CREATE POLICY "Public read access for cached quick stops"
    ON cached_quick_stops FOR SELECT
    USING (EXISTS (SELECT 1 FROM cities WHERE cities.id = cached_quick_stops.city_id AND cities.is_active = true));

-- Service role has full access (for Edge Functions)
CREATE POLICY "Service role full access to cached_quick_stops"
    ON cached_quick_stops FOR ALL
    USING (auth.role() = 'service_role');
