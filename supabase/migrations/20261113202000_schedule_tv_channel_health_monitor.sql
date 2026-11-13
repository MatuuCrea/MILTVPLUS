-- Store only non-secret project routing data and the public publishable key in Vault.
-- The Edge Function checks this key itself; its server-side secret key never leaves Edge Functions.
do $$
begin
  if not exists (select 1 from vault.decrypted_secrets where name = 'miltvplus_project_url') then
    perform vault.create_secret('https://rbnuxapfsnaabrzuxvdy.supabase.co', 'miltvplus_project_url');
  end if;
  if not exists (select 1 from vault.decrypted_secrets where name = 'miltvplus_publishable_key') then
    perform vault.create_secret('sb_publishable_tmBU-IB7P0db0w7RG9TMEw_GD754hb7', 'miltvplus_publishable_key');
  end if;
  if not exists (select 1 from cron.job where jobname = 'miltvplus-tv-channel-health') then
    perform cron.schedule(
      'miltvplus-tv-channel-health',
      '*/5 * * * *',
      $command$
        select net.http_post(
          url := (select decrypted_secret from vault.decrypted_secrets where name = 'miltvplus_project_url') || '/functions/v1/tv-channel-health',
          headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'apikey', (select decrypted_secret from vault.decrypted_secrets where name = 'miltvplus_publishable_key')
          ),
          body := jsonb_build_object('trigger', 'scheduled', 'scheduled_at', now()),
          timeout_milliseconds := 120000
        );
      $command$
    );
  end if;
end;
$$;
