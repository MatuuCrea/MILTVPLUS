/**
 * MILTVPLUS — gestor multimedia compartido y multiplataforma.
 *
 * Catálogos: TV en vivo, Deportes, Películas y Series. Adultos permanece
 * deliberadamente vacío y bloqueado.
 *
 * Reproducción interna:
 * - Android: puente nativo hacia Media3 / ExoPlayer.
 * - Windows: puente nativo hacia LibVLC.
 * - Tizen: AVPlay.
 * - webOS / navegador compatible: HTMLVideoElement.
 *
 * El backend debe resolver únicamente URLs directas autorizadas (HLS/DASH/MP4).
 * Este módulo nunca convierte páginas web de YouTube en streams ni las abre
 * fuera de la aplicación.
 */

const SECTION_CONFIG = Object.freeze({
  tv_en_vivo: { api: "tv_en_vivo", title: "Televisión en Vivo", partition: "country" },
  deportes: { api: "deportes", title: "Deportes en Vivo", partition: "country" },
  peliculas: { api: "peliculas", title: "Películas", partition: "genre" },
  series: { api: "series", title: "Series", partition: "genre" }
});

const ADULT_SECTION = "adultos";
const COUNTRY_CODES = Object.freeze({
  argentina: "AR", mexico: "MX", espana: "ES",
  internacional: "ALL", global: "ALL", todo_el_mundo: "ALL"
});
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export class MediaManagerError extends Error {
  constructor(message, status = 0) {
    super(message);
    this.name = "MediaManagerError";
    this.status = status;
  }
}

function normalizeCountry(value, fallback = "AR") {
  const raw = String(value || fallback).trim().toLowerCase();
  const code = COUNTRY_CODES[raw] || raw.toUpperCase();
  if (code !== "ALL" && !/^[A-Z]{2}$/.test(code)) {
    throw new MediaManagerError("País no válido: utiliza un código ISO de dos letras o ALL.");
  }
  return code;
}

function normalizeBaseUrl(value) {
  if (typeof value !== "string" || !value.trim()) {
    throw new MediaManagerError("Falta configurar resolverBaseUrl para cargar catálogos y resolver streams.");
  }
  let url;
  try { url = new URL(value); } catch {
    throw new MediaManagerError("La URL del servicio multimedia no es válida.");
  }
  if (url.protocol !== "https:" && !["localhost", "127.0.0.1"].includes(url.hostname)) {
    throw new MediaManagerError("El servicio multimedia debe utilizar HTTPS.");
  }
  return url.href.replace(/\/$/, "");
}

function isSafeStreamUrl(value) {
  if (typeof value !== "string" || value.length > 4096) return false;
  try {
    const url = new URL(value);
    return url.protocol === "https:" && !url.username && !url.password;
  } catch { return false; }
}

function getMimeType(protocol) {
  switch (String(protocol || "").toLowerCase()) {
    case "hls": return "application/x-mpegURL";
    case "dash": return "application/dash+xml";
    case "mp4": return "video/mp4";
    case "https": return "";
    default: throw new MediaManagerError("Protocolo multimedia no compatible.");
  }
}

function assertResolvedStream(resolved) {
  if (!resolved || resolved.mode !== "direct_stream" || !isSafeStreamUrl(resolved.streamUrl)) {
    throw new MediaManagerError("El servidor no devolvió una URL directa HTTPS apta para reproducción integrada.");
  }
  getMimeType(resolved.protocol);
}

function getGlobal() {
  return typeof globalThis !== "undefined" ? globalThis : {};
}

/**
 * Puente común para apps nativas. El host Android debe conectar play() con
 * Media3/ExoPlayer; el host Windows debe conectarlo con LibVLC.
 *
 * Contrato: window.MILTVPLUS_NATIVE_PLAYER.play({url,mimeType,title,fullscreen})
 * y .stop(). El puente debe resolver la promesa al iniciar o rechazarla con
 * un error legible. Nunca se entrega una página HTML como URL de stream.
 */
export class NativeBridgeAdapter {
  constructor(bridge) {
    if (!bridge || typeof bridge.play !== "function") {
      throw new MediaManagerError("El puente nativo no implementa play().");
    }
    this.bridge = bridge;
  }

  async play(_target, resolved) {
    assertResolvedStream(resolved);
    await this.bridge.play({
      url: resolved.streamUrl,
      mimeType: getMimeType(resolved.protocol),
      title: String(resolved.title || "MILTVPLUS"),
      fullscreen: true
    });
    return { title: resolved.title || "MILTVPLUS", mode: "direct_stream", renderer: "native" };
  }

  async stop() {
    if (typeof this.bridge.stop === "function") await this.bridge.stop();
  }
}

/** Reproductor HTML5 para webOS y dispositivos con video nativo compatible. */
export class HtmlVideoAdapter {
  async play(video, resolved) {
    assertResolvedStream(resolved);
    const VideoElement = getGlobal().HTMLVideoElement;
    if (!VideoElement || !(video instanceof VideoElement)) {
      throw new MediaManagerError("Se necesita un elemento de video nativo válido.");
    }
    const protocol = String(resolved.protocol || "").toLowerCase();
    if (protocol === "dash") {
      throw new MediaManagerError("DASH no está disponible en este motor HTML5. Utiliza el reproductor nativo de la plataforma.");
    }
    if (!["hls", "https", "mp4"].includes(protocol)) {
      throw new MediaManagerError("Formato no compatible con el reproductor HTML5.");
    }

    video.pause();
    video.removeAttribute("src");
    while (video.firstChild) video.removeChild(video.firstChild);
    video.src = resolved.streamUrl;
    video.load();
    await video.play();

    // Fullscreen se solicita en la misma interacción del usuario que inició play().
    const doc = getGlobal().document;
    if (typeof video.requestFullscreen === "function" && !doc?.fullscreenElement) {
      try { await video.requestFullscreen(); } catch { /* El SO/TV puede controlar el fullscreen. */ }
    } else if (typeof video.webkitEnterFullscreen === "function") {
      try { video.webkitEnterFullscreen(); } catch { /* No soportado por todos los modelos. */ }
    }
    return { title: resolved.title || "MILTVPLUS", mode: "direct_stream", renderer: "html-video" };
  }

  stop(video) {
    const VideoElement = getGlobal().HTMLVideoElement;
    if (!VideoElement || !(video instanceof VideoElement)) return;
    video.pause();
    video.removeAttribute("src");
    video.load();
  }
}

/** Adaptador Tizen AVPlay (la app debe tener permisos AVPlay en config.xml). */
export class TizenAvPlayAdapter {
  constructor(avplay = getGlobal().webapis?.avplay) {
    if (!avplay) throw new MediaManagerError("AVPlay no está disponible en este televisor Tizen.");
    this.avplay = avplay;
  }

  play(_target, resolved) {
    assertResolvedStream(resolved);
    const av = this.avplay;
    return new Promise((resolve, reject) => {
      try {
        try { av.stop(); } catch { /* No había sesión activa. */ }
        try { av.close(); } catch { /* No había sesión abierta. */ }
        av.open(resolved.streamUrl);
        av.setDisplayMethod("PLAYER_DISPLAY_MODE_FULL_SCREEN");
        av.setListener({
          onstreamcompleted: () => { try { av.stop(); } catch {} },
          onerror: error => reject(new MediaManagerError("AVPlay no pudo reproducir el stream: " + String(error)))
        });
        av.prepareAsync(
          () => {
            try {
              av.setDisplayRect(0, 0, getGlobal().screen?.width || 1920, getGlobal().screen?.height || 1080);
              av.play();
              resolve({ title: resolved.title || "MILTVPLUS", mode: "direct_stream", renderer: "tizen-avplay" });
            } catch (error) { reject(error); }
          },
          error => reject(new MediaManagerError("AVPlay no pudo preparar el stream: " + String(error)))
        );
      } catch (error) { reject(error); }
    });
  }

  stop() {
    try { this.avplay.stop(); } finally {
      try { this.avplay.close(); } catch { /* Ignorar cierre sin sesión. */ }
    }
  }
}

function selectDefaultAdapter() {
  const root = getGlobal();
  const bridge = root.MILTVPLUS_NATIVE_PLAYER;
  if (bridge?.play) return new NativeBridgeAdapter(bridge);
  if (root.webapis?.avplay) return new TizenAvPlayAdapter(root.webapis.avplay);
  return new HtmlVideoAdapter();
}

export class AppMediaManager {
  constructor({
    resolverBaseUrl,
    country = "AR",
    getAccessToken = async () => null,
    adapter = null,
    fetchImpl = getGlobal().fetch?.bind(getGlobal()),
    adultCatalogEnabled = false
  } = {}) {
    this.baseUrl = normalizeBaseUrl(resolverBaseUrl);
    this.country = normalizeCountry(country);
    if (typeof fetchImpl !== "function") throw new MediaManagerError("Fetch no está disponible en este dispositivo.");
    this.getAccessToken = getAccessToken;
    this.adapter = adapter || selectDefaultAdapter();
    this.fetchImpl = fetchImpl;
    // El catálogo +18 se mantiene vacío incluso si alguien pasa true.
    this.adultCatalogEnabled = false;
  }

  async request(path) {
    const token = await this.getAccessToken();
    const headers = { Accept: "application/json" };
    if (typeof token === "string" && token.trim()) headers.Authorization = "Bearer " + token.trim();
    const response = await this.fetchImpl(this.baseUrl + path, {
      method: "GET", headers, cache: "no-store"
    });
    let body;
    try { body = await response.json(); } catch { body = null; }
    if (!response.ok || !body?.success) {
      throw new MediaManagerError(body?.error || "No se pudo consultar el servicio multimedia.", response.status);
    }
    return body;
  }

  async getCatalog(sectionName, { country = this.country, subcategory = null } = {}) {
    if (sectionName === ADULT_SECTION) return { title: "Contenido para Adultos", items: [] };
    const config = SECTION_CONFIG[sectionName];
    if (!config) throw new MediaManagerError("La sección solicitada no existe.", 404);
    const selectedCountry = normalizeCountry(country, this.country);
    const body = await this.request(
      "/api/v2/catalog/" + encodeURIComponent(config.api) + "/" + encodeURIComponent(selectedCountry)
    );
    let items = Array.isArray(body.items) ? body.items.slice() : [];

    // Deportes es una vista separada: nunca mezcla canales no clasificados como deportes.
    if (config.filter) {
      items = items.filter(item => String(item.category || "").toLowerCase() === config.filter);
    }
    if (subcategory && config.partition === "genre") {
      const wanted = String(subcategory).trim().toLowerCase();
      items = items.filter(item => {
        const genres = Array.isArray(item.genres) ? item.genres : [];
        return genres.concat(item.genre || []).some(value => String(value).trim().toLowerCase() === wanted);
      });
    }
    return { title: config.title, country: selectedCountry, items };
  }

  async getContenidoPorPais(seccion, subcategoria) {
    if (seccion === ADULT_SECTION) throw new MediaManagerError("La sección para adultos está vacía y protegida.", 403);
    if (seccion === "tv_en_vivo" || seccion === "deportes") {
      return (await this.getCatalog(seccion, { country: subcategoria || this.country })).items;
    }
    if (seccion === "peliculas" || seccion === "series") {
      return (await this.getCatalog(seccion, { subcategory: subcategoria || null })).items;
    }
    throw new MediaManagerError("La sección solicitada no existe.", 404);
  }

  async play(sectionName, itemId, nativeVideoComponent = null, country = this.country) {
    if (sectionName === ADULT_SECTION) throw new MediaManagerError("La sección para adultos está vacía y protegida.", 403);
    const config = SECTION_CONFIG[sectionName];
    if (!config) throw new MediaManagerError("La sección solicitada no existe.", 404);
    if (typeof itemId !== "string" || !UUID_RE.test(itemId)) {
      throw new MediaManagerError("El identificador del contenido no es válido.", 400);
    }
    const selectedCountry = normalizeCountry(country, this.country);
    const body = await this.request(
      "/api/v2/resolve/" + encodeURIComponent(config.api) + "/" +
      encodeURIComponent(selectedCountry) + "/" + encodeURIComponent(itemId)
    );
    assertResolvedStream(body);
    return this.adapter.play(nativeVideoComponent, body);
  }

  async reproducirEnApp(seccion, subcategoria, itemId, nativeVideoComponent = null) {
    if (seccion === ADULT_SECTION) throw new MediaManagerError("La sección para adultos está vacía y protegida.", 403);
    const country = (seccion === "tv_en_vivo" || seccion === "deportes")
      ? normalizeCountry(subcategoria, this.country)
      : this.country;
    return this.play(seccion, itemId, nativeVideoComponent, country);
  }

  async stop(nativeVideoComponent = null) {
    if (typeof this.adapter.stop === "function") await this.adapter.stop(nativeVideoComponent);
  }
}

