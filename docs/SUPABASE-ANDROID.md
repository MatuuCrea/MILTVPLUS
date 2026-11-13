# Conexión Android ↔ Supabase

La aplicación consulta Supabase REST en modo de solo lectura para los catálogos públicos publicados. El acceso queda limitado por las políticas RLS de cada tabla y por el filtro `is_published=eq.true`.

- URL del proyecto: `https://rbnuxapfsnaabrzuxvdy.supabase.co`
- La clave incluida en `BuildConfig` es una clave **publishable** (pública), no una clave `service_role`.
- No se incluye contraseña PostgreSQL ni secreto de servidor en el APK.
- Tablas consultables: `catalog_items`, `radio_stations`, `sports_events`, `music_items`, `theater_events`, `epg_programs`.
- La consulta usa HTTPS, límites de tiempo y una lista permitida de tablas.
- Las tablas vacías siguen mostrando estados vacíos: no se fabrican canales, eventos ni enlaces de reproducción.

Esta primera integración no implementa todavía inicio de sesión, escrituras autenticadas, sincronización de favoritos ni alta remota de fuentes. Esas operaciones requieren Auth y políticas específicas por usuario; no se deben habilitar con permisos anónimos.
