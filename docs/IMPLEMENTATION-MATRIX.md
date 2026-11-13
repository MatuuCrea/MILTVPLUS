# MILTVPLUS — matriz de implementación y aceptación

Este documento distingue código existente, integración parcial y dependencias externas. No se considera una función terminada sólo porque exista una tabla o una pantalla.

## Estado verificado en el repositorio

| Módulo | Estado | Trabajo necesario para darlo por terminado |
|---|---|---|
| Android móvil | Parcialmente implementado | QA en versiones Android soportadas, permisos, ciclo de vida, accesibilidad y rendimiento |
| Android TV / Google TV | Parcial | Foco D-pad consistente, navegación con control remoto, banner/launcher assets y pruebas en hardware/emulador TV |
| Navegación Compose | Implementada como base | Completar rutas de detalle, estados de error/vacío y restauración de estado |
| Media3 / HLS | Base implementada | Errores recuperables, reconexión, subtítulos/audio tracks, selección de calidad y pruebas con streams licenciados |
| Importación M3U/M3U8 | Base implementada | Pruebas de parser ampliadas, actualización/eliminación de fuentes, persistencia de permisos y manejo de listas grandes |
| Favoritos | Local | Login y sincronización por usuario; conflictos offline/online |
| Supabase REST | Lectura pública parcial | Validar permisos Data API, políticas RLS y contrato de columnas por tabla en el proyecto real |
| Supabase Auth | Pendiente | Registro/login, refresh token, cierre de sesión, recuperación de contraseña y almacenamiento seguro de sesión |
| Fuentes del usuario | Esquema parcial | CRUD autenticado, propietario obligatorio, validación y sincronización; nunca permitir fuentes arbitrarias sin consentimiento |
| Catálogo TV/VOD | Pendiente de datos y detalle | Metadatos editoriales, fichas, clasificación, idioma, proveedor, derechos y disponibilidad territorial |
| Series | Pendiente | Entidades serie/temporada/episodio, orden, relaciones y navegación |
| EPG | Parcial | Grilla por canal, zona horaria, programas vigentes/siguientes, cobertura temporal y asociación estable de canales |
| Radio | Esquema y lectura parcial | Metadatos, géneros, países, reproducción, imagen y validación de derechos |
| Deportes | Esquema y lectura parcial | Competición, disciplina, horario, proveedor oficial y enlaces de emisión autorizados |
| Música | Esquema y lectura parcial | Catálogo de artistas/álbumes/listas, proveedor y reproducción autorizada |
| Teatro | Esquema y lectura parcial | Evento, recinto, fecha, entradas/información oficial y emisión autorizada |
| Perfiles y preferencias | Esquema parcial | Auth, CRUD propio, sincronización y reglas de privacidad |
| Control parental / +18 | Esquema parcial | PIN seguro, clasificación por contenido, bloqueo por perfil, consentimiento verificable y pruebas |
| Marca | Pendiente | Incorporar el archivo original del logo aprobado; no sustituirlo por una imagen inventada |
| Tizen / webOS / Web | Fuera del cliente Android | Diseñar clientes separados, contratos API compartidos y QA por plataforma |
| Publicación | Pendiente | Firma release, gestión segura de claves, política de privacidad, fichas de tienda, revisión de derechos y despliegue |
| CI/CD | Existe pipeline Android | Ejecutar y confirmar JVM tests, assembleDebug y upload-artifact para cada SHA nuevo |

## Contratos de metadatos necesarios

Los campos siguientes son el contrato funcional objetivo. Antes de ejecutar migraciones, hay que compararlos con el esquema desplegado para no sobrescribir cambios del proyecto.

### TV y VOD (\`catalog_items\`)
- Identidad: \`id\`, \`slug\`, \`title\`, \`kind\` (\`live\`, \`movie\`, \`series\`, \`episode\`).
- Descripción: \`description\`, \`genre\`, \`release_year\`, \`duration_seconds\`, \`rating\`.
- Localización: \`country_code\`, \`language_code\`, \`audio_languages\`, \`subtitle_languages\`.
- Imagen: \`poster_url\`, \`backdrop_url\`, \`logo_url\`.
- Relaciones: \`series_id\`, \`season_number\`, \`episode_number\`, \`parent_id\`.
- Reproducción: \`stream_url\` o \`authorized_stream_url\`, \`provider_id\`, \`license_region\`, \`available_from\`, \`available_until\`.
- Publicación: \`is_published\`, \`created_at\`, \`updated_at\`.

### EPG (\`epg_programs\`)
- \`channel_external_id\`, \`title\`, \`description\`, \`starts_at\`, \`ends_at\`, \`country_code\`, \`source_id\`, \`is_published\`.
- \`ends_at\` debe ser posterior a \`starts_at\`; guardar timestamps con zona/UTC y convertir al huso del dispositivo en UI.
- La grilla debe asociar programas a un identificador de canal estable, no al nombre visible.

### Radio (\`radio_stations\`)
- \`title\`, \`stream_url\`, \`country_code\`, \`language_code\`, \`genre\`, \`logo_url\`, \`is_published\`.
- Cada stream debe tener fuente, territorio y permiso de distribución verificables antes de publicarse.

### Deportes (\`sports_events\`)
- \`title\`, \`sport\`, \`competition\`, \`country_code\`, \`starts_at\`, \`ends_at\`, \`official_info_url\`, \`authorized_stream_url\`, \`is_published\`.
- Un enlace informativo oficial no equivale a un derecho de retransmisión.

### Música (\`music_items\`)
- \`item_type\`, \`title\`, \`creator\`, \`country_code\`, \`external_url\`, \`artwork_url\`, \`is_published\`.
- \`external_url\` puede abrir al proveedor autorizado; no debe interpretarse automáticamente como un stream directo.

### Teatro (\`theater_events\`)
- \`title\`, \`venue\`, \`country_code\`, \`starts_at\`, \`info_url\`, \`authorized_stream_url\`.

## Bloqueos externos que no se pueden inventar

1. **Logo original:** se necesita el archivo de marca aprobado para incorporarlo como recurso binario.
2. **Contenido:** hacen falta proveedores, listas o streams con autorización documentada, territorio, vigencia y metadatos reales.
3. **Supabase:** se debe confirmar que la migración está aplicada en el proyecto correcto, que las tablas están expuestas en Data API cuando corresponda y que las políticas RLS coinciden con el modelo de acceso.
4. **Auth:** no se deben habilitar escrituras anónimas ni incluir \`service_role\`/secret keys en el APK.
5. **Plataformas externas:** Tizen y webOS necesitan proyectos/clientes independientes; no se compilan dentro del APK Android.
6. **Release:** la firma de producción y las credenciales de tiendas requieren control del propietario.

## Criterios de aceptación global

- \`:app:testDebugUnitTest\` termina en verde.
- \`:app:assembleDebug\` termina en verde y se publica el APK correspondiente al mismo SHA.
- Ninguna clave privilegiada aparece en el código cliente, APK, logs o artefactos.
- RLS se prueba con rol anónimo y usuario autenticado, incluyendo intentos de leer/modificar filas ajenas.
- Ningún contenido se publica sin procedencia, permiso, territorio y vigencia comprobables.
- La app muestra estados de carga, vacío y error sin fabricar contenido.
- Las funciones de TV se prueban con control remoto real o emulador Android TV.
