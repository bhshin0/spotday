-- Migration: 007_update_events_cron
-- Description: Update sync-events cron to run just after midnight Eastern

-- Remove the old schedule
DO $$
BEGIN
    PERFORM cron.unschedule('sync-events-nightly');
EXCEPTION WHEN OTHERS THEN
    -- Job doesn't exist, ignore
END;
$$;

-- Schedule sync-events at 12:15 AM Eastern (5:15 AM UTC)
-- During EST (winter): 12:15 AM Eastern
-- During EDT (summer): 1:15 AM Eastern  
-- This ensures events are refreshed right after midnight for the new day
SELECT cron.schedule(
    'sync-events-nightly',
    '15 5 * * *',  -- 5:15 AM UTC = 12:15 AM EST / 1:15 AM EDT
    $$
    SELECT net.http_post(
        url := 'https://zzlrwpommwkyamkpamze.supabase.co/functions/v1/sync-events',
        headers := '{"Content-Type": "application/json", "Authorization": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inp6bHJ3cG9tbXdreWFta3BhbXplIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc2ODE3NDU3MSwiZXhwIjoyMDgzNzUwNTcxfQ.3VdeNroLpgzTocijWiPns5StX-Y1AneAHxdHVv7AZhY"}'::jsonb,
        body := '{}'::jsonb
    );
    $$
);
