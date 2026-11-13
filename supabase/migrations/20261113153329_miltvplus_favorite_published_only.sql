-- Favorites may reference only catalog rows visible to the authenticated client.
DROP POLICY IF EXISTS "favorites owner inserts" ON public.user_favorites;
CREATE POLICY "favorites owner inserts"
ON public.user_favorites
FOR INSERT
TO authenticated
WITH CHECK (
  auth.uid() = user_id
  AND EXISTS (
    SELECT 1
    FROM public.catalog_items AS item
    WHERE item.id = catalog_item_id
      AND item.is_published = true
  )
);
