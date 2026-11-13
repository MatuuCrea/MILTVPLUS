# MILTVPLUS V22.34.9

Aplicación de entretenimiento para Android móvil, Android TV y TV Box Android, con un cliente Web/PWA separado. Kotlin, Jetpack Compose, Media3 y Supabase.

## Funciones desarrolladas

### Android
- Inicio con accesos a TV en vivo, películas, series, deportes, radios, música, teatro, EPG, +18, países, fuentes, cuenta y ajustes.
- Reproducción Media3/HLS para URLs HTTPS de proveedores autorizados.
- Importación M3U/M3U8 local con límite de 5 MB y validación HTTPS.
- Catálogo remoto con filtros por país y territorios de licencia, tipo de contenido, imágenes y enlaces oficiales.
- EPG agrupada por identificador estable, ordenada por horario local y marcada cuando el programa está en vivo.
- Cuenta Supabase Auth: registro, login, recuperación, actualización de contraseña, cierre, renovación y callback de email.
- Sesión cifrada AES-GCM con clave Android Keystore.
- Favoritos en nube para UUID de `catalog_items`; favoritos M3U locales siguen locales.
- Preferencias locales y sincronizadas: tema, acento, país, idioma, audio preferido, subtítulos, preferencia de avisos y autoplay.
- Fuentes remotas por cuenta: alta, listado, eliminación y descarga M3U sólo después de aprobación administrativa; el backend fuerza las nuevas fuentes a deshabilitadas.
- Control +18 con declaración, PIN PBKDF2 con sal, bloqueo temporal tras cinco intentos incorrectos y catálogo filtrado por clasificación.
- Launcher Android TV con categoría Leanback y banner gráfico provisional; D-pad y foco requieren QA en dispositivos reales.

### Web / PWA
El cliente independiente está en [web/](web/). Incluye navegación de catálogo, búsqueda, filtro regional, Auth, favoritos, preferencias, fuentes, control +18 local y service worker que cachea sólo archivos estáticos. No cachea respuestas privadas de API.

## Supabase

Proyecto: `rbnuxapfsnaabrzuxvdy`.

- Los clientes usan exclusivamente la clave publishable. Nunca incluir `service_role`, secret keys ni contraseñas de base de datos.
- Las tablas privadas usan RLS y políticas por propietario.
- El rol de usuario puede enviar y eliminar fuentes propias, pero no puede aprobarlas ni habilitarlas; un trigger obliga flags de verificación a false al insertar.
- Las tablas editoriales sólo permiten publicar con derechos verificados, titular, referencia, región y ventana de vigencia activa.
- El trigger de perfil/preferencias vive en el esquema no expuesto `app_private`.
- El catálogo sigue vacío en la última comprobación: no hay canales, películas, series, radios, eventos, música, teatro ni EPG publicados.
- No se cargaron URLs ni metadatos ficticios.

### Configuración necesaria para confirmar emails y recuperación Android

En Supabase Dashboard → Authentication → URL Configuration → Redirect URLs, agregar:

```
miltvplus://auth/callback
```

El cliente Android ya declara ese deep link. Sin esta URL permitida, los enlaces de confirmación/recuperación pueden no volver a la app.

## Límites pendientes
- La preferencia de notificaciones se guarda y sincroniza; el servicio que envía avisos push/reminders todavía no está desplegado.
- Spotify abre el servicio oficial; OAuth y reproducción integrada requieren una aplicación Spotify aprobada.
- La declaración +18 y el PIN son barreras de producto, no verificación legal certificada de identidad/edad.
- Tizen y webOS requieren clientes independientes y todavía no están desarrollados.
- Web/PWA requiere hosting HTTPS, iconos de marca y QA de navegadores.
- Falta incorporar el logo oficial original.
- Firma release, privacidad y publicación en tiendas siguen pendientes.

## Construcción Android

Requisitos: JDK 17, Android SDK 35, Gradle 8.9.

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`. Sólo considerar válido el artefacto cuando GitHub Actions confirme el mismo SHA, los tests, el ensamblado y la subida del APK.

## Documentación
- [Matriz de implementación](docs/IMPLEMENTATION-MATRIX.md)
- [Especificación funcional](docs/PRODUCT-SPEC.md)
- [Integración y auditoría Supabase](docs/SUPABASE-ANDROID.md)
- [Cliente Web/PWA](web/README.md)
