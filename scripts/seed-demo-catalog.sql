-- MILTVPLUS safe demo catalogue fixtures
-- Purpose: exercise catalog cards and category filters in a DEV/QA Supabase project.
-- These rows are deliberately unpublished, carry no stream URLs, and grant no rights.
-- They MUST NOT be promoted to production or marked is_published=true without
-- documentary authorization, a verified official source, and a regional rights review.
-- Safe to re-run: stable UUIDs + ON CONFLICT updates. Adult catalogue remains empty.

begin;

insert into public.tv_channels
  (id, slug, title, description, country_code, language_code, category,
   is_free_to_air, playback_type, source_status, access_model, is_published,
   rights_verified, rights_regions, sort_order)
values
  ('a1000000-0000-4000-8000-000000000001', 'demo-tv-no-stream',
   'DEMO QA — TV sin emisión', 'Ficha de prueba de catálogo. No contiene señal ni enlace de reproducción.',
   'AR', 'es', 'other', false, 'official_web', 'pending_review', 'unknown',
   false, false, '{}', 99999)
on conflict (id) do update set
  title=excluded.title, description=excluded.description, is_published=false,
  stream_url=null, official_watch_url=null, rights_verified=false,
  rights_holder=null, rights_reference=null, rights_regions='{}';

insert into public.sports_events
  (id, title, description, sport, competition, country_code, starts_at,
   official_info_url, authorized_stream_url, is_published, rights_verified, rights_regions)
values
  ('a2000000-0000-4000-8000-000000000001',
   'DEMO QA — Evento deportivo sin emisión',
   'Evento ficticio de prueba para verificar tarjetas, filtros y orden cronológico. No es un evento real.',
   'demo', 'QA fixture', 'AR', '2026-11-15T20:00:00Z',
   null, null, false, false, '{}')
on conflict (id) do update set
  title=excluded.title, description=excluded.description, is_published=false,
  authorized_stream_url=null, rights_verified=false, rights_holder=null,
  rights_reference=null, rights_regions='{}';

insert into public.catalog_items
  (id, title, description, kind, country_code, language_code, genre, genres,
   release_year, age_rating, is_published, is_adult_content, rights_verified, rights_regions)
values
  ('a3000000-0000-4000-8000-000000000001',
   'DEMO QA — Película sin vídeo',
   'Ficha ficticia para probar el diseño y los filtros. No incluye archivo audiovisual.',
   'movie', 'AR', 'es', 'demo', array['demo'], 2026, 'ATP',
   false, false, false, '{}'),
  ('a3000000-0000-4000-8000-000000000002',
   'DEMO QA — Serie sin episodios',
   'Ficha ficticia de serie para probar la navegación por temporadas.',
   'series', 'AR', 'es', 'demo', array['demo'], 2026, 'ATP',
   false, false, false, '{}')
on conflict (id) do update set
  title=excluded.title, description=excluded.description, is_published=false,
  is_adult_content=false, stream_url=null, rights_verified=false,
  rights_holder=null, rights_reference=null, rights_regions='{}';

insert into public.catalog_items
  (id, title, description, kind, country_code, language_code, genre, genres,
   release_year, age_rating, series_id, season_number, episode_number,
   is_published, is_adult_content, rights_verified, rights_regions)
values
  ('a3000000-0000-4000-8000-000000000003',
   'DEMO QA — Episodio sin vídeo',
   'Episodio ficticio vinculado a la serie de prueba. No incluye archivo audiovisual.',
   'episode', 'AR', 'es', 'demo', array['demo'], 2026, 'ATP',
   'a3000000-0000-4000-8000-000000000002', 1, 1,
   false, false, false, '{}')
on conflict (id) do update set
  title=excluded.title, description=excluded.description, is_published=false,
  is_adult_content=false, stream_url=null, rights_verified=false,
  rights_holder=null, rights_reference=null, rights_regions='{}';

insert into public.music_items
  (id, item_type, title, creator, country_code, album, genre, release_year,
   is_published, rights_verified, rights_regions, stream_url, external_url)
values
  ('a4000000-0000-4000-8000-000000000001',
   'playlist', 'DEMO QA — Playlist sin audio', 'MILTVPLUS QA',
   'AR', 'Demo', 'demo', 2026, false, false, '{}', null, null)
on conflict (id) do update set
  title=excluded.title, is_published=false, stream_url=null, external_url=null,
  rights_verified=false, rights_holder=null, rights_reference=null, rights_regions='{}';

commit;

-- Expected DEV/QA fixture totals after running:
-- TV: 1 unpublished; Sports: 1 unpublished; Movies: 1 unpublished;
-- Series: 1 unpublished + 1 unpublished episode; Music: 1 unpublished playlist;
-- Adult: 0 rows. Public catalog endpoints continue returning only licensed published items.
