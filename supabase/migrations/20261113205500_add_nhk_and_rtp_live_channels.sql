-- Add official live pages for NHK World Japan and seven RTP linear channels.
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
  ('nhk-world-japan', 'NHK World-Japan', 'Canal internacional de NHK con reproductor oficial Live TV y programación continua en inglés.', 'JP', 'en', 'news', 'free_web', 'https://www3.nhk.or.jp/nhkworld/en/live_tv/', 401, 'Página oficial NHK World Live TV; el monitor verifica accesibilidad HTTP, no decodificación del reproductor.'),
  ('rtp1-portugal', 'RTP 1', 'Señal lineal oficial RTP 1 en directo; algunos programas pueden tener restricciones territoriales.', 'PT', 'pt', 'general', 'geo_restricted', 'https://www.rtp.pt/play/direto/rtp1', 402, 'Página oficial RTP Play Direto; puede aplicar restricciones de derechos fuera de Portugal.'),
  ('rtp2-portugal', 'RTP 2', 'Señal lineal oficial RTP 2 en directo; algunos programas pueden tener restricciones territoriales.', 'PT', 'pt', 'culture', 'geo_restricted', 'https://www.rtp.pt/play/direto/rtp2', 403, 'Página oficial RTP Play Direto; puede aplicar restricciones de derechos fuera de Portugal.'),
  ('rtp3-portugal', 'RTP 3', 'Canal informativo RTP Notícias en directo; algunos programas pueden tener restricciones territoriales.', 'PT', 'pt', 'news', 'geo_restricted', 'https://www.rtp.pt/play/direto/rtp3', 404, 'Página oficial RTP Play Direto; puede aplicar restricciones de derechos fuera de Portugal.'),
  ('rtp-africa-portugal', 'RTP África', 'Señal internacional RTP África en directo para audiencias lusófonas; pueden aplicar restricciones de derechos.', 'PT', 'pt', 'general', 'geo_restricted', 'https://www.rtp.pt/play/direto/rtpafrica', 405, 'Página oficial RTP Play Direto; puede aplicar restricciones de derechos fuera de Portugal.'),
  ('rtp-mundo-portugal', 'RTP Mundo', 'Señal internacional RTP Mundo en directo para la diáspora; pueden aplicar restricciones de derechos.', 'PT', 'pt', 'general', 'geo_restricted', 'https://www.rtp.pt/play/direto/rtpinternacional', 406, 'Página oficial RTP Play Direto; puede aplicar restricciones de derechos fuera de Portugal.'),
  ('rtp-acores-portugal', 'RTP Açores', 'Señal regional oficial RTP Açores en directo; pueden aplicar restricciones de derechos.', 'PT', 'pt', 'general', 'geo_restricted', 'https://www.rtp.pt/play/direto/rtpa%C3%A7ores', 407, 'Página oficial RTP Play Direto; puede aplicar restricciones de derechos fuera de Portugal.'),
  ('rtp-madeira-portugal', 'RTP Madeira', 'Señal regional oficial RTP Madeira en directo; pueden aplicar restricciones de derechos.', 'PT', 'pt', 'general', 'geo_restricted', 'https://www.rtp.pt/play/direto/rtpmadeira', 408, 'Página oficial RTP Play Direto; puede aplicar restricciones de derechos fuera de Portugal.')
) as candidate(slug,title,description,country_code,language_code,category,access_model,official_watch_url,sort_order,last_check_detail)
where not exists (
  select 1 from public.tv_channels existing where existing.slug = candidate.slug
);
