import "jsr:@supabase/functions-js/edge-runtime.d.ts";

type Channel = {
  id: string;
  title: string;
  playback_type: "official_web" | "hls" | "dash" | "youtube_live";
  official_watch_url: string | null;
  stream_url: string | null;
  last_checked_at: string | null;
};

type CheckResult = {
  status: "online" | "offline" | "blocked" | "pending_review";
  detail: string;
};

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "https://rbnuxapfsnaabrzuxvdy.supabase.co";
const legacyServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
const secretKeysRaw = Deno.env.get("SUPABASE_SECRET_KEYS");
const secretKeys = secretKeysRaw ? JSON.parse(secretKeysRaw) as Record<string, string> : {};
const serviceKey = legacyServiceKey ?? secretKeys.default;
const serviceHeaders: HeadersInit = {
  apikey: serviceKey ?? "",
  ...(legacyServiceKey ? { Authorization: `Bearer ${legacyServiceKey}` } : {}),
  "Content-Type": "application/json",
  Accept: "application/json",
};

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

async function checkChannel(channel: Channel): Promise<CheckResult> {
  const url = channel.playback_type === "official_web"
    ? channel.official_watch_url
    : channel.stream_url;

  if (!safeHttps(url)) {
    return { status: "blocked", detail: "La ficha no contiene una URL HTTPS segura para comprobar." };
  }

  try {
    const response = await fetch(url, {
      method: "GET",
      redirect: "follow",
      headers: {
        "User-Agent": "MILTVPLUS-TV-Health/1.0",
        Range: "bytes=0-8191",
        Accept: "*/*",
      },
      signal: AbortSignal.timeout(9000),
    });
    const finalUrl = new URL(response.url);
    const sample = await readSample(response);
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

    if (channel.playback_type === "youtube_live") {
      const liveEvidence = /"isLiveNow"\s*:\s*true|"isLive"\s*:\s*true|"isLiveContent"\s*:\s*true/i.test(sample);
      if (!liveEvidence) {
        return { status: "pending_review", detail: "La página de YouTube responde, pero no se pudo confirmar que haya una emisión en directo ahora." };
      }
      return { status: "online", detail: "La página oficial expone un marcador de emisión en directo; el reproductor de YouTube conserva sus restricciones." };
    }

    return {
      status: "online",
      detail: `Página oficial accesible (HTTP ${response.status}); este chequeo no verifica que el reproductor esté emitiendo vídeo en directo.`,
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return { status: "offline", detail: `Fallo de conexión o timeout: ${message.slice(0, 240)}` };
  }
}

async function patchStatus(id: string, result: CheckResult, checkedAt: string): Promise<void> {
  const endpoint = new URL("/rest/v1/tv_channels", SUPABASE_URL);
  endpoint.searchParams.set("id", `eq.${id}`);
  const response = await fetch(endpoint, {
    method: "PATCH",
    headers: { ...serviceHeaders, Prefer: "return=minimal" },
    body: JSON.stringify({
      source_status: result.status,
      last_checked_at: checkedAt,
      last_check_detail: result.detail.slice(0, 500),
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
  if (!serviceKey) {
    return new Response(JSON.stringify({ error: "Falta SUPABASE_SERVICE_ROLE_KEY o SUPABASE_SECRET_KEYS.default en el entorno seguro de Edge Functions." }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }

  try {
    const endpoint = new URL("/rest/v1/tv_channels", SUPABASE_URL);
    endpoint.searchParams.set("select", "id,title,playback_type,official_watch_url,stream_url,last_checked_at");
    endpoint.searchParams.set("is_published", "eq.true");
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
    const summary = { checked: 0, online: 0, offline: 0, blocked: 0, pending_review: 0, errors: [] as string[] };
    for (let offset = 0; offset < channels.length; offset += 10) {
      const batch = channels.slice(offset, offset + 10);
      await Promise.all(batch.map(async (channel) => {
        const result = await checkChannel(channel);
        try {
          await patchStatus(channel.id, result, checkedAt);
          summary.checked++;
          summary[result.status]++;
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
