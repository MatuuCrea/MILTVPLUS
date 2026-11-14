# Arquitectura

Android nativo: Jetpack Compose → repositorios → Supabase Auth/Edge Functions → catálogo y Media3. Windows nativo: WPF/.NET 8 → Supabase Auth/Edge Functions → catálogo TMDB y catálogo editorial; LibVLC se reserva para reproducción de fuentes autorizadas. Las credenciales TMDB permanecen en secretos de Supabase; los clientes sólo usan la clave publicable y un JWT de usuario. No se embeben claves privilegiadas. Los catálogos TMDB son metadatos, no derechos ni URLs de reproducción. Tizen/webOS requieren clientes y toolchains independientes.
