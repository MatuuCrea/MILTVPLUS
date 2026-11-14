# MILTVPLUS · hoja única de trabajo, QA y publicación

**Actualizado:** 14 de noviembre de 2026  
**Rama de trabajo:** `feat/responsive-mobile-navigation` → `main`  
**PR de referencia:** https://github.com/MatuuCrea/MILTVPLUS/pull/10

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
| Android CI | **Aprobado** en SHA `117e25a12990e6dd1c12c43d8d266f01016ceaef`; run #324: pruebas JVM, APK debug y artefacto correctos | QA físico móvil y TV sigue pendiente |
| Windows CI | **Aprobado** en SHA `117e25a12990e6dd1c12c43d8d266f01016ceaef`; run #165: publicación x64 e instalador Inno Setup correctos | Instalación limpia, WebView2/LibVLC y DPI en equipos reales siguen pendientes |
| Android responsive | Navegación compacta inferior y sidebar en pantallas amplias incorporadas en PR #10 | D-pad, foco, Back y reproducción con mando en hardware |
| Windows responsive | Ajuste de ancho mínimo, sidebar, márgenes y barra de herramientas incorporados; el cambio pasó Windows CI en el SHA indicado arriba | Verificar resoluciones bajas, escalado 100–200 % y controles no recortados en hardware real |
| TV en vivo | Directorio de páginas oficiales; monitor de disponibilidad | No hay endpoints HLS/DASH directos con derechos de redistribución verificados |
| Música | Reproductor/cola en clientes; búsquedas hacia proveedores oficiales en Windows | Catálogo propio de pistas y URLs de reproducción autorizadas: actualmente cero |
| Películas y series | Pantallas/metadatos TMDB en Android según documentación del proyecto | Temporadas/episodios, acciones completas y derechos de reproducción; TMDB no entrega licencias ni streams |
| EPG | Estructura/campos y sección descritos en el proyecto | Grilla poblada con datos licenciados, zona horaria, actualización y pruebas |
| Windows funcional | WPF, LibVLC y WebView2; controles de reproducción y catálogo | Paridad de Auth, favoritos, +18, ajustes, EPG y flujos Android pendientes |
| Publicación | Workflows Android/Windows preparados | Release firmada, instalación limpia, QA, atribuciones y notas de versión |
| Samsung/LG | No son objetivos del APK Android | Apps nativas independientes para Tizen y webOS |

## Prioridad P0 · estabilizar la rama actual\n\n**Estado CI actual:** Android #324 y Windows #165 terminaron con conclusión `success` en el SHA `117e25a12990e6dd1c12c43d8d266f01016ceaef`. Esto verifica compilación, pruebas automatizadas y empaquetado; no equivale a QA físico ni a publicación. El PR #10 sigue abierto y no se fusionó.

1. Mantener PR #10 abierto hasta validar el último SHA y revisar los dos workflows.
2. Corregir únicamente fallos reproducibles de CI; no ocultar errores ni relajar pruebas para conseguir un verde artificial.
3. Android móvil: probar instalación, navegación inferior, búsqueda, TV, reproducción autorizada, favoritos, cuenta, ajustes, rotación y recuperación de errores.
4. Android TV/Google TV/TV Box/TV Stick Android: probar foco visible, D-pad, OK, Back, navegación sin pantalla táctil, teclado/mando y controles de Media3. Registrar marca/modelo, versión de Android, ABI, RAM y códecs.
5. Windows 10/11: probar ventana compacta y maximizada, resoluciones 1280×720 y 1366×768, escalado DPI 100/125/150/200 %, búsqueda, barra de herramientas, pantalla completa, importación M3U/M3U8, LibVLC y WebView2. Confirmar que el scroll horizontal de la barra superior es accesible.
6. Revisar los textos y recursos de interfaz para que no sugieran planes «Premium» o monetización. El compromiso visible debe seguir siendo gratuito y sin anuncios.

**Salida P0:** CI verde para el SHA exacto, revisión del diff y registro de pruebas manuales. No fusionar ni publicar automáticamente.

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
