# MILTVPLUS · hoja única de trabajo, QA y publicación

**Actualizado:** 14 de noviembre de 2026  
**Rama principal actual:** `main`  
**Integraciones recientes:** PR #10 (navegación responsive), #13 (clientes nativos Tizen/webOS y Fire TV), #15 (navegación Smart TV y derechos regionales) y #16 (vigencia de derechos en Android/Windows) están fusionadas.

Este documento reúne el orden de trabajo y los criterios de salida. Una tarea sólo pasa a «completa» cuando existe implementación verificable y la prueba correspondiente; compilar no equivale a probar en un dispositivo real.

## Reglas que no se negocian

- MILTVPLUS permanece 100 % gratuita: sin anuncios, banners comerciales, suscripciones, compras, pagos, donaciones solicitadas, afiliación ni SDK de Ads/Billing.
- No incorporar señales, películas, series, música ni listas de terceros sin autorización y procedencia comprobables.
- HTTPS por sí solo no demuestra derechos. Una página oficial, un marcador «en vivo» o un manifiesto accesible tampoco prueban reproducción continua ni permiso de redistribución.
- No guardar secretos en el cliente, repositorio, documentación ni mensajes. No alterar secretos ni servicios de backend sin necesidad y autorización explícitas.
- Preservar identidad visual y funciones existentes. Marcar como pendiente cualquier función sin prueba integral.

## Estado verificado al 14 de noviembre de 2026

| Área | Estado comprobable | Qué falta |
|---|---|---|
| Android CI | **Aprobado** en SHA `6ee906f0536f387eb99387b449787e06078c249f`; run #327: pruebas JVM, APK debug y artefacto correctos | QA físico móvil y TV sigue pendiente |
| Windows CI | **Aprobado** en SHA `6ee906f0536f387eb99387b449787e06078c249f`; run #168: publicación x64 e instalador Inno Setup correctos | Instalación limpia, WebView2/LibVLC y DPI en equipos reales siguen pendientes |
| Android responsive | Navegación compacta inferior y sidebar en pantallas amplias incorporadas en PR #10 | D-pad, foco, Back y reproducción con mando en hardware |
| Windows responsive | Ajuste de ancho mínimo, sidebar, márgenes y barra de herramientas incorporados; el cambio pasó Windows CI #168 en el SHA indicado arriba | Verificar resoluciones bajas, escalado 100–200 % y controles no recortados en hardware real |
| TV en vivo | Directorio de páginas oficiales; monitor de disponibilidad | No hay endpoints HLS/DASH directos con derechos de redistribución verificados |
| Música | Reproductor/cola en clientes; búsquedas hacia proveedores oficiales en Windows | Catálogo propio de pistas y URLs de reproducción autorizadas: actualmente cero |
| Películas y series | Pantallas/metadatos TMDB en Android según documentación del proyecto | Temporadas/episodios, acciones completas y derechos de reproducción; TMDB no entrega licencias ni streams |
| EPG | Estructura/campos y sección descritos en el proyecto | Grilla poblada con datos licenciados, zona horaria, actualización y pruebas |
| Windows funcional | WPF, LibVLC y WebView2; controles de reproducción y catálogo | Paridad de Auth, favoritos, +18, ajustes, EPG y flujos Android pendientes |
| Publicación | Workflows Android/Windows preparados | Release firmada, instalación limpia, QA, atribuciones y notas de versión |
| Samsung Tizen | Cliente nativo inicial en `platforms/tizen/`, catálogo RLS, D-pad y AVPlay | WGT firmado, SDK/certificado y QA físico por modelo |
| LG webOS | Cliente nativo inicial en `platforms/webos/`, catálogo RLS, D-pad y HTML5 video | IPK firmado, SDK y QA físico por versión |
| Fire TV | APK Android compartido como punto de partida | Validar sideload, launcher, D-pad y codecs por modelo |

## Prioridad P0 · estabilizar main tras PR #16

**Última integración:** PR #16 fue fusionada por squash el 14 de noviembre de 2026 en el SHA `53e2dcfca1a4942b56e5cab58ea2abc4be8e40b0`. En su SHA exacto de PR `4def816845929fb54ce7f7f552f6f1051cd3f133`, Android CI #367, Windows Desktop #177 y Smart TV Clients #28 finalizaron con `success`. Tras el merge, Smart TV Clients del SHA de main terminó correctamente; Android CI y Windows Desktop del SHA de merge se encuentran ejecutándose en la última comprobación y deben confirmarse antes de declarar main completamente validada.

1. Revisar los tres workflows del SHA de merge y corregir cualquier fallo reproducible sin relajar pruebas.
2. Android móvil: probar instalación, navegación, búsqueda, TV, reproducción autorizada, favoritos, cuenta, ajustes, rotación y recuperación de errores.
3. Android TV/Google TV/TV Box/Fire TV compatible: probar foco visible, D-pad, OK, Back, navegación sin pantalla táctil, controles Media3 y códecs en equipos reales.
4. Windows 10/11: probar instalación limpia, ventana compacta/maximizada, DPI 100–200 %, búsqueda, pantalla completa, M3U/M3U8, LibVLC y WebView2.
5. Samsung Tizen y LG webOS: compilar con SDK oficial, firmar WGT/IPK y ejecutar QA con mandos físicos por modelo.
6. Mantener las reglas de gratuidad, derechos activos y ausencia de fuentes inventadas.

**Salida P0:** tres CI verdes para el SHA de merge y evidencia separada de QA físico. Un CI verde no equivale a certificación de hardware ni de derechos de emisión.

## Prioridad P1 · completar funciones y probarlas de punta a punta

### Catálogo y reproducción
- Mantener separados metadatos, páginas oficiales y fuentes reproducibles. Cada señal nativa requiere URL HLS/DASH real, titular, referencia de autorización, territorios, vigencia y prueba de audio/vídeo.
- La lista M3U/M3U8 local es aportada por la persona usuaria: validar formato, tamaño, HTTPS y errores; no certificar ni sincronizar sus derechos.
- Para películas/series, implementar acciones de ficha, favoritos/Like, compartir, tráiler oficial y reproducir sólo cuando exista un proveedor autorizado. Si no hay fuente licenciada, mostrar metadatos y el estado real sin botón engañoso.
- Para música, terminar la integración de catálogo sólo con archivos o streams autorizados; validar cola, segundo plano, controles del sistema, reanudación y errores en Android/Windows.
- Completar EPG: fuente de datos licenciada, relación canal-programa, UTC/zona local, horario de verano, programa actual/siguiente, grilla, búsqueda y refresco.
- Revisar el control +18 en todos los puntos de entrada, bloqueo temporal, almacenamiento del PIN, cambio/restablecimiento y comportamiento sin sesión.
- Probar Auth, expiración/renovación de sesión, cierre de sesión, recuperación, favoritos y preferencias con dos dispositivos y errores de red.
- Implementar notificaciones push reales sólo si se decide usar un proveedor compatible con la política de gratuidad; la preferencia de notificaciones no equivale a entrega push.

### Windows parity
- Definir y completar flujos de cuenta/Auth, favoritos sincronizados, control +18, preferencias, EPG, estados vacíos, errores y actualización.
- No duplicar reglas de autorización en el cliente: mantener la política de acceso en backend/RLS.
- Añadir pruebas automatizadas de parsing de M3U, validación HTTPS, filtros, navegación y estado del reproductor donde sea viable.

### TMDB
- No activar la integración pública hasta comprobar términos aplicables al uso no comercial, incluir el logotipo oficial permitido y el aviso exacto: “This product uses the TMDB API but is not endorsed or certified by TMDB.”
- Mantener el token exclusivamente del lado servidor. TMDB aporta metadatos/imágenes, nunca derechos de reproducción.

## Prioridad P2 · distribución y publicación

### Android
- Generar APK release firmada con la clave protegida del propietario; nunca usar APK debug como release.
- Verificar firma, SHA-256, versionCode/versionName, ABI, minSdk, permisos y actualización sobre una instalación previa.
- Probar instalación limpia y actualización en móvil y Android TV. Comprobar que el mecanismo de descarga informa claramente y solicita confirmación del sistema.
- Publicar notas de versión, dispositivos/arquitecturas soportados, limitaciones conocidas, hash y canal oficial de descarga.
- Si se publica en Google Play, completar ficha, clasificación de contenido, política de privacidad, seguridad de datos, capturas, iconos y revisión de políticas antes de anunciar disponibilidad.

### Windows
- Probar el instalador x64 en Windows 10/11 limpio, con y sin .NET preinstalado, y con/sin WebView2 Runtime.
- Verificar instalación, actualización, desinstalación, accesos directos, permisos, dependencias nativas, LibVLC y SHA-256.
- Evaluar certificado Authenticode y firma del instalador/ejecutable. Sin certificado válido, comunicar advertencias de SmartScreen con honestidad.
- Publicar EXE, hashes, notas, requisitos y ruta de soporte en una release oficial.

### Seguridad y calidad común
- Revisar secretos, permisos mínimos, RLS, JWT, almacenamiento local, logs, privacidad, accesibilidad, contraste, lectores de pantalla, foco, consumo, memoria y rendimiento.
- Ejecutar pruebas de regresión y de carga sólo sobre servicios propios/autorizados. No desplegar relay, transcodificador ni CDN con costos recurrentes sin dimensionamiento y aprobación.
- Mantener artefactos reproducibles, SHA del código publicado y trazabilidad entre tag, workflows y archivos de distribución.

## Prioridad P3 · plataformas con desarrollo separado

**Actualización 14/11/2026:** se añadieron proyectos iniciales Tizen y webOS en `platforms/tizen/` y `platforms/webos/`, con manifiestos, recursos de marca, lectura del catálogo publicado, controles remotos y adaptadores de reproducción. Se añadió validación estática en `smart-tv-clients.yml`. No existen todavía WGT/IPK firmados ni QA físico; requieren SDK y certificados del propietario. Ver `docs/NATIVE-TV-CLIENTS.md`.


### Samsung Smart TV · Tizen
- Crear aplicación de TV independiente con Tizen Studio y el SDK/TV Extensions de Samsung; no reutilizar ni presentar el APK Android como instalador Tizen.
- Definir navegación de diez pies, foco y teclas del control remoto, ciclo de vida, red, almacenamiento y compatibilidad por año/modelo.
- Integrar reproducción con APIs y formatos oficialmente soportados por la familia de televisores objetivo; verificar codecs, subtítulos, DRM y restricciones regionales con documentación del fabricante.
- Crear paquete firmado, certificados de desarrollo/distribución, pruebas en emulador y televisores físicos, y preparar publicación en Samsung Apps/Developer Portal.

### LG Smart TV · webOS
- Crear aplicación independiente con webOS TV SDK/CLI y APIs de TV; no instalar APK Android.
- Implementar navegación con Magic Remote/D-pad, foco, ciclo de vida, reproducción soportada, subtítulos y manejo de memoria/red.
- Empaquetar IPK, firmar con el flujo de desarrollador, probar en emulador y equipos físicos por versión de webOS, y preparar revisión/publicación en LG Content Store.

### Arquitectura compartida sin fingir una app universal
- Compartir contratos de API, modelos de catálogo, reglas de autorización, identidad visual y documentación donde sea técnicamente razonable.
- Mantener clientes, reproductores, paquetes, firmas, QA y ciclos de publicación separados para Android, Windows, Tizen y webOS.
- No anunciar compatibilidad con un fabricante/modelo hasta superar pruebas físicas de esa familia.

## Bloqueos que requieren insumos externos

- Autorizaciones/endpoints reales de titulares de canales, música, películas y series.
- Modelos físicos de Android TV/TV Box/TV Stick, Samsung y LG para QA.
- Confirmación de términos/atribución TMDB antes de uso público.
- Certificado Authenticode para reducir advertencias en Windows.
- Acceso y revisión de tiendas/cuentas de desarrollador para publicaciones oficiales.
- Aprobación de presupuesto antes de cualquier infraestructura de streaming propia con costos recurrentes.

## Regla de cierre

Cada elemento se marca como **completo** sólo con: commit identificable, CI verde en ese SHA cuando aplique, prueba funcional registrada, limitaciones documentadas y autorización de contenido comprobable. Si falta hardware, licencia, certificado o acceso de tienda, queda explícitamente bloqueado; no se simula ni se declara terminado.
