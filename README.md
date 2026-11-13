# MILTVPLUS V22.34.9

MILTVPLUS es un producto de aplicaciones instalables nativas: Android (teléfonos, Android TV y TV Box) y Windows x64. No se ofrece una PWA ni una página web como aplicación.

## Android

Cliente nativo Kotlin, Jetpack Compose y Android Media3.
- Navegación para TV en vivo, películas, series, deportes, radios, música, teatro, EPG, cuenta y ajustes.
- Reproducción Media3/HLS de fuentes HTTPS autorizadas.
- Importación M3U/M3U8 local con límite de 5 MB y validación HTTPS.
- Catálogo Supabase, favoritos, preferencias, Auth y sesión cifrada con Android Keystore.
- Control +18 con PIN PBKDF2 y bloqueo temporal.
- Icono Android con logotipo MILTVPLUS y banner Android TV.

Construcción:
```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`. La APK debug se firma con la clave debug automática; no es una release firmada para distribución comercial.

## Windows

Cliente de escritorio nativo WPF/.NET 8 en `windows/MILTVPLUS.Desktop`. No utiliza navegador ni WebView. Incluye navegación de secciones, búsqueda, consulta por tabla con proyecciones compatibles con el esquema real de Supabase y generación automática del icono Windows desde el logotipo de marca para el ejecutable y el instalador.

El workflow `.github/workflows/windows.yml` publica la aplicación x64 autocontenida y construye un instalador `MILTVPLUS-Setup-x64.exe` con Inno Setup. El workflow `.github/workflows/release-native.yml` prepara APK y EXE como activos directos de una GitHub Release al etiquetar `v*`; el APK generado actualmente es debug-signed y no debe tratarse como versión comercial. El cliente Windows incorpora reproducción nativa mediante LibVLC (incluido soporte HLS según el formato y códec del proveedor), validación HTTPS antes de iniciar un flujo e importación local M3U/M3U8 con límite de 5 MB. La lista local no se sincroniza con la nube ni se considera verificada por el mero hecho de usar HTTPS. Todavía faltan Auth, favoritos, control +18, preferencias, EPG/series completas y QA físico; no se declara paridad funcional con Android.

## Catálogo y derechos

Proyecto Supabase: `rbnuxapfsnaabrzuxvdy`. Los clientes sólo usan la clave publishable. Nunca colocar service_role, secret keys ni contraseñas en el cliente.

El catálogo estaba vacío en la última auditoría. No se agregan canales, películas, series, eventos, radios ni enlaces ficticios. Cada publicación requiere titular, referencia de derechos, territorios y vigencia. La autorización del propietario de esta aplicación para desarrollar el software no sustituye las licencias de los titulares de cada emisión.

## Pendientes externos y de producto

- Incorporar fuentes y metadatos de proveedores con autorización verificable.
- Agregar en Supabase Auth Redirect URLs: `miltvplus://auth/callback`.
- Preparar firma Android release con un keystore aportado de forma segura por el propietario.
- Probar APK en móviles y Android TV físicos (incluido D-pad).
- Completar Windows: reproducción, cuenta, seguridad, ajustes y pruebas.
- Implementar entrega real de notificaciones push; hoy sólo existe la preferencia.
- Completar temporadas/episodios y grilla EPG.
- Validar accesibilidad, privacidad, licencias y publicación en tiendas.

## CI

- Android: pruebas JVM + `:app:assembleDebug` + artefacto APK.
- Windows: publicación x64 autocontenida + instalador Inno Setup.
- No hay workflow ni artefacto de publicación Web/PWA.
