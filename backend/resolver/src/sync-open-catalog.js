import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const ROOT = dirname(fileURLToPath(import.meta.url));
const MANIFEST_PATH = join(ROOT, "approved-catalog.json");
const SUPABASE_URL = process.env.SUPABASE_URL;
const SERVICE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;
const MAX_MANIFEST_BYTES = 1_000_000;
const TIMEOUT_MS = 12_000;

function requiredString(value, field) {
  if (typeof value !== "string" || !value.trim()) throw new Error(`Missing ${field}`);
  return value.trim();
}
function httpsUrl(value, field) {
  const raw = requiredString(value, field);
  const url = new URL(raw);
  if (url.protocol !== "https:" || url.username || url.password || url.port) {
    throw new Error(`Unsafe URL in ${field}`);
  }
  return url.toString();
}
function validateRights(item) {
  if (item.rights_review_status !== "approved" || item.rights_verified !== true) {
    throw new Error("Rights not curator-approved");
  }
  requiredString(item.rights_holder, "rights_holder");
  const evidence = httpsUrl(item.rights_reference, "rights_reference");
  if (!Array.isArray(item.rights_regions) || !item.rights_regions.length) {
    throw new Error("rights_regions must be a non-empty array");
  }
  if (!item.rights_valid_from || !Number.isFinite(Date.parse(item.rights_valid_from))) {
    throw new Error("rights_valid_from must be an ISO date");
  }
  if (item.rights_valid_until && !Number.isFinite(Date.parse(item.rights_valid_until))) {
    throw new Error("rights_valid_until must be an ISO date");
  }
  if (item.rights_valid_until && Date.parse(item.rights_valid_until) <= Date.now()) {
    throw new Error("Rights have expired");
  }
  return evidence;
}
async function request(url, init = {}) {
  const response = await fetch(url, { ...init, signal: AbortSignal.timeout(TIMEOUT_MS), redirect: "manual" });
  if (response.status >= 300 && response.status < 400) throw new Error("Redirects are not accepted for catalog validation");
  if (!response.ok) throw new Error(`Source returned HTTP ${response.status}`);
  return response;
}
async function validateMediaUrl(value, type) {
  const url = httpsUrl(value, "stream_url");
  if (type === "hls") {
    const response = await request(url, { headers: { Range: "bytes=0-16383" } });
    const body = (await response.text()).slice(0, 16384);
    if (!body.startsWith("#EXTM3U") || !body.includes("#EXT-X-")) {
      throw new Error("URL is not a valid HLS media/master playlist");
    }
  } else {
    const response = await request(url, { method: "HEAD" });
    const contentType = (response.headers.get("content-type") || "").toLowerCase();
    const allowed = type === "video" ? ["video/", "application/mp4"] : ["audio/", "application/ogg"];
    if (!allowed.some((prefix) => contentType.includes(prefix))) {
      throw new Error(`Unexpected content-type for ${type}: ${contentType || "missing"}`);
    }
  }
  return url;
}
function baseRights(item, evidence) {
  return {
    rights_verified: true,
    rights_holder: item.rights_holder.trim(),
    rights_reference: evidence,
    rights_regions: item.rights_regions.map((x) => String(x).toUpperCase()),
    rights_valid_from: item.rights_valid_from,
    rights_valid_until: item.rights_valid_until || null,
    is_published: true
  };
}
async function upsert(table, rows) {
  if (!rows.length) return 0;
  const url = new URL(`/rest/v1/${table}`, SUPABASE_URL);
  url.searchParams.set("on_conflict", "id");
  const response = await request(url, {
    method: "POST",
    headers: {
      apikey: SERVICE_KEY,
      Authorization: `Bearer ${SERVICE_KEY}`,
      "Content-Type": "application/json",
      Prefer: "resolution=merge-duplicates,return=representation"
    },
    body: JSON.stringify(rows)
  });
  const data = await response.json();
  if (!Array.isArray(data)) throw new Error(`Unexpected upsert response for ${table}`);
  return data.length;
}
async function prepare(item) {
  const evidence = validateRights(item);
  const rights = baseRights(item, evidence);
  const title = requiredString(item.title, "title");
  const id = requiredString(item.id, "id");
  if (!/^[0-9a-f-]{36}$/i.test(id)) throw new Error("id must be a stable UUID");
  const country = String(item.country_code || "").toUpperCase();
  if (!/^[A-Z]{2}$/.test(country)) throw new Error("country_code must be ISO alpha-2");

  if (item.type === "tv") {
    const stream = await validateMediaUrl(item.stream_url, "hls");
    return { table: "tv_channels", row: {
      id, slug: requiredString(item.slug, "slug"), title, country_code: country,
      language_code: item.language_code || null, category: item.category || "general",
      is_free_to_air: true, playback_type: "hls", source_status: "online",
      last_checked_at: new Date().toISOString(), last_check_detail: "HLS manifest validated by rights-gated ingest",
      access_model: "free_to_air", stream_url: stream, official_watch_url: httpsUrl(item.official_watch_url, "official_watch_url"),
      ...rights
    }};
  }
  if (item.type === "movie" || item.type === "series" || item.type === "episode") {
    const stream = await validateMediaUrl(item.stream_url, "video");
    return { table: "catalog_items", row: {
      id, title, kind: item.type, country_code: country, language_code: item.language_code || null,
      description: item.description || null, genre: item.genre || null,
      genres: Array.isArray(item.genres) ? item.genres : [],
      release_year: item.release_year || null, duration_seconds: item.duration_seconds || null,
      series_id: item.type === "episode" ? requiredString(item.series_id, "series_id") : null,
      season_number: item.type === "episode" ? Number(item.season_number) : null,
      episode_number: item.type === "episode" ? Number(item.episode_number) : null,
      age_rating: item.age_rating || "ATP", poster_url: item.poster_url || null,
      stream_url: stream, source_url: null, is_adult_content: false, ...rights
    }};
  }
  if (item.type === "music") {
    const stream = await validateMediaUrl(item.stream_url, "audio");
    return { table: "music_items", row: {
      id, item_type: "track", title, creator: item.creator || null, country_code: country,
      album: item.album || null, genre: item.genre || null, release_year: item.release_year || null,
      language_code: item.language_code || null, external_url: null, source_url: evidence,
      stream_url: stream, is_featured: false, ...rights
    }};
  }
  if (item.type === "sports") {
    const stream = await validateMediaUrl(item.stream_url, "hls");
    const start = requiredString(item.starts_at, "starts_at");
    if (!Number.isFinite(Date.parse(start))) throw new Error("starts_at must be an ISO date");
    return { table: "sports_events", row: { id, title, description: item.description || null, sport: requiredString(item.sport, "sport"), competition: item.competition || null, country_code: country, starts_at: start, ends_at: item.ends_at || null, official_info_url: httpsUrl(item.official_info_url, "official_info_url"), authorized_stream_url: stream, source_url: evidence, poster_url: item.poster_url || null, ...rights } };
  }
  throw new Error(`Unsupported type: ${item.type}`);
}

if (!SUPABASE_URL || !SERVICE_KEY) {
  throw new Error("Set SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY in the protected backend environment");
}
const raw = await readFile(MANIFEST_PATH, "utf8");
if (Buffer.byteLength(raw, "utf8") > MAX_MANIFEST_BYTES) throw new Error("Manifest exceeds size limit");
const manifest = JSON.parse(raw);
if (!Array.isArray(manifest.items)) throw new Error("Manifest must contain an items array");

const prepared = [];
const rejected = [];
for (const [index, item] of manifest.items.entries()) {
  try { prepared.push(await prepare(item)); }
  catch (error) { rejected.push({ index, title: item?.title || null, reason: error.message }); }
}
const grouped = new Map();
for (const entry of prepared) {
  if (!grouped.has(entry.table)) grouped.set(entry.table, []);
  grouped.get(entry.table).push(entry.row);
}
const inserted = {};
for (const [table, rows] of grouped) inserted[table] = await upsert(table, rows);
const report = {
  manifest_items: manifest.items.length,
  accepted_and_upserted: Object.values(inserted).reduce((sum, count) => sum + count, 0),
  inserted_by_table: inserted,
  rejected_count: rejected.length,
  rejected
};
console.log(JSON.stringify(report, null, 2));
