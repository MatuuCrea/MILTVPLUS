import { buildTmdbRoute, type RequestInput, type TmdbRoute } from "./routes.ts";

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
