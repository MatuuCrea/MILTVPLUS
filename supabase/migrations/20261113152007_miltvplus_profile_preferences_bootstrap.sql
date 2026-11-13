-- Create one preferences row for every newly registered account and index series parent links.
CREATE INDEX IF NOT EXISTS catalog_items_parent_id_idx ON public.catalog_items(parent_id);

CREATE OR REPLACE FUNCTION app_private.handle_new_miltvplus_user()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
BEGIN
  INSERT INTO public.profiles(id, display_name)
  VALUES (NEW.id, COALESCE(NULLIF(NEW.raw_user_meta_data ->> 'name', ''), split_part(NEW.email, '@', 1)))
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.user_preferences(user_id)
  VALUES (NEW.id)
  ON CONFLICT (user_id) DO NOTHING;

  RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION app_private.handle_new_miltvplus_user() FROM PUBLIC, anon, authenticated;
