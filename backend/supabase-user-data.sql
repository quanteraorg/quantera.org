-- Quantera account sync (watchlist, paper trades, alerts, settings). Run once in Supabase: SQL Editor -> New query -> paste -> Run.
-- Safe to run again. Each signed-in person can read and write only their own row; nobody else (and no anonymous visitor) can see it.
create table if not exists public.user_data (
  user_id    uuid primary key references auth.users (id) on delete cascade,
  data       jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);
alter table public.user_data enable row level security;
drop policy if exists "users read their own data" on public.user_data;
drop policy if exists "users add their own data" on public.user_data;
drop policy if exists "users update their own data" on public.user_data;
drop policy if exists "users delete their own data" on public.user_data;
create policy "users read their own data" on public.user_data for select to authenticated using (auth.uid() = user_id);
create policy "users add their own data" on public.user_data for insert to authenticated with check (auth.uid() = user_id);
create policy "users update their own data" on public.user_data for update to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "users delete their own data" on public.user_data for delete to authenticated using (auth.uid() = user_id);

-- Check: every Quantera table should show rowsecurity = true.
select tablename, rowsecurity from pg_tables where schemaname = 'public' and tablename in ('user_data', 'leaderboard', 'push_alerts', 'waitlist', 'events') order by tablename;
