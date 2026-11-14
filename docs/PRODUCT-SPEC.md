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

## 3 bis. Catálogo TMDB
- Películas populares, mejor valoradas y próximos estrenos; series populares y en emisión.
- Búsqueda separada de películas/series, fichas, pósteres, fondos, reparto y temporadas.
- Acciones de ficha Android: favoritos locales («Me gusta»), compartir mediante el selector nativo, reproducción reservada para una URL autorizada y tráiler oficial de YouTube incrustado cuando existe en TMDB.
- Windows: ventana WPF nativa para películas/series, acceso con cuenta Supabase, listas, búsqueda, fichas, reparto, tráiler oficial incrustado, temporadas/episodios, favoritos locales separados por tipo y copia del enlace para compartir.
- Series: selección de temporada y carga de episodios.
- Idioma `es-AR` y región `AR` donde corresponde.
- TMDB aporta metadatos e imágenes, no archivos de vídeo, licencias de reproducción ni disponibilidad territorial.
- Credenciales TMDB exclusivamente en secretos de Supabase Edge Functions; los clientes Android y Windows requieren una sesión JWT de Supabase. El token de Windows se mantiene sólo en memoria de la ventana y se refresca durante la sesión.

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
- Sección separada y excluida de listados generales.
- Declaración, PIN PBKDF2 con sal y bloqueo tras cinco intentos fallidos.
- Catálogo +18 consultado sólo tras desbloquear. No reemplaza verificación legal de edad.

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
