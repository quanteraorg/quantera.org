-- Row level security regression check. Paste into Supabase: SQL Editor -> New query -> Run.
-- Everything happens inside one transaction that ends in ROLLBACK, so it leaves no data behind.
-- Success: the run finishes with "RLS check passed" (as a NOTICE). Any failure stops with an exception naming the problem.
-- Checks: a signed-in user sees and changes only their own saved data and leaderboard row; the public (anon) key can
-- read the leaderboard but nothing in user_data, waitlist, feedback, events or push_alerts.
begin;

-- Two throwaway users (rolled back at the end)
insert into auth.users (id, instance_id, aud, role, email, created_at, updated_at)
values ('00000000-0000-4000-8000-00000000000a', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'rls-check-a@invalid.test', now(), now()),
       ('00000000-0000-4000-8000-00000000000b', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'rls-check-b@invalid.test', now(), now());
insert into public.user_data (user_id, data) values
  ('00000000-0000-4000-8000-00000000000a', '{"who":"a"}'),
  ('00000000-0000-4000-8000-00000000000b', '{"who":"b"}');
insert into public.leaderboard (user_id, nickname) values ('00000000-0000-4000-8000-00000000000b', 'rls-check-b');

-- Act as user A
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-4000-8000-00000000000a","role":"authenticated"}', true);

do $$
declare n int;
begin
  select count(*) into n from public.user_data;
  if n <> 1 or not exists (select 1 from public.user_data where user_id = '00000000-0000-4000-8000-00000000000a') then
    raise exception 'RLS: user A can see % user_data rows (expected only their own)', n;
  end if;

  update public.user_data set data = '{"who":"hacked"}' where user_id = '00000000-0000-4000-8000-00000000000b';
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'RLS: user A changed user B''s saved data'; end if;

  update public.leaderboard set ret_pct = 999 where user_id = '00000000-0000-4000-8000-00000000000b';
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'RLS: user A changed user B''s leaderboard row'; end if;

  begin
    insert into public.leaderboard (user_id, nickname) values ('00000000-0000-4000-8000-00000000000c', 'imposter');
    raise exception 'RLS: user A inserted a leaderboard row for someone else';
  exception when insufficient_privilege then null; -- expected: blocked by the policy
  end;

  begin
    insert into public.user_data (user_id, data) values ('00000000-0000-4000-8000-00000000000b', '{}');
    raise exception 'RLS: user A inserted saved data for user B';
  exception when insufficient_privilege or unique_violation then null; -- expected
  end;
end $$;

-- Act as the public (anon) key
reset role;
set local role anon;
select set_config('request.jwt.claims', '{"role":"anon"}', true);

do $$
declare n int; t text;
begin
  select count(*) into n from public.leaderboard;
  if n < 1 then raise exception 'RLS: anon can''t read the public leaderboard'; end if;
  foreach t in array array['user_data', 'waitlist', 'feedback', 'events', 'push_alerts'] loop
    if to_regclass('public.' || t) is null then continue; end if; -- table not created on this project
    begin
      execute format('select count(*) from public.%I', t) into n;
      if n > 0 then raise exception 'RLS: anon can read % row(s) of %', n, t; end if;
    exception when insufficient_privilege then null; -- blocked outright: also fine
    end;
  end loop;
  raise notice 'RLS check passed';
end $$;

rollback;
