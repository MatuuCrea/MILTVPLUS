create extension if not exists pgcrypto;

create table public.catalog_items (
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(title) between 1 and 180),
  description text,
  kind text not null check (kind in ('live','movie','series')),
  country_code char(2),
  language_code text,
  poster_url text check (poster_url is null or poster_url like 'https://%'),
  stream_url text check (stream_url is null or stream_url like 'https://%'),
  is_published boolean not null default false,
  created_at timestamptz not null default now()
);

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text check (display_name is null or char_length(display_name) <= 60),
  created_at timestamptz not null default now()
);

create table public.user_favorites (
  user_id uuid not null references auth.users(id) on delete cascade,
  catalog_item_id uuid not null references public.catalog_items(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, catalog_item_id)
);

alter table public.catalog_items enable row level security;
alter table public.profiles enable row level security;
alter table public.user_favorites enable row level security;

grant usage on schema public to anon, authenticated;
grant select on public.catalog_items to anon, authenticated;
grant select, insert, update on public.profiles to authenticated;
grant select, insert, delete on public.user_favorites to authenticated;

create policy "published catalog readable"
  on public.catalog_items for select to anon, authenticated
  using (is_published = true);

create policy "profile owner reads"
  on public.profiles for select to authenticated
  using ((select auth.uid()) = id);

create policy "profile owner inserts"
  on public.profiles for insert to authenticated
  with check ((select auth.uid()) = id);

create policy "profile owner updates"
  on public.profiles for update to authenticated
  using ((select auth.uid()) = id)
  with check ((select auth.uid()) = id);

create policy "favorites owner reads"
  on public.user_favorites for select to authenticated
  using ((select auth.uid()) = user_id);

create policy "favorites owner inserts"
  on public.user_favorites for insert to authenticated
  with check ((select auth.uid()) = user_id);

create policy "favorites owner deletes"
  on public.user_favorites for delete to authenticated
  using ((select auth.uid()) = user_id);

create index catalog_kind_published_idx on public.catalog_items(kind, is_published);
create index user_favorites_catalog_item_idx on public.user_favorites(catalog_item_id);
