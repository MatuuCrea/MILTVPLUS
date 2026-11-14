# MILTVPLUS — empaquetado Smart TV para desarrollo

El script empaqueta los clientes oficiales integrados en `main`.

## Samsung Tizen
Instala Tizen Studio, Samsung TV Extension y Samsung Certificate Extension. Crea un perfil **Samsung / TV** en Certificate Manager, incluyendo el DUID del televisor de pruebas, y exporta su nombre en `TIZEN_CERT_PROFILE`. Luego `tizen build-web` compila y `tizen package -t wgt -s` firma el WGT.

**No se puede sustituir ese perfil por un certificado OpenSSL genérico** para instalarlo en televisores Samsung actuales: la política de firma Samsung exige el perfil/certificado de Samsung y el DUID. El script no inventa un perfil que el TV rechazaría.

## LG webOS TV
Instala el CLI mantenido `@webos-tools/cli` (perfil `tv`). `ares-package --no-minify` crea el IPK de desarrollo. En webOS TV, la instalación de desarrollo se autentica mediante Developer Mode y la clave del dispositivo obtenida con `ares-novacom --getkey`; no se usa una firma de aplicación OpenSSL autogenerada ni las opciones de firma de webOS Signage.

## Ejecutar
```bash
export TIZEN_CERT_PROFILE="MilTVPlusSamsungTV"
bash scripts/package-smart-tvs.sh
```
Salidas: `dist/smart-tv/tizen/*.wgt`, `dist/smart-tv/webos/*.ipk` y `SHA256SUMS`.

No se almacenan certificados, contraseñas ni claves privadas en Git. Estos paquetes son para pruebas de desarrollo; antes de distribución pública hay que validar instalación, foco/control remoto, reproducción por códec/modelo y ciclo de vida en TVs físicos y completar los requisitos de publicación de cada fabricante.
