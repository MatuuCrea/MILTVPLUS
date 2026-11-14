import test from "node:test";
import assert from "node:assert/strict";
import { SupabaseCatalog } from "../src/catalog.js";

test("publishable keys are sent as apikey without being misused as JWT bearer tokens", async () => {
  let captured;
  const client = new SupabaseCatalog({
    url: "https://project.supabase.co",
    anonKey: "sb_publishable_test",
    fetchImpl: async (_url, init) => {
      captured = init.headers;
      return new Response("[]", { status: 200 });
    }
  });
  await client.request("/rest/v1/tv_channels?select=id");
  assert.equal(captured.apikey, "sb_publishable_test");
  assert.equal("Authorization" in captured, false);
});

test("user JWT is forwarded for RLS-protected authenticated requests", async () => {
  let captured;
  const client = new SupabaseCatalog({
    url: "https://project.supabase.co",
    anonKey: "sb_publishable_test",
    fetchImpl: async (_url, init) => {
      captured = init.headers;
      return new Response("[]", { status: 200 });
    }
  });
  await client.request("/rest/v1/age_access_consents", "user-access-token");
  assert.equal(captured.Authorization, "Bearer user-access-token");
});

test("legacy JWT anon keys remain compatible", async () => {
  let captured;
  const client = new SupabaseCatalog({
    url: "https://project.supabase.co",
    anonKey: "header.payload.signature",
    fetchImpl: async (_url, init) => {
      captured = init.headers;
      return new Response("[]", { status: 200 });
    }
  });
  await client.request("/rest/v1/tv_channels?select=id");
  assert.equal(captured.Authorization, "Bearer header.payload.signature");
});

test("sports catalog maps authorized_stream_url to normalized stream_url", async () => {
  let requestedUrl;
  const client = new SupabaseCatalog({
    url: "https://project.supabase.co",
    anonKey: "sb_publishable_test",
    fetchImpl: async (url) => {
      requestedUrl = String(url);
      return new Response(JSON.stringify([{ id: "event-1", stream_url: "https://licensed.example/live" }]), { status: 200 });
    }
  });
  const rows = await client.list({ category: "deportes", country: "AR" });
  const parsed = new URL(requestedUrl);
  assert.equal(parsed.pathname, "/rest/v1/sports_events");
  assert.match(parsed.searchParams.get("select"), /stream_url:authorized_stream_url/);
  assert.equal(rows[0].stream_url, "https://licensed.example/live");
});

test("sports resolver lookup uses the same schema alias", async () => {
  let requestedUrl;
  const client = new SupabaseCatalog({
    url: "https://project.supabase.co",
    anonKey: "sb_publishable_test",
    fetchImpl: async (url) => {
      requestedUrl = String(url);
      return new Response(JSON.stringify([{ id: "event-1", stream_url: "https://licensed.example/live" }]), { status: 200 });
    }
  });
  const row = await client.find({ category: "deportes", country: "AR", id: "event-1" });
  const parsed = new URL(requestedUrl);
  assert.equal(parsed.pathname, "/rest/v1/sports_events");
  assert.match(parsed.searchParams.get("select"), /stream_url:authorized_stream_url/);
  assert.equal(row.stream_url, "https://licensed.example/live");
});
