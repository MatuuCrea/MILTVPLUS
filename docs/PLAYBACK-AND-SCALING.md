# MILTVPLUS · reproducción en vivo y escalabilidad

## Reproductores implementados

### Android móvil, Android TV y TV Box
- Reproductor dedicado `ChannelPlayerActivity` con apertura automática al seleccionar una señal integrada desde el catálogo global o la lista local.
- Orientación horizontal, barras del sistema ocultas y pantalla completa inmersiva.
- Media3 ExoPlayer con extensiones HLS y MPEG-DASH. La selección adaptativa de calidad queda a cargo del selector de pistas de Media3, usando las variantes que publique el emisor y el ancho de banda medido.
- Ajuste de imagen `FIT`: conserva la relación de aspecto y evita recortar la imagen para llenar la pantalla.
- Controles de reproducción, canal anterior/siguiente y salida de pantalla completa.
- Reconexión con hasta tres reintentos escalonados después de un error de reproducción.
- Durante una sesión iniciada desde el catálogo remoto, vuelve a consultar la lista cada 60 segundos. Si cambió el conjunto de URLs, conserva el canal actual y su posición cuando sigue disponible; no reinicia la reproducción si sólo cambió el metadato.

### Windows
- LibVLC con decodificación por hardware automática, caché de red/live y opción de reconexión HTTP.
- Pantalla completa mediante botón, doble clic o F11; Escape vuelve a ventana.
- Canal anterior/siguiente y refresco de catálogo cada 60 segundos mientras se navega por canales.

## Catálogo y actualizaciones
- Android y Windows consultan todas las filas publicadas de `tv_channels`, sin restringir la consulta al país guardado en el perfil.
- Paginación de 500 registros por página, hasta 10.000 filas por consulta para evitar el límite anterior de 100.
- Filtros locales por país ISO y categoría.
- El monitor de GitHub Actions corre cada seis horas una vez integrado en la rama predeterminada. Comprueba páginas oficiales y, para filas publicadas con `stream_url`, hace una prueba de manifiesto HLS/DASH.

## Calidad y límites reales
- El reproductor puede elegir automáticamente entre las variantes que entregue una fuente HLS/DASH. No puede crear una variante 4K, mejorar una fuente SD, garantizar una tasa de bits o cambiar la calidad si el emisor no publica esas variantes.
- El ajuste FIT mantiene la relación de aspecto. La cadencia de cuadros y sincronización dependen del manifiesto, del decodificador, de la pantalla y de Android/Windows; no se debe prometer una frecuencia concreta en Hz.
- Los reintentos ayudan ante fallas transitorias, pero no pueden reparar un origen caído, una URL revocada, una restricción geográfica, DRM incompatible o una red saturada.
- La prueba de manifiesto confirma que se recibió una cabecera HLS/DASH reconocible. No equivale a una prueba prolongada de audio/vídeo, a QA en cada dispositivo ni a una prueba de concurrencia.

## Arquitectura para muchos espectadores
- Para canales de terceros con licencia, el cliente debe reproducir directamente desde el CDN/origen autorizado del titular. Supabase distribuye metadatos y no transporta video; esto evita convertir la API de catálogo en un cuello de botella de ancho de banda.
- Para señales propias o licenciadas que deban ser empaquetadas por MILTVPLUS, la infraestructura necesaria es ingestión redundante, transcodificación ABR, empaquetado HLS/DASH, almacenamiento/origin y CDN multi-región. La escalabilidad debe dimensionarse con bitrate medio, concurrencia pico, regiones y SLA del proveedor.
- No se desplegó un relay/transcoder/CDN propio en esta fase: requiere fuentes de ingestión autorizadas, presupuesto de transferencia/egress y pruebas de carga. Crear servidores con costos recurrentes sin dimensionamiento y aprobación sería irresponsable.

## Estado de contenido
- Los canales publicados actualmente son enlaces oficiales web o fuentes aún sin stream directo documentado.
- El reproductor integrado se habilita sólo para filas publicadas con un `stream_url` HTTPS. Una página `official_watch_url` se abre en el sitio del emisor; no se finge que es un stream embebido.
- Para incorporar una señal integrada se exige endpoint HLS/DASH real, titular, referencia de autorización, territorios, vigencia, logo auténtico y prueba de reproducción en dispositivos.
- No se añaden URLs de listas IPTV de terceros ni se extraen streams de páginas oficiales sin permiso.
