# MILTVPLUS — empaquetado Smart TV

Los clientes oficiales están en `platforms/tizen` y `platforms/webos`. El menú único y los paneles de ambas plataformas ya están integrados en `main` (PR #22). Este procedimiento empaqueta exactamente esas carpetas; no genera una interfaz alternativa.

## Requisitos

- **Samsung Tizen:** Tizen Studio + TV Extensions + Certificate Manager. Crear un perfil de certificado de TV en el equipo/runner y verificar que Tizen CLI lo reconoce.
- **LG webOS:** webOS TV SDK/CLI (`ares-package`) y la clave privada de desarrollador correspondiente al flujo oficial de firma de LG.
- Bash, `find`, `sha256sum`.

## Configuración segura

1. Copiar `scripts/tv-signing.env.example` fuera del repositorio.
2. Crear el perfil Samsung con Certificate Manager y exportar `TIZEN_CERT_PROFILE` con su nombre exacto.
3. Configurar `WEBOS_PRIVATE_KEY` y `WEBOS_KEY_PASSPHRASE` mediante variables de entorno/secretos protegidos.
4. No subir claves, certificados, contraseñas ni archivos de perfil privado a Git. No usar credenciales de prueba para producción.

## Empaquetar

Desde la raíz del repositorio:

```bash
export TIZEN_CERT_PROFILE="MilTVPlusTV"
export WEBOS_PRIVATE_KEY="/ruta/segura/webos-developer-private-key.pem"
read -rsp "Contraseña webOS: " WEBOS_KEY_PASSPHRASE; export WEBOS_KEY_PASSPHRASE; echo
bash scripts/package-smart-tvs.sh
unset WEBOS_KEY_PASSPHRASE
```

Salidas esperadas:

- `dist/smart-tv/tizen/*.wgt`
- `dist/smart-tv/webos/*.ipk`
- `dist/smart-tv/SHA256SUMS`

El script se detiene si falta un SDK, perfil, clave o paquete generado. No etiqueta un paquete sin firma como firmado.

## Publicación y validación

Un WGT/IPK generado no demuestra compatibilidad física. Antes de publicar, instalar en TVs reales de las generaciones soportadas, validar el certificado/firmante, control remoto, navegación de foco, AVPlay/HTML5 video, HLS/DASH por modelo, suspensión/reanudación, memoria y salida de la app. Mantener los binarios y secretos fuera del repositorio.

## Deportes, Música y Adultos

- Deportes usa el catálogo TV publicado y una categoría exclusiva `sports`; los canales de otras categorías no deben mezclarse.
- La autorización de reproducción se vuelve a comprobar en el resolver y sólo se aceptan streams directos HTTPS compatibles con el cliente.
- Música conserva su catálogo regional, filtros por género/época, mezcla y reproductor integrado.
- Adultos 18+ conserva su panel visual, pero el catálogo permanece vacío y bloqueado: sin consulta, URLs, ni reproducción.
- MILTVPLUS sigue 100 % gratuita: sin anuncios, pagos, suscripciones, compras ni SDK de monetización.
