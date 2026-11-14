# Matriz de compatibilidad y publicación por dispositivo

Este documento define lo que debe existir y probarse antes de declarar cada plataforma como disponible. Un workflow verde demuestra que el proyecto compila en CI; no demuestra por sí solo compatibilidad con todos los equipos físicos.

## Android móvil y tablet
- **Paquete:** APK Android nativo, firmado con la clave de publicación del propietario.
- **Base:** Kotlin, Jetpack Compose, Media3 y minSdk 23 (Android 6.0); confirmar compatibilidad real con cada versión y fabricante antes de anunciarla.
- **Recursos:** icono adaptativo/launcher MILTVPLUS, gráficos de marca, permisos mínimos, HTTPS, almacenamiento seguro de sesión, player Media3 y catálogo autorizado.
- **Pruebas:** instalación limpia y actualización, login/recuperación, reproducción HLS/HTTPS autorizada, rotación, audio/subtítulos, favoritos, PIN parental, importación M3U limitada, accesibilidad, consumo y manejo de errores.
- **Distribución:** APK release firmado; publicar versión, arquitectura, tamaño, SHA-256 y certificado esperado. Nunca distribuir APK debug como versión final.

## Android TV y Google TV
- **Paquete:** variante Android TV compatible; el manifiesto declara Leanback y pantalla táctil opcional.
- **Recursos:** banner TV original, icono launcher, interfaz de diez pies, foco visible, estados de selección, navegación D-pad/OK/Back y controles accesibles desde mando.
- **Pruebas:** emulador Android TV y al menos un dispositivo físico; navegación completa sin pantalla táctil, foco en todos los controles, reproducción y recuperación de errores con mando.
- **Salida:** no declarar compatible hasta completar prueba real de control remoto y validar instalación en el dispositivo objetivo.

## TV Box y TV Stick / dongle HDMI
- **Qué significa:** TV Box es un decodificador externo; TV Stick o dongle HDMI es un dispositivo compacto conectado al HDMI. Algunos usan Android TV/Google TV y otros sistemas propietarios.
- **Compatibilidad:** sólo equipos con Android compatible que superen la matriz Android TV. No todos los TV Box/TV Stick son Android ni todos soportan los mismos códecs, DRM, memoria o arquitectura.
- **Recursos:** paquete Android TV cuando corresponda, icono/banner TV, navegación D-pad, soporte para control remoto y validación del reproductor/códecs.
- **Pruebas:** registrar marca/modelo, versión del SO, ABI/CPU, RAM, resolución, códecs y resultado. Probar instalación, foco, reproducción, suspensión/reanudación y rendimiento por familia.
- **No prometer:** compatibilidad universal con cualquier aparato HDMI.

## Windows 10/11 x64
- **Paquete:** aplicación nativa WPF/.NET 8 autocontenida y un instalador Inno Setup .exe.
- **Recursos:** ejecutable x64, runtime .NET y bibliotecas nativas incluidas, WebView2 Runtime Evergreen como requisito para el visor integrado de páginas oficiales, icono .ico original en varios tamaños, manifiesto, desinstalador y firma Authenticode recomendada para reducir advertencias SmartScreen.
- **Pruebas:** instalación y desinstalación en Windows limpio, primer inicio sin .NET preinstalado y con/sin WebView2 Runtime, actualización, permisos, catálogo, reproducción HLS/HTTPS autorizada mediante LibVLC, apertura de páginas oficiales con WebView2, cambio de canal, pantalla completa, importación M3U limitada, audio, escalado DPI y errores.
- **Alcance actual:** el cliente Windows es una base funcional en desarrollo; aún no tiene paridad Android en autenticación, favoritos, PIN parental, ajustes, EPG y otras funciones. No anunciar equivalencia hasta implementar y probar esas áreas.

## Samsung Tizen
- **Proyecto nativo inicial:** `platforms/tizen/`; manifiesto TV, icono de marca, catálogo Supabase publicado, foco remoto, flechas/OK/BACK y adaptador AVPlay para HLS/DASH HTTPS autorizados.
- **Paquete:** WGT firmado por Tizen Studio y perfil/certificado Samsung TV.
- **Estado:** código integrado; SDK, certificado del propietario, compilación WGT y QA por generación/modelo todavía pendientes. AVPlay y codecs varían por modelo.

## LG webOS
- **Proyecto nativo inicial:** `platforms/webos/`; appinfo, iconos, catálogo Supabase publicado, foco remoto, flechas/OK/BACK y HTML5 video para formatos admitidos.
- **Paquete:** IPK firmado por el flujo oficial webOS TV SDK/CLI.
- **Estado:** código integrado; SDK, firma, compilación IPK y QA por versión/modelo todavía pendientes. HTML5 video no garantiza soporte universal HLS/DASH.

Un APK no se instala directamente en Tizen o webOS. Ver `docs/NATIVE-TV-CLIENTS.md`.

## Web oficial
- **Función actual:** portal informativo, novedades, ayuda y distribución de instaladores oficiales cuando estén listos.
- **No es:** una PWA ni un reproductor web de MILTVPLUS. La web no sustituye las aplicaciones nativas.

### Controles de escritorio Windows
- `F11`: alternar pantalla completa; `Esc`: salir de pantalla completa.
- `Ctrl + ← / →`: canal anterior / siguiente.
- `Espacio`: pausar o reanudar la reproducción actual.
- `M`: silenciar o restaurar el audio.
- `Ctrl + O`: importar una lista M3U/M3U8 local (límite de 5 MB; se aceptan sólo URLs HTTPS).
- `Ctrl + R`: actualizar la sección actual.
- `Ctrl + F`: enfocar la búsqueda.
- Los atajos de reproducción y navegación no interceptan teclas mientras se escribe en campos de texto o se editan filtros.

## Criterios comunes de salida
1. Fuentes y metadatos de contenido con derechos, territorios y vigencia documentados; no publicar streams inventados ni no autorizados.
2. Firma de publicación y verificación SHA-256 de cada instalador.
3. CI verde para el commit/tag exacto que se publica.
4. QA manual documentado en dispositivos físicos de cada familia anunciada.
5. Pruebas de seguridad, privacidad, accesibilidad, recuperación de errores y rendimiento.
6. Notas de versión, requisitos, limitaciones conocidas, actualización y soporte.
7. Si una función no está implementada o probada, etiquetarla como «en desarrollo» o «compatibilidad en validación», no como disponible.

## Estado conocido al 14 de noviembre de 2026
- Android CI y Windows installer CI pasaron en commits recientes; eso confirma compilación/paquetizado en runners, no QA físico.
- Android release signing requiere los secretos protegidos ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS y ANDROID_KEY_PASSWORD. Sin ellos, el workflow falla intencionalmente y no publica un APK debug como final.
- El cliente Windows compila y empaqueta, incorpora LibVLC y WebView2 para reproducción directa y páginas oficiales, pero no tiene paridad completa con Android. La ventana adapta barra lateral, márgenes y barra de herramientas a anchos compactos; queda pendiente probar escalado DPI, resoluciones bajas y desbordes en hardware real.
- El catálogo no debe poblarse con fuentes no autorizadas ni datos ficticios.
