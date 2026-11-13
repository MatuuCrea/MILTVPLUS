# MILTVPLUS Web

Cliente estático con manifest y service worker que consume las mismas tablas públicas de Supabase que Android.

## Publicación
- Publicar `web/` en hosting estático HTTPS (GitHub Pages, Cloudflare Pages, Netlify o Render Static Site).
- No requiere build ni backend propio.
- HTTPS es obligatorio para service workers y Supabase.
- La clave publishable en `app.js` es pública; nunca colocar service_role ni secret keys.

## Funciones
- Navegación TV/VOD, películas, series, deportes, radios, música, teatro, EPG y sección +18.
- Búsqueda y filtro regional por país y territorios de licencia.
- Catálogo REST sólo con `is_published=true`.
- Registro, inicio, recuperación y cierre de sesión Supabase Auth.
- Favoritos de catálogo, preferencias, fuentes y constancia +18 sincronizados por cuenta y protegidos por RLS.
- Fuentes nuevas deshabilitadas y sin verificar hasta aprobación administrativa.
- Tema, acento, idioma, audio preferido, subtítulos, preferencias de avisos y autoplay.
- PIN +18 derivado con PBKDF2 y sal local.
- Service worker cachea sólo archivos estáticos; no cachea datos privados.

## Límites
- No se inventa contenido: catálogo vacío se muestra vacío.
- No hay reproducción HLS universal; se abre la fuente HTTPS autorizada.
- Spotify abre el servicio oficial; no se simula OAuth ni reproducción integrada.
- El PIN web es barrera local, no verificación legal de edad.
- Logo oficial integrado como icono Web/PWA. Hosting HTTPS y QA de navegador siguen pendientes.
