-- Migration: 012_cleanup_hours
-- Consolidate hours handling: weekly_hours becomes the single source of truth
-- Remove redundant open_hour/close_hour columns

-- ============================================
-- STEP 1: Ensure all places have weekly_hours
-- ============================================

-- Default hours by place type for any remaining nulls
UPDATE cached_places
SET weekly_hours = CASE place_type
    WHEN 'NIGHTLIFE' THEN jsonb_build_object(
        'monday', jsonb_build_object('open', '16:00', 'close', '02:00'),
        'tuesday', jsonb_build_object('open', '16:00', 'close', '02:00'),
        'wednesday', jsonb_build_object('open', '16:00', 'close', '02:00'),
        'thursday', jsonb_build_object('open', '16:00', 'close', '02:00'),
        'friday', jsonb_build_object('open', '16:00', 'close', '02:00'),
        'saturday', jsonb_build_object('open', '16:00', 'close', '02:00'),
        'sunday', jsonb_build_object('open', '16:00', 'close', '00:00')
    )
    WHEN 'RESTAURANT' THEN jsonb_build_object(
        'monday', jsonb_build_object('open', '11:00', 'close', '22:00'),
        'tuesday', jsonb_build_object('open', '11:00', 'close', '22:00'),
        'wednesday', jsonb_build_object('open', '11:00', 'close', '22:00'),
        'thursday', jsonb_build_object('open', '11:00', 'close', '22:00'),
        'friday', jsonb_build_object('open', '11:00', 'close', '23:00'),
        'saturday', jsonb_build_object('open', '10:00', 'close', '23:00'),
        'sunday', jsonb_build_object('open', '10:00', 'close', '21:00')
    )
    WHEN 'MUSEUM' THEN jsonb_build_object(
        'monday', NULL,
        'tuesday', jsonb_build_object('open', '10:00', 'close', '17:00'),
        'wednesday', jsonb_build_object('open', '10:00', 'close', '17:00'),
        'thursday', jsonb_build_object('open', '10:00', 'close', '21:00'),
        'friday', jsonb_build_object('open', '10:00', 'close', '17:00'),
        'saturday', jsonb_build_object('open', '10:00', 'close', '17:00'),
        'sunday', jsonb_build_object('open', '11:00', 'close', '17:00')
    )
    WHEN 'PARK' THEN jsonb_build_object(
        'monday', jsonb_build_object('open', '06:00', 'close', '22:00'),
        'tuesday', jsonb_build_object('open', '06:00', 'close', '22:00'),
        'wednesday', jsonb_build_object('open', '06:00', 'close', '22:00'),
        'thursday', jsonb_build_object('open', '06:00', 'close', '22:00'),
        'friday', jsonb_build_object('open', '06:00', 'close', '22:00'),
        'saturday', jsonb_build_object('open', '06:00', 'close', '22:00'),
        'sunday', jsonb_build_object('open', '06:00', 'close', '22:00')
    )
    WHEN 'WELLNESS' THEN jsonb_build_object(
        'monday', jsonb_build_object('open', '09:00', 'close', '21:00'),
        'tuesday', jsonb_build_object('open', '09:00', 'close', '21:00'),
        'wednesday', jsonb_build_object('open', '09:00', 'close', '21:00'),
        'thursday', jsonb_build_object('open', '09:00', 'close', '21:00'),
        'friday', jsonb_build_object('open', '09:00', 'close', '21:00'),
        'saturday', jsonb_build_object('open', '09:00', 'close', '18:00'),
        'sunday', NULL
    )
    WHEN 'SHOPPING' THEN jsonb_build_object(
        'monday', jsonb_build_object('open', '10:00', 'close', '21:00'),
        'tuesday', jsonb_build_object('open', '10:00', 'close', '21:00'),
        'wednesday', jsonb_build_object('open', '10:00', 'close', '21:00'),
        'thursday', jsonb_build_object('open', '10:00', 'close', '21:00'),
        'friday', jsonb_build_object('open', '10:00', 'close', '21:00'),
        'saturday', jsonb_build_object('open', '10:00', 'close', '21:00'),
        'sunday', jsonb_build_object('open', '11:00', 'close', '18:00')
    )
    ELSE jsonb_build_object(
        'monday', jsonb_build_object('open', '09:00', 'close', '18:00'),
        'tuesday', jsonb_build_object('open', '09:00', 'close', '18:00'),
        'wednesday', jsonb_build_object('open', '09:00', 'close', '18:00'),
        'thursday', jsonb_build_object('open', '09:00', 'close', '18:00'),
        'friday', jsonb_build_object('open', '09:00', 'close', '18:00'),
        'saturday', jsonb_build_object('open', '09:00', 'close', '18:00'),
        'sunday', jsonb_build_object('open', '09:00', 'close', '18:00')
    )
END
WHERE weekly_hours IS NULL;

-- ============================================
-- STEP 2: Make weekly_hours NOT NULL
-- ============================================
ALTER TABLE cached_places
ALTER COLUMN weekly_hours SET NOT NULL;

-- ============================================
-- STEP 3: Drop legacy columns
-- ============================================
ALTER TABLE cached_places DROP COLUMN IF EXISTS open_hour;
ALTER TABLE cached_places DROP COLUMN IF EXISTS close_hour;

-- ============================================
-- STEP 4: Update helper function
-- ============================================
CREATE OR REPLACE FUNCTION get_place_hours(
    p_place_id TEXT,
    p_day TEXT  -- 'monday', 'tuesday', etc.
) RETURNS TABLE(open_time TEXT, close_time TEXT, is_closed BOOLEAN) AS $$
DECLARE
    v_hours JSONB;
    v_day_hours JSONB;
BEGIN
    SELECT weekly_hours INTO v_hours
    FROM cached_places
    WHERE id = p_place_id;
    
    IF v_hours IS NULL THEN
        RETURN QUERY SELECT '09:00'::TEXT, '18:00'::TEXT, FALSE;
        RETURN;
    END IF;
    
    v_day_hours := v_hours->p_day;
    
    IF v_day_hours IS NULL OR v_day_hours = 'null'::jsonb THEN
        RETURN QUERY SELECT NULL::TEXT, NULL::TEXT, TRUE;
        RETURN;
    END IF;
    
    RETURN QUERY SELECT 
        v_day_hours->>'open', 
        v_day_hours->>'close',
        FALSE;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- STEP 5: Add index for performance
-- ============================================
CREATE INDEX IF NOT EXISTS idx_cached_places_weekly_hours_gin 
ON cached_places USING gin (weekly_hours);

-- Drop old function if exists
DROP FUNCTION IF EXISTS is_place_open_at(TEXT, TEXT, TEXT);
