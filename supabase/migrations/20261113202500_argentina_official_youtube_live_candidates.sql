-- Argentina live candidates: use verified broadcaster YouTube channels' /live routes.
-- They stay unpublished until the scheduled health monitor sees an explicit current-live marker.
update public.tv_channels
set official_watch_url = 'https://www.youtube.com/@c5n/live',
    source_status = 'pending_review',
    is_published = false,
    last_checked_at = null,
    last_check_detail = 'Canal oficial verificado en YouTube; esperando confirmación automática de emisión en directo actual.'
where slug = 'c5n';

update public.tv_channels
set official_watch_url = 'https://www.youtube.com/@cronicatv/live',
    source_status = 'pending_review',
    is_published = false,
    last_checked_at = null,
    last_check_detail = 'Canal oficial verificado en YouTube; esperando confirmación automática de emisión en directo actual.'
where slug = 'cronica-tv';

update public.tv_channels
set official_watch_url = 'https://www.youtube.com/@canal26/live',
    source_status = 'pending_review',
    is_published = false,
    last_checked_at = null,
    last_check_detail = 'Canal oficial verificado en YouTube; esperando confirmación automática de emisión en directo actual.'
where slug = 'canal-26';

insert into public.tv_channels (
  slug, title, description, country_code, language_code, category,
  is_free_to_air, access_model, official_watch_url, playback_type,
  source_status, is_published, sort_order, last_check_detail
)
select
  candidate.slug, candidate.title, candidate.description, 'AR', 'es', 'news',
  false, 'free_web', candidate.official_watch_url, 'official_web',
  'pending_review', false, candidate.sort_order,
  'Canal oficial verificado en YouTube; esperando confirmación automática de emisión en directo actual.'
from (values
  ('a24', 'A24', 'Señal informativa argentina; sólo se publica mientras el canal oficial confirma una emisión en directo.', 'https://www.youtube.com/@A24com/live', 103),
  ('tn-todo-noticias', 'TN (Todo Noticias)', 'Señal informativa argentina; sólo se publica mientras el canal oficial confirma una emisión en directo.', 'https://www.youtube.com/@todonoticias/live', 104)
) as candidate(slug, title, description, official_watch_url, sort_order)
where not exists (
  select 1 from public.tv_channels existing where existing.slug = candidate.slug
);
