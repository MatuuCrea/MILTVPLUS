-- Automated TV source health: allow published rows to retain their publication
-- while their source status changes. Public clients only see published + online rows.
create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net;

grant usage on schema cron to postgres;
grant all privileges on all tables in schema cron to postgres;

alter table public.tv_channels
  drop constraint if exists tv_channels_published_requirements;

alter table public.tv_channels
  add constraint tv_channels_published_requirements check (
    not is_published or (
      official_watch_url is not null
      and (
        (playback_type = 'official_web' and stream_url is null)
        or (
          playback_type in ('hls','dash','youtube_live')
          and stream_url is not null
          and rights_verified
          and rights_holder is not null
          and rights_reference is not null
          and cardinality(rights_regions) > 0
          and rights_valid_from is not null
        )
      )
    )
  );

create or replace function app_private.enforce_tv_channel_publication()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();

  if new.is_published then
    if new.official_watch_url is null then
      raise exception 'Publication requires an official HTTPS viewing page';
    end if;

    if new.playback_type = 'official_web' then
      if new.stream_url is not null then
        raise exception 'Official-web directory entries must not contain an embedded stream URL';
      end if;
    else
      if new.stream_url is null or not new.rights_verified
         or new.rights_holder is null or new.rights_reference is null
         or cardinality(new.rights_regions) = 0 or new.rights_valid_from is null
         or new.rights_valid_from > now()
         or (new.rights_valid_until is not null and new.rights_valid_until <= now()) then
        raise exception 'Embedded streams require active, verified redistribution rights for the requested territories';
      end if;
    end if;

    -- Require a healthy source when first publishing, but permit the health monitor
    -- to set offline/blocked/pending_review later without unpublishing the record.
    if tg_op = 'INSERT' then
      if new.source_status <> 'online' then
        raise exception 'A source must pass its initial health check before publication';
      end if;
    elsif not old.is_published and new.source_status <> 'online' then
      raise exception 'A source must pass its initial health check before publication';
    end if;
  end if;

  return new;
end;
$$;

drop policy if exists "published tv channels readable" on public.tv_channels;
create policy "published tv channels readable" on public.tv_channels
  for select to anon, authenticated
  using (is_published = true and source_status = 'online');

comment on constraint tv_channels_published_requirements on public.tv_channels is
  'Published official pages need an official URL; embedded streams additionally need active documented redistribution rights. Source health is tracked independently.';
