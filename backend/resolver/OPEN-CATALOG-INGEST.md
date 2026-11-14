# Rights-gated open catalog ingestion

Run from `backend/resolver` with Node 22:

```sh
SUPABASE_URL=https://<project>.supabase.co \
SUPABASE_SERVICE_ROLE_KEY=<server-only-secret> \
node src/sync-open-catalog.js
```

The script reads `src/approved-catalog.json`, validates direct media URLs, checks HLS manifests or media content types, and upserts accepted rows into the existing catalog tables. It never downloads or republishes media.

## Required human review

Each manifest item must contain `rights_review_status: "approved"`, `rights_verified: true`, a named `rights_holder`, an HTTPS `rights_reference` that documents redistribution/embedding rights, explicit `rights_regions`, and a valid `rights_valid_from` (plus optional `rights_valid_until`). Approval is a curator's legal review, not inferred from a public M3U index, a successful HTTP response, a Creative Commons label without license/scope review, or a public-domain claim without provenance.

Supported item types: `tv` (HLS), `movie`, `series`, `episode` (direct MP4-like video), and `music` (direct audio). HLS requires an HTTPS manifest beginning with `#EXTM3U` and containing an HLS tag. Video/audio URLs must answer HEAD with an appropriate media content type. Redirects are rejected.

Only use a Supabase service-role key in the protected backend environment; never put it in mobile, desktop, Tizen, webOS, source control, or a public build. The manifest starts empty intentionally. No catalog rows are invented or published by default. Review rejected entries from the JSON report before correcting their source or rights metadata.
