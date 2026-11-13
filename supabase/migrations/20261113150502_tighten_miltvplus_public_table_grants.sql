-- Least-privilege grants for MILTVPLUS public schema.
-- RLS remains enabled and is still the row-level authorization boundary.
REVOKE ALL PRIVILEGES ON TABLE
  public.catalog_items,
  public.profiles,
  public.user_favorites,
  public.media_sources,
  public.epg_programs,
  public.radio_stations,
  public.sports_events,
  public.music_items,
  public.theater_events,
  public.user_preferences,
  public.age_access_consents
FROM anon, authenticated;

-- Public clients can only read explicitly published catalog rows.
GRANT SELECT ON TABLE
  public.catalog_items,
  public.epg_programs,
  public.radio_stations,
  public.sports_events,
  public.music_items,
  public.theater_events
TO anon, authenticated;

-- Authenticated users can access only their own rows, enforced by existing RLS policies.
GRANT SELECT, INSERT, UPDATE ON TABLE public.profiles TO authenticated;
GRANT SELECT, INSERT, DELETE ON TABLE public.user_favorites TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
  public.media_sources,
  public.user_preferences,
  public.age_access_consents
TO authenticated;

-- Prevent future public tables from inheriting broad anonymous grants for this owner.
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM anon;
