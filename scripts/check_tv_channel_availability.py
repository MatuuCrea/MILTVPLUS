#!/usr/bin/env python3
"""Check official pages and published direct stream manifests; never redistribute media."""
from __future__ import annotations

import json
import os
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlparse
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
INVENTORIES = [
    ROOT / "data" / "catalog" / "argentina-channel-inventory.json",
    ROOT / "data" / "catalog" / "international-channel-inventory.json",
]
REPORT = ROOT / "artifacts" / "tv-channel-availability.json"
TIMEOUT_SECONDS = 15
SUPABASE_URL = os.environ.get("SUPABASE_URL", "https://rbnuxapfsnaabrzuxvdy.supabase.co")
SUPABASE_PUBLISHABLE_KEY = os.environ.get(
    "SUPABASE_PUBLISHABLE_KEY", "sb_publishable_tmBU-IB7P0db0w7RG9TMEw_GD754hb7"
)


def classify(status: int | None, error: str | None = None) -> tuple[str, str]:
    if error:
        return "offline", error
    if status is None:
        return "pending_review", "No se recibió un código HTTP."
    if 200 <= status < 400:
        return "online", f"HTTP {status}"
    if status in (401, 403, 429):
        return "blocked", f"HTTP {status}: el sitio rechazó o limitó el chequeo automatizado."
    if status >= 500:
        return "offline", f"HTTP {status}: error del servidor."
    return "pending_review", f"HTTP {status}: requiere revisión manual."


def check_url(url: str, *, stream_type: str | None = None) -> dict:
    parsed = urlparse(url)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
        return {"status": "blocked", "http_status": None, "final_url": None,
                "detail": "Se rechazó una URL que no es HTTPS segura."}

    request = Request(url, headers={
        "User-Agent": "MILTVPLUS-Availability-Monitor/2.0",
        "Range": "bytes=0-4095",
        "Accept": "*/*"
    }, method="GET")

    status = None
    final_url = None
    content_type = ""
    sample = b""
    error = None
    try:
        with urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            status = response.status
            final_url = response.geturl()
            content_type = response.headers.get("Content-Type", "").lower()
            sample = response.read(4096)
    except HTTPError as exc:
        status = exc.code
        final_url = exc.geturl()
    except (URLError, TimeoutError, OSError) as exc:
        error = f"{type(exc).__name__}: {exc}"

    final = urlparse(final_url) if final_url else None
    if final and (final.scheme != "https" or not final.hostname):
        return {"status": "blocked", "http_status": status, "final_url": final_url,
                "detail": "El destino redirigido dejó HTTPS; se bloqueó por seguridad."}

    state, detail = classify(status, error)
    if state == "online" and stream_type in ("hls", "dash"):
        text_sample = sample.decode("utf-8", errors="ignore").lstrip("\ufeff \r\n\t")
        if stream_type == "hls" and not text_sample.startswith("#EXTM3U"):
            state, detail = "pending_review", "El servidor respondió, pero no entregó una cabecera HLS válida."
        elif stream_type == "dash" and "<MPD" not in text_sample[:4096]:
            state, detail = "pending_review", "El servidor respondió, pero no entregó un manifiesto MPEG-DASH reconocible."
    elif state == "online" and stream_type and not sample:
        state, detail = "pending_review", "El servidor respondió sin entregar bytes de muestra."
    return {
        "status": state,
        "http_status": status,
        "final_url": final_url,
        "content_type": content_type,
        "detail": detail
    }


def fetch_published_channels() -> list[dict]:
    if not SUPABASE_PUBLISHABLE_KEY.startswith("sb_publishable_"):
        raise RuntimeError("Falta la clave publishable para leer el catálogo.")
    base = SUPABASE_URL.rstrip("/")
    if urlparse(base).scheme != "https":
        raise RuntimeError("La URL de Supabase debe usar HTTPS.")
    output: list[dict] = []
    page_size = 500
    for offset in range(0, 10000, page_size):
        query = urlencode({
            "select": "slug,title,country_code,official_watch_url,stream_url,playback_type,source_status",
            "is_published": "eq.true",
            "order": "country_code.asc,sort_order.asc,title.asc",
            "limit": str(page_size),
            "offset": str(offset)
        })
        request = Request(base + "/rest/v1/tv_channels?" + query, headers={
            "apikey": SUPABASE_PUBLISHABLE_KEY,
            "Authorization": "Bearer " + SUPABASE_PUBLISHABLE_KEY,
            "Accept": "application/json"
        })
        with urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            if response.status != 200:
                raise RuntimeError("Supabase respondió HTTP " + str(response.status))
            page = json.loads(response.read(2 * 1024 * 1024))
        output.extend(page)
        if len(page) < page_size:
            break
    return output


def main() -> int:
    checked_at = datetime.now(timezone.utc).isoformat()
    results = []
    for inventory_path in INVENTORIES:
        if not inventory_path.exists():
            continue
        inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
        channels = inventory.get("required_news_channels", inventory.get("channels", []))
        for channel in channels:
            url = channel.get("official_watch_url")
            if not url:
                continue
            result = check_url(url)
            results.append({
                "slug": channel["slug"],
                "name": channel["name"],
                "country_code": channel.get("country_code", inventory.get("country_code", "??")),
                "check_type": "official_page",
                "official_watch_url": url,
                **result
            })

    stream_results = []
    catalog_error = None
    try:
        for channel in fetch_published_channels():
            stream_url = channel.get("stream_url")
            if not stream_url:
                continue
            stream_type = channel.get("playback_type")
            result = check_url(stream_url, stream_type=stream_type)
            stream_results.append({
                "slug": channel.get("slug"),
                "name": channel.get("title"),
                "country_code": channel.get("country_code"),
                "check_type": "direct_stream_manifest",
                "playback_type": stream_type,
                "configured_source_status": channel.get("source_status"),
                **result
            })
    except Exception as exc:
        catalog_error = f"{type(exc).__name__}: {exc}"

    REPORT.parent.mkdir(parents=True, exist_ok=True)
    report = {
        "checked_at": checked_at,
        "scope": "official_page_reachability_and_direct_manifest_smoke_test_only",
        "limitations": [
            "HTTP/manifest checks do not guarantee continuous playback, audio/video decode, geographic availability, or viewer capacity.",
            "Only published direct stream_url values are tested; official web pages are not treated as embeddable streams.",
            "No channel is automatically marked licensed or authorized by this monitor."
        ],
        "catalog_error": catalog_error,
        "official_pages": results,
        "direct_streams": stream_results
    }
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    summary = ["## MILTVPLUS · disponibilidad de páginas y streams", "",
               f"Chequeo UTC: {checked_at}", "",
               "### Páginas oficiales", "",
               "| Canal | Estado | HTTP | Detalle |", "|---|---|---:|---|"]
    for item in results:
        summary.append(f"| {item['name']} | **{item['status']}** | {item['http_status'] or '—'} | {item['detail']} |")
    summary += ["", "### Manifiestos de reproducción integrados", "",
                "| Canal | Tipo | Estado | HTTP | Detalle |", "|---|---|---|---:|---|"]
    for item in stream_results:
        summary.append(f"| {item['name']} | {item['playback_type']} | **{item['status']}** | {item['http_status'] or '—'} | {item['detail']} |")
    if catalog_error:
        summary += ["", "**No se pudo consultar el catálogo Supabase:** " + catalog_error]
    summary += ["", "Un manifiesto accesible no demuestra reproducción estable, decodificación en dispositivos, capacidad concurrente ni derechos de redistribución."]
    summary_text = "\n".join(summary) + "\n"
    summary_path = Path(os.environ.get("GITHUB_STEP_SUMMARY", "/tmp/miltvplus-summary.md"))
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    summary_path.write_text(summary_text, encoding="utf-8")
    print(summary_text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
