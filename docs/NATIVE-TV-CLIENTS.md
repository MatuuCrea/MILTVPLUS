# Clientes nativos Smart TV — MILTVPLUS

| Dispositivo | Proyecto | Paquete | Estado |
|---|---|---|---|
| Android móvil | app/ Kotlin Compose | APK | CI compila; release y QA físico pendientes |
| Android TV / Google TV / Android TV Box | app/ con Leanback | APK universal Android | CI compila; D-pad físico pendiente |
| Fire TV / Fire OS | cliente Android compatible | APK Android | Compatibilidad por modelo y versión Fire OS pendiente |
| Windows 10/11 x64 | windows/ WPF .NET 8 | EXE Inno Setup | CI compila; Authenticode y QA físico pendientes |
| Samsung Tizen TV | platforms/tizen/ | WGT firmado | Código nativo inicial integrado; SDK/certificado/QA pendientes |
| LG webOS TV | platforms/webos/ | IPK firmado | Código nativo inicial integrado; SDK/certificado/QA pendientes |

## Tizen y webOS
Los nuevos clientes consultan sólo filas publicadas de tv_channels mediante la clave Supabase publishable y RLS. La navegación incluye flechas, OK, BACK y foco visible. Sólo se intenta reproducción integrada cuando playback_type es hls o dash y la URL es HTTPS. Tizen usa AVPlay; webOS usa HTML5 video. Los codecs y perfiles admitidos dependen del año/modelo. Las páginas official_web/youtube_live no se convierten en streams: se informa que requieren el reproductor del emisor.

Los WGT/IPK no se generan en este runner porque hacen falta SDK, perfiles de TV, certificados de desarrollo y pruebas/empaquetado propios del fabricante. No se declaran instaladores firmados ni QA físico completado.

## Fire TV
Se reutiliza como punto de partida el APK Android compatible; no hay un paquete distinto todavía. Antes de anunciar soporte deben probarse sideload, launcher, D-pad, foco, codecs, suspensión/reanudación y APIs disponibles sin depender de Google Play Services.

MILTVPLUS permanece gratuita y sin anuncios, pagos, suscripciones ni SDK de monetización. No publicar señales sin titular, referencia de autorización, territorios y vigencia verificables.
