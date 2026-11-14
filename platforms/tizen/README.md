# MILTVPLUS Samsung Tizen TV

Cliente web nativo de Tizen TV. Incluye lectura del catálogo Supabase publicado, navegación con flechas/OK/BACK y AVPlay para HLS/DASH cuando el modelo y el manifiesto lo admiten.

## Construcción
1. Instalar Samsung Tizen Studio y TV Extensions oficiales.
2. Abrir esta carpeta como Web Application de perfil TV.
3. Crear/seleccionar certificado de desarrollo en Certificate Manager y firmar con perfil Samsung TV.
4. Ejecutar en emulador y TV físico; generar WGT firmado desde Tizen Studio.
5. Probar por generación/modelo antes de anunciar compatibilidad.

No se ha generado WGT: requiere SDK, certificado y firma del propietario. Verificar AVPlay, codecs, foco, control remoto, suspensión y reproducción en hardware real. La clave Supabase publishable es pública y RLS limita las filas; nunca agregar service-role keys.