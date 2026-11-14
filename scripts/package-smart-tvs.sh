#!/usr/bin/env bash
# Build signed Samsung Tizen WGT and LG webOS IPK using locally installed vendor SDKs.
# No signing credentials are stored in this repository.
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${MILTVPLUS_TV_OUT_DIR:-$ROOT/dist/smart-tv}"
mkdir -p "$OUT_DIR"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "ERROR: falta la herramienta '$1'. Instala el SDK oficial del fabricante y vuelve a intentar." >&2
    exit 2
  }
}

require_file() {
  [[ -f "$1" ]] || {
    echo "ERROR: no existe el archivo de firma configurado: $1" >&2
    exit 2
  }
}

# Samsung Tizen: el perfil debe estar creado en Certificate Manager y visible para Tizen CLI.
require_cmd tizen
: "${TIZEN_CERT_PROFILE:?Configura TIZEN_CERT_PROFILE con el perfil de firma TV de Tizen Studio}"
mkdir -p "$OUT_DIR/tizen"
tizen package -t wgt -s "$TIZEN_CERT_PROFILE" -o "$OUT_DIR/tizen" -- "$ROOT/platforms/tizen"
TIZEN_PACKAGE="$(find "$OUT_DIR/tizen" -maxdepth 1 -type f -name '*.wgt' -print -quit)"
[[ -n "$TIZEN_PACKAGE" && -s "$TIZEN_PACKAGE" ]] || {
  echo "ERROR: Tizen CLI no produjo un WGT." >&2
  exit 3
}
echo "TIZEN_WGT=$TIZEN_PACKAGE"

# LG webOS: ares-package signs the IPK with the developer private key supplied by LG.
require_cmd ares-package
: "${WEBOS_PRIVATE_KEY:?Configura WEBOS_PRIVATE_KEY con la ruta de la clave privada de desarrollador}"
: "${WEBOS_KEY_PASSPHRASE:?Configura WEBOS_KEY_PASSPHRASE con la contraseña de esa clave}"
require_file "$WEBOS_PRIVATE_KEY"
mkdir -p "$OUT_DIR/webos"
ares-package --sign "$WEBOS_PRIVATE_KEY" --passphrase "$WEBOS_KEY_PASSPHRASE" --outdir "$OUT_DIR/webos" "$ROOT/platforms/webos"
WEBOS_PACKAGE="$(find "$OUT_DIR/webos" -maxdepth 1 -type f -name '*.ipk' -print -quit)"
[[ -n "$WEBOS_PACKAGE" && -s "$WEBOS_PACKAGE" ]] || {
  echo "ERROR: webOS CLI no produjo un IPK." >&2
  exit 3
}
echo "WEBOS_IPK=$WEBOS_PACKAGE"

(
  cd "$OUT_DIR"
  find tizen webos -type f \( -name '*.wgt' -o -name '*.ipk' \) -print0 |
    sort -z |
    xargs -0 sha256sum > SHA256SUMS
)
echo "Paquetes generados. La instalación/QA en TV físico sigue siendo una validación separada."
