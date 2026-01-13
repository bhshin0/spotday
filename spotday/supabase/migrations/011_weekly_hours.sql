-- Migration: 010_weekly_hours
-- Description: Add weekly_hours JSONB column to cached_places for day-specific opening hours
-- This enables the itinerary generator to respect actual business hours by day of week

-- ============================================
-- WEEKLY HOURS COLUMN
-- ============================================
-- Format: {
--   "monday": {"open": "11:00", "close": "22:00"},
--   "tuesday": {"open": "11:00", "close": "22:00"},
--   "wednesday": null,  -- closed this day
--   "thursday": {"open": "11:00", "close": "22:00"},
--   "friday": {"open": "11:00", "close": "02:00"},  -- overnight closing
--   "saturday": {"open": "10:00", "close": "02:00"},
--   "sunday": {"open": "10:00", "close": "21:00"}
-- }

ALTER TABLE cached_places 
ADD COLUMN IF NOT EXISTS weekly_hours JSONB;

COMMENT ON COLUMN cached_places.weekly_hours IS 
'Day-specific opening hours from Google Places API. Keys are lowercase day names (monday-sunday). Value is {open, close} in 24h format or null if closed. Close times after midnight (e.g., "02:00") indicate overnight hours.';

-- Create index for querying places by whether they have hours data
CREATE INDEX IF NOT EXISTS idx_cached_places_has_weekly_hours 
ON cached_places ((weekly_hours IS NOT NULL));

-- ============================================
-- HELPER FUNCTION: Check if place is open
-- ============================================
-- Usage: SELECT is_place_open_at('ChIJ...', 'friday', '21:30')
CREATE OR REPLACE FUNCTION is_place_open_at(
    p_place_id TEXT,
    p_day TEXT,  -- 'monday', 'tuesday', etc.
    p_time TEXT  -- '21:30' format
) RETURNS BOOLEAN AS $$
DECLARE
    v_hours JSONB;
    v_day_hours JSONB;
    v_open_time TEXT;
    v_close_time TEXT;
BEGIN
    -- Get the weekly hours for this place
    SELECT weekly_hours INTO v_hours
    FROM cached_places
    WHERE id = p_place_id;
    
    IF v_hours IS NULL THEN
        -- No hours data, fall back to open_hour/close_hour columns
        RETURN TRUE;  -- Assume open if no data
    END IF;
    
    -- Get hours for the specific day
    v_day_hours := v_hours->p_day;
    
    IF v_day_hours IS NULL OR v_day_hours = 'null'::jsonb THEN
        RETURN FALSE;  -- Closed on this day
    END IF;
    
    v_open_time := v_day_hours->>'open';
    v_close_time := v_day_hours->>'close';
    
    -- Handle overnight hours (close time < open time means closes after midnight)
    IF v_close_time < v_open_time THEN
        -- Overnight: open if time >= open OR time < close
        RETURN p_time >= v_open_time OR p_time < v_close_time;
    ELSE
        -- Normal hours: open if time >= open AND time < close
        RETURN p_time >= v_open_time AND p_time < v_close_time;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- BACKFILL: Convert existing open_hour/close_hour to weekly_hours
-- ============================================
-- This creates uniform hours for all days based on existing data
-- Will be overwritten when sync-places runs with real Google data
UPDATE cached_places
SET weekly_hours = jsonb_build_object(
    'monday', jsonb_build_object('open', lpad(open_hour::text, 2, '0') || ':00', 'close', lpad(close_hour::text, 2, '0') || ':00'),
    'tuesday', jsonb_build_object('open', lpad(open_hour::text, 2, '0') || ':00', 'close', lpad(close_hour::text, 2, '0') || ':00'),
    'wednesday', jsonb_build_object('open', lpad(open_hour::text, 2, '0') || ':00', 'close', lpad(close_hour::text, 2, '0') || ':00'),
    'thursday', jsonb_build_object('open', lpad(open_hour::text, 2, '0') || ':00', 'close', lpad(close_hour::text, 2, '0') || ':00'),
    'friday', jsonb_build_object('open', lpad(open_hour::text, 2, '0') || ':00', 'close', lpad(close_hour::text, 2, '0') || ':00'),
    'saturday', jsonb_build_object('open', lpad(open_hour::text, 2, '0') || ':00', 'close', lpad(close_hour::text, 2, '0') || ':00'),
    'sunday', jsonb_build_object('open', lpad(open_hour::text, 2, '0') || ':00', 'close', lpad(close_hour::text, 2, '0') || ':00')
)
WHERE weekly_hours IS NULL;
