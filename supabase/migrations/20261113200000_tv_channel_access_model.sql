-- Distinguish terrestrial free-to-air from free official web streams and paywalled access.
ALTER TABLE public.tv_channels
  ADD COLUMN IF NOT EXISTS access_model text NOT NULL DEFAULT 'unknown'
  CHECK (access_model IN ('free_web','free_to_air','free_with_ads','subscription','geo_restricted','unknown'));

COMMENT ON COLUMN public.tv_channels.access_model IS
  'Access model for the official viewing route. free_to_air means terrestrial FTA; free_web means publicly accessible official web viewing and does not imply worldwide availability.';
