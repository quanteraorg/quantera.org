-- Quantera paper-trading leaderboard. Run once in Supabase: SQL Editor -> New query -> paste -> Run.
-- Anyone can read the table; each signed-in user can only create, change or delete their own row.
create table if not exists public.leaderboard (
  user_id    uuid primary key references auth.users (id) on delete cascade,
  nickname   text not null check (char_length(nickname) between 2 and 24),
  ret_pct    double precision not null default 0,
  week_pct   double precision,
  equity     double precision,
  trades     integer not null default 0,
  updated_at timestamptz not null default now()
);
alter table public.leaderboard enable row level security;
drop policy if exists "leaderboard is public" on public.leaderboard;
drop policy if exists "users add their own row" on public.leaderboard;
drop policy if exists "users update their own row" on public.leaderboard;
drop policy if exists "users remove their own row" on public.leaderboard;
create policy "leaderboard is public" on public.leaderboard for select using (true);
create policy "users add their own row" on public.leaderboard for insert to authenticated with check (auth.uid() = user_id);
create policy "users update their own row" on public.leaderboard for update to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "users remove their own row" on public.leaderboard for delete to authenticated using (auth.uid() = user_id);
create index if not exists leaderboard_ret on public.leaderboard (ret_pct desc);
