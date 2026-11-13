-- A user can submit/delete their own source, never approve or activate it.
REVOKE UPDATE ON TABLE public.media_sources FROM authenticated;
GRANT SELECT, INSERT, DELETE ON TABLE public.media_sources TO authenticated;

CREATE OR REPLACE FUNCTION app_private.guard_media_source_approval()
RETURNS trigger LANGUAGE plpgsql SET search_path = ''
AS $$
BEGIN
  IF auth.role() = 'authenticated' THEN
    IF TG_OP = 'INSERT' THEN
      NEW.is_enabled := false;
      NEW.is_verified := false;
    ELSE
      IF NEW.is_enabled IS DISTINCT FROM OLD.is_enabled
         OR NEW.is_verified IS DISTINCT FROM OLD.is_verified THEN
        RAISE EXCEPTION 'Only an administrator may verify or enable a media source';
      END IF;
    END IF;
  END IF;
  RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION app_private.guard_media_source_approval() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS media_sources_admin_approval_only ON public.media_sources;
CREATE TRIGGER media_sources_admin_approval_only
BEFORE INSERT OR UPDATE ON public.media_sources
FOR EACH ROW EXECUTE FUNCTION app_private.guard_media_source_approval();
