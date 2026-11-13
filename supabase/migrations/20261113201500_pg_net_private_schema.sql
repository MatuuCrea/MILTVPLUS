-- Keep the pg_net extension itself outside the exposed public schema.
-- The extension's HTTP API remains in its dedicated net schema.
drop extension if exists pg_net;
create extension pg_net with schema extensions;
