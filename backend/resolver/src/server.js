import express from "express";
import helmet from "helmet";
import { rateLimit } from "express-rate-limit";
import { CatalogError, SupabaseCatalog } from "./catalog.js";
import { hasActiveRights, isRecentCheck, isSafeHttpsUrl } from "./rights.js";

const app = express();
const port = Number(process.env.PORT || 3000);
const supabaseUrl = process.env.SUPABASE_URL || "https://rbnuxapfsnaabrzuxvdy.supabase.co";
const supabaseKey = process.env.SUPABASE_ANON_KEY || process.env.SUPABASE_PUBLISHABLE_KEY || "";
const allowedOrigins = new Set((process.env.CORS_ORIGINS || "").split(",").map((x) => x.trim()).filter(Boolean));
const catalog = supabaseKey ? new SupabaseCatalog({ url: supabaseUrl, anonKey: supabaseKey }) : null;

app.disable("x-powered-by");
app.set("trust proxy", 1);
app.use(helmet({ crossOriginResourcePolicy: { policy: "cross-origin" } }));
app.use((req, res, next) => {
  const origin = req.headers.origin;
  if (origin && allowedOrigins.has(origin)) {
    res.setHeader("Access-Control-Allow-Origin", origin);
    res.setHeader("Vary", "Origin");
    res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
    res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  }
  if (req.method === "OPTIONS") return res.sendStatus(origin && !allowedOrigins.has(origin) ? 403 : 204);
  next();
});
app.use(rateLimit({ windowMs: 60_000, limit: 120, standardHeaders: "draft-7", legacyHeaders: false }));

function bearerToken(req) {
  const value = req.get("authorization") || "";
  const match = value.match(/^Bearer\s+(.+)$/i);
  return match?.[1]?.trim() || null;
}

function validateCategory(category) {
  return ["tv_en_vivo", "deportes", "peliculas", "series", "adultos", "musica"].includes(category);
}

function validateCountry(value) {
  if (value === "global" || value === "ALL") return "ALL";
  const normalized = String(value || "").toUpperCase();
  if (!/^[A-Z]{2}$/.test(normalized)) throw new CatalogError("El país debe ser un código ISO 3166-1 alpha-2.", 400);
  return normalized;
}

function isOfficialYoutubeLive(value) {
  if (!isSafeHttpsUrl(value)) return false;
  try {
    const url = new URL(value);
    const host = url.hostname.toLowerCase();
    return ["youtube.com", "www.youtube.com", "m.youtube.com"].includes(host) &&
      /^\/(?:@[^/]+|channel\/[^/]+|c\/[^/]+|user\/[^/]+)\/live\/?$/i.test(url.pathname);
  } catch {
    return false;
  }
}

function mediaResponse(row, category, region) {
  if (!row) throw new CatalogError("Contenido no encontrado o no publicado.", 404);
  const title = row.title;
  if (category === "tv_en_vivo") {
    if (row.source_status !== "online" || !isRecentCheck(row.last_checked_at)) throw new CatalogError("La fuente oficial no tiene una comprobación reciente y disponible.", 503);
    if (row.playback_type === "official_web" || row.playback_type === "youtube_live") {
      const validPage = row.playback_type === "youtube_live"
        ? isOfficialYoutubeLive(row.official_watch_url)
        : isSafeHttpsUrl(row.official_watch_url);
      if (!validPage) throw new CatalogError("La página oficial no tiene una URL HTTPS válida para su tipo de fuente.", 503);
      return { success: true, title, mode: "official_web", protocol: "https_page", watchUrl: row.official_watch_url, directPlayback: false };
    }
    if (!["hls", "dash"].includes(row.playback_type) ||
        !isSafeHttpsUrl(row.stream_url) || !hasActiveRights(row, region)) {
      throw new CatalogError("La reproducción integrada está bloqueada: faltan derechos vigentes para esta región o una fuente válida.", 403);
    }
    return { success: true, title, mode: "direct_stream", protocol: row.playback_type, streamUrl: row.stream_url, directPlayback: true };
  }

  if (category === "deportes") {
    if (isSafeHttpsUrl(row.stream_url) && hasActiveRights(row, region)) {
      return { success: true, title, mode: "direct_stream", protocol: "https", streamUrl: row.stream_url, directPlayback: true, startsAt: row.starts_at || null, endsAt: row.ends_at || null };
    }
    if (isSafeHttpsUrl(row.official_info_url)) {
      return { success: true, title, mode: "official_web", protocol: "https_page", watchUrl: row.official_info_url, directPlayback: false, startsAt: row.starts_at || null, endsAt: row.ends_at || null };
    }
    throw new CatalogError("El evento no tiene una transmisión ni una página oficial disponible.", 503);
  }

  if (category === "musica") {
    if (!isSafeHttpsUrl(row.stream_url) || !hasActiveRights(row, region)) {
      throw new CatalogError("Esta pista no tiene una URL de audio directa con autorización vigente para esta región.", 403);
    }
    return { success: true, title, creator: row.creator || null, album: row.album || null, genre: row.genre || null,
      artworkUrl: isSafeHttpsUrl(row.artwork_url) ? row.artwork_url : null,
      mode: "direct_stream", mediaType: "audio", protocol: "audio", streamUrl: row.stream_url, directPlayback: true };
  }

  if (category === "adultos" && row.is_adult_content !== true) {
    throw new CatalogError("El contenido no está clasificado para el espacio +18.", 403);
  }
  if (row.is_adult_content === true && category !== "adultos") {
    throw new CatalogError("Este contenido pertenece al espacio +18.", 403);
  }
  if (isSafeHttpsUrl(row.stream_url) && hasActiveRights(row, region)) {
    return { success: true, title, mode: "direct_stream", protocol: "https", streamUrl: row.stream_url, directPlayback: true };
  }
  if (isSafeHttpsUrl(row.source_url)) {
    return { success: true, title, mode: "official_web", protocol: "https_page", watchUrl: row.source_url, directPlayback: false };
  }
  throw new CatalogError("No existe una fuente de reproducción autorizada y vigente para este contenido.", 503);
}

app.get("/healthz", async (_req, res) => {
  if (!catalog) {
    return res.status(503).json({ service: "miltvplus-authorized-media-resolver", status: "configuration_required", version: "1.0.0" });
  }
  try {
    await catalog.request("/rest/v1/tv_channels?select=id&limit=1");
    return res.status(200).json({ service: "miltvplus-authorized-media-resolver", status: "ready", version: "1.0.0" });
  } catch {
    return res.status(503).json({ service: "miltvplus-authorized-media-resolver", status: "upstream_unavailable", version: "1.0.0" });
  }
});

app.get("/api/v2/catalog/:category/:country", async (req, res, next) => {
  try {
    const { category } = req.params;
    if (!validateCategory(category)) throw new CatalogError("Categoría no admitida.", 400);
    const country = validateCountry(req.params.country);
    let accessToken = bearerToken(req);
    if (category === "adultos") {
      if (!catalog) throw new CatalogError("El catálogo no está configurado.", 503);
      await catalog.verifyAdultConsent(accessToken);
      res.set("Cache-Control", "private, no-store");
      return res.json({ success: true, category, country, count: 0, items: [] });
    }
    if (!catalog) throw new CatalogError("El catálogo no está configurado.", 503);
    const rows = await catalog.list({ category, country, accessToken });
    const items = rows
      .filter((row) => category === "adultos" ? row.is_adult_content === true : row.is_adult_content !== true)
      .map((row) => {
        const directRights = hasActiveRights(row, country === "ALL" ? "" : country);
        if (category === "deportes") {
          return { ...row, stream_url: directRights && isSafeHttpsUrl(row.stream_url) ? row.stream_url : null };
        }
        if (category === "musica") {
          return { ...row, stream_url: directRights && isSafeHttpsUrl(row.stream_url) ? row.stream_url : null };
        }
        if (category === "tv_en_vivo") {
          const directMode = row.playback_type === "hls" || row.playback_type === "dash";
          const healthy = row.source_status === "online" && isRecentCheck(row.last_checked_at);
          return {
            ...row,
            source_status: healthy ? "online" : "pending_review",
            stream_url: directMode && healthy && directRights ? row.stream_url : null
          };
        }
        return { ...row, stream_url: directRights ? row.stream_url : null };
      });
    res.set("Cache-Control", category === "adultos" ? "private, no-store" : "public, max-age=30");
    res.json({ success: true, category, country, count: items.length, items });
  } catch (error) { next(error); }
});

app.get("/api/v2/resolve/:category/:country/:contentId", async (req, res, next) => {
  try {
    const { category, contentId } = req.params;
    if (!validateCategory(category)) throw new CatalogError("Categoría no admitida.", 400);
    if (!/^[0-9a-f-]{36}$/i.test(contentId)) throw new CatalogError("Identificador de contenido inválido.", 400);
    const country = validateCountry(req.params.country);
    const region = country === "ALL" ? "" : country;
    const accessToken = bearerToken(req);
    if (category === "adultos") {
      await catalog?.verifyAdultConsent(accessToken);
      throw new CatalogError("La sección +18 permanece vacía y no tiene streams activos.", 403);
    }
    if (!catalog) throw new CatalogError("El catálogo no está configurado.", 503);
    const row = await catalog.find({ category, country, id: contentId, accessToken });
    const result = mediaResponse(row, category, region);
    res.set("Cache-Control", "private, no-store");
    res.json(result);
  } catch (error) { next(error); }
});

app.use((error, _req, res, _next) => {
  const status = Number.isInteger(error?.status) ? error.status : 500;
  if (status >= 500) console.error("[resolver]", error?.message || "unknown error");
  res.status(status).json({
    success: false,
    error: status >= 500 ? "El servicio no pudo completar la solicitud. Reintentá más tarde." : error.message
  });
});

if (process.env.NODE_ENV !== "test") {
  app.listen(port, "0.0.0.0", () => {
    console.log("[MILTVPLUS Resolver] listening on " + port);
  });
}

export { app };
