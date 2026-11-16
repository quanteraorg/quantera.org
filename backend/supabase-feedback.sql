-- Quantera feedback form: messages people send from the site (bug, wrong data, idea, other).
-- Written only by the server with the service key (Store.feedback); no public access (RLS on, no policies).
-- Email is optional and only kept if the person chose to give it for a reply.
create table if not exists public.feedback (
  id          bigserial primary key,
  kind        text not null check (kind in ('bug', 'data', 'idea', 'other')),
  message     text not null check (char_length(message) between 5 and 2000),
  page        text not null default '' check (char_length(page) <= 200),
  email       text check (email is null or char_length(email) <= 260),
  created_at  timestamptz not null default now()
);
alter table public.feedback enable row level security;
create index if not exists feedback_created on public.feedback (created_at desc);
