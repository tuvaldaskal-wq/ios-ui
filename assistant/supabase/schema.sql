-- Aria — Supabase schema, row-level security, and triggers.
-- Run this in the Supabase SQL editor (Dashboard → SQL → New query).

-- ---------------------------------------------------------------------------
-- Profiles: one row per auth user. `is_admin` gates the admin panel.
-- ---------------------------------------------------------------------------
create table if not exists public.profiles (
    id          uuid primary key references auth.users(id) on delete cascade,
    email       text,
    plan        text not null default 'free',     -- 'free' | 'pro' | ...
    is_admin    boolean not null default false,
    created_at  timestamptz not null default now()
);

-- Auto-create a profile when a user signs up.
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
    insert into public.profiles (id, email) values (new.id, new.email)
    on conflict (id) do nothing;
    return new;
end; $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- Helper: is the current user an admin?
create or replace function public.is_admin()
returns boolean language sql stable security definer set search_path = public as $$
    select coalesce((select is_admin from public.profiles where id = auth.uid()), false);
$$;

-- ---------------------------------------------------------------------------
-- Messages: every chat turn (user + assistant). Visible to its owner; admins
-- can read all. Inserts happen via the Edge Function (service role).
-- ---------------------------------------------------------------------------
create table if not exists public.messages (
    id          bigint generated always as identity primary key,
    user_id     uuid not null references auth.users(id) on delete cascade,
    role        text not null,                    -- 'user' | 'assistant'
    content     text not null,
    created_at  timestamptz not null default now()
);
create index if not exists messages_user_idx on public.messages(user_id, created_at);

-- ---------------------------------------------------------------------------
-- Usage: token counts + computed cost per request (your API cost).
-- ---------------------------------------------------------------------------
create table if not exists public.usage (
    id            bigint generated always as identity primary key,
    user_id       uuid not null references auth.users(id) on delete cascade,
    model         text,
    input_tokens  int not null default 0,
    output_tokens int not null default 0,
    cost_usd      numeric(10,6) not null default 0,
    created_at    timestamptz not null default now()
);
create index if not exists usage_user_idx on public.usage(user_id, created_at);

-- ---------------------------------------------------------------------------
-- Payments: money the user has paid you (subscription revenue).
-- Insert a row from your billing webhook (Stripe / local gateway).
-- ---------------------------------------------------------------------------
create table if not exists public.payments (
    id          bigint generated always as identity primary key,
    user_id     uuid not null references auth.users(id) on delete cascade,
    amount_usd  numeric(10,2) not null,
    provider    text,
    created_at  timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- Row-level security: users see only their own rows; admins see everything.
-- ---------------------------------------------------------------------------
alter table public.profiles enable row level security;
alter table public.messages enable row level security;
alter table public.usage    enable row level security;
alter table public.payments enable row level security;

create policy profiles_self_or_admin on public.profiles for select
    using (id = auth.uid() or public.is_admin());
create policy profiles_self_update on public.profiles for update
    using (id = auth.uid());

create policy messages_self_or_admin on public.messages for select
    using (user_id = auth.uid() or public.is_admin());
create policy usage_self_or_admin on public.usage for select
    using (user_id = auth.uid() or public.is_admin());
create policy payments_self_or_admin on public.payments for select
    using (user_id = auth.uid() or public.is_admin());

-- ---------------------------------------------------------------------------
-- Make yourself an admin (run once, with your email):
--   update public.profiles set is_admin = true where email = 'you@example.com';
-- ---------------------------------------------------------------------------
