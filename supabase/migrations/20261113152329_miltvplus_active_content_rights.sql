-- Publishing is blocked unless documented distribution rights are currently active.
ALTER TABLE public.catalog_items DROP CONSTRAINT IF EXISTS catalog_items_episode_parent_check;
ALTER TABLE public.catalog_items ADD CONSTRAINT catalog_items_episode_parent_check
  CHECK (kind <> 'episode' OR (
    series_id IS NOT NULL AND season_number IS NOT NULL AND season_number > 0
    AND episode_number IS NOT NULL AND episode_number > 0
  ));

ALTER TABLE public.catalog_items DROP CONSTRAINT IF EXISTS catalog_items_published_rights_check;
ALTER TABLE public.catalog_items ADD CONSTRAINT catalog_items_published_rights_check
  CHECK (NOT is_published OR (rights_verified AND rights_holder IS NOT NULL AND rights_reference IS NOT NULL AND cardinality(rights_regions) > 0 AND rights_valid_from IS NOT NULL));
ALTER TABLE public.radio_stations DROP CONSTRAINT IF EXISTS radio_stations_published_rights_check;
ALTER TABLE public.radio_stations ADD CONSTRAINT radio_stations_published_rights_check
  CHECK (NOT is_published OR (rights_verified AND rights_holder IS NOT NULL AND rights_reference IS NOT NULL AND cardinality(rights_regions) > 0 AND rights_valid_from IS NOT NULL));
ALTER TABLE public.sports_events DROP CONSTRAINT IF EXISTS sports_events_published_rights_check;
ALTER TABLE public.sports_events ADD CONSTRAINT sports_events_published_rights_check
  CHECK (NOT is_published OR (rights_verified AND rights_holder IS NOT NULL AND rights_reference IS NOT NULL AND cardinality(rights_regions) > 0 AND rights_valid_from IS NOT NULL));
ALTER TABLE public.music_items DROP CONSTRAINT IF EXISTS music_items_published_rights_check;
ALTER TABLE public.music_items ADD CONSTRAINT music_items_published_rights_check
  CHECK (NOT is_published OR (rights_verified AND rights_holder IS NOT NULL AND rights_reference IS NOT NULL AND cardinality(rights_regions) > 0 AND rights_valid_from IS NOT NULL));
ALTER TABLE public.theater_events DROP CONSTRAINT IF EXISTS theater_events_published_rights_check;
ALTER TABLE public.theater_events ADD CONSTRAINT theater_events_published_rights_check
  CHECK (NOT is_published OR (rights_verified AND rights_holder IS NOT NULL AND rights_reference IS NOT NULL AND cardinality(rights_regions) > 0 AND rights_valid_from IS NOT NULL));
ALTER TABLE public.epg_programs DROP CONSTRAINT IF EXISTS epg_programs_published_rights_check;
ALTER TABLE public.epg_programs ADD CONSTRAINT epg_programs_published_rights_check
  CHECK (NOT is_published OR (rights_verified AND rights_holder IS NOT NULL AND rights_reference IS NOT NULL AND cardinality(rights_regions) > 0 AND rights_valid_from IS NOT NULL));

CREATE OR REPLACE FUNCTION app_private.enforce_active_content_rights()
RETURNS trigger LANGUAGE plpgsql SET search_path = ''
AS $$
BEGIN
  IF NEW.is_published THEN
    IF NOT NEW.rights_verified OR NEW.rights_holder IS NULL OR NEW.rights_reference IS NULL
       OR cardinality(NEW.rights_regions) = 0 OR NEW.rights_valid_from IS NULL THEN
      RAISE EXCEPTION 'Publishing requires verified rights holder, reference, regions and start date';
    END IF;
    IF NEW.rights_valid_from > now() THEN
      RAISE EXCEPTION 'Distribution rights are not active yet';
    END IF;
    IF NEW.rights_valid_until IS NOT NULL AND NEW.rights_valid_until <= now() THEN
      RAISE EXCEPTION 'Distribution rights have expired';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION app_private.enforce_active_content_rights() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS catalog_items_rights_window ON public.catalog_items;
CREATE TRIGGER catalog_items_rights_window BEFORE INSERT OR UPDATE ON public.catalog_items FOR EACH ROW EXECUTE FUNCTION app_private.enforce_active_content_rights();
DROP TRIGGER IF EXISTS radio_stations_rights_window ON public.radio_stations;
CREATE TRIGGER radio_stations_rights_window BEFORE INSERT OR UPDATE ON public.radio_stations FOR EACH ROW EXECUTE FUNCTION app_private.enforce_active_content_rights();
DROP TRIGGER IF EXISTS sports_events_rights_window ON public.sports_events;
CREATE TRIGGER sports_events_rights_window BEFORE INSERT OR UPDATE ON public.sports_events FOR EACH ROW EXECUTE FUNCTION app_private.enforce_active_content_rights();
DROP TRIGGER IF EXISTS music_items_rights_window ON public.music_items;
CREATE TRIGGER music_items_rights_window BEFORE INSERT OR UPDATE ON public.music_items FOR EACH ROW EXECUTE FUNCTION app_private.enforce_active_content_rights();
DROP TRIGGER IF EXISTS theater_events_rights_window ON public.theater_events;
CREATE TRIGGER theater_events_rights_window BEFORE INSERT OR UPDATE ON public.theater_events FOR EACH ROW EXECUTE FUNCTION app_private.enforce_active_content_rights();
DROP TRIGGER IF EXISTS epg_programs_rights_window ON public.epg_programs;
CREATE TRIGGER epg_programs_rights_window BEFORE INSERT OR UPDATE ON public.epg_programs FOR EACH ROW EXECUTE FUNCTION app_private.enforce_active_content_rights();
