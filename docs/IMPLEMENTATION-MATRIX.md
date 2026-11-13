# MILTVPLUS — matriz real de implementación

La arquitectura de producto es nativa: Android y Windows. No existe cliente Web/PWA en el repositorio después de la retirada de sus archivos.

| Área | Estado actual | Pendiente verificable |
|---|---|---|
| Android móvil | Kotlin, Jetpack Compose, navegación, catálogo, Auth, favoritos, preferencias, comprobación de releases, descarga APK y verificación SHA-256 con instalador del sistema | QA físico, permiso de instalación y primera release firmada |
| Android TV / TV Box | Launcher Leanback, icono de marca, banner TV | QA D-pad y foco con mando real |
| Reproductor Android | Media3/HLS para HTTPS | Validación con fuentes de proveedores licenciados |
| M3U Android | Importación local/remota aprobada, límite 5 MB, HTTPS | QA con listas autorizadas reales |
| Cuenta Android | Supabase Auth, recuperación, cambio, logout, refresh, callback, sesión cifrada | Redirect URL en Dashboard y prueba con correo real |
| Favoritos y preferencias | Sincronización Supabase con RLS | Conflictos offline y QA multi-dispositivo |
| +18 Android | PIN PBKDF2, bloqueo temporal, declaración y filtros | Verificación de edad conforme a cada jurisdicción |
| Catálogo editorial | Tablas y reglas RLS/derechos | Catálogo vacío; incorporar proveedores y derechos reales |
| Series | Modelo de series/temporadas/episodios | Pantalla dedicada y contenido real |
| EPG | Consulta ordenada y agrupada por canal | Grilla temporal y datos de varios días |
| Deportes/radio/música/teatro | Modelos y navegación Android | Datos y fuentes autorizadas; Spotify sigue siendo enlace externo |
| Windows | Cliente WPF nativo .NET 8, navegación, búsqueda, catálogo publicado, reproductor LibVLC/HLS, importación local M3U/M3U8 HTTPS de hasta 5 MB, comprobación de releases y actualizador con SHA-256; workflow x64 + instalador | Auth, favoritos, +18, preferencias, EPG/series completas, pruebas físicas y validación de codecs/proveedores |
| Web/PWA | Retirada del repositorio | No es un producto ni se publicará |
| Tizen / webOS | No implementado | Clientes nativos separados si se decide ampliar plataformas |
| Marca | Logotipo incorporado a launcher Android y banner TV | Icono Windows .ico/.exe e instalador |
| Firma/publicación | APK debug de CI; pipeline de release firmado y checksum configurado; instalador Windows | Aportar keystore Android permanente y sus cuatro secretos; certificado Authenticode Windows; QA de instalación limpia |
| Push | Preferencia almacenada | Servicio y entrega push no implementados |

## Seguridad y publicación

- Las filas editoriales requieren titular, referencia de derechos, territorios y ventana de vigencia.
- Las fuentes de usuarios se registran deshabilitadas/no verificadas; sólo administración puede aprobarlas.
- Favoritos sólo pueden apuntar a elementos publicados.
- Ninguna clave privilegiada se incluye en los clientes.
- Una URL HTTPS no demuestra derecho de retransmisión.
- Una autorización para desarrollar la aplicación no concede derechos sobre canales o contenidos de terceros.

## Criterios de aceptación

1. Android JVM tests y assembleDebug verdes para el mismo SHA.
2. APK descargable e instalable en dispositivo Android físico.
3. Windows build y Setup x64 verdes en runner Windows.
4. Instalación/desinstalación probada en Windows limpio.
5. Auth, RLS, filtros regionales y control +18 probados.
6. Reproducción probada únicamente con streams licenciados.
7. D-pad, foco, accesibilidad y rendimiento probados en dispositivos reales.
