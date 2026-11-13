# MILTVPLUS — matriz real de implementación

Actualizada para la entrega integral de noviembre de 2026. “Implementado” describe código presente; “pendiente de validación” requiere pruebas o configuración externa.

| Área | Estado actual | Pendiente verificable |
|---|---|---|
| Inicio y navegación | Implementado | QA de accesibilidad, restauración de estado y navegación larga |
| Diseño oscuro/claro/sistema | Implementado | QA visual en pantallas pequeñas y TV |
| Colores de acento | Azul eléctrico, violeta y verde | QA de contraste WCAG |
| TV en vivo | Catálogo remoto + M3U local/remoto aprobado + Media3 | Fuentes/licencias reales y programación de proveedores |
| Películas | Modelo VOD, filtros y pósteres | Contenido real, ficha detallada y pruebas de reproducción |
| Series | Modelo series/temporadas/episodios | Ingesta real y pantalla dedicada de temporadas/episodios |
| Deportes | Catálogo, horarios y enlaces oficiales/autorizados | Feeds reales y licencias de retransmisión |
| Radios | Catálogo, logos, metadatos y reproducción | Emisoras publicadas con autorización |
| Música | Catálogo, enlaces externos y Spotify oficial | OAuth/SDK Spotify con Client ID aprobado |
| Teatro | Catálogo, recinto, fechas e información oficial | Datos reales y derechos de emisión cuando aplique |
| EPG | Consulta ordenada, agrupación por canal, hora local y marca EN VIVO | Grilla temporal completa y cobertura de varios días |
| +18 Android | PIN PBKDF2, bloqueo de intentos, declaración y catálogo filtrado | Verificación legal de edad por región y QA con contenido real |
| +18 Web | PIN PBKDF2 local y consentimiento en cuenta | Verificación legal de edad y QA |
| Países | ISO del runtime Android y lista regional Web; filtro por territorio licenciado | QA de cobertura y contratos por territorio |
| Cuenta/Auth Android | Registro, login, recuperación, cambio, logout, refresh, callback y sesión cifrada | Redirect URL Supabase y prueba real de email/dispositivo |
| Favoritos | CRUD nube Android/Web para catálogo UUID con RLS | Conflictos offline y favoritos M3U locales |
| Preferencias | Local y nube Android/Web: tema, región, idioma, audio, subtítulos, preferencia de avisos, autoplay y acento | Entrega push/reminders aún no desplegada; QA multi-dispositivo |
| Fuentes remotas | CRUD Android/Web por propietario; trigger fuerza disabled/unverified; importación sólo al aprobar | Revisión administrativa y QA de importación remota |
| Fuentes locales | Importador M3U/M3U8 con límite 5 MB, BOM UTF-8 y títulos con comas | QA adicional con playlists reales de proveedores autorizados |
| Android TV / TV Box | Launcher Leanback, banner y agrupación de foco | D-pad, foco visible, banner oficial y QA físico |
| Web/PWA | Cliente estático, Auth, catálogo, favoritos, preferencias, fuentes, +18 y caché estática; REST público usa sólo la clave publishable en `apikey`, reserva Bearer para sesiones de usuario | Hosting HTTPS, iconos de marca, QA de navegador y HLS integrado |
| Tizen / webOS | No implementado | Clientes separados con SDKs y pruebas de fabricante |
| Marca oficial | Pendiente | Incorporar archivo original del logo aprobado |
| Publicación | Debug CI existente | Firma release, claves privadas, privacidad, derechos y tiendas |
| Spotify | Enlace oficial externo | Aplicación Spotify registrada, OAuth y permisos de reproducción |
| CI/CD | Workflow JVM tests + assembleDebug + APK artifact | Confirmar resultado para el commit integral actual |

## Seguridad y publicación

- Una fila no puede publicarse sin titular, referencia de derechos, regiones y fecha de inicio.
- Un trigger bloquea publicaciones antes del inicio y después del vencimiento de derechos.
- RLS define el acceso por fila; grants SQL limitan además por rol.
- Los usuarios no pueden aprobar ni habilitar sus propias fuentes M3U. Los favoritos sólo aceptan contenido publicado.
- Ninguna clave privilegiada se incluye en Android o Web.
- Una URL HTTPS no demuestra derecho de retransmisión.
- Un enlace informativo oficial no equivale a una transmisión.
- El catálogo vacío se representa como vacío: no se insertan datos ficticios.

## Criterios de aceptación

1. `:app:testDebugUnitTest` verde.
2. `:app:assembleDebug` verde.
3. APK subido para el mismo SHA.
4. Registro, confirmación, recuperación y cierre probados con cuenta real.
5. Dos usuarios no pueden leer/modificar filas privadas ajenas.
6. Filtros regionales y de clasificación no muestran contenido fuera de región ni +18 fuera de la sección protegida.
7. TV probada con mando D-pad y móvil en distintos tamaños.
8. Publicación sólo con derechos vigentes y verificados.
