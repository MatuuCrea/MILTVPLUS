# Instalación de prueba de MILTVPLUS

**Estado de estos paquetes:** compilaciones de CI para pruebas, no una release final firmada para distribución. El APK Android es `debug` y usa firma de depuración. El instalador Windows es x64, autocontenido, pero no tiene firma Authenticode. No usar estos paquetes como distribución pública.

## Descargas verificadas de CI

Estos artefactos corresponden a la última compilación Android validada de la PR #13 (ya fusionada en `main`) y al instalador Windows x64 validado en la PR #11:

- **Android móvil + Android TV/Google TV:** [Android CI #335 — artefacto `miltvplus-debug-apk`](https://github.com/MatuuCrea/MILTVPLUS/actions/runs/43552924214). Dentro del ZIP está `app-debug.apk`. Es un único APK universal: el manifiesto incluye launcher normal y Leanback; no son dos instaladores distintos ni dos APK separados.
- **Windows 10/11 x64:** [Windows Desktop #172 — artefacto `MILTVPLUS-Windows-Setup-x64`](https://github.com/MatuuCrea/MILTVPLUS/actions/runs/43552924265). Dentro del ZIP está `MILTVPLUS-Setup-x64.exe`.

Android CI #351 terminó en `success` para el código de la PR #13 (SHA `84bc1bd142973e32f4fad231911538ab706927ad`), fusionado en `main` mediante `863d58c4c298356f3d5cbe82f21758b026dee55e`. Windows Desktop #172 terminó en `success` para el código Windows sin cambios desde la PR #11. SHA-256 de los ZIP de Actions (no del APK/EXE extraído): Android `5bbfabfa31b00b921c77ab991aab649f292c122eed91b62cceea51014f27148c`; Windows `986f8b979a4fa0784472100c8f0ffab3df1fc860ce7c37d80e6ddc3ea73d533`.

En GitHub, abrí el enlace, bajá hasta **Artifacts**, descargá el ZIP correspondiente y extraé su contenido. Los artefactos de Actions tienen retención limitada y pueden caducar; si GitHub ya no los muestra, hay que volver a ejecutar CI desde la rama principal para obtener paquetes nuevos. No descargues instaladores desde páginas de terceros.

## Windows 10/11 x64 — `.exe`

1. En la PC, abrí el run **Windows Desktop #172** y descargá `MILTVPLUS-Windows-Setup-x64`.
2. Extraé el ZIP. Deberías obtener `MILTVPLUS-Setup-x64.exe`.
3. Comprobá que el archivo provenga del artefacto de GitHub Actions enlazado arriba. No lo ejecutes si el nombre, origen o descarga no coinciden.
4. Ejecutá el instalador. Elegí la carpeta de destino y completá el asistente.
5. Iniciá MILTVPLUS desde el acceso directo del menú Inicio o escritorio.
6. Para el reproductor de páginas oficiales se necesita Microsoft Edge WebView2 Runtime. Si falta, instalá el runtime Evergreen desde el sitio oficial de Microsoft y volvé a abrir la app.
7. LibVLC se utiliza para streams directos HLS/DASH autorizados. Si no hay un stream autorizado, la ficha abre la página del emisor y depende de sus restricciones.

**Advertencia de Windows:** el instalador de prueba no tiene certificado Authenticode. SmartScreen/Defender puede mostrar una advertencia de editor desconocido. No desactives Defender ni las protecciones del sistema para instalarlo. Si el archivo no coincide con el artefacto oficial, cancelá.

Desinstalación: Configuración → Aplicaciones → Aplicaciones instaladas → MILTVPLUS → Desinstalar.

## Android móvil (teléfono/tablet) — `.apk`

1. En el teléfono, abrí el run **Android CI #335** y descargá `miltvplus-debug-apk`.
2. Extraé el ZIP y localizá `app-debug.apk`.
3. Transferí el APK al teléfono mediante USB o una nube privada de tu propiedad.
4. Abrí el APK desde Archivos. Android puede pedir permiso temporal para que esa aplicación instale paquetes; autorizá sólo el gestor de archivos que estás usando y sólo para esta instalación.
5. Confirmá **Instalar** y luego abrí MILTVPLUS.
6. Si ya tenés una versión release firmada con otra clave, Android puede rechazar la actualización por incompatibilidad de firma. No desinstales una versión con datos que quieras conservar sin hacer antes una copia/exportación compatible.

El APK debug no es la versión de publicación y no debe compartirse como instalador final.

## Android TV / Google TV / TV Box Android — `.apk`

Se instala el mismo `app-debug.apk` universal de Android móvil. No se inventa un `.exe`, `.wgt` ni `.ipk` para estos equipos. El manifiesto Android declara launcher Leanback, banner de TV y pantalla táctil opcional; **todavía falta QA físico de navegación D-pad y reproducción**. Para Fire TV, el APK es candidato sólo en modelos con **Fire OS basado en Android**. Algunos Fire TV nuevos usan **Vega OS** y no pueden instalar APK Android; quedan excluidos de este paquete. La detección `amazon.hardware.fire_tv` fuerza la interfaz de TV en Fire OS, pero instalación, launcher, códecs y reproducción siguen pendientes de QA por modelo.

### Instalación por USB
1. Descargá el mismo `app-debug.apk` indicado en Android móvil.
2. Copialo a un pendrive.
3. Conectá el pendrive al TV/Box y abrí un gestor de archivos confiable.
4. Permití la instalación desde ese gestor sólo durante el proceso y confirmá la instalación.
5. Abrí MILTVPLUS desde Aplicaciones. Si el launcher no la muestra, revisá la lista completa de aplicaciones instaladas.

### Instalación mediante ADB (si el TV lo permite)
1. Activá Opciones de desarrollador en el TV y habilitá Depuración de red/USB.
2. Desde una PC con Android Platform Tools instalado, conectate a la IP que muestra el TV:
   ```bash
   adb connect IP_DEL_TV:5555
   adb install -r app-debug.apk
   ```
3. Aceptá la huella RSA en la pantalla del TV cuando se solicite.
4. Al terminar las pruebas, desactivá la depuración ADB.

No publiques la dirección IP del TV ni mantengas ADB habilitado en redes públicas.

## Samsung Tizen y LG webOS

El APK Android **no se instala** en televisores Samsung Tizen ni LG webOS. Esas plataformas necesitan aplicaciones nativas independientes: paquete Tizen firmado (`.wgt`/formato exigido por el SDK de la versión) y paquete webOS (`.ipk`). No hay instalador para esos sistemas en esta entrega.

## Primera prueba funcional: lista de comprobación

- [ ] La aplicación abre sin cerrarse.
- [ ] Se puede navegar por las secciones con pantalla táctil.
- [ ] En TV, el foco se ve y se puede recorrer con flechas, OK y Atrás.
- [ ] La búsqueda permite escribir y limpiar el texto.
- [ ] El directorio muestra el estado real de cada fuente.
- [ ] Una página oficial abre el reproductor del propio emisor.
- [ ] Un stream directo sólo se reproduce si tiene autorización y manifiesto válido.
- [ ] Si el emisor bloquea la reproducción, la app informa el problema y no se cierra.
- [ ] En Windows, funcionan ventana, búsqueda, pantalla completa, importación local y desinstalación.
- [ ] Se registra modelo de dispositivo, versión del sistema, resolución y resultado.

## Qué significa «canal disponible»

Una ficha de canal no demuestra por sí sola que exista un vivo en ese momento. Hay tres estados distintos:
1. Página oficial accesible.
2. Emisión en vivo confirmada por el reproductor del titular en ese momento y territorio.
3. Reproducción integrada en MILTVPLUS con autorización explícita de distribución.

El catálogo actual incluye páginas oficiales y reproductores de los emisores, pero no tiene streams HLS/DASH directos con derechos de redistribución verificados. No se puede prometer que todos los canales públicos estén emitiendo 24/7 ni que un emisor permita insertar su player en todos los dispositivos.

## Para cerrar la prueba física

Anotá para cada equipo: marca/modelo, versión Android/Windows, resolución, conexión (Wi-Fi/Ethernet), si la app instala, si navega, qué canal probaste y qué error exacto aparece. No compartas contraseñas, tokens ni claves de firma.
