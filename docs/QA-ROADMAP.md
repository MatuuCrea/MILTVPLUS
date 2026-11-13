# QA y trabajo pendiente

## Estado verificado
- Proyecto Supabase MILTVPLUS activo y saludable.
- Las 9 migraciones del repositorio están aplicadas en la base productiva.
- Las tablas públicas revisadas tienen RLS habilitado; el asesor de seguridad devolvió cero alertas.
- El catálogo editorial está vacío (0 filas en las tablas principales). No hay emisiones ni títulos reproducibles hasta incorporar fuentes y metadatos autorizados.
- Android CI ya produjo APK debug en una ejecución anterior; una APK debug no es una release apta para distribución.
- No hay GitHub Releases publicadas todavía.
- Los actualizadores Android y Windows ya consultan releases y exigen verificar SHA-256 antes de abrir el instalador. Su recorrido completo queda pendiente hasta publicar la primera release estable firmada.

## Pruebas pendientes antes de declarar lanzamiento
- Android teléfono y Android TV físicos: D-pad/foco, reproducción HLS autorizada, rotación, 403/404/500, offline, playlist vacía, accesibilidad y privacidad.
- Instalación y actualización Android con la misma clave de firma en al menos dos versiones consecutivas.
- Windows: instalación/desinstalación limpia, actualización desde una versión anterior, reproducción y validación de códecs/proveedores.
- Cuenta: agregar miltvplus://auth/callback a Supabase Auth Redirect URLs y probar registro, confirmación de correo, recuperación y cambio de contraseña.
- Validar filtros de edad, RLS, preferencias y favoritos con dos cuentas de prueba.
- Probar checksum correcto y rechazo de APK/EXE alterados.
- Completar grilla EPG, navegación de temporadas/episodios y accesibilidad de pantallas grandes.

## Dependencias externas
- El propietario debe conservar un keystore Android permanente y configurar ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS y ANDROID_KEY_PASSWORD como secretos de GitHub Actions. No se debe regenerar el keystore entre releases.
- El propietario/proveedor debe aportar fuentes de contenido con titular, referencia de derechos, territorios y vigencia. No se inventan URLs ni licencias.
- Las notificaciones push requieren proyecto/credenciales de entrega (por ejemplo, Firebase Cloud Messaging) y configuración de backend; la preferencia local no es una entrega push.
- Windows requiere pruebas físicas y, para una distribución de confianza, certificado Authenticode.
- Samsung Tizen y LG webOS necesitan aplicaciones nativas separadas.
