# Integración Android/Web ↔ Supabase

Proyecto: `rbnuxapfsnaabrzuxvdy` · API URL `https://rbnuxapfsnaabrzuxvdy.supabase.co`.

## Cliente y autenticación

- Clientes usan sólo la clave publishable; no hay `service_role` ni secreto de servidor en APK/Web.
- Android implementa Supabase Auth REST: signup, password grant, refresh, logout, recuperación, actualización y callback.
- Access/refresh tokens Android se cifran con AES-GCM y Android Keystore. La renovación se serializa para evitar rotación simultánea.
- Callback móvil: `miltvplus://auth/callback`.
- Acción manual pendiente: agregar esa URL en Supabase Dashboard → Authentication → URL Configuration → Redirect URLs.
- El trigger de perfil/preferencias vive en `app_private.handle_new_miltvplus_user`, dentro de esquema no expuesto.
- Web conserva tokens en sessionStorage y no cachea respuestas privadas con el service worker.

## Sincronización

- `user_favorites`: lectura, inserción idempotente y eliminación con JWT; sólo UUID de `catalog_items`.
- `user_preferences`: upsert por `user_id` con tema, país, idioma, subtítulos, notificaciones, autoplay, acento e idioma de audio.
- `media_sources`: alta, listado y eliminación por propietario. El rol authenticated tiene SELECT/INSERT/DELETE, no UPDATE. Un trigger fuerza `is_enabled=false` y `is_verified=false` al insertar; sólo el administrador puede aprobar y activar. Android descarga la lista únicamente si ambos flags son true.
- `age_access_consents`: declaración +18 por cuenta. El PIN no se sube; se deriva localmente con PBKDF2 y sal.
- RLS vincula cada fila privada a `auth.uid()`.

## Metadatos editoriales y derechos

`catalog_items` admite live/movie/series/episode, slug, géneros, año, duración, clasificación, idiomas, poster/backdrop/logo, serie/temporada/episodio, proveedor, región de licencia, disponibilidad, destacados y popularidad.

Radio, deportes, música, teatro y EPG incluyen metadatos descriptivos y procedencia/derechos. Para publicar se exige `rights_verified`, titular, referencia, región y fecha de inicio. Un trigger privado impide publicar antes del inicio o después del vencimiento. No hay auto-publicación.

## Auditoría y estado

- Las 11 tablas originales tienen RLS habilitado.
- `anon` conserva SELECT sólo sobre las seis tablas de catálogo público.
- Perfiles, favoritos, fuentes, preferencias y consentimientos son sólo para usuarios autenticados y sus políticas restringen al propietario.
- Asesor de seguridad: sin lints en la última comprobación.
- El asesor de rendimiento sólo informa índices aún no utilizados; las tablas siguen vacías, por lo que no se eliminan índices preventivamente.
- Migraciones registradas: inicial, catálogo extendido, grants públicos mínimos, experiencia de producto, bootstrap de preferencias, ventana de derechos y protección de aprobación de fuentes.
- El catálogo sigue vacío: no hay canales, películas, series, eventos, radios, música, teatro ni EPG publicados.

## Límites

La clave publishable identifica el proyecto, pero RLS y grants determinan el acceso. Nunca insertar contenido sin procedencia y derechos verificables. Una fuente nueva queda deshabilitada hasta revisión administrativa.
