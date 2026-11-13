# MILTVPLUS Windows · reproducción de canales

## Modos de reproducción

1. **Stream directo autorizado:** LibVLC reproduce el endpoint HTTPS HLS/DASH. El selector adaptativo y la decodificación disponible dependen de las variantes que publique el titular y de la GPU/CPU del equipo.
2. **Página oficial del emisor:** Microsoft Edge WebView2 muestra la página oficial dentro de MILTVPLUS. Se utiliza el reproductor publicado por el titular; no se extraen manifiestos privados, no se evaden DRM ni restricciones geográficas y no se convierte una página web en un stream nativo.

Al seleccionar cualquiera de esos dos tipos de ficha, la ventana pasa a pantalla completa. Los botones Anterior/Siguiente recorren las fichas reproducibles del filtro actual. Escape/F11 y el botón de pantalla completa permiten volver al modo ventana.

## Requisitos de WebView2

- Windows 10/11 x64 compatible.
- Microsoft Edge WebView2 Evergreen Runtime instalado.
- Conexión HTTPS al dominio oficial del canal.
- Cookies, DRM, región y política de autoplay pueden depender del emisor y de su plataforma. Algunos sitios pueden rechazar la vista embebida; en ese caso MILTVPLUS muestra el error dentro de la aplicación y permanece abierta; no lanza el navegador predeterminado desde la reproducción de TV.

La aplicación no descarga ni distribuye el video del emisor cuando usa este modo.

## Validación antes de publicar

- Compilar el proyecto WPF x64 en CI.
- Instalar en Windows limpio con WebView2 Runtime disponible y ausente.
- Probar al menos un stream HLS/DASH autorizado con LibVLC.
- Probar páginas oficiales en WebView2, navegación HTTPS, pantalla completa y salida.
- Probar cambio anterior/siguiente entre una mezcla de streams directos y páginas oficiales.
- Verificar que un error del sitio no deja el reproductor bloqueado, mantiene MILTVPLUS abierta y permite cambiar de canal o salir del modo pantalla completa.
- Confirmar con hardware real la decodificación, sincronización y escalado; CI no sustituye esa prueba.

## Límites de catálogo

La existencia de una ficha o una URL oficial no prueba que el canal esté transmitiendo en directo en ese instante. El monitor técnico de URLs tampoco certifica reproducción continua, licencia ni disponibilidad mundial. No publicar como «directo verificado» un enlace que sólo lleve a programas grabados o a una portada genérica.
