-- Global free-to-air channel directory. A free/public signal is not automatically licensed
-- for redistribution; publication requires a documented, active grant from the rights holder.
CREATE TABLE IF NOT EXISTS public.tv_channels (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  slug text NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,79}$'),
  title text NOT NULL CHECK (char_length(title) BETWEEN 1 AND 180),
  description text,
  country_code char(2) NOT NULL CHECK (country_code ~ '^[A-Z]{2}$'),
  language_code text,
  category text NOT NULL CHECK (category IN ('news','general','public','sports','education','culture','music','religion','regional','international','other')),
  is_free_to_air boolean NOT NULL DEFAULT false,
  logo_url text CHECK (logo_url IS NULL OR logo_url ~ '^https://'),
  logo_source_url text CHECK (logo_source_url IS NULL OR logo_source_url ~ '^https://'),
  official_watch_url text CHECK (official_watch_url IS NULL OR official_watch_url ~ '^https://'),
  stream_url text CHECK (stream_url IS NULL OR stream_url ~ '^https://'),
  playback_type text NOT NULL DEFAULT 'official_web' CHECK (playback_type IN ('hls','dash','youtube_live','official_web')),
  source_status text NOT NULL DEFAULT 'unverified' CHECK (source_status IN ('unverified','pending_review','online','offline','blocked','geo_restricted')),
  last_checked_at timestamptz,
  last_check_detail text,
  rights_holder text,
  rights_reference text,
  rights_regions text[] NOT NULL DEFAULT '{}',
  rights_valid_from timestamptz,
  rights_valid_until timestamptz,
  rights_verified boolean NOT NULL DEFAULT false,
  is_published boolean NOT NULL DEFAULT false,
  sort_order integer NOT NULL DEFAULT 1000,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT tv_channels_published_requirements CHECK (
    NOT is_published OR (
      rights_verified AND rights_holder IS NOT NULL AND rights_reference IS NOT NULL
      AND cardinality(rights_regions) > 0 AND rights_valid_from IS NOT NULL
      AND logo_url IS NOT NULL AND official_watch_url IS NOT NULL
      AND source_status = 'online'
    )
  )
);

ALTER TABLE public.tv_channels ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.tv_channels FROM anon, authenticated;
GRANT SELECT ON public.tv_channels TO anon, authenticated;
DROP POLICY IF EXISTS "published tv channels readable" ON public.tv_channels;
CREATE POLICY "published tv channels readable" ON public.tv_channels
  FOR SELECT TO anon, authenticated USING (is_published = true);

CREATE INDEX IF NOT EXISTS tv_channels_country_category_order_idx
  ON public.tv_channels(country_code, category, sort_order, title) WHERE is_published;
CREATE INDEX IF NOT EXISTS tv_channels_slug_idx ON public.tv_channels(slug);

CREATE OR REPLACE FUNCTION app_private.enforce_tv_channel_publication()
RETURNS trigger LANGUAGE plpgsql SET search_path = ''
AS $$
BEGIN
  NEW.updated_at := now();
  IF NEW.is_published THEN
    IF NOT NEW.rights_verified OR NEW.rights_holder IS NULL OR NEW.rights_reference IS NULL
       OR cardinality(NEW.rights_regions) = 0 OR NEW.rights_valid_from IS NULL
       OR NEW.rights_valid_from > now()
       OR (NEW.rights_valid_until IS NOT NULL AND NEW.rights_valid_until <= now()) THEN
      RAISE EXCEPTION 'TV channel publication requires active, verified rights for the requested territories';
    END IF;
    IF NEW.logo_url IS NULL OR NEW.official_watch_url IS NULL OR NEW.source_status <> 'online' THEN
      RAISE EXCEPTION 'TV channel publication requires an actual logo, official viewing page and online source check';
    END IF;
    IF NEW.playback_type IN ('hls','dash') AND NEW.stream_url IS NULL THEN
      RAISE EXCEPTION 'Direct playback requires a verified HTTPS stream URL';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION app_private.enforce_tv_channel_publication() FROM PUBLIC, anon, authenticated;
DROP TRIGGER IF EXISTS tv_channels_publication_guard ON public.tv_channels;
CREATE TRIGGER tv_channels_publication_guard
  BEFORE INSERT OR UPDATE ON public.tv_channels
  FOR EACH ROW EXECUTE FUNCTION app_private.enforce_tv_channel_publication();
