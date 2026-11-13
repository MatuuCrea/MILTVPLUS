# MILTVPLUS V22.34.9

Aplicación Android nativa en Kotlin, Jetpack Compose y Media3, con orientación a móviles y Android TV / Google TV.

## Funciones implementadas en el repositorio

- Navegación Inicio, TV local, Fuentes, Favoritos y Ajustes.
- Importación de listas M3U/M3U8 locales mediante el selector de documentos de Android.
- Parser M3U con validación de cabecera, extracción de nombre/grupo/logo HTTPS y rechazo de esquemas locales.
- Persistencia local de listas y favoritos mediante SharedPreferences.
- Reproducción Media3/ExoPlayer para fuentes HTTPS y soporte de HLS mediante la dependencia correspondiente.
- Consulta REST de solo lectura a Supabase para filas publicadas de catálogo, radios, deportes, música, teatro y EPG.
- Estados de carga, error y catálogo vacío; no se generan canales ni enlaces de prueba falsos.
- GitHub Actions ejecuta pruebas JVM, `:app:assembleDebug` y publica el APK como artefacto.

## Uso

Abrir **Fuentes** y seleccionar una lista M3U/M3U8 propia, pública o licenciada. M3U/M3U8 es un formato técnico, no una autorización para retransmitir contenido.

El catálogo remoto sólo mostrará elementos que estén cargados y publicados en Supabase. No hay proveedores ni contenido autorizado cargado por defecto.

## Construcción

Requisitos: JDK 17, Android SDK 35 y Gradle 8.9. Desde la raíz:

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

El APK de depuración se genera en `app/build/outputs/apk/debug/app-debug.apk`. El artefacto de Actions se considera disponible sólo cuando el job confirma correctamente el paso `upload-artifact`.

## Backend Supabase

Proyecto: `rbnuxapfsnaabrzuxvdy`.

La aplicación tiene un cliente REST Android de sólo lectura que consulta las tablas publicadas `catalog_items`, `radio_stations`, `sports_events`, `music_items`, `theater_events` y `epg_programs`. La clave configurada en BuildConfig es una clave publishable pública; no se incluye una clave service_role ni una contraseña de base de datos.

Las tablas tienen Row Level Security (RLS). Las políticas públicas limitan la lectura a filas publicadas. Las tablas de fuentes y datos de usuario están restringidas por identidad autenticada. Las tablas de catálogo estaban vacías en la última verificación, por lo que el catálogo puede aparecer vacío hasta que se cargue contenido con derechos y se publique.

La integración actual no incluye inicio de sesión, escrituras autenticadas, sincronización de favoritos con la nube ni alta remota de fuentes. Esas funciones requieren Supabase Auth y políticas específicas por usuario antes de habilitar operaciones de escritura.

Más detalles: [docs/SUPABASE-ANDROID.md](docs/SUPABASE-ANDROID.md).

## Estado y pendientes conocidos

Implementado y comprobado por CI: pruebas JVM, compilación Debug y publicación del artefacto APK en la ejecución indicada por GitHub Actions.

Todavía pendiente de desarrollo y validación:
- Inicio de sesión y flujos de cuenta con Supabase Auth.
- Sincronización remota de favoritos, preferencias y fuentes por usuario.
- Consumo EPG por canal y horario; actualmente la sección consulta filas pero no construye una grilla de programación.
- Vistas de detalle y reproducción VOD con metadatos y proveedores autorizados.
- Catálogos reales, acuerdos/licencias y validación editorial de cada URL.
- Controles parentales y consentimiento de edad completos.
- Navegación D-pad/remote, pruebas de accesibilidad y QA físico en Android TV/Google TV y distintos móviles.
- Aplicaciones específicas para Tizen, webOS y Web; no están incluidas en este APK Android.

## Marca

El único logo oficial debe ser el archivo de marca aprobado por el propietario. No se reemplaza por iconos genéricos. El recurso binario original todavía debe incorporarse al repositorio.
