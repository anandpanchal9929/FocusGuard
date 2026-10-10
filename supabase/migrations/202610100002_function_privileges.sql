-- Explicitly override default grants from an existing Supabase project.
revoke all on function public.is_room_member(uuid) from public, anon;
revoke all on function public.join_room(text) from public, anon;
revoke all on function public.start_room(uuid,integer) from public, anon;
revoke all on function public.apply_settings(uuid,jsonb,jsonb,timestamptz) from public, anon;
revoke all on function public.focusguard_delete_data() from public, anon;
revoke all on function public.add_room_owner() from public, anon, authenticated;
revoke all on function public.refresh_leaderboard() from public, anon, authenticated;
grant execute on function public.is_room_member(uuid),public.join_room(text),public.start_room(uuid,integer),public.apply_settings(uuid,jsonb,jsonb,timestamptz),public.focusguard_delete_data() to authenticated;
