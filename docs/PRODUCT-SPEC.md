# MILTVPLUS — especificación funcional

## 1. Interfaz
- Tema oscuro, claro y sistema; acentos azul eléctrico, violeta y verde.
- Inicio por módulos, tarjetas visuales, búsqueda, filtros y estados vacíos/error.

## 2. TV en vivo
- `catalog_items.kind=live`, M3U/M3U8 local y remoto aprobado, Media3.
- HTTPS, filtro por región y favoritos de catálogo.
- EPG por `channel_external_id`, hora local y marca de programa en vivo.

## 3. Películas y series
- movie/series/episode, póster, backdrop, género, año, duración, clasificación, idioma, proveedor, licencia y disponibilidad.
- Relaciones serie/temporada/episodio. Falta ficha y navegación dedicada de temporadas.

## 4. Deportes
- Disciplina, competición, región, horarios, póster e información oficial.
- Transmisión sólo con `authorized_stream_url` y derechos publicados.

## 5. Radios
- Emisoras por país/idioma/género, logo y stream HTTPS.
- Fuentes verificadas antes de publicar.

## 6. Música
- Artistas, álbumes, géneros, año, idioma, proveedor y artwork.
- Links externos se abren en el proveedor. Spotify abre su web oficial; OAuth requiere Client ID y aprobación.

## 7. Teatro
- Evento, recinto, país, fecha, género, póster e información oficial.
- Stream sólo con autorización explícita.

## 8. +18
- Sección visual independiente con identidad nocturna negro/berenjena, acentos magenta/rosa, banner 18+, tarjetas y categorías Recomendados, Romance, Parejas, Pasión, Hot, Premium y Solo adultos; entrada directa desde la cabecera principal.
- Las categorías filtran metadatos reales del catálogo; no crean títulos ni enlaces de relleno. Las tarjetas sólo habilitan Reproducir cuando existe una URL directa publicada.
- Declaración, PIN PBKDF2 con sal y bloqueo tras cinco intentos fallidos.
- Catálogo +18 consultado sólo tras desbloquear y filtrado por clasificación 18+. En esta revisión se reutiliza `catalog_items`; todavía no existe una tabla/ingesta dedicada de contenido adulto, ni un inventario de URLs adultas verificadas. No se deben insertar URLs de terceros sin permiso explícito del titular, verificación de edad/procedencia y derechos territoriales.
- El aviso de mayoría de edad y el PIN son controles de producto, no sustituyen verificación legal de edad ni controles de cumplimiento exigibles por jurisdicción.

## 9. Ajustes
- Tema, acento, idioma, país, audio preferido, subtítulos, preferencia de avisos y autoplay.
- Persistencia local y nube. Entrega push/reminders todavía no desplegada.

## 10. Países
- Android usa países ISO del runtime; Web usa Intl.
- Filtro por país y regiones de derechos; no anula restricciones contractuales.

## 11. Multiplataforma
- Android móvil, Android TV y TV Box Android comparten APK.
- Web/PWA vive en `web/`.
- Tizen y webOS requieren clientes independientes.

## 12. Calidad y seguridad
- HTTPS, claves publishable, Keystore, RLS, grants mínimos, protección de fuentes y trigger de derechos vigentes.
- No se fabrican canales. JVM tests y assembleDebug en GitHub Actions.
- D-pad, QA físico, release signing, privacidad, logo oficial y tiendas siguen como criterios de salida.
