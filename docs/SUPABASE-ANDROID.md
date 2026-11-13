# Conexión Android ↔ Supabase

La aplicación consulta Supabase REST en modo de solo lectura para los catálogos públicos publicados. El acceso queda limitado por las políticas RLS de cada tabla y por el filtro `is_published=eq.true`.

- URL del proyecto: `https://rbnuxapfsnaabrzuxvdy.supabase.co`
- La clave incluida en `BuildConfig` es una clave **publishable** (pública), no una clave `service_role`.
- No se incluye contraseña PostgreSQL ni secreto de servidor en el APK.
- Tablas consultables: `catalog_items`, `radio_stations`, `sports_events`, `music_items`, `theater_events`, `epg_programs`.
- La consulta usa HTTPS, límites de tiempo y una lista permitida de tablas.
- Las tablas vacías siguen mostrando estados vacíos: no se fabrican canales, eventos ni enlaces de reproducción.

Esta primera integración no implementa todavía inicio de sesión, escrituras autenticadas, sincronización de favoritos ni alta remota de fuentes. Esas operaciones requieren Auth y políticas específicas por usuario; no se deben habilitar con permisos anónimos.

## Auditoría de backend (13 de noviembre de 2026)

La conexión del proyecto fue verificada contra Supabase:

- Proyecto activo: `MILTVPLUS` (`rbnuxapfsnaabrzuxvdy`), región `us-east-1`, PostgreSQL 17.
- Las 11 tablas públicas existentes tienen RLS habilitado.
- Las migraciones registradas son `miltvplus_initial_catalog`, `miltvplus_extended_catalog` y `tighten_miltvplus_public_table_grants`.
- Las tablas consultadas no contienen registros: no hay canales, eventos, películas, radios ni programación publicada todavía.
- No hay Edge Functions desplegadas.
- El asesor de seguridad de Supabase no informó hallazgos en la comprobación realizada.
- Se comprobó que el rol `anon` conserva únicamente `SELECT` sobre las seis tablas de catálogo público. Se revocaron sus permisos sobre perfiles, favoritos, fuentes y datos privados.
- El rol `authenticated` tiene permisos mínimos por tabla y las políticas RLS restringen cada registro al propietario autenticado.

La migración de endurecimiento está versionada en `supabase/migrations/20261113150502_tighten_miltvplus_public_table_grants.sql` y ya fue aplicada al proyecto. Los avisos de índices no utilizados son informativos: las tablas están vacías y no justifican borrar índices antes de que exista tráfico real.

## Lo que sigue bloqueando la sincronización real

1. El cliente Android todavía no implementa Supabase Auth: no hay registro/login, renovación segura de sesión ni cierre de sesión.
2. Los favoritos sólo se guardan localmente. La tabla `user_favorites` ya tiene RLS por `user_id`, pero falta el cliente autenticado.
3. Las preferencias y fuentes también necesitan cliente autenticado y pruebas de aislamiento entre usuarios.
4. El catálogo necesita datos reales, metadatos completos y autorización territorial verificable. No se insertaron datos ficticios.
5. El proyecto Android debe volver a pasar JVM tests y `assembleDebug` para el SHA actual; la consulta de estados disponible no devolvió checks para los últimos commits, por lo que no se declara compilación aprobada.
