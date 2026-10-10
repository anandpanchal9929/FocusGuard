# Device acceptance checklist

Use a disposable test device/account and record OS, device model, app versions and results. Do not mark these checks passed until actually performed.

1. Deny usage permission: dashboard shows unavailable, daily limits do not invent usage. Grant and revoke permission while running; refresh.
2. Activate accessibility after reading disclosure. Verify whole-app blocking, focus-only rules, paused focus and breaks, profile changes and emergency/dialer/settings access.
3. Start focus, background app, kill its process, reopen: remaining time recovers; completed sessions save exactly once. Force-stop and reboot: document service recovery and vendor limits.
4. Schedule 22:00–06:00 on Monday; verify Tuesday 05:59 applies Monday and Tuesday 06:00 ends. Change timezone and test DST transitions.
5. Reach a daily app limit; keep the app open and confirm periodic re-evaluation. Cross midnight; confirm allowance and daily reset behavior.
6. Strict session: editing/pausing blocked, 30-second normal end cooldown, emergency end available. Revoking accessibility and uninstalling remain possible.
7. Test current YouTube/Instagram ordinary screens versus Shorts/Reels. Record false positives/negatives. Disable detectors if interfaces changed.
8. DNS consent, notification, blocked subdomain, allowed domain, scheduled DNS rule, other-VPN conflict, offline NXDOMAIN response, upstream failure, stop/revoke. Verify encrypted DNS bypass disclosure.
9. New-session sync opt-in; disconnect and reconnect, retry after 500/401 responses, duplicate upload, sign out and change accounts. Ensure queue never uploads old data under a different user.
10. Two cloud accounts: cannot read each other's history/preferences; joining requires the correct invite; owner alone starts room; users cannot forge other members/leaderboard rows.
11. Room websocket interruption: personal timer continues; state reconnects and is marked stale offline. New app launch: restore cloud history by ID.
12. Export through document picker; delete local history separately from cloud account. Confirm cascading cloud deletion.
13. TalkBack, large fonts, small screens, light/dark contrast, rotation, notification denial and background battery policy.
