-- Adult catalogue segregation: a self-attested 18+ consent is required at the database layer.
-- This is an access gate, not identity or legal-age verification.
ALTER TABLE public.catalog_items
  ADD COLUMN IF NOT EXISTS is_adult_content boolean NOT NULL DEFAULT false;

DROP POLICY IF EXISTS "published catalog readable" ON public.catalog_items;
CREATE POLICY "published catalog readable"
  ON public.catalog_items FOR SELECT TO anon, authenticated
  USING (
    is_published = true
    AND (
      is_adult_content = false
      OR (
        auth.uid() IS NOT NULL
        AND EXISTS (
          SELECT 1
          FROM public.age_access_consents consent
          WHERE consent.user_id = (SELECT auth.uid())
            AND consent.confirmed_18_plus = true
            AND consent.confirmed_at IS NOT NULL
        )
      )
    )
  );

CREATE INDEX IF NOT EXISTS catalog_adult_published_idx
  ON public.catalog_items (is_adult_content, kind, country_code)
  WHERE is_published = true;

COMMENT ON COLUMN public.catalog_items.is_adult_content IS
  'Explicit adult classification. RLS only exposes published adult rows to authenticated users with a recorded 18+ self-attestation; this is not formal age verification.';
