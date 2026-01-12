-- Migration: 006_setup_cron_jobs
-- Description: Set up cron jobs for nightly data sync

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE EXTENSION IF NOT EXISTS pg_net;

-- Remove any existing schedules to avoid duplicates (ignore errors if they don't exist)
DO $$
BEGIN
    PERFORM cron.unschedule('sync-events-nightly');
EXCEPTION WHEN OTHERS THEN
    -- Job doesn't exist, ignore
END;
$$;

DO $$
BEGIN
    PERFORM cron.unschedule('sync-weather-periodic');
EXCEPTION WHEN OTHERS THEN
    -- Job doesn't exist, ignore
END;
$$;

DO $$
BEGIN
    PERFORM cron.unschedule('sync-places-monthly');
EXCEPTION WHEN OTHERS THEN
    -- Job doesn't exist, ignore
END;
$$;

-- Schedule sync-events nightly at 11 PM PT (7:00 UTC next day)
-- This ensures events are fresh before the next day begins
SELECT cron.schedule(
    'sync-events-nightly',
    '0 7 * * *',  -- 7:00 UTC = 11:00 PM PT (previous day)
    $$
    SELECT net.http_post(
        url := 'https://zzlrwpommwkyamkpamze.supabase.co/functions/v1/sync-events',
        headers := '{"Content-Type": "application/json", "Authorization": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inp6bHJ3cG9tbXdreWFta3BhbXplIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc2ODE3NDU3MSwiZXhwIjoyMDgzNzUwNTcxfQ.3VdeNroLpgzTocijWiPns5StX-Y1AneAHxdHVv7AZhY"}'::jsonb,
        body := '{}'::jsonb
    );
    $$
);

-- Schedule sync-weather every 3 hours for fresh forecasts
SELECT cron.schedule(
    'sync-weather-periodic',
    '0 */3 * * *',  -- Every 3 hours
    $$
    SELECT net.http_post(
        url := 'https://zzlrwpommwkyamkpamze.supabase.co/functions/v1/sync-weather',
        headers := '{"Content-Type": "application/json", "Authorization": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inp6bHJ3cG9tbXdreWFta3BhbXplIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc2ODE3NDU3MSwiZXhwIjoyMDgzNzUwNTcxfQ.3VdeNroLpgzTocijWiPns5StX-Y1AneAHxdHVv7AZhY"}'::jsonb,
        body := '{}'::jsonb
    );
    $$
);

-- Schedule sync-places monthly on the 1st at 2 AM PT (10:00 UTC)
SELECT cron.schedule(
    'sync-places-monthly',
    '0 10 1 * *',  -- 1st of each month at 10:00 UTC = 2:00 AM PT
    $$
    SELECT net.http_post(
        url := 'https://zzlrwpommwkyamkpamze.supabase.co/functions/v1/sync-places',
        headers := '{"Content-Type": "application/json", "Authorization": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inp6bHJ3cG9tbXdreWFta3BhbXplIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc2ODE3NDU3MSwiZXhwIjoyMDgzNzUwNTcxfQ.3VdeNroLpgzTocijWiPns5StX-Y1AneAHxdHVv7AZhY"}'::jsonb,
        body := '{}'::jsonb
    );
    $$
);

-- View scheduled jobs
-- SELECT * FROM cron.job;
