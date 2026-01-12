-- Migration: 008_nightlife_staleness
-- Description: Add nightlife category and staleness tracking columns to cached_places
-- Also creates unmatched_venues table for manual review of failed Google lookups

-- ============================================
-- NIGHTLIFE CATEGORY COLUMN
-- ============================================
-- Categories: cocktail_bar, dive_bar, rooftop_bar, wine_bar, 
--             sports_bar, live_music, brewery, karaoke
ALTER TABLE cached_places 
ADD COLUMN IF NOT EXISTS nightlife_category TEXT;

COMMENT ON COLUMN cached_places.nightlife_category IS 
'Specific nightlife type from LLM: cocktail_bar, dive_bar, rooftop_bar, wine_bar, sports_bar, live_music, brewery, karaoke';

-- ============================================
-- STALENESS TRACKING COLUMNS
-- ============================================
-- Track when venue was last verified via Google API
ALTER TABLE cached_places 
ADD COLUMN IF NOT EXISTS last_verified_at TIMESTAMPTZ DEFAULT NOW();

-- Track if Google reports venue as permanently closed
ALTER TABLE cached_places 
ADD COLUMN IF NOT EXISTS is_permanently_closed BOOLEAN DEFAULT false;

COMMENT ON COLUMN cached_places.last_verified_at IS 
'Timestamp when this venue was last verified via Google API. Used for 90-day staleness filtering.';

COMMENT ON COLUMN cached_places.is_permanently_closed IS 
'True if Google business_status = CLOSED_PERMANENTLY. Venue will be hidden from results.';

-- Update existing rows to have current timestamp
UPDATE cached_places 
SET last_verified_at = NOW() 
WHERE last_verified_at IS NULL;

-- ============================================
-- UNMATCHED VENUES TABLE (for manual review)
-- ============================================
-- Stores LLM-generated venues that couldn't be matched on Google
CREATE TABLE IF NOT EXISTS unmatched_venues (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    llm_name TEXT NOT NULL,
    city_id TEXT NOT NULL REFERENCES cities(id) ON DELETE CASCADE,
    category TEXT,
    neighborhood_hint TEXT,
    why_notable TEXT,
    reason TEXT NOT NULL CHECK (reason IN ('NOT_FOUND', 'WRONG_LOCATION', 'CLOSED', 'LOW_CONFIDENCE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_unmatched_venues_city_id ON unmatched_venues(city_id);
CREATE INDEX IF NOT EXISTS idx_unmatched_venues_reason ON unmatched_venues(reason);

COMMENT ON TABLE unmatched_venues IS 
'LLM-generated venues that failed Google lookup. Review periodically to fix prompts or manually add venues.';

-- ============================================
-- ROW LEVEL SECURITY
-- ============================================
ALTER TABLE unmatched_venues ENABLE ROW LEVEL SECURITY;

-- Service role has full access (for Edge Functions)
CREATE POLICY "Service role full access to unmatched_venues"
    ON unmatched_venues FOR ALL
    USING (auth.role() = 'service_role');

-- Public read access for debugging (optional - can remove if not needed)
CREATE POLICY "Public read access for unmatched_venues"
    ON unmatched_venues FOR SELECT
    USING (true);
