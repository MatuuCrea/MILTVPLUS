# Supabase connection — MILTVPLUS

## Verified hosted project
- Project ref: `rbnuxapfsnaabrzuxvdy`
- Project URL: `https://rbnuxapfsnaabrzuxvdy.supabase.co`
- Region: `us-east-1`
- PostgreSQL: 17
- State at verification: `ACTIVE_HEALTHY`

## Database applied
Migration `20261113135529_miltvplus_initial_catalog` was applied to the hosted database and verified through Supabase management tools. It creates:
- `public.catalog_items`: live/movie/series catalogue; public read is restricted to published rows.
- `public.profiles`: each authenticated user can read/insert/update only their own row.
- `public.user_favorites`: each authenticated user can read/insert/delete only their own favorites.

RLS is enabled on all three tables. Security advisor returned no findings after migration. The initial database is empty; no channels, movies or series have been fabricated or imported.

## Keys and credentials
The publishable key is intended for public client applications. It is not a password and must still be paired with RLS. It may be included in an Android app after the Android Supabase client is implemented.
Never place an `sb_secret_*` key, legacy `service_role` key, database password, or full Postgres connection URI in Android, Git history, public source, or APK assets.

The provided Postgres URI contained the literal placeholder `[YOUR-PASSWORD]`, not an actual password. It was not used. Database work was performed using the authenticated Supabase management integration.

## Local CLI notes (Supabase CLI v3)
This repository already contains `supabase/config.toml` and the migration file. Do not run `supabase init` again.
The hosted project has already been linked in this repository config by project ref. Supabase CLI v3 changes database commands to local-by-default; hosted database operations require the explicit remote mode and confirmation. Read the current v3 migration guide before adding CLI commands to CI:
https://supabase.com/docs/guides/local-development/cli/migrating-to-v3

If you use a local CLI session, authenticate interactively with `supabase login`. Do not paste the personal access token into chat or commit it. No CLI access token or database password is needed for the current GitHub Android build workflow.

## Android client still pending
The Android module currently does not initialize a Supabase SDK client. Add the compatible supabase-kt BOM, Ktor Android engine, local.properties/BuildConfig wiring, Auth/PostgREST repository and tests as a separate tested change. The currently committed app targets minSdk 23; current supabase-kt documentation lists Android API 26 as its minimum unless core library desugaring is enabled, so do not blindly add the SDK without resolving this compatibility first.
