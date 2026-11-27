-- Quantera waitlist (newsletter / early-access sign-ups). Run once in Supabase: SQL Editor -> New query -> paste -> Run.
-- Server-only: row level security is on with no policies, so the public (anon) and signed-in keys can't read or write it;
-- the API server uses the service key. Safe to run again: it only creates what is missing.
-- Matches the live table as of 27 Nov 2026.
create table if not exists public.waitlist (
  id         bigint generated always as identity primary key,
  email      text not null unique,
  interest   text,
  source     text,
  created_at timestamptz not null default now(),
  first_name text check (first_name is null or char_length(first_name) <= 40)
);
-- Older projects created the table from the README one-liner without first_name
alter table public.waitlist add column if not exists first_name text;
do $$ begin
  if not exists (select 1 from pg_constraint where conrelid = 'public.waitlist'::regclass and conname = 'waitlist_first_name_check') then
    alter table public.waitlist add constraint waitlist_first_name_check check (first_name is null or char_length(first_name) <= 40);
  end if;
end $$;
alter table public.waitlist enable row level security;
