# SpotDay Supabase Backend

This directory contains the Supabase configuration, database migrations, and Edge Functions for SpotDay.

## Setup

### 1. Install Supabase CLI

```bash
# macOS
brew install supabase/tap/supabase

# Linux
curl -sSL https://raw.githubusercontent.com/supabase/cli/main/install.sh | bash

# npm
npm install -g supabase
```

### 2. Login to Supabase

```bash
supabase login
```

### 3. Link to Project

```bash
cd supabase
supabase link --project-ref zzlrwpommwkyamkpamze
```

### 4. Run Database Migrations

```bash
supabase db push
```

Or apply migrations manually in the Supabase Dashboard SQL Editor.

## Edge Functions

### Deploy Functions

```bash
# Deploy all functions
supabase functions deploy

# Deploy individual function
supabase functions deploy sync-events
supabase functions deploy sync-places
supabase functions deploy sync-weather
supabase functions deploy seed-city
```

### Set Environment Variables (Secrets)

```bash
# Ticketmaster API Key
supabase secrets set TICKETMASTER_API_KEY=2U9tGIoEqnJLxyyymKGB4NsQrGG8ekTr

# Google Places API Key (for sync-places function)
supabase secrets set GOOGLE_PLACES_API_KEY=your_google_places_api_key

# OpenWeatherMap API Key (for sync-weather function)
# Get free API key at: https://openweathermap.org/api
supabase secrets set OPENWEATHERMAP_API_KEY=your_openweathermap_api_key

# Anthropic API Key (for seed-city function)
supabase secrets set ANTHROPIC_API_KEY=your_anthropic_api_key

# Supabase service role (auto-set, but can be explicit)
supabase secrets set SUPABASE_URL=https://zzlrwpommwkyamkpamze.supabase.co
supabase secrets set SUPABASE_SERVICE_ROLE_KEY=your_service_role_key
```

### Test Functions Locally

```bash
# Serve functions locally
supabase functions serve

# Test sync-events
curl -X POST http://localhost:54321/functions/v1/sync-events \
  -H "Authorization: Bearer YOUR_ANON_KEY" \
  -H "Content-Type: application/json"

# Test seed-city
curl -X POST http://localhost:54321/functions/v1/seed-city \
  -H "Authorization: Bearer YOUR_SERVICE_ROLE_KEY" \
  -H "Content-Type: application/json" \
  -d '{"city_name": "Portland", "state_code": "OR", "country": "USA"}'
```

### Invoke Deployed Functions

```bash
# Sync events for all active cities
curl -X POST https://zzlrwpommwkyamkpamze.supabase.co/functions/v1/sync-events \
  -H "Authorization: Bearer YOUR_ANON_KEY"

# Sync events for specific city
curl -X POST https://zzlrwpommwkyamkpamze.supabase.co/functions/v1/sync-events \
  -H "Authorization: Bearer YOUR_ANON_KEY" \
  -H "Content-Type: application/json" \
  -d '{"city_id": "san_francisco"}'

# Seed a new city (requires service role key)
curl -X POST https://zzlrwpommwkyamkpamze.supabase.co/functions/v1/seed-city \
  -H "Authorization: Bearer YOUR_SERVICE_ROLE_KEY" \
  -H "Content-Type: application/json" \
  -d '{"city_name": "Austin", "state_code": "TX", "country": "USA"}'

# Sync places for all neighborhoods
curl -X POST https://zzlrwpommwkyamkpamze.supabase.co/functions/v1/sync-places \
  -H "Authorization: Bearer YOUR_ANON_KEY"

# Sync places for specific city
curl -X POST https://zzlrwpommwkyamkpamze.supabase.co/functions/v1/sync-places \
  -H "Authorization: Bearer YOUR_ANON_KEY" \
  -H "Content-Type: application/json" \
  -d '{"city_id": "san_francisco"}'

# Sync places for specific neighborhood
curl -X POST https://zzlrwpommwkyamkpamze.supabase.co/functions/v1/sync-places \
  -H "Authorization: Bearer YOUR_ANON_KEY" \
  -H "Content-Type: application/json" \
  -d '{"city_id": "san_francisco", "neighborhood_id": "mission"}'

# Sync weather for all active cities
curl -X POST https://zzlrwpommwkyamkpamze.supabase.co/functions/v1/sync-weather \
  -H "Authorization: Bearer YOUR_ANON_KEY"

# Sync weather for specific city
curl -X POST https://zzlrwpommwkyamkpamze.supabase.co/functions/v1/sync-weather \
  -H "Authorization: Bearer YOUR_ANON_KEY" \
  -H "Content-Type: application/json" \
  -d '{"city_id": "san_francisco"}'
```

## Cron Scheduling

### Option 1: pg_cron (Database)

Enable pg_cron extension in Supabase Dashboard, then:

```sql
-- Enable pg_cron and pg_net
CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE EXTENSION IF NOT EXISTS pg_net;

-- Schedule sync-events daily at 6 AM PT (14:00 UTC)
SELECT cron.schedule(
  'sync-events-daily',
  '0 14 * * *',
  $$
  SELECT net.http_post(
    'https://zzlrwpommwkyamkpamze.supabase.co/functions/v1/sync-events',
    '{}',
    'application/json'
  );
  $$
);

-- Schedule sync-places monthly on 1st at 3 AM PT (11:00 UTC)
SELECT cron.schedule(
  'sync-places-monthly',
  '0 11 1 * *',
  $$
  SELECT net.http_post(
    'https://zzlrwpommwkyamkpamze.supabase.co/functions/v1/sync-places',
    '{}',
    'application/json'
  );
  $$
);

-- Schedule sync-weather every 3 hours
SELECT cron.schedule(
  'sync-weather-3h',
  '0 */3 * * *',
  $$
  SELECT net.http_post(
    'https://zzlrwpommwkyamkpamze.supabase.co/functions/v1/sync-weather',
    '{}',
    'application/json'
  );
  $$
);
```

### Option 2: External Scheduler

Use services like:
- **Supabase Database Webhooks** (coming soon)
- **GitHub Actions** (scheduled workflows)
- **Vercel Cron**
- **Railway** cron jobs

## Database Schema

- `cities` - City profiles with size/density for neighborhood count
- `neighborhoods` - LLM-seeded or curated neighborhood data
- `cached_events` - Ticketmaster events cache
- `cached_places` - Google Places cache
- `cached_weather` - OpenWeatherMap 5-day forecast cache
- `sync_log` - Sync operation logs

## Adding a New City

1. Call the `seed-city` Edge Function with city details
2. Review the generated neighborhoods in the database
3. Set `is_active = true` to make it available in the app
4. The next `sync-events` run will fetch events for the new city
