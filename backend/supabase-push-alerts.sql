-- Background price alerts (Web Push). Run once in the Supabase SQL editor.
-- Only the server (service key) reads and writes this table, so RLS is on with no policies.
create table if not exists public.push_alerts (
  id bigint generated always as identity primary key,
  endpoint text not null,
  p256dh text not null,
  auth text not null,
  symbol text not null,
  op text not null check (op in ('>', '<')),
  price double precision not null,
  created_at timestamptz not null default now(),
  fired_at timestamptz,
  fired_price double precision
);
create index if not exists push_alerts_active on public.push_alerts (symbol) where fired_at is null;
create index if not exists push_alerts_endpoint on public.push_alerts (endpoint);
alter table public.push_alerts enable row level security;
