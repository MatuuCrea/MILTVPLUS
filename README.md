# MILTVPLUS V22.34.9

Aplicación Android nativa para Android, Android TV y Google TV. Kotlin, Jetpack Compose y Media3.

## Incluido
- Navegación Inicio, TV, Fuentes, Favoritos y Ajustes.
- Importación local M3U/M3U8 con SAF, validación y agrupación.
- Búsqueda y favoritos de sesión.
- Media3 ExoPlayer + HLS como dependencias del motor.
- Backend Supabase MILTVPLUS enlazado: catálogo, perfiles y favoritos con RLS.
- CI Android para pruebas JVM y ensamblado Debug.

## Uso
Abrir Fuentes y seleccionar un archivo M3U/M3U8 propio o autorizado. No se incluyen canales de prueba falsos. M3U es un formato, no una licencia de retransmisión.

## Construcción
JDK 17 + Android SDK 35. Ejecutar `gradle :app:testDebugUnitTest :app:assembleDebug` desde la raíz.

## Backend
Proyecto Supabase: `rbnuxapfsnaabrzuxvdy` (región us-east-1, PostgreSQL 17).
La migración inicial se aplicó al proyecto y está versionada en `supabase/migrations/`. Las tres tablas tienen RLS activado. El catálogo sólo permite lectura de filas publicadas; perfiles y favoritos están limitados por `auth.uid()`.
La conexión desde el cliente Android (SDK, autenticación y sincronización) aún no está implementada. La clave publishable es pública; nunca agregar una secret key ni contraseña de Postgres al APK.

## Estado real
No se afirma que haya APK compilado/probado ni backend de producción con contenido cargado. Persistencia local, integración de superficie/lifecycle Media3, integración Android-Supabase, EPG, VOD, cuentas y clientes Tizen/webOS siguen pendientes de implementación y QA. Tizen/webOS requieren apps separadas.

## Marca
El único logo oficial es la imagen de referencia del propietario. No se sustituye por iconos de stock ni variantes. El PNG maestro aún debe incorporarse como recurso binario de GitHub.
