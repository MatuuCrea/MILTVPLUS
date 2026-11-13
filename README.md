# MILTVPLUS V22.34.9

Aplicación Android nativa para Android, Android TV y Google TV. Kotlin, Jetpack Compose y Media3.

## Incluido
- Navegación Inicio, TV, Fuentes, Favoritos y Ajustes.
- Importación local M3U/M3U8 con SAF, validación y agrupación.
- Búsqueda y favoritos de sesión.
- Media3 ExoPlayer + HLS como dependencias del motor.
- Esquema inicial Supabase con RLS y CI de Android.

## Uso
Abrir Fuentes y seleccionar un archivo M3U/M3U8 propio o autorizado. No se incluyen canales de prueba falsos. M3U es un formato, no una licencia de retransmisión.

## Construcción
JDK 17 + Android SDK 35. Ejecutar `gradle :app:testDebugUnitTest :app:assembleDebug` desde la raíz.

## Estado real
Esta entrega incorpora la base Android y CI; no se afirma que haya APK compilado/probado ni backend desplegado. Persistencia, integración completa de superficie/lifecycle Media3, EPG, VOD, cuentas y clientes Tizen/webOS siguen pendientes de implementación y QA. Tizen/webOS requieren apps separadas.

## Marca
El único logo oficial es la imagen de referencia del propietario. No se sustituye por un icono genérico. El PNG maestro aún debe incorporarse como recurso binario de GitHub; este commit no afirma que ese archivo ya esté allí.
