begin;
create extension if not exists pgtap with schema extensions;
select plan(8);
insert into auth.users(id,aud,role,email) values
 ('00000000-0000-0000-0000-000000000001','authenticated','authenticated','a@test.invalid'),
 ('00000000-0000-0000-0000-000000000002','authenticated','authenticated','b@test.invalid');
insert into public.profiles(id) values('00000000-0000-0000-0000-000000000001'),('00000000-0000-0000-0000-000000000002');
insert into public.focus_sessions(id,user_id,started_at,ended_at,focused_ms,completed) values
 ('00000000-0000-0000-0000-000000000011','00000000-0000-0000-0000-000000000001',now(),now(),0,true),
 ('00000000-0000-0000-0000-000000000012','00000000-0000-0000-0000-000000000002',now(),now(),0,true);
insert into public.focus_rooms(id,owner_id,name,invite_code) values
 ('00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000001','A','isolation-invite');
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select is((select count(*)::int from public.profiles),1,'profiles are isolated');
select is((select count(*)::int from public.focus_sessions),1,'sessions are isolated');
select is((select count(*)::int from public.focus_rooms),0,'private rooms are invisible');
select throws_ok($$insert into public.user_preferences(user_id) values('00000000-0000-0000-0000-000000000001')$$,'42501',null,'cannot insert preferences for another account');
select throws_ok($$insert into public.room_members(room_id,user_id) values('00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000002')$$,'42501',null,'cannot bypass invite function');
select lives_ok($$select public.join_room('isolation-invite')$$,'valid invite joins room');
select is((select count(*)::int from public.focus_rooms),1,'joined room is visible');
select throws_ok($$insert into public.leaderboard_records(room_id,user_id,focused_ms) values('00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000002',100)$$,'42501',null,'clients cannot forge leaderboard records');
select * from finish();
rollback;
