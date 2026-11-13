import "jsr:@supabase/functions-js/edge-runtime.d.ts";

type Channel = {
  id: string;
  title: string;
  playback_type: "official_web" | "hls" | "dash" | "youtube_live";
  official_watch_url: string | null;
  stream_url: string | null;
  last_checked_at: string | null;
  is_published: boolean;
  logo_url: string | null;
  logo_source_url: string | null;
};

type CheckResult = {
  status: "online" | "offline" | "blocked" | "pending_review";
  detail: string;
  logo?: { url: string; sourceUrl: string };
};

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "https://rbnuxapfsnaabrzuxvdy.supabase.co";
const legacyServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
const secretKeysRaw = Deno.env.get("SUPABASE_SECRET_KEYS");
const secretKeys = secretKeysRaw ? JSON.parse(secretKeysRaw) as Record<string, string> : {};
const serviceKey = legacyServiceKey ?? secretKeys.default;
const publishableKeysRaw = Deno.env.get("SUPABASE_PUBLISHABLE_KEYS");
const publishableKeys = publishableKeysRaw ? JSON.parse(publishableKeysRaw) as Record<string, string> : {};
const expectedPublishableKey = publishableKeys.default ?? Deno.env.get("SUPABASE_ANON_KEY");
const serviceHeaders: HeadersInit = {
  apikey: serviceKey ?? "",
  ...(legacyServiceKey ? { Authorization: `Bearer ${legacyServiceKey}` } : {}),
  "Content-Type": "application/json",
  Accept: "application/json",
};

function isYoutubeLivePage(value: string | null): boolean {
  if (!value) return false;
  try {
    const url = new URL(value);
    return (url.hostname === "youtube.com" || url.hostname === "www.youtube.com")
      ? /^\/(?:@[^/]+|channel\/[^/]+|c\/[^/]+|[A-Za-z0-9_-]+)\/live\/?$/i.test(url.pathname)
      : false;
  } catch {
    return false;
  }
}

function safeHttps(value: string | null): value is string {
  if (!value) return false;
  try {
    const url = new URL(value);
    return url.protocol === "https:" && !!url.hostname && !url.username && !url.password;
  } catch {
    return false;
  }
}

async function readSample(response: Response, maxBytes = 8192): Promise<string> {
  if (!response.body) return "";
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  try {
    while (total < maxBytes) {
      const { value, done } = await reader.read();
      if (done || !value) break;
      const take = value.subarray(0, Math.min(value.length, maxBytes - total));
      chunks.push(take);
      total += take.length;
      if (take.length < value.length) break;
    }
  } finally {
    await reader.cancel().catch(() => undefined);
  }
  const merged = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    merged.set(chunk, offset);
    offset += chunk.length;
  }
  return new TextDecoder().decode(merged);
}

async function fetchYoutubeLogo(channelUrl: string | null): Promise<{ url: string; sourceUrl: string } | null> {
  if (!isYoutubeLivePage(channelUrl)) return null;
  try {
    const source = new URL(channelUrl!);
    let path = source.pathname;
    if (path.toLowerCase().endsWith("/live/")) path = path.slice(0, -6);
    else if (path.toLowerCase().endsWith("/live")) path = path.slice(0, -5);
    source.pathname = path;
    let sourceUrl = source.toString();
    if (sourceUrl.endsWith("/")) sourceUrl = sourceUrl.slice(0, -1);
    const response = await fetch(sourceUrl, {
      method: "GET",
      redirect: "follow",
      headers: {
        "User-Agent": "Mozilla/5.0 MILTVPLUS official logo lookup",
        Range: "bytes=0-1048575",
        Accept: "text/html",
      },
      signal: AbortSignal.timeout(9000),
    });
    if (!response.ok || new URL(response.url).protocol !== "https:") return null;
    const sample = await readSample(response, 1048576);
    const marker = 'property="og:image"';
    const markerIndex = sample.indexOf(marker);
    if (markerIndex < 0) return null;
    const tagStart = sample.lastIndexOf("<meta", markerIndex);
    const tagEnd = sample.indexOf(">", markerIndex);
    if (tagStart < 0 || tagEnd < 0) return null;
    const tag = sample.slice(tagStart, tagEnd + 1);
    const contentMarker = 'content="';
    const contentIndex = tag.indexOf(contentMarker);
    if (contentIndex < 0) return null;
    const valueStart = contentIndex + contentMarker.length;
    const valueEnd = tag.indexOf('"', valueStart);
    if (valueEnd < 0) return null;
    const logoUrl = tag.slice(valueStart, valueEnd);
    const logo = new URL(logoUrl);
    const allowedHost = logo.hostname === "yt3.googleusercontent.com" ||
      logo.hostname.endsWith(".googleusercontent.com") ||
      logo.hostname.endsWith(".ggpht.com");
    if (logo.protocol !== "https:" || !allowedHost) return null;
    return { url: logo.toString(), sourceUrl };
  } catch {
    return null;
  }
}
function extractOfficialFavicon(sample: string, pageUrl: string): { url: string; sourceUrl: string } | null {
  try {
    const base = new URL(pageUrl);
    const lower = sample.toLowerCase();
    const markers = ['rel="apple-touch-icon"', 'rel="icon"', 'rel="shortcut icon"'];
    for (const marker of markers) {
      const markerIndex = lower.indexOf(marker);
      if (markerIndex < 0) continue;
      const tagStart = lower.lastIndexOf("<link", markerIndex);
      const tagEnd = lower.indexOf(">", markerIndex);
      if (tagStart < 0 || tagEnd < 0) continue;
      const tag = sample.slice(tagStart, tagEnd + 1);
      const hrefMarker = 'href="';
      const hrefIndex = tag.toLowerCase().indexOf(hrefMarker);
      if (hrefIndex < 0) continue;
      const valueStart = hrefIndex + hrefMarker.length;
      const valueEnd = tag.indexOf('"', valueStart);
      if (valueEnd < 0) continue;
      const icon = new URL(tag.slice(valueStart, valueEnd), pageUrl);
      if (icon.protocol === "https:" && !!icon.hostname && !icon.username && !icon.password) {
        return { url: icon.toString(), sourceUrl: pageUrl };
      }
    }
  } catch {
    return null;
  }
  return null;
}

async function fetchOfficialFaviconPage(pageUrl: string | null): Promise<{ url: string; sourceUrl: string } | null> {
  if (!safeHttps(pageUrl)) return null;
  try {
    const response = await fetch(pageUrl, {
      method: "GET",
      redirect: "follow",
      headers: {
        "User-Agent": "Mozilla/5.0 MILTVPLUS official logo lookup",
        Range: "bytes=0-65535",
        Accept: "text/html",
      },
      signal: AbortSignal.timeout(9000),
    });
    if (!response.ok || new URL(response.url).protocol !== "https:") return null;
    const sample = await readSample(response, 65536);
    return extractOfficialFavicon(sample, response.url);
  } catch {
    return null;
  }
}


async function checkChannel(channel: Channel): Promise<CheckResult> {
  const url = channel.playback_type === "official_web"
    ? channel.official_watch_url
    : channel.stream_url;

  if (!safeHttps(url)) {
    return { status: "blocked", detail: "La ficha no contiene una URL HTTPS segura para comprobar." };
  }

  try {
    const requestUrl = new URL(url);
    const youtubePage = requestUrl.hostname === "youtube.com" || requestUrl.hostname === "www.youtube.com";
    const response = await fetch(url, {
      method: "GET",
      redirect: "follow",
      headers: {
        "User-Agent": "MILTVPLUS-TV-Health/1.0",
        Range: youtubePage ? "bytes=0-1048575" : "bytes=0-8191",
        Accept: "*/*",
      },
      signal: AbortSignal.timeout(9000),
    });
    const finalUrl = new URL(response.url);
    const sample = await readSample(response, youtubePage ? 1048576 : 8192);
    const officialLogo = channel.playback_type === "official_web" && !youtubePage
      ? extractOfficialFavicon(sample, response.url)
      : null;
    if (finalUrl.protocol !== "https:") {
      return { status: "blocked", detail: "La redirección abandonó HTTPS y se bloqueó por seguridad." };
    }
    if ([401, 403, 429].includes(response.status)) {
      return { status: "blocked", detail: `HTTP ${response.status}: el titular o CDN rechazó/limitó el chequeo automatizado.` };
    }
    if (response.status >= 500) {
      return { status: "offline", detail: `HTTP ${response.status}: error del servidor de origen.` };
    }
    if (response.status < 200 || response.status >= 400) {
      return { status: "pending_review", detail: `HTTP ${response.status}: requiere revisión manual.` };
    }

    if (channel.playback_type === "hls") {
      const manifest = sample.replace(/^\uFEFF/, "").trimStart();
      if (!manifest.startsWith("#EXTM3U")) {
        return { status: "pending_review", detail: "La URL responde, pero no entregó un manifiesto HLS reconocible." };
      }
      if (/#EXT-X-ENDLIST|#EXT-X-PLAYLIST-TYPE:VOD/i.test(manifest)) {
        return { status: "pending_review", detail: "El manifiesto parece VOD/finalizado; no se publica como señal en directo." };
      }
      return { status: "online", detail: "Manifiesto HLS accesible y sin marcadores VOD/ENDLIST; falta QA de reproducción continua en dispositivos." };
    }

    if (channel.playback_type === "dash") {
      if (!/<MPD\b/i.test(sample)) {
        return { status: "pending_review", detail: "La URL responde, pero no entregó un manifiesto MPEG-DASH reconocible." };
      }
      if (!/type\s*=\s*["']dynamic["']/i.test(sample)) {
        return { status: "pending_review", detail: "El MPD no declara type=dynamic; no se confirma como emisión en directo." };
      }
      return { status: "online", detail: "Manifiesto MPEG-DASH dinámico accesible; falta QA de reproducción continua en dispositivos." };
    }

    const youtubeLiveRoute = channel.playback_type === "youtube_live" || isYoutubeLivePage(channel.official_watch_url);
    if (youtubeLiveRoute) {
      const normalizedSample = sample.replaceAll(String.fromCharCode(92) + '"', '"');
      const liveEvidence =
        normalizedSample.includes('"isLiveNow":true') ||
        normalizedSample.includes('"isLiveNow": true') ||
        normalizedSample.includes('"isLive":true') ||
        normalizedSample.includes('"isLive": true');
      if (!liveEvidence) {
        return { status: "pending_review", detail: "La página oficial de YouTube responde, pero no confirma una emisión en directo en este momento." };
      }
      return { status: "online", detail: "YouTube confirma una emisión en directo ahora; se usa el reproductor oficial y sus restricciones." };
    }

    return {
      status: "online",
      detail: `Página oficial accesible (HTTP ${response.status}); este chequeo no verifica que el reproductor esté emitiendo vídeo en directo.`,
      ...(officialLogo ? { logo: officialLogo } : {}),
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return { status: "offline", detail: `Fallo de conexión o timeout: ${message.slice(0, 240)}` };
  }
}

async function patchStatus(id: string, result: CheckResult, checkedAt: string, publishCandidate = false, logo: { url: string; sourceUrl: string } | null = null): Promise<void> {
  const endpoint = new URL("/rest/v1/tv_channels", SUPABASE_URL);
  endpoint.searchParams.set("id", `eq.${id}`);
  const response = await fetch(endpoint, {
    method: "PATCH",
    headers: { ...serviceHeaders, Prefer: "return=minimal" },
    body: JSON.stringify({
      source_status: result.status,
      last_checked_at: checkedAt,
      last_check_detail: result.detail.slice(0, 500),
      ...(publishCandidate ? { is_published: true } : {}),
      ...(logo ? { logo_url: logo.url, logo_source_url: logo.sourceUrl } : {}),
    }),
    signal: AbortSignal.timeout(9000),
  });
  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`No se pudo actualizar el canal ${id}: HTTP ${response.status} ${detail.slice(0, 240)}`);
  }
}

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") {
    return new Response(JSON.stringify({ error: "POST requerido" }), {
      status: 405,
      headers: { "Content-Type": "application/json" },
    });
  }
  // The new publishable key is public by design; rate limiting below prevents repeated checks.
  // verify_jwt is disabled for this function because publishable keys are not JWTs.
  if (!expectedPublishableKey || request.headers.get("apikey") !== expectedPublishableKey) {
    return new Response(JSON.stringify({ error: "API key pública no válida." }), {
      status: 401,
      headers: { "Content-Type": "application/json" },
    });
  }
  if (!serviceKey) {
    return new Response(JSON.stringify({ error: "Falta SUPABASE_SERVICE_ROLE_KEY o SUPABASE_SECRET_KEYS.default en el entorno seguro de Edge Functions." }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }

  try {
    const endpoint = new URL("/rest/v1/tv_channels", SUPABASE_URL);
    endpoint.searchParams.set("select", "id,title,playback_type,official_watch_url,stream_url,last_checked_at,is_published,logo_url,logo_source_url");
    endpoint.searchParams.set("order", "last_checked_at.asc.nullsfirst,title.asc");
    endpoint.searchParams.set("limit", "100");
    const response = await fetch(endpoint, {
      headers: serviceHeaders,
      signal: AbortSignal.timeout(12000),
    });
    if (!response.ok) throw new Error(`No se pudo leer el catálogo: HTTP ${response.status} ${(await response.text()).slice(0, 240)}`);
    const channels = await response.json() as Channel[];
    if (channels.length === 0) {
      return new Response(JSON.stringify({ checked: 0, message: "No hay canales publicados para comprobar." }), {
        headers: { "Content-Type": "application/json" },
      });
    }

    const nowMs = Date.now();
    const freshWindowMs = 4 * 60 * 1000;
    if (channels.every((channel) => channel.last_checked_at && nowMs - Date.parse(channel.last_checked_at) < freshWindowMs)) {
      return new Response(JSON.stringify({ checked: 0, skipped: true, message: "El catálogo ya fue comprobado recientemente." }), {
        headers: { "Content-Type": "application/json" },
      });
    }

    const checkedAt = new Date().toISOString();
    const summary = { checked: 0, online: 0, offline: 0, blocked: 0, pending_review: 0, published: 0, errors: [] as string[] };
    for (let offset = 0; offset < channels.length; offset += 10) {
      const batch = channels.slice(offset, offset + 10);
      await Promise.all(batch.map(async (channel) => {
        try {
          if (!channel.is_published && !isYoutubeLivePage(channel.official_watch_url)) {
            const manualReview: CheckResult = {
              status: "pending_review",
              detail: "Pendiente de revisión manual; no se publica automáticamente porque no es una ruta oficial de YouTube Live verificable.",
            };
            await patchStatus(channel.id, manualReview, checkedAt);
            summary.checked++;
            summary.pending_review++;
            return;
          }
          const result = await checkChannel(channel);
          let logo = channel.logo_url ? null : (result.logo ?? null);
          if (!channel.logo_url && !logo) {
            logo = isYoutubeLivePage(channel.official_watch_url)
              ? await fetchYoutubeLogo(channel.official_watch_url)
              : await fetchOfficialFaviconPage(channel.logo_source_url ?? channel.official_watch_url);
          }
          const publishCandidate = !channel.is_published &&
            result.status === "online" &&
            result.detail.startsWith("YouTube confirma una emisión en directo ahora");
          const effectiveResult = !channel.is_published && !publishCandidate
            ? { status: "pending_review" as const, detail: result.detail, ...(result.logo ? { logo: result.logo } : {}) }
            : result;
          await patchStatus(channel.id, effectiveResult, checkedAt, publishCandidate, logo);
          summary.checked++;
          summary[effectiveResult.status]++;
          if (publishCandidate) summary.published++;
        } catch (error) {
          summary.errors.push(error instanceof Error ? error.message : String(error));
        }
      }));
    }

    console.log("tv-channel-health", JSON.stringify(summary));
    return new Response(JSON.stringify(summary), {
      headers: { "Content-Type": "application/json" },
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error("tv-channel-health failed", message);
    return new Response(JSON.stringify({ error: message }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
