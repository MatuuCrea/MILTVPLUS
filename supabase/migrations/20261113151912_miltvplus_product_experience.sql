-- MILTVPLUS catalogue metadata, content-rights publication gates and Auth profile bootstrap.
-- Additive migration; no content is fabricated or automatically published.

ALTER TABLE public.catalog_items
  ADD COLUMN IF NOT EXISTS slug text,
  ADD COLUMN IF NOT EXISTS genre text,
  ADD COLUMN IF NOT EXISTS genres text[] NOT NULL DEFAULT '{}',
  ADD COLUMN IF NOT EXISTS release_year integer,
  ADD COLUMN IF NOT EXISTS duration_seconds integer,
  ADD COLUMN IF NOT EXISTS age_rating text,
  ADD COLUMN IF NOT EXISTS audio_languages text[] NOT NULL DEFAULT '{}',
  ADD COLUMN IF NOT EXISTS subtitle_languages text[] NOT NULL DEFAULT '{}',
  ADD COLUMN IF NOT EXISTS backdrop_url text,
  ADD COLUMN IF NOT EXISTS logo_url text,
  ADD COLUMN IF NOT EXISTS series_id uuid REFERENCES public.catalog_items(id) ON DELETE CASCADE,
  ADD COLUMN IF NOT EXISTS season_number integer,
  ADD COLUMN IF NOT EXISTS episode_number integer,
  ADD COLUMN IF NOT EXISTS parent_id uuid REFERENCES public.catalog_items(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS provider_id text,
  ADD COLUMN IF NOT EXISTS license_region char(2),
  ADD COLUMN IF NOT EXISTS available_from timestamptz,
  ADD COLUMN IF NOT EXISTS available_until timestamptz,
  ADD COLUMN IF NOT EXISTS is_featured boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS popularity_score integer NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS rights_holder text,
  ADD COLUMN IF NOT EXISTS rights_reference text,
  ADD COLUMN IF NOT EXISTS rights_regions text[] NOT NULL DEFAULT '{}',
  ADD COLUMN IF NOT EXISTS rights_valid_from timestamptz,
  ADD COLUMN IF NOT EXISTS rights_valid_until timestamptz,
  ADD COLUMN IF NOT EXISTS rights_verified boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS source_url text,
  ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

ALTER TABLE public.catalog_items DROP CONSTRAINT IF EXISTS catalog_items_kind_check;
ALTER TABLE public.catalog_items DROP CONSTRAINT IF EXISTS catalog_items_episode_parent_check;
ALTER TABLE public.catalog_items DROP CONSTRAINT IF EXISTS catalog_items_release_year_check;
ALTER TABLE public.catalog_items DROP CONSTRAINT IF EXISTS catalog_items_duration_check;
ALTER TABLE public.catalog_items DROP CONSTRAINT IF EXISTS catalog_items_availability_check;
ALTER TABLE public.catalog_items DROP CONSTRAINT IF EXISTS catalog_items_published_rights_check;
ALTER TABLE public.radio_stations DROP CONSTRAINT IF EXISTS radio_stations_published_rights_check;
ALTER TABLE public.sports_events DROP CONSTRAINT IF EXISTS sports_events_published_rights_check;
ALTER TABLE public.music_items DROP CONSTRAINT IF EXISTS music_items_published_rights_check;
ALTER TABLE public.theater_events DROP CONSTRAINT IF EXISTS theater_events_published_rights_check;
ALTER TABLE public.epg_programs DROP CONSTRAINT IF EXISTS epg_programs_time_range_check;
ALTER TABLE public.epg_programs DROP CONSTRAINT IF EXISTS epg_programs_published_rights_check;

ALTER TABLE public.catalog_items ADD CONSTRAINT catalog_items_kind_check CHECK (kind IN ('live','movie','series','episode'));
ALTER TABLE public.catalog_items ADD CONSTRAINT catalog_items_episode_parent_check
  CHECK (kind <> 'episode' OR (series_id IS NOT NULL AND season_number > 0 AND episode_number > 0));
ALTER TABLE public.catalog_items ADD CONSTRAINT catalog_items_release_year_check
  CHECK (release_year IS NULL OR release_year BETWEEN 1888 AND 2200);
ALTER TABLE public.catalog_items ADD CONSTRAINT catalog_items_duration_check
  CHECK (duration_seconds IS NULL OR duration_seconds > 0);
ALTER TABLE public.catalog_items ADD CONSTRAINT catalog_items_availability_check
  CHECK (available_until IS NULL OR available_from IS NULL OR available_until > available_from);
ALTER TABLE public.catalog_items ADD CONSTRAINT catalog_items_published_rights_check
  CHECK (NOT is_published OR (rights_verified AND rights_holder IS NOT NULL AND rights_reference IS NOT NULL AND cardinality(rights_regions) > 0));

ALTER TABLE public.radio_stations
  ADD COLUMN IF NOT EXISTS description text,
  ADD COLUMN IF NOT EXISTS website_url text,
  ADD COLUMN IF NOT EXISTS rights_holder text,
  ADD COLUMN IF NOT EXISTS rights_reference text,
  ADD COLUMN IF NOT EXISTS rights_regions text[] NOT NULL DEFAULT '{}',
  ADD COLUMN IF NOT EXISTS rights_valid_from timestamptz,
  ADD COLUMN IF NOT EXISTS rights_valid_until timestamptz,
  ADD COLUMN IF NOT EXISTS rights_verified boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS source_url text,
  ADD COLUMN IF NOT EXISTS is_featured boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE public.radio_stations ADD CONSTRAINT radio_stations_published_rights_check
  CHECK (NOT is_published OR (rights_verified AND rights_holder IS NOT NULL AND rights_reference IS NOT NULL AND cardinality(rights_regions) > 0));

ALTER TABLE public.sports_events
  ADD COLUMN IF NOT EXISTS description text,
  ADD COLUMN IF NOT EXISTS poster_url text,
  ADD COLUMN IF NOT EXISTS provider_id text,
  ADD COLUMN IF NOT EXISTS rights_holder text,
  ADD COLUMN IF NOT EXISTS rights_reference text,
  ADD COLUMN IF NOT EXISTS rights_regions text[] NOT NULL DEFAULT '{}',
  ADD COLUMN IF NOT EXISTS rights_valid_from timestamptz,
  ADD COLUMN IF NOT EXISTS rights_valid_until timestamptz,
  ADD COLUMN IF NOT EXISTS rights_verified boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS source_url text,
  ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE public.sports_events ADD CONSTRAINT sports_events_published_rights_check
  CHECK (NOT is_published OR (rights_verified AND rights_holder IS NOT NULL AND rights_reference IS NOT NULL AND cardinality(rights_regions) > 0));

ALTER TABLE public.music_items
  ADD COLUMN IF NOT EXISTS album text,
  ADD COLUMN IF NOT EXISTS genre text,
  ADD COLUMN IF NOT EXISTS release_year integer,
  ADD COLUMN IF NOT EXISTS language_code text,
  ADD COLUMN IF NOT EXISTS provider_id text,
  ADD COLUMN IF NOT EXISTS rights_holder text,
  ADD COLUMN IF NOT EXISTS rights_reference text,
  ADD COLUMN IF NOT EXISTS rights_regions text[] NOT NULL DEFAULT '{}',
  ADD COLUMN IF NOT EXISTS rights_valid_from timestamptz,
  ADD COLUMN IF NOT EXISTS rights_valid_until timestamptz,
  ADD COLUMN IF NOT EXISTS rights_verified boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS source_url text,
  ADD COLUMN IF NOT EXISTS is_featured boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE public.music_items ADD CONSTRAINT music_items_published_rights_check
  CHECK (NOT is_published OR (rights_verified AND rights_holder IS NOT NULL AND rights_reference IS NOT NULL AND cardinality(rights_regions) > 0));

ALTER TABLE public.theater_events
  ADD COLUMN IF NOT EXISTS description text,
  ADD COLUMN IF NOT EXISTS genre text,
  ADD COLUMN IF NOT EXISTS poster_url text,
  ADD COLUMN IF NOT EXISTS website_url text,
  ADD COLUMN IF NOT EXISTS provider_id text,
  ADD COLUMN IF NOT EXISTS rights_holder text,
  ADD COLUMN IF NOT EXISTS rights_reference text,
  ADD COLUMN IF NOT EXISTS rights_regions text[] NOT NULL DEFAULT '{}',
  ADD COLUMN IF NOT EXISTS rights_valid_from timestamptz,
  ADD COLUMN IF NOT EXISTS rights_valid_until timestamptz,
  ADD COLUMN IF NOT EXISTS rights_verified boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS source_url text,
  ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE public.theater_events ADD CONSTRAINT theater_events_published_rights_check
  CHECK (NOT is_published OR (rights_verified AND rights_holder IS NOT NULL AND rights_reference IS NOT NULL AND cardinality(rights_regions) > 0));

ALTER TABLE public.epg_programs
  ADD COLUMN IF NOT EXISTS category text,
  ADD COLUMN IF NOT EXISTS language_code text,
  ADD COLUMN IF NOT EXISTS age_rating text,
  ADD COLUMN IF NOT EXISTS rights_holder text,
  ADD COLUMN IF NOT EXISTS rights_reference text,
  ADD COLUMN IF NOT EXISTS rights_regions text[] NOT NULL DEFAULT '{}',
  ADD COLUMN IF NOT EXISTS rights_valid_from timestamptz,
  ADD COLUMN IF NOT EXISTS rights_valid_until timestamptz,
  ADD COLUMN IF NOT EXISTS rights_verified boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE public.epg_programs ADD CONSTRAINT epg_programs_time_range_check CHECK (ends_at > starts_at);
ALTER TABLE public.epg_programs ADD CONSTRAINT epg_programs_published_rights_check
  CHECK (NOT is_published OR (rights_verified AND rights_holder IS NOT NULL AND rights_reference IS NOT NULL AND cardinality(rights_regions) > 0));

ALTER TABLE public.user_preferences
  ADD COLUMN IF NOT EXISTS accent_color text NOT NULL DEFAULT 'electric-blue',
  ADD COLUMN IF NOT EXISTS notifications_enabled boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS autoplay_enabled boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS preferred_audio_language text,
  ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

ALTER TABLE public.media_sources ALTER COLUMN owner_id SET NOT NULL;
ALTER TABLE public.media_sources ALTER COLUMN is_enabled SET DEFAULT false;
ALTER TABLE public.media_sources ALTER COLUMN is_verified SET DEFAULT false;

CREATE INDEX IF NOT EXISTS catalog_items_featured_idx ON public.catalog_items(is_featured, popularity_score DESC) WHERE is_published = true;
CREATE INDEX IF NOT EXISTS catalog_items_country_kind_idx ON public.catalog_items(country_code, kind, created_at DESC) WHERE is_published = true;
CREATE INDEX IF NOT EXISTS catalog_items_series_episode_idx ON public.catalog_items(series_id, season_number, episode_number) WHERE kind = 'episode';
CREATE INDEX IF NOT EXISTS epg_channel_schedule_idx ON public.epg_programs(channel_external_id, starts_at) WHERE is_published = true;
CREATE INDEX IF NOT EXISTS radio_country_genre_idx ON public.radio_stations(country_code, genre) WHERE is_published = true;
CREATE INDEX IF NOT EXISTS sports_schedule_idx ON public.sports_events(starts_at, sport) WHERE is_published = true;

CREATE SCHEMA IF NOT EXISTS app_private;
REVOKE ALL ON SCHEMA app_private FROM PUBLIC, anon, authenticated;
CREATE OR REPLACE FUNCTION app_private.handle_new_miltvplus_user()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
BEGIN
  INSERT INTO public.profiles(id, display_name)
  VALUES (NEW.id, COALESCE(NULLIF(NEW.raw_user_meta_data ->> 'name', ''), split_part(NEW.email, '@', 1)))
  ON CONFLICT (id) DO NOTHING;
  RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION app_private.handle_new_miltvplus_user() FROM PUBLIC, anon, authenticated;
DROP TRIGGER IF EXISTS on_auth_user_created_miltvplus ON auth.users;
CREATE TRIGGER on_auth_user_created_miltvplus AFTER INSERT ON auth.users
FOR EACH ROW EXECUTE FUNCTION app_private.handle_new_miltvplus_user();
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM anon;
