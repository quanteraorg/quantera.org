-- Quantera account sync (watchlist, paper trades, alerts, settings). Run once in Supabase: SQL Editor -> New query -> paste -> Run.
-- Matches the live project. Safe to run again. Each signed-in person can read, add and update only their own row; anonymous visitors
-- get nothing. Rows are deleted by the server (service key) when an account is deleted, and by the foreign key cascade.
-- (select auth.uid()) is evaluated once per query instead of once per row (Supabase performance advisor).
create table if not exists public.user_data (
  user_id    uuid primary key references auth.users (id) on delete cascade,
  data       jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);
alter table public.user_data enable row level security;
drop policy if exists user_data_select on public.user_data;
drop policy if exists user_data_insert on public.user_data;
drop policy if exists user_data_update on public.user_data;
create policy user_data_select on public.user_data for select to authenticated using ((select auth.uid()) = user_id);
create policy user_data_insert on public.user_data for insert to authenticated with check ((select auth.uid()) = user_id);
create policy user_data_update on public.user_data for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

-- Check: every Quantera table should show rowsecurity = true.
select tablename, rowsecurity from pg_tables where schemaname = 'public' and tablename in ('user_data', 'leaderboard', 'push_alerts', 'waitlist', 'events') order by tablename;
