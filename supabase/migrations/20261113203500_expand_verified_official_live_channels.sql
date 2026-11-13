-- Replace third-party directory links with official broadcaster live pages.
update public.tv_channels
set title = 'DW English',
    official_watch_url = 'https://www.dw.com/en/live-tv/channel-english',
    source_status = 'online',
    is_published = true,
    last_checked_at = null,
    last_check_detail = 'Página oficial DW Live TV; el monitor verifica accesibilidad HTTP, no decodificación del reproductor.'
where slug = 'dw-mxplus';

update public.tv_channels
set title = 'Canal 22.1',
    description = 'Señal lineal oficial 22.1 de Canal Veintidós; abre el reproductor de transmisión del emisor.',
    official_watch_url = 'https://www.canal22.org.mx/streaming-22-1.html',
    source_status = 'online',
    is_published = true,
    last_checked_at = null,
    last_check_detail = 'Página oficial de streaming Canal 22.1; el monitor verifica accesibilidad HTTP, no decodificación del reproductor.'
where slug = 'canal-22-mx';

update public.tv_channels
set title = 'Canal Once · Once.3',
    description = 'Señal digital oficial Once.3 de Canal Once; abre el reproductor de señal en vivo del emisor.',
    official_watch_url = 'https://www.canalonce.mx/en-vivo',
    source_status = 'online',
    is_published = true,
    last_checked_at = null,
    last_check_detail = 'Página oficial de señal en vivo Once.3; el monitor verifica accesibilidad HTTP, no decodificación del reproductor.'
where slug = 'canal-11-mx';

update public.tv_channels
set title = 'France 24 English',
    official_watch_url = 'https://www.youtube.com/@france24_en/live',
    source_status = 'pending_review',
    is_published = false,
    last_checked_at = null,
    last_check_detail = 'Canal oficial verificado de FRANCE 24; se publica sólo cuando YouTube confirma una emisión en directo actual.'
where slug = 'france-24-mxplus';

update public.tv_channels
set official_watch_url = 'https://www.youtube.com/@abcnewsaustralia/live',
    source_status = 'pending_review',
    is_published = false,
    last_checked_at = null,
    last_check_detail = 'Canal oficial ABC News; se publica sólo cuando YouTube confirma una emisión en directo actual.'
where slug = 'abc-news-au';

update public.tv_channels
set title = 'Prasar Bharati · Portal de canales',
    description = 'Directorio oficial de canales de Prasar Bharati, no una señal individual; se conserva como referencia y no se publica en la lista de canales.',
    source_status = 'pending_review',
    is_published = false,
    last_checked_at = null,
    last_check_detail = 'Portal de directorio, no canal individual; requiere seleccionar una señal concreta.'
where slug = 'prasar-bharati-live-tv';

insert into public.tv_channels (
  slug, title, description, country_code, language_code, category,
  is_free_to_air, access_model, official_watch_url, playback_type,
  source_status, is_published, sort_order, last_check_detail
)
select
  candidate.slug, candidate.title, candidate.description, candidate.country_code,
  candidate.language_code, candidate.category, false, 'free_web',
  candidate.official_watch_url, 'official_web', candidate.source_status,
  candidate.is_published, candidate.sort_order, candidate.last_check_detail
from (values
  ('france-24-es', 'France 24 Español', 'Señal informativa internacional en español; se publica sólo cuando el canal oficial confirma una emisión en directo actual.', 'FR', 'es', 'news', 'https://www.youtube.com/@France24_es/live', 'pending_review', false, 201, 'Canal oficial verificado de FRANCE 24 Español; esperando marcador de emisión en directo actual.'),
  ('dd-news-india', 'DD News', 'Canal informativo público de India; se publica sólo cuando el canal oficial confirma una emisión en directo actual.', 'IN', 'hi', 'news', 'https://www.youtube.com/ddnews/live', 'pending_review', false, 202, 'Canal oficial de Prasar Bharati; esperando marcador de emisión en directo actual.'),
  ('dd-india', 'DD India', 'Señal internacional de Doordarshan; se publica sólo cuando el canal oficial confirma una emisión en directo actual.', 'IN', 'en', 'news', 'https://www.youtube.com/ddindia/live', 'pending_review', false, 203, 'Canal oficial de Prasar Bharati; esperando marcador de emisión en directo actual.'),
  ('tv-publica-argentina', 'Televisión Pública', 'Señal abierta nacional argentina; se publica sólo cuando el canal oficial confirma una emisión en directo actual.', 'AR', 'es', 'general', 'https://www.youtube.com/channel/UCs231K71Bnu5295_x0MB5Pg/live', 'pending_review', false, 105, 'Canal oficial verificado; esperando marcador de emisión en directo actual.'),
  ('america-tv-argentina', 'América TV', 'Señal abierta argentina; se publica sólo cuando el canal oficial confirma una emisión en directo actual.', 'AR', 'es', 'general', 'https://www.youtube.com/channel/UC6NVDkuzY2exMOVFw4i9oHw/live', 'pending_review', false, 106, 'Canal oficial de América TV; esperando marcador de emisión en directo actual.'),
  ('el-nueve', 'El Nueve', 'Señal abierta argentina; reproductor oficial de transmisión en directo de El Nueve.', 'AR', 'es', 'general', 'https://www.elnueve.com.ar/en-vivo', 'online', true, 107, 'Página oficial de El Nueve en vivo; el monitor verifica accesibilidad HTTP, no decodificación del reproductor.'),
  ('mx-nuestro-cine', 'MX Nuestro Cine', 'Señal lineal de cine mexicano de Canal 22; abre el reproductor oficial de transmisión.', 'MX', 'es', 'culture', 'https://www.canal22.org.mx/mx_nuestro_cine.html', 'online', true, 204, 'Página oficial de MX Nuestro Cine; el monitor verifica accesibilidad HTTP, no decodificación del reproductor.'),
  ('canal-once-internacional', 'Canal Once Internacional', 'Señal internacional de Canal Once, con programación lineal gratuita por internet.', 'MX', 'es', 'general', 'https://canalonce.mx/mexico', 'online', true, 205, 'Página oficial de señal internacional; el monitor verifica accesibilidad HTTP, no decodificación del reproductor.')
) as candidate(slug,title,description,country_code,language_code,category,official_watch_url,source_status,is_published,sort_order,last_check_detail)
where not exists (select 1 from public.tv_channels existing where existing.slug = candidate.slug);
