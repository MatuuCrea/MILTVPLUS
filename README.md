# MILTVPLUS V22.34.9

MILTVPLUS es un producto de aplicaciones instalables nativas: Android (teléfonos, Android TV, TV Box y TV Stick con Android compatible) y Windows x64. No se ofrece una PWA ni una página web como aplicación.

## Android

Cliente nativo Kotlin, Jetpack Compose y Android Media3.
- Navegación para TV en vivo, películas, series, deportes, radios, música, teatro, EPG, cuenta y ajustes.
- Reproducción Media3 HLS/MPEG-DASH de fuentes HTTPS autorizadas, con pantalla completa horizontal, selección adaptativa de variantes, reconexión limitada y cambio de canal.
- Reproducción de páginas oficiales en un WebView inmersivo cuando el canal no tiene una URL HLS/DASH autorizada; el reproductor del emisor mantiene sus propias restricciones y controles.
- Actualización del directorio cada 60 segundos durante navegación y reproducción remota; la cola Windows se sincroniza automáticamente con las fichas nuevas.
- Monitor Supabase automático cada 5 minutos: comprueba hasta 100 canales por ejecución, guarda estado y detalle, y oculta por RLS las fuentes caídas/bloqueadas hasta que se recuperen.
- Importación M3U/M3U8 local con límite de 5 MB y validación HTTPS.
- Catálogo Supabase, favoritos, preferencias, Auth y sesión cifrada con Android Keystore.
- Directorio global de TV por país/categoría (`tv_channels`), con campos para logotipos y procedencia, páginas oficiales, filtros de países y fallback al sitio del canal cuando no existe un stream directo autorizado. Las 46 fichas publicadas tienen imágenes de marca obtenidas de avatares oficiales de YouTube o iconos de los sitios oficiales; los iconos del sitio identifican al emisor y no siempre son un logotipo individual de cada canal.
- Control +18 con PIN PBKDF2 y bloqueo temporal.
- Icono Android con logotipo MILTVPLUS y banner Android TV.
- Comprobación automática de releases estables al iniciar y desde Ajustes; descarga APK sólo desde GitHub Releases, verifica SHA-256 y abre el instalador del sistema Android con confirmación del usuario.

Construcción:
```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`. La APK debug se firma con la clave debug automática; no es una release firmada para distribución comercial.

## Windows

Cliente de escritorio nativo WPF/.NET 8 en `windows/MILTVPLUS.Desktop`. Usa LibVLC para streams HLS/DASH directos y Microsoft Edge WebView2 para mostrar dentro de la aplicación las páginas oficiales de los emisores cuando no hay un endpoint directo autorizado. El visor oficial sólo puede reproducir lo que el propio emisor permita; respeta sus restricciones, DRM y geobloqueos. Si WebView2 Runtime no está instalado o el emisor bloquea la vista integrada, se informa dentro de MILTVPLUS y la aplicación permanece abierta; no se lanza un navegador externo desde el flujo de TV. Incluye navegación de secciones, búsqueda, consulta por tabla con proyecciones compatibles con el esquema real de Supabase y generación automática del icono Windows desde el logotipo de marca para el ejecutable y el instalador.

El workflow `.github/workflows/windows.yml` publica la aplicación x64 autocontenida y construye un instalador `MILTVPLUS-Setup-x64.exe` con Inno Setup. Windows consulta la última release al iniciar y permite descargar el instalador oficial, verificar SHA-256 y ejecutar el actualizador con confirmación. El workflow `.github/workflows/release-native.yml` prepara un APK release firmado y el EXE como activos directos de una GitHub Release al etiquetar `v*`; el flujo genera `SHA256SUMS`, exige los secretos protegidos de firma Android y falla de forma segura si faltan. Hasta publicar la primera release firmada, los actualizadores no encontrarán una versión descargable. El cliente Windows incorpora reproducción mediante LibVLC y WebView2, pantalla completa automática al seleccionar un canal (F11/Escape/doble clic), cambio anterior/siguiente entre señales directas y páginas oficiales, decodificación por hardware, caché de red y reconexión HTTP; valida HTTPS antes de iniciar un flujo e importa listas M3U/M3U8 con límite de 5 MB. La lista local no se sincroniza con la nube ni se considera verificada por el mero hecho de usar HTTPS. Todavía faltan Auth, favoritos, control +18, preferencias, EPG/series completas y QA físico; no se declara paridad funcional con Android. La nueva sección Canales por país también consulta `tv_channels` y abre la página oficial si no hay URL directa.

## Compatibilidad y criterios de lanzamiento

La matriz detallada por dispositivo está en [`docs/DEVICE-RELEASE-MATRIX.md`](docs/DEVICE-RELEASE-MATRIX.md). La arquitectura de reproducción, actualizaciones y escalabilidad está en [`docs/PLAYBACK-AND-SCALING.md`](docs/PLAYBACK-AND-SCALING.md). Android TV y Google TV requieren pruebas de navegación D-pad en hardware real; TV Box/TV Stick sólo se consideran compatibles si ejecutan Android compatible y superan esa validación. Samsung Tizen y LG webOS requieren apps nativas separadas. La web oficial es un portal de información/descargas, no una aplicación de streaming.

El workflow de releases exige un APK Android firmado con keystore protegido. Deben configurarse los secretos `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` y `ANDROID_KEY_PASSWORD` en GitHub Actions antes de etiquetar una versión. El workflow falla si falta cualquiera; no publica APK debug como release. El instalador Windows se genera como `.exe`, pero todavía requiere certificado Authenticode para una distribución más confiable y QA de instalación en equipos limpios.

## Catálogo y derechos

Proyecto Supabase: `rbnuxapfsnaabrzuxvdy`. Los clientes sólo usan la clave publishable. Nunca colocar service_role, secret keys ni contraseñas en el cliente.

La tabla `tv_channels` contiene 48 registros de 17 países: 46 páginas oficiales publicadas y 2 fichas en revisión (Diputados TV y el portal de canales de Prasar Bharati, que no es una señal individual). De las 46 publicadas, 12 páginas de canales YouTube oficiales mostraron un marcador de emisión en directo en el último chequeo; las otras 34 son páginas oficiales de señal cuyo HTTP responde, pero el monitor no puede certificar la reproducción interna. No hay URLs HLS/DASH directas integradas; las 46 fichas publicadas tienen una imagen de marca con procedencia oficial (12 avatares de canal YouTube y 34 iconos de sitios/emisores; estos últimos no siempre son logos individuales); no se marcó ningún derecho de redistribución como verificado. El catálogo incluye también CPAC Canada, CNA Live TV, Rai News 24, NHK World-Japan y siete señales RTP de Portugal (algunas con restricciones territoriales). El monitor `tv-channel-health` se ejecuta cada 5 minutos, actualiza disponibilidad y retira automáticamente las fuentes que dejan de estar al aire o accesibles; las páginas oficiales mantienen las restricciones del titular. Los canales publicados abren las plataformas oficiales dentro del visor inmersivo Android o del visor WebView2 de Windows; el visor puede depender de las restricciones, geobloqueos, DRM y políticas del emisor. No se agregan streams ficticios. Cada señal integrada requiere titular, referencia de derechos, territorios, vigencia y una prueba de reproducción. La autorización del propietario de esta aplicación para desarrollar el software no sustituye las licencias de los titulares de cada emisión.

## Pendientes externos y de producto

- Sustituir los iconos genéricos de sitio por logotipos individuales de canal donde el emisor publique un asset específico; los 46 assets actuales tienen procedencia oficial (12 avatares oficiales de canal y 34 iconos de emisores/sitios, no todos logos individuales).
- Obtener endpoints HLS/DASH autorizados para habilitar reproducción integrada nativa; hoy las señales usan el reproductor oficial del emisor/YouTube y el conteo de streams nativos integrados sigue en cero.
- Completar la revisión de Diputados TV y del portal de Prasar Bharati; Crónica TV ya quedó publicada mediante su canal oficial de YouTube Live.
- Incorporar endpoints HLS/DASH realmente en directo y metadatos de proveedores con autorización verificable; el monitor actual no sustituye pruebas de reproducción en dispositivo.
- Agregar en Supabase Auth Redirect URLs: `miltvplus://auth/callback`.
- Preparar firma Android release con un keystore aportado de forma segura por el propietario.
- Probar APK en móviles y Android TV físicos (incluido D-pad).
- Completar Windows: cuenta, seguridad, ajustes y pruebas físicas de reproducción; verificar WebView2 Runtime en instalaciones limpias.
- Implementar entrega real de notificaciones push; hoy sólo existe la preferencia.
- Completar temporadas/episodios y grilla EPG.
- Validar accesibilidad, privacidad, licencias y publicación en tiendas.

## CI

- Android: pruebas JVM + `:app:assembleDebug` + artefacto APK.
- Windows: publicación x64 autocontenida + instalador Inno Setup.
- No hay workflow ni artefacto de publicación Web/PWA.
