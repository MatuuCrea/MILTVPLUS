# Catálogo mundial de TV abierta y señales públicas

## Alcance de esta entrega

Esta fase implementa la estructura de datos y la integración de lectura para un directorio de canales de TV en Android y Windows. Películas y series quedan fuera de alcance y no se deben poblar en esta fase.

El directorio se organiza por país ISO 3166-1 alpha-2 y categoría. Cada fila representa una señal, no una lista de reproducción genérica. Los países se consultan por `country_code`; no se deduce el país por idioma o por el nombre del canal.

## Reglas obligatorias para publicar una señal

Hay dos modalidades distintas de publicación:

**A. Directorio con apertura web oficial (`official_web`).** Requiere nombre, país, categoría, URL HTTPS de la página oficial y comprobación reciente con estado `online`. MILTVPLUS sólo abre el sitio del titular; no descarga, extrae, inserta ni retransmite su señal. El logotipo es opcional y sólo se añade cuando se tiene una URL de imagen y procedencia verificables.

**B. Reproducción integrada (`hls`, `dash`, `youtube_live`).** Requiere titular y referencia documental de autorización de distribución para MILTVPLUS, territorios expresos, vigencia activa, URL HTTPS directa comprobada y compatibilidad con el reproductor. La base bloquea su publicación si falta cualquiera de esas pruebas.

Ambas modalidades requieren categoría, país, idioma y tipo de reproducción correctamente identificados.

Que una señal sea gratuita, pública, esté disponible en YouTube o sea de aire no concede automáticamente permiso para retransmitirla, extraer su stream o redistribuirla dentro de otra aplicación. Cuando no exista una URL directa licenciada, el botón abre la página oficial del titular; no se intenta extraer ni replicar la señal.

La base de datos distingue ambos casos: los enlaces `official_web` sólo se publican si la página oficial está comprobada y no contienen `stream_url`; las señales integradas exigen derechos documentados y vigentes. Los clientes sólo leen filas publicadas y no pueden aprobar fuentes.

## Argentina: catálogo prioritario

El inventario de descubrimiento debe incluir, como mínimo, estas señales/candidatos de noticias y actualidad, además de los canales públicos y regionales:

- C5N (obligatorio)
- Crónica TV (obligatorio)
- Todo Noticias (TN)
- A24
- LN+
- Canal 26
- Argentina 12 / IP Noticias
- Canal E
- Senado TV
- Diputados TV

También se debe investigar televisión pública y de aire, señales provinciales/universitarias, educativas, culturales, deportivas y radios con transmisión audiovisual. Cada registro queda en revisión hasta verificar titular, logo, página oficial, territorios y derechos de distribución.

## Cobertura internacional

La misma taxonomía soporta todos los países y territorios con código ISO de dos letras. No se insertan filas de relleno ni se muestra un país como cubierto sólo por tener un selector. La cobertura se mide por señales publicadas, separando por país y categoría. Se deben mantener categorías: noticias, general, pública, deportes, educación, cultura, música, religión, regional, internacional y otros.

## Sincronización y salud de señal

- El backend es la fuente central de catálogo para Android, Android TV/TV Box y Windows.
- Los clientes refrescan el catálogo desde Supabase; no guardan listas de URLs inventadas.
- Cada fuente conserva `last_checked_at`, `source_status` y detalle del último chequeo.
- `online` significa que el chequeo técnico reciente tuvo éxito; no sustituye la licencia.
- No se debe rotar ni reescribir URLs de reproducción sin comprobar el endpoint nuevo.
- Las señales geo-restringidas, offline, bloqueadas o no verificadas no se publican como disponibles.

## Estado de la implementación

- Migración base: `supabase/migrations/20261113170000_global_tv_channels.sql`; endurecimiento de páginas oficiales, salud y publicación en migraciones posteriores.
- Android: catálogo por país/categoría, reproductor Media3 para HLS/DASH directos y visor WebView inmersivo para páginas oficiales/YouTube Live. Las páginas YouTube Live no se envían a Media3 como si fueran manifiestos.
- Windows: LibVLC para HLS/DASH directos y WebView2 integrado para páginas oficiales/YouTube Live; pantalla completa automática y navegación anterior/siguiente entre fichas reproducibles.
- Supabase contiene 48 fichas en 17 países: 46 publicadas y 2 pendientes. Distribución publicada: AR 9, AU 1, BR 1, CA 1, CL 1, DE 1, ES 5, FR 2, GB 1, IN 2, IT 1, JP 1, MX 10, PT 7, QA 1, SG 1, TR 1.
- 12 de las fichas publicadas mostraron un marcador de emisión en directo actual en el reproductor/página oficial de YouTube durante el último chequeo: Argentina (A24, América TV, C5N, Canal 26, Crónica TV, Televisión Pública y TN), Australia (ABC News Australia), Francia (France 24 English y France 24 Español) e India (DD India y DD News).
- Las otras 23 fichas publicadas son páginas oficiales de señal accesibles. Su respuesta HTTP no demuestra por sí sola que el player interno esté emitiendo en ese instante; el detalle del chequeo lo indica y el emisor conserva control de disponibilidad, geobloqueo y DRM.
- Se agregaron CPAC Canada, CNA Live TV, Rai News 24, NHK World-Japan y siete señales RTP de Portugal (RTP Play puede aplicar geobloqueo). Las 2 fichas pendientes son Diputados TV y el portal de Prasar Bharati, que no se cuenta como canal individual.
- No hay URLs HLS/DASH directas integradas y no se ha marcado ningún derecho de redistribución como verificado. Las 46 fichas publicadas tienen imágenes de marca con procedencia oficial: 12 avatares de canales YouTube y 34 iconos de sitios/emisores; estos últimos identifican al emisor y no siempre son logos individuales de canal. Los 12 casos YouTube usan el reproductor oficial del titular, no una retransmisión nativa de MILTVPLUS.
- Android y Windows consultan el catálogo global y filtran localmente por país/categoría. La lectura pública sólo devuelve filas publicadas con `source_status='online'`.

## Monitor de disponibilidad

- `scripts/check_tv_channel_availability.py` consulta las páginas oficiales registradas en los inventarios de Argentina e internacional; no intenta extraer reproductores ni URLs de streaming.
- `.github/workflows/tv-channel-availability.yml` ejecuta pruebas de clasificación en cada PR y programa comprobaciones reales cada seis horas una vez integrado en la rama por defecto. También admite ejecución manual.
- Cada ejecución genera un informe JSON como artefacto de GitHub Actions y un resumen por canal.
- HTTP 403/429 se clasifica como `blocked` (el robot fue rechazado o limitado), no como señal caída. Un estado HTTP 2xx/3xx sólo confirma que la página web responde; no demuestra que el stream esté al aire, que funcione en todos los países ni que exista licencia para retransmitir.
- Supabase Cron ejecuta `tv-channel-health` cada cinco minutos; el Edge Function procesa hasta 100 fichas por ejecución, comprueba páginas/manifiestos y actualiza `source_status`, `last_checked_at` y `last_check_detail`. Las páginas YouTube `/live` sólo se publican automáticamente si el HTML contiene un marcador de emisión actual; el resto de candidatos permanece en revisión. La política RLS oculta fuentes offline/bloqueadas y permite que reaparezcan al recuperarse.

## Fuera de alcance de esta fase

Películas y series permanecen sin implementación de catálogo/contenido. No importar ni publicar VOD hasta que el propietario habilite expresamente esa fase y se acrediten los derechos correspondientes.


El inventario internacional se amplió con páginas de señal en vivo de MXPlus para canales públicos mexicanos, RTVE Play para señales españolas, ABC iview para ABC News, TVN Play para TV Chile y la WebTV oficial de TV Brasil. Algunos servicios limitan contenido por país o por derechos de programas; cada ficha conserva ese estado y no se convierte en stream HLS/DASH.


## Auditoría específica de señales públicas argentinas

Se agregó `data/catalog/argentina-public-channel-audit.json`. Es un inventario de descubrimiento separado del catálogo de producción: **no inserta canales en Supabase, no los marca como disponibles y no crea URLs de reproducción**.

### Señales públicas con operador institucional identificado

CAISAU identifica oficialmente a Encuentro, Pakapaka y DeporTV como señales bajo su conducción y enlaza sus respectivos sitios. Se incorporan al inventario como páginas oficiales candidatas, pero su emisión online actual y su compatibilidad de reproducción todavía deben probarse. Televisión Pública, Senado TV y Diputados TV también están identificados con páginas institucionales/oficiales para una nueva verificación.

Fuente primaria de titularidad: [CAISAU — Argentina.gob.ar](https://www.argentina.gob.ar/jefatura/contenidos-artisticos-e-informativos-sau).

### Señales TDA y canales provinciales

El archivo contiene candidatos de señales nacionales TDA (AUNAR, Cine.Ar, TEC, Construir TV) y medios locales/provinciales de 20 jurisdicciones para investigación. Los nombres provinciales proceden de un directorio secundario y **no se consideran confirmación de titularidad pública, página oficial, emisión online ni permiso de retransmisión**. Cada candidato necesita verificación individual en el dominio de su titular.

La televisión digital abierta terrestre puede ser gratuita mediante antena, pero eso no implica que exista una señal online, ni que MILTVPLUS pueda redistribuirla. El catálogo debe distinguir:
- **Pública estatal/parlamentaria:** titularidad u operación pública documentada.
- **Abierta privada:** señal de aire gratuita cuyo titular es privado.
- **TDA terrestre:** recepción por antena y cobertura geográfica; no es una URL de streaming.
- **Página oficial online:** acceso mediante el reproductor/sitio del titular, sujeto a sus restricciones.
- **Stream integrado:** sólo con endpoint técnico y autorización expresa de distribución para MILTVPLUS.

Ninguna de las fichas nuevas se considera «en vivo» hasta probar el reproductor en el dispositivo y territorio correspondientes. Ninguna queda habilitada para retransmisión integrada sin referencia documental de derechos vigente.


### Corte de la base productiva verificado el 14/11/2026

Consulta de sólo lectura a `public.tv_channels` en el proyecto conectado:
- 48 fichas en total, 46 publicadas y 2 sin publicar.
- Argentina: 10 fichas, 9 publicadas, 9 con `source_status='online'` y 1 pendiente de revisión (Diputados TV).
- Argentina tiene 1 ficha parlamentaria clasificada `public` (Senado TV). Televisión Pública figura actualmente en `general`, por lo que la clasificación del catálogo necesita revisión versionada antes de alterarla.
- **0 streams directos configurados** y **0 derechos de redistribución verificados** en las fichas consultadas.
- `source_status='online'` sólo significa que el chequeo de página respondió; no confirma que haya vídeo en vivo ni que el player funcione en teléfono/TV.

Las señales Encuentro, Pakapaka y DeporTV ya están identificadas por CAISAU en el inventario de auditoría, pero todavía no están incorporadas a la base productiva. Sus sitios requieren prueba individual del reproductor; la página oficial de DeporTV respondió y mostró contenido editorial, lo que no acredita una emisión en directo permanente.
