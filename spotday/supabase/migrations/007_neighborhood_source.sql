-- Migration: 007_neighborhood_source
-- Description: Add neighborhood_source column to track how neighborhood assignments were made
-- This helps identify places that were reassigned due to overlapping neighborhood syncs

-- Add neighborhood_source column to cached_places
-- Values:
--   SYNC: Assigned during initial sync (neighborhood that fetched this place)
--   DISTANCE_TIE: Reassigned because another neighborhood's center was closer
--   MANUAL: Manually overridden by admin
ALTER TABLE cached_places 
ADD COLUMN IF NOT EXISTS neighborhood_source TEXT 
DEFAULT 'SYNC' 
CHECK (neighborhood_source IN ('SYNC', 'DISTANCE_TIE', 'MANUAL'));

-- Update existing rows to have SYNC as source (they were synced before this column existed)
UPDATE cached_places 
SET neighborhood_source = 'SYNC' 
WHERE neighborhood_source IS NULL;

-- Add comment for documentation
COMMENT ON COLUMN cached_places.neighborhood_source IS 
'How the neighborhood_id was assigned: SYNC (during fetch), DISTANCE_TIE (reassigned to closer center), MANUAL (admin override)';
