# Arquitectura

Android nativo: Compose UI → ViewModel → parser M3U → catálogo en memoria → Media3. La siguiente integración debe conectar Player.Listener, PlayerSurface y liberar ExoPlayer según lifecycle. Supabase/Postgres queda como backend opcional para catálogo editorial, perfiles y sincronización con opt-in. No se embeben claves privilegiadas. Tizen/webOS requieren clientes y toolchains independientes.
