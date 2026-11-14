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
- Samsung Tizen y LG webOS ya tienen proyectos nativos iniciales en `platforms/tizen/` y `platforms/webos/`; faltan SDKs, certificados del propietario, WGT/IPK firmados y QA físico por modelo/versión.
- Fire TV comparte APK Android como candidato; falta validar instalación, launcher, D-pad, codecs y APIs Fire OS por modelo.
- El workflow Smart TV valida sintaxis/manifiestos/reglas estáticas; no reemplaza compilación de fabricante ni pruebas físicas.

## Correcciones Smart TV incorporadas
- La navegación Tizen/webOS calcula columnas reales del grid; izquierda/derecha mueven dentro de la fila y arriba/abajo conservan la columna, con foco visible y desplazamiento vertical para catálogos largos.
- Los clientes Tizen/webOS consultan los campos de derechos y sólo habilitan HLS/DASH directo cuando la autorización está verificada, vigente y cubre el territorio detectado por la configuración regional del televisor. Si no puede determinarse el territorio, no se habilita reproducción directa.
- La página oficial del emisor continúa disponible como alternativa; abrir esa página no se presenta como reproducción integrada ni como autorización de redistribución.
- Estas reglas tienen pruebas automatizadas. Aún requieren validar con mandos y televisores físicos de varias generaciones.
