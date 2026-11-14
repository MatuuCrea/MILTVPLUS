# MILTVPLUS LG webOS TV

Aplicación nativa webOS empaquetable como IPK. Incluye catálogo Supabase publicado, navegación de diez pies con flechas/OK/BACK y HTML5 video para HLS/DASH admitidos por cada modelo.

## Construcción
1. Instalar LG webOS TV SDK/CLI oficial.
2. Configurar emulador o TV y modo Developer.
3. Empaquetar esta carpeta con ares-package y desplegar con ares-install según la guía de LG.
4. Firmar y validar el paquete en el flujo oficial de desarrollador.
5. Probar control remoto, foco, reproducción, suspensión, memoria y codecs por versión de webOS.

No se ha generado IPK: requiere SDK, firma y QA físico. HTML5 video no garantiza soporte universal de formatos. No raspar reproductores externos ni agregar service-role keys.