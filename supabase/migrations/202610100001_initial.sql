create extension if not exists pgcrypto;
create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default 'Student' check (length(display_name) <= 80),
  updated_at timestamptz not null default now()
);
create table public.user_preferences (
  user_id uuid primary key references auth.users(id) on delete cascade,
  settings jsonb not null default '{}', updated_at timestamptz not null default now()
);
create table public.focus_sessions (
  id uuid primary key, user_id uuid not null references auth.users(id) on delete cascade,
  started_at timestamptz not null, ended_at timestamptz not null,
  focused_ms bigint not null check (focused_ms >= 0), completed boolean not null,
  created_at timestamptz not null default now(), check (ended_at >= started_at)
);
create index on public.focus_sessions(user_id, started_at desc);
create table public.blocked_app_rules (
  id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id) on delete cascade,
  package_name text not null, rule jsonb not null, updated_at timestamptz not null default now(),
  unique (user_id, package_name)
);
create table public.usage_goals (
  user_id uuid primary key references auth.users(id) on delete cascade,
  daily_minutes integer not null check(daily_minutes between 1 and 1440), updated_at timestamptz not null default now()
);
create table public.sync_operations (
  id uuid primary key, user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);
create table public.friendships (
  user_id uuid not null references auth.users(id) on delete cascade,
  friend_id uuid not null references auth.users(id) on delete cascade,
  accepted boolean not null default false, created_at timestamptz not null default now(),
  primary key(user_id,friend_id), check(user_id<>friend_id)
);
create table public.focus_rooms (
  id uuid primary key default gen_random_uuid(), owner_id uuid not null references auth.users(id) on delete cascade,
  name text not null check(length(name) between 1 and 80),
  invite_code text not null unique default encode(gen_random_bytes(12),'hex'),
  started_at timestamptz, duration_minutes integer not null default 25 check(duration_minutes between 1 and 240),
  created_at timestamptz not null default now()
);
create table public.room_members (
  room_id uuid not null references public.focus_rooms(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'ready' check(status in ('ready','focusing','paused','finished')),
  updated_at timestamptz not null default now(), primary key(room_id,user_id)
);
create index on public.room_members(user_id,room_id);
create table public.leaderboard_records (
  room_id uuid not null references public.focus_rooms(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  focused_ms bigint not null default 0 check(focused_ms>=0), updated_at timestamptz not null default now(),
  primary key(room_id,user_id)
);
-- Membership helpers use a pinned search path and avoid recursive RLS policies.
create function public.is_room_member(room uuid) returns boolean language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.room_members m where m.room_id=room and m.user_id=(select auth.uid()))
    or exists(select 1 from public.focus_rooms r where r.id=room and r.owner_id=(select auth.uid()));
$$;
revoke all on function public.is_room_member(uuid) from public;
grant execute on function public.is_room_member(uuid) to authenticated;
do $$ declare tab text; owner_column text; begin
  foreach tab in array array['profiles','user_preferences','focus_sessions','blocked_app_rules','usage_goals','sync_operations'] loop
    owner_column := case when tab='profiles' then 'id' else 'user_id' end;
    execute format('alter table public.%I enable row level security',tab);
    execute format('create policy own_data on public.%I for all to authenticated using (%I=(select auth.uid())) with check (%I=(select auth.uid()))',tab,owner_column,owner_column);
  end loop;
end $$;
alter table public.friendships enable row level security;
create policy friends_read on public.friendships for select to authenticated using(auth.uid() in (user_id,friend_id));
create policy friends_request on public.friendships for insert to authenticated with check(auth.uid()=user_id and not accepted);
create policy friends_accept on public.friendships for update to authenticated using(auth.uid()=friend_id) with check(auth.uid()=friend_id);
create policy friends_delete on public.friendships for delete to authenticated using(auth.uid() in (user_id,friend_id));
alter table public.focus_rooms enable row level security;
create policy rooms_read on public.focus_rooms for select to authenticated using(public.is_room_member(id));
create policy rooms_insert on public.focus_rooms for insert to authenticated with check(owner_id=auth.uid());
create policy rooms_update on public.focus_rooms for update to authenticated using(owner_id=auth.uid()) with check(owner_id=auth.uid());
create policy rooms_delete on public.focus_rooms for delete to authenticated using(owner_id=auth.uid());
alter table public.room_members enable row level security;
create policy members_read on public.room_members for select to authenticated using(public.is_room_member(room_id));
create policy members_update on public.room_members for update to authenticated using(user_id=auth.uid()) with check(user_id=auth.uid() and public.is_room_member(room_id));
create policy members_leave on public.room_members for delete to authenticated using(user_id=auth.uid());
alter table public.leaderboard_records enable row level security;
create policy leaderboard_read on public.leaderboard_records for select to authenticated using(public.is_room_member(room_id));
-- No client writes to leaderboard: trusted server computation only.
grant select,insert,update,delete on public.profiles,public.user_preferences,public.focus_sessions,public.blocked_app_rules,public.usage_goals,public.sync_operations,public.friendships,public.focus_rooms,public.room_members to authenticated;
grant select on public.leaderboard_records to authenticated;
revoke update on public.friendships from authenticated;
grant update(accepted) on public.friendships to authenticated;
revoke all on all tables in schema public from anon;
create function public.join_room(invite text) returns uuid language plpgsql security definer set search_path = '' as $$
declare room uuid; begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  select id into room from public.focus_rooms where invite_code=invite;
  if room is null then raise exception 'Invalid invite'; end if;
  insert into public.room_members(room_id,user_id) values(room,auth.uid()) on conflict do nothing;
  return room;
end $$;
revoke all on function public.join_room(text) from public;
grant execute on function public.join_room(text) to authenticated;
create function public.delete_my_account() returns void language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  delete from auth.users where id=auth.uid();
end $$;
revoke all on function public.delete_my_account() from public;
grant execute on function public.delete_my_account() to authenticated;
alter publication supabase_realtime add table public.room_members, public.focus_rooms;
create function public.add_room_owner() returns trigger language plpgsql security definer set search_path = '' as $$
begin insert into public.room_members(room_id,user_id) values(new.id,new.owner_id); return new; end $$;
create trigger room_owner after insert on public.focus_rooms for each row execute function public.add_room_owner();
create function public.start_room(room uuid, minutes integer) returns void language plpgsql security definer set search_path = '' as $$
begin
  if minutes not between 1 and 240 then raise exception 'Invalid duration'; end if;
  update public.focus_rooms set started_at=now(), duration_minutes=minutes where id=room and owner_id=auth.uid();
  if not found then raise exception 'Only the room owner can start this room'; end if;
end $$;
revoke all on function public.start_room(uuid,integer) from public;
grant execute on function public.start_room(uuid,integer) to authenticated;
create function public.refresh_leaderboard() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.leaderboard_records(room_id,user_id,focused_ms)
    select m.room_id,new.user_id,new.focused_ms from public.room_members m join public.focus_rooms r on r.id=m.room_id
    where m.user_id=new.user_id and new.started_at>=r.started_at and new.started_at<r.started_at+make_interval(mins=>r.duration_minutes)
  on conflict(room_id,user_id) do update set focused_ms=public.leaderboard_records.focused_ms+excluded.focused_ms,updated_at=now();
  return new;
end $$;
create trigger session_leaderboard after insert on public.focus_sessions for each row execute function public.refresh_leaderboard();
