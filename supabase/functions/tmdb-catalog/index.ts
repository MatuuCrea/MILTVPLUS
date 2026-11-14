// TMDB catalog proxy. Only allowlisted actions are exposed; credentials stay in Edge Function secrets.
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Vary": "Origin",
};

const TMDB_BASE = "https://api.themoviedb.org/3";
const CACHE_TTL_MS = 5 * 60 * 1000;
const MAX_CACHE_ENTRIES = 100;
const MAX_RESPONSE_BYTES = 1_500_000;
const cache = new Map<string, { expiresAt: number; body: string }>();

type RequestInput = {
  action?: string;
  query?: string;
  id?: number;
  season?: number;
  page?: number;
};

type TmdbRoute = { path: string; params: Record<string, string> };

const ACTIONS = new Set([
  "movie_popular",
  "movie_top_rated",
  "movie_upcoming",
  "tv_popular",
  "tv_on_the_air",
  "search_movie",
  "search_tv",
  "movie_details",
  "tv_details",
  "movie_credits",
  "tv_credits",
  "tv_season",
]);

export function buildTmdbRoute(input: RequestInput): TmdbRoute {
  const action = input.action ?? "";
  if (!ACTIONS.has(action)) throw new Error("Acción de catálogo no permitida.");

  const page = Math.min(500, Math.max(1, Number.isInteger(input.page) ? input.page! : 1));
  const params: Record<string, string> = {
    language: "es-AR",
    page: String(page),
  };

  if (["movie_popular", "movie_top_rated", "movie_upcoming", "tv_popular", "tv_on_the_air"].includes(action)) {
    if (action.startsWith("movie_")) params.region = "AR";
    const endpoint = action.replace("_", "/");
    return { path: `/${endpoint}`, params };
  }

  if (action === "search_movie" || action === "search_tv") {
    const query = (input.query ?? "").trim();
    if (query.length < 2 || query.length > 100) throw new Error("La búsqueda debe tener entre 2 y 100 caracteres.");
    params.query = query;
    params.include_adult = "false";
    return { path: action === "search_movie" ? "/search/movie" : "/search/tv", params };
  }

  const id = Number(input.id);
  if (!Number.isSafeInteger(id) || id <= 0) throw new Error("Identificador TMDB inválido.");
  if (action === "movie_details") return { path: `/movie/${id}`, params: { ...params, append_to_response: "credits,videos,images" } };
  if (action === "tv_details") return { path: `/tv/${id}`, params: { ...params, append_to_response: "credits,videos,images" } };
  if (action === "movie_credits") return { path: `/movie/${id}/credits`, params };
  if (action === "tv_credits") return { path: `/tv/${id}/credits`, params };
  const season = Number(input.season);
  if (!Number.isSafeInteger(season) || season < 0 || season > 100) throw new Error("Temporada inválida.");
  return { path: `/tv/${id}/season/${season}`, params };
}

function json(body: unknown, status = 200, cacheControl = "no-store"): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json; charset=utf-8", "Cache-Control": cacheControl },
  });
}

async function readLimited(response: Response): Promise<string> {
  if (!response.body) return "";
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      if (!value) continue;
      total += value.byteLength;
      if (total > MAX_RESPONSE_BYTES) {
        await reader.cancel();
        throw new Error("La respuesta del catálogo supera el límite permitido.");
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  const merged = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    merged.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return new TextDecoder().decode(merged);
}

function cacheKey(route: TmdbRoute): string {
  return route.path + "?" + new URLSearchParams(Object.entries(route.params).sort(([a], [b]) => a.localeCompare(b))).toString();
}

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return json({ error: "Método no permitido." }, 405);

  let input: RequestInput;
  try {
    input = await request.json() as RequestInput;
  } catch {
    return json({ error: "JSON inválido." }, 400);
  }

  let route: TmdbRoute;
  try {
    route = buildTmdbRoute(input);
  } catch (error) {
    return json({ error: error instanceof Error ? error.message : "Solicitud inválida." }, 400);
  }

  const bearer = Deno.env.get("TMDB_READ_ACCESS_TOKEN")?.trim();
  const apiKey = Deno.env.get("TMDB_API_KEY")?.trim();
  if (!bearer && !apiKey) return json({ error: "TMDB no está configurado en el servidor." }, 503);

  const key = cacheKey(route);
  const cached = cache.get(key);
  if (cached && cached.expiresAt > Date.now()) {
    return new Response(cached.body, {
      headers: { ...corsHeaders, "Content-Type": "application/json; charset=utf-8", "Cache-Control": "private, max-age=60", "X-MILTVPLUS-Cache": "HIT" },
    });
  }

  const url = new URL(TMDB_BASE + route.path);
  for (const [name, value] of Object.entries(route.params)) url.searchParams.set(name, value);
  if (!bearer && apiKey) url.searchParams.set("api_key", apiKey);

  try {
    const upstream = await fetch(url, {
      method: "GET",
      headers: {
        Accept: "application/json",
        ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}),
      },
      signal: AbortSignal.timeout(12_000),
    });
    const body = await readLimited(upstream);
    if (!upstream.ok) {
      return json({ error: upstream.status === 404 ? "No se encontró el contenido solicitado." : "TMDB no pudo completar la consulta.", upstream_status: upstream.status }, upstream.status === 404 ? 404 : 502);
    }
    try {
      JSON.parse(body);
    } catch {
      return json({ error: "TMDB devolvió una respuesta no válida." }, 502);
    }
    if (cache.size >= MAX_CACHE_ENTRIES) {
      const oldest = cache.keys().next().value;
      if (oldest) cache.delete(oldest);
    }
    cache.set(key, { expiresAt: Date.now() + CACHE_TTL_MS, body });
    return new Response(body, {
      headers: { ...corsHeaders, "Content-Type": "application/json; charset=utf-8", "Cache-Control": "private, max-age=60", "X-MILTVPLUS-Cache": "MISS" },
    });
  } catch {
    return json({ error: "No se pudo conectar con el catálogo TMDB. Intentá nuevamente." }, 502);
  }
});
