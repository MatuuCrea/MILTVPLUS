/**
 * MILTVPLUS — Media Manager compartido para clientes web.
 *
 * No contiene listas de canales ni URLs de ejemplo: el catálogo procede del
 * resolver autorizado. La sección +18 permanece deshabilitada y vacía.
 *
 * Uso:
 *   import { AppMediaManager } from "../shared/media-manager.js";
 *   const media = new AppMediaManager({
 *     resolverBaseUrl: window.MILTVPLUS_CONFIG?.resolverBaseUrl,
 *     country: "AR",
 *     getAccessToken: async () => null
 *   });
 *   const catalog = await media.getCatalog("tv_en_vivo");
 *   // Invocar play() directamente desde el click/OK del usuario.
 *   await media.play("tv_en_vivo", item.id, videoElement);
 */

const SECTIONS = Object.freeze({
  tv_en_vivo: "tv_en_vivo",
  deportes: "tv_en_vivo",
  peliculas: "peliculas",
  series: "series"
});

const ADULT_SECTION = "adultos";
const COUNTRY_CODES = Object.freeze({
  argentina: "AR",
  mexico: "MX",
  espana: "ES",
  internacional: "ALL",
  global: "ALL",
  todo_el_mundo: "ALL"
});

function normalizeCountry(value, fallback = "AR") {
  const raw = String(value || fallback).trim().toLowerCase();
  const country = COUNTRY_CODES[raw] || raw.toUpperCase();
  if (country !== "ALL" && !/^[A-Z]{2}$/.test(country)) {
    throw new MediaManagerError("El país debe ser un código ISO de dos letras o ALL.");
  }
  return country;
}
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export class MediaManagerError extends Error {
  constructor(message, status = 0) {
    super(message);
    this.name = "MediaManagerError";
    this.status = status;
  }
}

function normalizeBaseUrl(value) {
  if (typeof value !== "string" || !value.trim()) {
    throw new MediaManagerError("Falta configurar la URL del servicio de reproducción.");
  }
  let url;
  try {
    url = new URL(value);
  } catch {
    throw new MediaManagerError("La URL del servicio de reproducción no es válida.");
  }
  if (url.protocol !== "https:" && url.hostname !== "localhost" && url.hostname !== "127.0.0.1") {
    throw new MediaManagerError("El servicio de reproducción debe utilizar HTTPS.");
  }
  return url.href.replace(/\/$/, "");
}

function safeMediaUrl(value) {
  if (typeof value !== "string" || value.length > 4096) return false;
  try {
    const url = new URL(value);
    return url.protocol === "https:" && !url.username && !url.password;
  } catch {
    return false;
  }
}

export class HtmlVideoAdapter {
  async play(video, resolved) {
    if (!(video instanceof HTMLVideoElement)) {
      throw new MediaManagerError("El reproductor de video no es válido.");
    }
    if (resolved.mode !== "direct_stream" || !safeMediaUrl(resolved.streamUrl)) {
      throw new MediaManagerError("Esta fuente no admite reproducción directa integrada.");
    }
    const protocol = String(resolved.protocol || "").toLowerCase();
    if (protocol === "dash") {
      throw new MediaManagerError("Este reproductor HTML5 no incluye un motor DASH. Se necesita un adaptador Media3, AVPlay o LibVLC.");
    }
    if (protocol !== "hls" && protocol !== "https") {
      throw new MediaManagerError("Formato de reproducción no compatible.");
    }

    video.pause();
    video.removeAttribute("src");
    video.load();
    video.src = resolved.streamUrl;
    video.load();
    await video.play();

    // La llamada debe producirse como consecuencia de una acción del usuario.
    if (typeof video.requestFullscreen === "function" && !document.fullscreenElement) {
      try { await video.requestFullscreen(); } catch { /* Algunos televisores bloquean fullscreen por API. */ }
    }
    return { title: resolved.title, mode: resolved.mode };
  }

  stop(video) {
    if (!(video instanceof HTMLVideoElement)) return;
    video.pause();
    video.removeAttribute("src");
    video.load();
  }
}

export class AppMediaManager {
  constructor({
    resolverBaseUrl,
    country = "AR",
    getAccessToken = async () => null,
    adapter = new HtmlVideoAdapter(),
    fetchImpl = globalThis.fetch?.bind(globalThis),
    adultCatalogEnabled = false
  } = {}) {
    this.baseUrl = normalizeBaseUrl(resolverBaseUrl);
    this.country = normalizeCountry(country);
    if (typeof fetchImpl !== "function") throw new MediaManagerError("Fetch no está disponible en este dispositivo.");
    this.getAccessToken = getAccessToken;
    this.adapter = adapter;
    this.fetchImpl = fetchImpl;
    // La sección se mantiene vacía hasta que exista habilitación explícita.
    this.adultCatalogEnabled = adultCatalogEnabled === true;
  }

  async request(path) {
    const token = await this.getAccessToken();
    const headers = { Accept: "application/json" };
    if (typeof token === "string" && token.trim()) headers.Authorization = "Bearer " + token.trim();
    const response = await this.fetchImpl(this.baseUrl + path, { method: "GET", headers, cache: "no-store" });
    let body;
    try { body = await response.json(); } catch { body = null; }
    if (!response.ok || !body?.success) {
      throw new MediaManagerError(body?.error || "No se pudo consultar el catálogo multimedia.", response.status);
    }
    return body;
  }

  async getCatalog(sectionName, { country = this.country, subcategory = null } = {}) {
    if (sectionName === ADULT_SECTION) {
      return { title: "Contenido para Adultos", items: [] };
    }
    const category = SECTIONS[sectionName];
    if (!category) throw new MediaManagerError("El catálogo solicitado no existe.", 404);
    const selectedCountry = normalizeCountry(country, this.country);
    const body = await this.request(
      "/api/v2/catalog/" + encodeURIComponent(category) + "/" + encodeURIComponent(selectedCountry)
    );
    let items = Array.isArray(body.items) ? body.items : [];
    if (sectionName === "deportes") {
      items = items.filter(item => String(item.category || "").toLowerCase() === "sports");
    }
    if (subcategory && (sectionName === "peliculas" || sectionName === "series")) {
      const wanted = String(subcategory).trim().toLowerCase();
      items = items.filter(item => {
        const genres = Array.isArray(item.genres) ? item.genres : [];
        if (item.genre) genres.push(item.genre);
        return genres.some(genre => String(genre).toLowerCase() === wanted);
      });
    }
    return {
      title: ({ tv_en_vivo: "Televisión en Vivo", deportes: "Deportes en Vivo", peliculas: "Películas", series: "Series" })[sectionName],
      items
    };
  }

  // API de compatibilidad en español para pantallas y módulos existentes.
  async getContenidoPorPais(seccion, subcategoria) {
    if (seccion === ADULT_SECTION) {
      throw new MediaManagerError("La sección para adultos permanece vacía y protegida.", 403);
    }
    if (seccion === "tv_en_vivo" || seccion === "deportes") {
      const country = normalizeCountry(subcategoria, this.country);
      const result = await this.getCatalog(seccion, { country });
      return result.items;
    }
    if (seccion === "peliculas" || seccion === "series") {
      const result = await this.getCatalog(seccion, { subcategory: subcategoria });
      return result.items;
    }
    throw new MediaManagerError("El catálogo solicitado no existe.", 404);
  }

  async play(sectionName, itemId, nativeVideoComponent, country = this.country) {
    if (sectionName === ADULT_SECTION) {
      throw new MediaManagerError("La sección para adultos permanece vacía y deshabilitada.", 403);
    }
    const category = SECTIONS[sectionName];
    if (!category) throw new MediaManagerError("El catálogo solicitado no existe.", 404);
    if (typeof itemId !== "string" || !UUID_RE.test(itemId)) {
      throw new MediaManagerError("El identificador del contenido no es válido.", 400);
    }
    const body = await this.request(
      "/api/v2/resolve/" + encodeURIComponent(category) + "/" +
      encodeURIComponent(normalizeCountry(country, this.country)) + "/" + encodeURIComponent(itemId)
    );
    if (body.mode !== "direct_stream") {
      throw new MediaManagerError("Este contenido no ofrece reproducción directa integrada. No se abrirán aplicaciones externas.");
    }
    return this.adapter.play(nativeVideoComponent, body);
  }

  // Mantiene la firma solicitada por la interfaz de catálogo en español.
  async reproducirEnApp(seccion, subcategoria, itemId, nativeVideoComponent) {
    if (seccion === ADULT_SECTION) {
      throw new MediaManagerError("La sección para adultos permanece vacía y protegida.", 403);
    }
    return this.play(seccion, itemId, nativeVideoComponent, normalizeCountry(subcategoria, this.country));
  }

  stop(nativeVideoComponent) {
    this.adapter.stop(nativeVideoComponent);
  }
}
