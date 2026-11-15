-- Quantera reaction tracker: how each company in a multi-company news story reacted, measured by the backend
-- (Reaction.java). Written and read only by the server with the service key; no public access (RLS on, no policies).
create table if not exists public.reaction_events (
  id            bigserial primary key,
  event_key     text not null,
  headline      text not null,
  published_at  timestamptz,
  day0          date not null,
  symbol        text not null,
  market_cap    double precision,
  move_pct      double precision not null,
  market_pct    double precision,
  abnormal_pct  double precision not null,
  typical_day   double precision,
  sigmas        double precision,
  beta          double precision,
  volume_ratio  double precision,
  named         boolean not null default false,
  created_at    timestamptz not null default now(),
  unique (event_key, symbol)
);
alter table public.reaction_events enable row level security;
create index if not exists reaction_events_day0 on public.reaction_events (day0 desc);
