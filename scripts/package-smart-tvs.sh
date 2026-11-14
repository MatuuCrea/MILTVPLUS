#!/usr/bin/env bash
# Build Samsung Tizen WGT and LG webOS TV IPK for developer testing.
# Samsung TV signing requires a Samsung TV certificate profile; webOS TV developer
# installation uses Developer Mode device credentials, not an app self-signed cert.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${MILTVPLUS_TV_OUT_DIR:-$ROOT/dist/smart-tv}"
mkdir -p "$OUT_DIR"
require_cmd() { command -v "$1" >/dev/null 2>&1 || { echo "ERROR: falta '$1'. Instala el SDK oficial." >&2; exit 2; }; }

# Samsung TV: the certificate profile is created with Samsung Certificate Extension
# and must include the target TV's DUID. A generic OpenSSL self-signed cert is not accepted
# by current Samsung TV emulator/device certificate policy.
require_cmd tizen
: "${TIZEN_CERT_PROFILE:?Crea un perfil Samsung/TV en Tizen Certificate Manager con el DUID del televisor y configura TIZEN_CERT_PROFILE}"
mkdir -p "$OUT_DIR/tizen"
tizen build-web -- "$ROOT/platforms/tizen"
TIZEN_BUILD="$ROOT/platforms/tizen/.buildResult"
[[ -d "$TIZEN_BUILD" ]] || { echo "ERROR: Tizen build-web no creó .buildResult." >&2; exit 3; }
tizen package -t wgt -s "$TIZEN_CERT_PROFILE" -o "$OUT_DIR/tizen" -- "$TIZEN_BUILD"
TIZEN_PACKAGE="$(find "$OUT_DIR/tizen" -maxdepth 1 -type f -name '*.wgt' -print -quit)"
[[ -n "$TIZEN_PACKAGE" && -s "$TIZEN_PACKAGE" ]] || { echo "ERROR: Tizen CLI no produjo un WGT." >&2; exit 3; }
echo "TIZEN_WGT=$TIZEN_PACKAGE"

# webOS TV: ares-package creates the IPK. Developer Mode authenticates installation
# with the TV's device key; the TV CLI does not expose app self-signing flags.
require_cmd ares-package
mkdir -p "$OUT_DIR/webos"
ares-package --no-minify --outdir "$OUT_DIR/webos" "$ROOT/platforms/webos"
WEBOS_PACKAGE="$(find "$OUT_DIR/webos" -maxdepth 1 -type f -name '*.ipk' -print -quit)"
[[ -n "$WEBOS_PACKAGE" && -s "$WEBOS_PACKAGE" ]] || { echo "ERROR: webOS CLI no produjo un IPK." >&2; exit 3; }
echo "WEBOS_IPK=$WEBOS_PACKAGE"

(
  cd "$OUT_DIR"
  find tizen webos -type f \( -name '*.wgt' -o -name '*.ipk' \) -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS
)
echo "Paquetes de desarrollo generados. Instala mediante Developer Mode/SDK y valida en TV físico."
