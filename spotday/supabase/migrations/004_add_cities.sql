-- Migration: 003_add_cities
-- Description: Add Phoenix, Charlotte, and Tucson to the cities table

-- Add Phoenix
INSERT INTO cities (id, name, country, state_code, size, density, center_lat, center_lng, estimated_areas, data_source, is_active)
VALUES ('phoenix', 'Phoenix', 'USA', 'AZ', 'large', 'spread_out', 33.4484, -112.0740, 8, 'LLM', true)
ON CONFLICT (id) DO NOTHING;

-- Add Charlotte
INSERT INTO cities (id, name, country, state_code, size, density, center_lat, center_lng, estimated_areas, data_source, is_active)
VALUES ('charlotte', 'Charlotte', 'USA', 'NC', 'large', 'moderate', 35.2271, -80.8431, 8, 'LLM', true)
ON CONFLICT (id) DO NOTHING;

-- Add Tucson
INSERT INTO cities (id, name, country, state_code, size, density, center_lat, center_lng, estimated_areas, data_source, is_active)
VALUES ('tucson', 'Tucson', 'USA', 'AZ', 'medium', 'spread_out', 32.2226, -110.9747, 6, 'LLM', true)
ON CONFLICT (id) DO NOTHING;
