# MILTVPLUS V22.34.9

Aplicación Android nativa para Android, Android TV y Google TV. Kotlin, Jetpack Compose y Media3.

## Incluido
- Navegación Inicio, TV, Fuentes, Favoritos y Ajustes.
- Importación local M3U/M3U8 con SAF, validación y agrupación.
- Búsqueda y favoritos de sesión.
- Media3 ExoPlayer + HLS como dependencias del motor.
- Backend Supabase MILTVPLUS: catálogo, perfiles, favoritos, fuentes, EPG, radios, eventos deportivos, música, teatro, preferencias y consentimiento de edad; RLS activado.
- CI Android para pruebas JVM y ensamblado Debug.

## Uso
Abrir Fuentes y seleccionar un archivo M3U/M3U8 propio o autorizado. No se incluyen canales de prueba falsos. M3U es un formato, no una licencia de retransmisión.

## Construcción
JDK 17 + Android SDK 35. Ejecutar `gradle :app:testDebugUnitTest :app:assembleDebug` desde la raíz.

## Backend
Proyecto Supabase: `rbnuxapfsnaabrzuxvdy` (región us-east-1, PostgreSQL 17).
La migración inicial se aplicó al proyecto y está versionada en `supabase/migrations/`. Las tres tablas tienen RLS activado. El catálogo sólo permite lectura de filas publicadas; perfiles y favoritos están limitados por `auth.uid()`.
Las tablas y políticas están creadas en producción, pero el cliente Android todavía no integra el SDK de Supabase ni sincroniza el catálogo remoto. Las tablas nuevas están vacías y no hay proveedores de contenido conectados. La clave publishable es pública; nunca agregar una secret key ni contraseña de Postgres al APK.

## Estado real
No se afirma que haya APK compilado/probado ni backend de producción con contenido cargado. La importación y persistencia local de listas/favoritos y la superficie Media3 están implementadas y pasan compilación automatizada. Pendientes: integración Android-Supabase y autenticación; sincronización real; consumo EPG/VOD; catálogos poblados con acuerdos/fuentes autorizadas; configuración persistente y controles completos; pruebas físicas en móviles/Android TV y clientes Tizen/webOS. No afirmar que estas áreas están terminadas hasta verificarlas. Tizen/webOS requieren apps separadas.

## Marca
El único logo oficial es la imagen de referencia del propietario. No se sustituye por iconos de stock ni variantes. El PNG maestro aún debe incorporarse como recurso binario de GitHub.
