alter table public.music_items
  add column if not exists stream_url text
    check (stream_url is null or stream_url ~ '^https://');

alter table public.music_items
  drop constraint if exists music_items_stream_rights_check;

alter table public.music_items
  add constraint music_items_stream_rights_check
  check (
    stream_url is null
    or (
      rights_verified = true
      and nullif(btrim(rights_holder), '') is not null
      and nullif(btrim(rights_reference), '') is not null
      and cardinality(rights_regions) > 0
    )
  );

create index if not exists music_items_published_stream_idx
  on public.music_items (created_at desc)
  where is_published = true and stream_url is not null;

comment on column public.music_items.stream_url is
  'Direct HTTPS audio stream. Only publish when redistribution rights are verified, documented, region-scoped, and current.';
