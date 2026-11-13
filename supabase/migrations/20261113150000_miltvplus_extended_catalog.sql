-- MILTVPLUS extended catalog and user settings schema.
-- All exposed tables use RLS; no demo content or unlicensed streams are seeded.
create table if not exists public.media_sources (
 id uuid primary key default gen_random_uuid(),
 owner_id uuid references auth.users(id) on delete cascade,
 name text not null check (char_length(name) between 1 and 120),
 source_type text not null check (source_type in ('m3u','m3u8','api','manual')),
 source_url text check (source_url is null or source_url like 'https://%'),
 country_code char(2),
 is_enabled boolean not null default false,
 is_verified boolean not null default false,
 created_at timestamptz not null default now()
);
create table if not exists public.epg_programs (
 id uuid primary key default gen_random_uuid(),
 channel_external_id text not null,
 title text not null,
 description text,
 starts_at timestamptz not null,
 ends_at timestamptz not null,
 country_code char(2),
 source_id uuid references public.media_sources(id) on delete set null,
 is_published boolean not null default false,
 created_at timestamptz not null default now(),
 check (ends_at > starts_at)
);
alter table public.epg_programs add column if not exists is_published boolean not null default false;
create table if not exists public.radio_stations (
 id uuid primary key default gen_random_uuid(),
 title text not null,
 stream_url text not null check (stream_url like 'https://%'),
 country_code char(2),
 language_code text,
 genre text,
 logo_url text check (logo_url is null or logo_url like 'https://%'),
 is_published boolean not null default false,
 created_at timestamptz not null default now()
);
create table if not exists public.sports_events (
 id uuid primary key default gen_random_uuid(),
 title text not null,
 sport text not null,
 competition text,
 country_code char(2),
 starts_at timestamptz not null,
 ends_at timestamptz,
 official_info_url text check (official_info_url is null or official_info_url like 'https://%'),
 authorized_stream_url text check (authorized_stream_url is null or authorized_stream_url like 'https://%'),
 is_published boolean not null default false,
 created_at timestamptz not null default now()
);
create table if not exists public.music_items (
 id uuid primary key default gen_random_uuid(),
 item_type text not null check (item_type in ('artist','album','playlist','track')),
 title text not null,
 creator text,
 country_code char(2),
 external_url text check (external_url is null or external_url like 'https://%'),
 artwork_url text check (artwork_url is null or artwork_url like 'https://%'),
 is_published boolean not null default false,
 created_at timestamptz not null default now()
);
create table if not exists public.theater_events (
 id uuid primary key default gen_random_uuid(),
 title text not null,
 venue text,
 country_code char(2),
 starts_at timestamptz,
 info_url text check (info_url is null or info_url like 'https://%'),
 authorized_stream_url text check (authorized_stream_url is null or authorized_stream_url like 'https://%'),
 is_published boolean not null default false,
 created_at timestamptz not null default now()
);
create table if not exists public.user_preferences (
 user_id uuid primary key references auth.users(id) on delete cascade,
 theme text not null default 'system' check (theme in ('system','light','dark')),
 country_code char(2),
 language_code text,
 subtitles_enabled boolean not null default true,
 updated_at timestamptz not null default now()
);
create table if not exists public.age_access_consents (
 user_id uuid primary key references auth.users(id) on delete cascade,
 confirmed_18_plus boolean not null default false,
 confirmed_at timestamptz,
 constraint age_consent_timestamp check ((confirmed_18_plus = false and confirmed_at is null) or (confirmed_18_plus = true and confirmed_at is not null))
);
alter table public.media_sources enable row level security;
alter table public.epg_programs enable row level security;
alter table public.radio_stations enable row level security;
alter table public.sports_events enable row level security;
alter table public.music_items enable row level security;
alter table public.theater_events enable row level security;
alter table public.user_preferences enable row level security;
alter table public.age_access_consents enable row level security;
grant select on public.radio_stations, public.sports_events, public.music_items, public.theater_events, public.epg_programs to anon, authenticated;
grant select, insert, update, delete on public.media_sources to authenticated;
grant select, insert, update, delete on public.user_preferences, public.age_access_consents to authenticated;
drop policy if exists "published radio is readable" on public.radio_stations;
create policy "published radio is readable" on public.radio_stations for select to anon, authenticated using (is_published);
drop policy if exists "published sports events readable" on public.sports_events;
create policy "published sports events readable" on public.sports_events for select to anon, authenticated using (is_published);
drop policy if exists "published music readable" on public.music_items;
create policy "published music readable" on public.music_items for select to anon, authenticated using (is_published);
drop policy if exists "published theater readable" on public.theater_events;
create policy "published theater readable" on public.theater_events for select to anon, authenticated using (is_published);
drop policy if exists "published epg readable" on public.epg_programs;
create policy "published epg readable" on public.epg_programs for select to anon, authenticated using (is_published);
drop policy if exists "source owner manages sources" on public.media_sources;
create policy "source owner manages sources" on public.media_sources for all to authenticated using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id);
drop policy if exists "preference owner manages preferences" on public.user_preferences;
create policy "preference owner manages preferences" on public.user_preferences for all to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
drop policy if exists "age consent owner manages consent" on public.age_access_consents;
create policy "age consent owner manages consent" on public.age_access_consents for all to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create index if not exists epg_channel_time_idx on public.epg_programs(channel_external_id, starts_at);
create index if not exists radio_country_genre_idx on public.radio_stations(country_code, genre) where is_published;
create index if not exists sports_starts_at_idx on public.sports_events(starts_at) where is_published;

create index if not exists epg_source_id_idx on public.epg_programs(source_id);
create index if not exists media_sources_owner_id_idx on public.media_sources(owner_id);
