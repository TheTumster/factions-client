-- Factions Client backend. Paste this whole file into Supabase > SQL Editor > Run.
-- Use a NEW free Supabase project (don't put this in the BGB production database).
--
-- Security model: there are no logins. Every request sends an "x-team-key" header.
-- Row Level Security only lets a request see/write rows whose `team` column equals that key.
-- So the team key is your group's password. Keep it private.

create or replace function public.request_team_key() returns text
language sql stable as $$
  select coalesce(current_setting('request.headers', true)::json ->> 'x-team-key', '')
$$;

-- Live positions (one row per player)
create table if not exists public.team_positions (
  team text not null,
  name text not null,
  server text not null,
  dim text not null,
  x double precision not null,
  y double precision not null,
  z double precision not null,
  updated_at timestamptz not null default now(),
  primary key (team, name)
);

-- Patch crumbs: where enemy TNT went off
create table if not exists public.crumbs (
  id bigserial primary key,
  team text not null,
  server text not null,
  dim text not null,
  reporter text not null,
  x int not null,
  y int not null,
  z int not null,
  created_at timestamptz not null default now()
);
create index if not exists crumbs_lookup on public.crumbs (team, server, dim, created_at);

-- Shared schematics (base64 file contents, max ~4 MB each)
create table if not exists public.schems (
  code text primary key,
  team text not null,
  name text not null,
  uploader text not null,
  data text not null,
  created_at timestamptz not null default now()
);

alter table public.team_positions enable row level security;
alter table public.crumbs enable row level security;
alter table public.schems enable row level security;

drop policy if exists team_rw on public.team_positions;
create policy team_rw on public.team_positions for all to anon
  using (team <> '' and team = public.request_team_key())
  with check (team <> '' and team = public.request_team_key());

drop policy if exists team_rw on public.crumbs;
create policy team_rw on public.crumbs for all to anon
  using (team <> '' and team = public.request_team_key())
  with check (team <> '' and team = public.request_team_key());

drop policy if exists team_rw on public.schems;
create policy team_rw on public.schems for all to anon
  using (team <> '' and team = public.request_team_key())
  with check (team <> '' and team = public.request_team_key());

-- Housekeeping: delete old crumbs every 10 minutes so the table stays small.
-- (Needs the pg_cron extension: Database > Extensions > enable "pg_cron". Skip if you don't care.)
-- select cron.schedule('fc-cleanup', '*/10 * * * *',
--   $$ delete from public.crumbs where created_at < now() - interval '1 hour' $$);
