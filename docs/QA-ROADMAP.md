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

## Control de vigencia de derechos en Android y Windows
- Android y Windows solicitan los campos de titular, referencia, territorios y ventana de derechos junto con cada fila publicada.
- Los reproductores directos bloquean URLs si no hay autorización verificada, vigente y aplicable a la región del dispositivo. En TV también se exige estado de fuente online.
- Un vencimiento malformado se trata como no autorizado (fail-closed). Las páginas oficiales siguen siendo una alternativa, sin convertirlas en streams propios.
- Las listas M3U importadas manualmente por el usuario conservan su flujo local explícito y muestran advertencia de responsabilidad; no se mezclan con las fuentes editoriales publicadas.

## Integración multiplataforma de derechos — 14/11/2026

- PR #16 fusionada en `main`: `53e2dcfca1a4942b56e5cab58ea2abc4be8e40b0`.
- CI sobre el SHA exacto de la PR `4def816845929fb54ce7f7f552f6f1051cd3f133`: Android CI #367, Windows Desktop #177 y Smart TV Clients #28 finalizaron correctamente.
- La validación Android y Windows bloquea reproducción directa editorial cuando faltan titular, referencia, territorio aplicable o vigencia; las fechas inválidas fallan cerradas.
- Las listas M3U/M3U8 importadas localmente por la persona usuaria permanecen separadas del catálogo editorial y conservan el aviso de responsabilidad.
- No se modificó Supabase productivo ni secretos durante esta integración.
- Después del merge, Smart TV Clients del SHA de main `53e2dcfca1a4942b56e5cab58ea2abc4be8e40b0` terminó correctamente. Android CI y Windows Desktop del mismo SHA estaban en ejecución en la última consulta; confirmar ambos resultados antes de cerrar P0.

## Próximos bloqueos de lanzamiento

1. QA físico de Android móvil, Android TV/Google TV, Fire TV compatible, Windows, Samsung Tizen y LG webOS.
2. Generar y firmar WGT de Tizen e IPK de webOS con SDK/certificados del propietario.
3. Completar paridad Windows de Auth, favoritos sincronizados, +18, preferencias, EPG y temporadas/episodios.
4. Completar grilla EPG y pantallas dedicadas de temporadas/episodios con datos reales.
5. Mantener TMDB desactivado para uso público hasta completar atribución oficial y confirmar términos aplicables.
6. Incorporar streams directos únicamente con derechos verificables por territorio y vigencia; el catálogo actual no autoriza inventar ni inferir URLs.
