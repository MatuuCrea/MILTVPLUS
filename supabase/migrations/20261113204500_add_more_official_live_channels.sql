-- Add official live viewing pages for Canada, Singapore and Italy.
insert into public.tv_channels (
  slug, title, description, country_code, language_code, category,
  is_free_to_air, access_model, official_watch_url, playback_type,
  source_status, is_published, sort_order, last_check_detail
)
select
  candidate.slug, candidate.title, candidate.description, candidate.country_code,
  candidate.language_code, candidate.category, false, candidate.access_model,
  candidate.official_watch_url, 'official_web', 'online', true,
  candidate.sort_order, candidate.last_check_detail
from (values
  ('cpac-canada-live', 'CPAC Canada', 'Señal oficial canadiense de asuntos públicos; página CPAC con reproductor marcado como Live.', 'CA', 'en', 'news', 'free_web', 'https://www.cpac.ca/en', 301, 'Página oficial CPAC con reproductor Live; el monitor verifica accesibilidad HTTP, no decodificación del vídeo.'),
  ('cna-singapore-live', 'CNA Live TV', 'Señal informativa en directo de Channel NewsAsia; página oficial Live TV.', 'SG', 'en', 'news', 'free_web', 'https://www.channelnewsasia.com/watch/live-tv-1441426?view=embed', 302, 'Página oficial CNA Live TV; el monitor verifica accesibilidad HTTP, no decodificación del vídeo.'),
  ('rai-news-24-italy', 'Rai News 24', 'Canal informativo italiano en directo a través de RaiPlay; puede aplicar restricciones territoriales.', 'IT', 'it', 'news', 'geo_restricted', 'https://www.raiplay.it/dirette/rainews24?channel=RaiNews', 303, 'Página oficial RaiPlay Dirette; puede aplicar geobloqueo y el monitor no decodifica el reproductor.')
) as candidate(slug,title,description,country_code,language_code,category,access_model,official_watch_url,sort_order,last_check_detail)
where not exists (
  select 1 from public.tv_channels existing where existing.slug = candidate.slug
);
