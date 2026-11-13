#!/usr/bin/env python3
"""Check official TV directory pages without extracting or replaying streams."""
from __future__ import annotations

import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
INVENTORY = ROOT / "data" / "catalog" / "argentina-channel-inventory.json"
REPORT = ROOT / "artifacts" / "tv-channel-availability.json"
TIMEOUT_SECONDS = 15


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


def check_official_page(url: str) -> dict:
    parsed = urlparse(url)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
        return {"status": "blocked", "http_status": None, "final_url": None,
                "detail": "Se rechazó una URL que no es HTTPS segura."}

    request = Request(url, headers={
        "User-Agent": "MILTVPLUS-Official-Page-Monitor/1.0",
        "Range": "bytes=0-0",
        "Accept": "text/html,application/xhtml+xml"
    }, method="GET")

    status = None
    final_url = None
    error = None
    try:
        with urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            status = response.status
            final_url = response.geturl()
    except HTTPError as exc:
        status = exc.code
        final_url = exc.geturl()
    except (URLError, TimeoutError, OSError) as exc:
        error = f"{type(exc).__name__}: {exc}"

    final = urlparse(final_url) if final_url else None
    if final and (final.scheme != "https" or not final.hostname):
        return {"status": "blocked", "http_status": status, "final_url": final_url,
                "detail": "El destino redirigido dejó HTTPS; se bloqueó por seguridad."}
    if final and final.hostname.lower().removeprefix("www.") != parsed.hostname.lower().removeprefix("www."):
        return {"status": "blocked", "http_status": status, "final_url": final_url,
                "detail": "La página redirigió a otro dominio; requiere validación manual."}

    state, detail = classify(status, error)
    return {"status": state, "http_status": status, "final_url": final_url, "detail": detail}


def main() -> int:
    inventory = json.loads(INVENTORY.read_text(encoding="utf-8"))
    checked_at = datetime.now(timezone.utc).isoformat()
    results = []
    for channel in inventory.get("required_news_channels", []):
        url = channel.get("official_watch_url")
        if not url:
            continue
        result = check_official_page(url)
        results.append({
            "slug": channel["slug"],
            "name": channel["name"],
            "country_code": inventory["country_code"],
            "official_watch_url": url,
            **result
        })

    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps({
        "checked_at": checked_at,
        "scope": "official_page_reachability_only_not_stream_or_rights_validation",
        "results": results
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    summary = ["## MILTVPLUS · disponibilidad de páginas oficiales", "",
               f"Chequeo UTC: {checked_at}", "",
               "| Canal | Estado web | HTTP | Detalle |", "|---|---|---:|---|"]
    for item in results:
        summary.append(f"| {item['name']} | **{item['status']}** | {item['http_status'] or '—'} | {item['detail']} |")
    summary += ["", "Este control sólo comprueba si la página oficial responde. No prueba que el stream esté al aire, que sea reproducible ni que exista autorización de redistribución."]
    summary_text = "\n".join(summary) + "\n"
    summary_path = Path(__import__("os").environ.get("GITHUB_STEP_SUMMARY", "/tmp/miltvplus-summary.md"))
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    summary_path.write_text(summary_text, encoding="utf-8")
    print(summary_text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
