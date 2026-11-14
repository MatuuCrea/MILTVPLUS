import test from "node:test";
import assert from "node:assert/strict";
import { hasActiveRights, isRecentCheck, isSafeHttpsUrl } from "../src/rights.js";

const active = {
  rights_verified: true,
  rights_holder: "Titular autorizado",
  rights_reference: "LIC-2026-001",
  rights_regions: ["AR", "UY"],
  rights_valid_from: "2026-01-01T00:00:00Z",
  rights_valid_until: "2027-01-01T00:00:00Z"
};

test("active rights require holder, reference, territory and active dates", () => {
  assert.equal(hasActiveRights(active, "AR", new Date("2026-11-14T12:00:00Z")), true);
});

test("rights fail closed for unverified, missing, future or expired grants", () => {
  assert.equal(hasActiveRights({ ...active, rights_verified: false }, "AR"), false);
  assert.equal(hasActiveRights({ ...active, rights_reference: "" }, "AR"), false);
  assert.equal(hasActiveRights({ ...active, rights_regions: [] }, "AR"), false);
  assert.equal(hasActiveRights({ ...active, rights_valid_from: "2027-01-01T00:00:00Z" }, "AR", new Date("2026-11-14T12:00:00Z")), false);
  assert.equal(hasActiveRights({ ...active, rights_valid_until: "2026-01-01T00:00:00Z" }, "AR", new Date("2026-11-14T12:00:00Z")), false);
  assert.equal(hasActiveRights({ ...active, rights_valid_until: "not-a-date" }, "AR"), false);
});

test("rights are region scoped and require an explicit region", () => {
  assert.equal(hasActiveRights(active, "BR"), false);
  assert.equal(hasActiveRights(active, ""), false);
  assert.equal(hasActiveRights({ ...active, rights_regions: ["001"] }, "BR"), true);
  assert.equal(hasActiveRights({ ...active, rights_regions: ["*"] }, "BR"), true);
});

test("only credential-free HTTPS URLs are accepted", () => {
  assert.equal(isSafeHttpsUrl("https://cdn.example.org/live/master.m3u8"), true);
  assert.equal(isSafeHttpsUrl("http://cdn.example.org/live.m3u8"), false);
  assert.equal(isSafeHttpsUrl("https://user:pass@example.org/video"), false);
  assert.equal(isSafeHttpsUrl("javascript:alert(1)"), false);
  assert.equal(isSafeHttpsUrl("not a url"), false);
});

test("TV health checks must be recent and not from the future", () => {
  const now = new Date("2026-11-14T12:00:00Z");
  assert.equal(isRecentCheck("2026-11-14T11:59:00Z", now), true);
  assert.equal(isRecentCheck("2026-11-14T11:00:00Z", now), false);
  assert.equal(isRecentCheck("2026-11-14T12:05:00Z", now), false);
  assert.equal(isRecentCheck(null, now), false);
});
