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

- Migración: `supabase/migrations/20261113170000_global_tv_channels.sql`.
- Android: el catálogo global incluye la tabla `tv_channels`, usa el país seleccionado y muestra el logotipo; si no existe stream directo, ofrece abrir la página oficial.
- Windows: sección `Canales por país`, logotipo, metadatos y apertura del sitio oficial como fallback.
- Supabase contiene siete registros publicados/en revisión: Argentina tiene C5N, Canal 26 y Senado TV publicados como enlaces oficiales; Crónica TV y Diputados TV quedan en revisión. Se sumaron Al Jazeera English (QA) y TRT World (TR), publicados sólo como páginas oficiales de TV en vivo. No hay streams integrados ni derechos de redistribución marcados como verificados.
- Android ordena por prioridad, filtra por país y categoría y muestra el modelo de acceso; Windows ofrece filtros dinámicos por país/categoría y muestra el modelo de acceso.
- Migración adicional: `supabase/migrations/20261113193000_official_web_directory_links.sql`.

## Monitor de disponibilidad

- `scripts/check_tv_channel_availability.py` consulta las páginas oficiales registradas en los inventarios de Argentina e internacional; no intenta extraer reproductores ni URLs de streaming.
- `.github/workflows/tv-channel-availability.yml` ejecuta pruebas de clasificación en cada PR y programa comprobaciones reales cada seis horas una vez integrado en la rama por defecto. También admite ejecución manual.
- Cada ejecución genera un informe JSON como artefacto de GitHub Actions y un resumen por canal.
- HTTP 403/429 se clasifica como `blocked` (el robot fue rechazado o limitado), no como señal caída. Un estado HTTP 2xx/3xx sólo confirma que la página web responde; no demuestra que el stream esté al aire, que funcione en todos los países ni que exista licencia para retransmitir.
- El monitor no actualiza automáticamente Supabase: esa sincronización queda pendiente de habilitar un mecanismo servidor seguro para escribir estados, sin exponer claves privilegiadas en los clientes.

## Fuera de alcance de esta fase

Películas y series permanecen sin implementación de catálogo/contenido. No importar ni publicar VOD hasta que el propietario habilite expresamente esa fase y se acrediten los derechos correspondientes.
