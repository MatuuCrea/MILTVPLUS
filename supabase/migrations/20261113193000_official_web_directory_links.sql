-- Separate official-site directory links from embedded/retransmitted streams.
-- Opening a broadcaster's own page does not grant MILTVPLUS rights to redistribute its stream.
ALTER TABLE public.tv_channels
  DROP CONSTRAINT IF EXISTS tv_channels_published_requirements;

ALTER TABLE public.tv_channels
  ADD CONSTRAINT tv_channels_published_requirements CHECK (
    NOT is_published OR (
      official_watch_url IS NOT NULL
      AND source_status = 'online'
      AND (
        (playback_type = 'official_web' AND stream_url IS NULL)
        OR
        (
          playback_type IN ('hls','dash','youtube_live')
          AND stream_url IS NOT NULL
          AND rights_verified
          AND rights_holder IS NOT NULL
          AND rights_reference IS NOT NULL
          AND cardinality(rights_regions) > 0
          AND rights_valid_from IS NOT NULL
        )
      )
    )
  );

CREATE OR REPLACE FUNCTION app_private.enforce_tv_channel_publication()
RETURNS trigger LANGUAGE plpgsql SET search_path = ''
AS $$
BEGIN
  NEW.updated_at := now();
  IF NEW.is_published THEN
    IF NEW.official_watch_url IS NULL OR NEW.source_status <> 'online' THEN
      RAISE EXCEPTION 'Publication requires a checked official viewing page and an online source status';
    END IF;

    IF NEW.playback_type = 'official_web' THEN
      IF NEW.stream_url IS NOT NULL THEN
        RAISE EXCEPTION 'Official-web directory entries must not contain an embedded stream URL';
      END IF;
    ELSE
      IF NEW.stream_url IS NULL OR NOT NEW.rights_verified
         OR NEW.rights_holder IS NULL OR NEW.rights_reference IS NULL
         OR cardinality(NEW.rights_regions) = 0 OR NEW.rights_valid_from IS NULL
         OR NEW.rights_valid_from > now()
         OR (NEW.rights_valid_until IS NOT NULL AND NEW.rights_valid_until <= now()) THEN
        RAISE EXCEPTION 'Embedded streams require active, verified redistribution rights for the requested territories';
      END IF;
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

COMMENT ON COLUMN public.tv_channels.playback_type IS
  'official_web opens the broadcaster website only; hls/dash/youtube_live require documented redistribution rights before publication.';
