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
