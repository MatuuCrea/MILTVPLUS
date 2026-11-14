import test, { before, after } from "node:test";
import assert from "node:assert/strict";
import { createServer } from "node:http";

process.env.NODE_ENV = "test";
process.env.SUPABASE_URL = "https://rbnuxapfsnaabrzuxvdy.supabase.co";
process.env.SUPABASE_ANON_KEY = "test-public-key";

const originalFetch = globalThis.fetch;
const now = Date.now();
const activeRights = {
  rights_verified: true,
  rights_holder: "Rights holder",
  rights_reference: "CONTRACT-001",
  rights_regions: ["AR"],
  rights_valid_from: new Date(now - 86_400_000).toISOString(),
  rights_valid_until: new Date(now + 86_400_000).toISOString()
};
const musicRows = [
  { id: "55555555-5555-4555-8555-555555555555", title: "Licensed song", creator: "Artist", album: "Album", genre: "Rock",
    stream_url: "https://audio.example/track.mp3", artwork_url: "https://images.example/cover.jpg", ...activeRights },
  { id: "66666666-6666-4666-8666-666666666666", title: "Unlicensed song", creator: "Artist", genre: "Jazz",
    stream_url: "https://audio.example/unlicensed.mp3", ...activeRights, rights_verified: false },
  { id: "77777777-7777-4777-8777-777777777777", title: "External-only listing", creator: "Artist", genre: "Ambient",
    external_url: "https://music.example/listing", ...activeRights }
];
const tvRows = [
  {
    id: "11111111-1111-4111-8111-111111111111", title: "Official YouTube",
    playback_type: "youtube_live", source_status: "online", last_checked_at: new Date().toISOString(),
    official_watch_url: "https://www.youtube.com/@Example/live",
    stream_url: "https://youtube.invalid/extracted.m3u8", ...activeRights
  },
  {
    id: "22222222-2222-4222-8222-222222222222", title: "Licensed HLS",
    playback_type: "hls", source_status: "online", last_checked_at: new Date().toISOString(),
    official_watch_url: "https://broadcaster.example/watch",
    stream_url: "https://cdn.example/live.m3u8", ...activeRights
  },
  {
    id: "33333333-3333-4333-8333-333333333333", title: "Expired HLS",
    playback_type: "hls", source_status: "online", last_checked_at: new Date().toISOString(),
    official_watch_url: "https://broadcaster.example/expired",
    stream_url: "https://cdn.example/expired.m3u8",
    ...activeRights,
    rights_valid_until: new Date(now - 60_000).toISOString()
  }
];

globalThis.fetch = async (input, init) => {
  const url = new URL(typeof input === "string" ? input : input.url);
  if (url.origin !== process.env.SUPABASE_URL) return originalFetch(input, init);
  if (url.pathname === "/auth/v1/user") {
    return new Response(JSON.stringify({ id: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa" }), { status: 200 });
  }
  if (url.pathname === "/rest/v1/age_access_consents") {
    return new Response(JSON.stringify([{ confirmed_18_plus: true, confirmed_at: new Date().toISOString() }]), { status: 200 });
  }
  if (url.pathname === "/rest/v1/tv_channels") {
    const id = url.searchParams.get("id")?.replace("eq.", "");
    return new Response(JSON.stringify(id ? tvRows.filter((row) => row.id === id) : tvRows), { status: 200 });
  }
  if (url.pathname === "/rest/v1/music_items") {
    const id = url.searchParams.get("id")?.replace("eq.", "");
    return new Response(JSON.stringify(id ? musicRows.filter((row) => row.id === id) : musicRows), { status: 200 });
  }
  if (url.pathname === "/rest/v1/catalog_items") {
    const adult = url.searchParams.get("is_adult_content") === "eq.true";
    return new Response(JSON.stringify(adult ? [{
      id: "44444444-4444-4444-8444-444444444444",
      title: "Adult restricted title", kind: "movie", is_adult_content: true,
      is_published: true, source_url: "https://provider.example/title"
    }] : []), { status: 200 });
  }
  return new Response(JSON.stringify({ message: "not found" }), { status: 404 });
};

const { app } = await import("../src/server.js");
const server = createServer(app);
let baseUrl;
before(async () => {
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  baseUrl = "http://127.0.0.1:" + server.address().port;
});
after(async () => {
  await new Promise((resolve) => server.close(resolve));
  globalThis.fetch = originalFetch;
});

test("YouTube Live remains on the official player and is never extracted", async () => {
  const response = await originalFetch(baseUrl + "/api/v2/resolve/tv_en_vivo/AR/11111111-1111-4111-8111-111111111111");
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.equal(body.mode, "official_web");
  assert.equal(body.watchUrl, "https://www.youtube.com/@Example/live");
  assert.equal("streamUrl" in body, false);
  assert.equal("adsStripped" in body, false);
});

test("direct HLS is returned only with active regional rights", async () => {
  const valid = await originalFetch(baseUrl + "/api/v2/resolve/tv_en_vivo/AR/22222222-2222-4222-8222-222222222222");
  const validBody = await valid.json();
  assert.equal(valid.status, 200);
  assert.equal(validBody.protocol, "hls");
  assert.equal(validBody.streamUrl, "https://cdn.example/live.m3u8");

  const expired = await originalFetch(baseUrl + "/api/v2/resolve/tv_en_vivo/AR/33333333-3333-4333-8333-333333333333");
  assert.equal(expired.status, 403);
});

test("catalog list redacts expired direct stream URLs", async () => {
  const response = await originalFetch(baseUrl + "/api/v2/catalog/tv_en_vivo/AR");
  const body = await response.json();
  assert.equal(response.status, 200);
  const expired = body.items.find((item) => item.title === "Expired HLS");
  assert.equal(expired.stream_url, null);
});

test("adult catalogue requires a signed-in account and recorded consent", async () => {
  const denied = await originalFetch(baseUrl + "/api/v2/catalog/adultos/AR");
  assert.equal(denied.status, 401);

  const allowed = await originalFetch(baseUrl + "/api/v2/catalog/adultos/AR", {
    headers: { Authorization: "Bearer test-user-jwt" }
  });
  const body = await allowed.json();
  assert.equal(allowed.status, 200);
  assert.equal(body.count, 0);
  assert.deepEqual(body.items, []);
  const blockedPlayback = await originalFetch(baseUrl + "/api/v2/resolve/adultos/AR/44444444-4444-4444-8444-444444444444", {
    headers: { Authorization: "Bearer test-user-jwt" }
  });
  assert.equal(blockedPlayback.status, 403);
});


test("music catalog is isolated and redacts streams without active regional rights", async () => {
  const response = await originalFetch(baseUrl + "/api/v2/catalog/musica/AR");
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.equal(body.category, "musica");
  assert.equal(body.count, 3);
  assert.equal(body.items.find((item) => item.title === "Licensed song").stream_url, "https://audio.example/track.mp3");
  assert.equal(body.items.find((item) => item.title === "Unlicensed song").stream_url, null);
  assert.equal(body.items.find((item) => item.title === "External-only listing").stream_url, null);
});

test("music resolver returns only direct audio streams and never a browser fallback", async () => {
  const valid = await originalFetch(baseUrl + "/api/v2/resolve/musica/AR/55555555-5555-4555-8555-555555555555");
  const body = await valid.json();
  assert.equal(valid.status, 200);
  assert.equal(body.mediaType, "audio");
  assert.equal(body.mode, "direct_stream");
  assert.equal(body.streamUrl, "https://audio.example/track.mp3");
  assert.equal("watchUrl" in body, false);
  const invalid = await originalFetch(baseUrl + "/api/v2/resolve/musica/AR/66666666-6666-4666-8666-666666666666");
  assert.equal(invalid.status, 403);
  const externalOnly = await originalFetch(baseUrl + "/api/v2/resolve/musica/AR/77777777-7777-4777-8777-777777777777");
  assert.equal(externalOnly.status, 403);
  const wrongRegion = await originalFetch(baseUrl + "/api/v2/resolve/musica/BR/55555555-5555-4555-8555-555555555555");
  assert.equal(wrongRegion.status, 403);
});
