-- Quantera usage events (anonymous counters such as "signup_opened"; no personal data). Run once in Supabase:
-- SQL Editor -> New query -> paste -> Run. Server-only: row level security is on with no policies, so only the API
-- server (service key) can read or write it. Safe to run again. Matches the live table as of 27 Nov 2026.
create table if not exists public.events (
  id         bigint generated always as identity primary key,
  name       text not null,
  created_at timestamptz not null default now()
);
alter table public.events enable row level security;
