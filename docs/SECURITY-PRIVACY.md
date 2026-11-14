# Seguridad y privacidad

- Importar únicamente playlists y streams propios, públicos o licenciados.
- No incluir listas comerciales, claves ni credenciales de terceros.
- No enviar playlists importadas a telemetría.
- Preferir HTTPS; cleartext está deshabilitado globalmente.
- Las credenciales TMDB (`TMDB_READ_ACCESS_TOKEN` y `TMDB_API_KEY`) viven sólo en secretos de Edge Functions; no se incluyen en APK, instalador, código ni logs.
- La función `tmdb-catalog` requiere JWT de usuario y limita las operaciones a rutas TMDB permitidas; no acepta URLs arbitrarias.
- Los metadatos y pósteres TMDB no conceden derechos de exhibición ni de streaming.
- Nunca incluir service_role key en Android; habilitar RLS en todas las tablas expuestas.
- Añadir límites de tamaño y cantidad de entradas antes de producción.
