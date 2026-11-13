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

## 9. Mapa de navegación y adaptación visual (referencia de las dos imágenes)

La referencia visual se implementa como dos espacios de producto con navegación compartida, no como una imagen estática:

### 9.1 Espacio principal argentino e internacional
- Cabecera: marca MILTVPLUS, estado de cuenta, actualización disponible, acceso a cuenta y botón de menú.
- Menú lateral modal expansible: se abre desde el botón ☰ o por gesto; se cierra desde el botón de contraer, tocando fuera o con Atrás. No queda fijo tapando el contenido.
- Destinos conectados: Inicio, TV en Vivo, Películas y Series, Deportes, Radios, Música, Teatro, Guía EPG, Países, Favoritos, Fuentes M3U, Adultos 18+, Configuración y Cuenta.
- Inicio: destacado, país activo, accesos a directo/favoritos/guía, canales publicados y accesos a catálogo. El país inicial sigue siendo Argentina (AR).
- TV en Vivo: catálogo remoto publicado, filtro por país y categoría, reproductor Media3 para URLs HLS/DASH directas autorizadas, o apertura del reproductor oficial cuando sólo se publica una página web.
- Países: selector desde el directorio ISO; las cantidades deben derivarse de registros reales, nunca de cifras de diseño.
- EPG: horarios agrupados por identificador externo del canal y ordenados por inicio; sólo se presenta cuando hay datos de programación válidos.
- Configuración: idioma, tema, acento, audio, subtítulos, autoplay, notificaciones, región, cuenta y controles parentales según capacidades persistidas.

### 9.2 Espacio Adultos 18+
- Identidad visual independiente negro/berenjena con acentos magenta y rosa, cabecera 18+, tarjetas y categorías Recomendados, Romance, Parejas, Pasión, Hot, Premium y Solo adultos.
- Confirmación de edad y PIN antes de consultar el catálogo; opción de bloquear la sección al salir.
- Categorías filtradas por metadatos del catálogo publicado; los botones sin título o URL disponible muestran estado no disponible, nunca una reproducción falsa.
- El contenido debe proceder de proveedores que autoricen explícitamente la distribución, con clasificación, derechos territoriales y URLs directas verificadas. No se insertan streams ni páginas de terceros por inferencia.

### 9.3 Diseño adaptable
- Teléfonos: una columna, tarjetas apiladas, listas desplazables y menú modal para no consumir ancho útil.
- Tablets y pantallas grandes: el contenido puede organizarse en paneles y filas más amplias; la navegación sigue siendo contraíble.
- Android TV: navegación enfocada con control remoto, elementos seleccionables y controles de reproducción accesibles por D-pad.
- El reproductor inmersivo se adapta a la orientación y al área disponible, oculta temporalmente las barras del sistema y conserva controles de cambio de canal.

### 9.4 Criterio de aceptación
Cada elemento de navegación debe abrir una pantalla real; cada acción de reproducción debe tener una URL autorizada reproducible o informar claramente que sólo existe el reproductor oficial/no hay señal directa. Las pruebas de compilación no sustituyen pruebas de reproducción, orientación, foco de TV ni tamaños físicos. La referencia visual completa sólo se considera entregada después de validar estas rutas en móvil, tablet y Android TV.

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
