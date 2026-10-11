# Verification and feature status

Repository created: https://github.com/anandpanchal9929/FocusGuard

## Implemented and compiled; device verification remains

- Native Compose onboarding, permissions, dashboard, analytics, installed-app search, rule editor, focus, strict mode, history, room/profile and privacy screens.
- Room-backed app rules, profile assignment, weekday schedules (including overnight), daily limits and temporary allowances.
- User-activated accessibility enforcement and local overlay. System settings/dialer/home protections and emergency escape.
- Timestamp-based countdown, stopwatch, pause/resume, short/long breaks, foreground timer notifications, history, daily goals and streaks.
- Optional Reels/Shorts view-ID heuristics, independently configured and explicitly experimental.
- Optional local DNS VPN with domain/subdomain matching and schedules. Limited to system UDP DNS; bypass limitations disclosed.
- Email auth, Android Keystore token storage, opted-in new-session sync, durable transactional outbox, duplicate-safe upload and cloud restore.
- Supabase migrations for all requested tables, RLS, invite-gated membership, room owner starts, Realtime subscription/poll recovery and self-reported leaderboards.
- Dark/light theme, permission recovery links, local JSON export, local-history deletion, FocusGuard cloud-data deletion and privacy disclosures.
- Unit, Room instrumentation and SQL account-isolation tests. CI build, emulator and database workflows; local release signing and public APK distribution.

## Partial or not yet implemented

- Settings/app-rule cloud sync is implemented with explicit opt-in and timestamp conflict handling, but requires hosted integration testing.
- Friend requests and acceptance use account UUIDs; email discovery and richer profiles are not implemented.
- VPN DNS-over-TCP and encrypted-DNS handling; encrypted page filtering is not offered.
- Detectors verified against current Instagram/YouTube interfaces; no device is available yet.
- Fine-grained automatic Pomodoro cycles; users start the next focus/break manually.
- App rules use one assignment per app rather than independently duplicating an app across profiles.
- Screen time is reconstructed from retained usage events, with midnight clipping. Missing old events can undercount, and multiwindow app totals can overlap.
- Premium visual/layout/accessibility QA on an actual device; the UI is functional source but not yet visually verified.

## Executed checks

GitHub Actions run [38069047325](https://github.com/anandpanchal9929/FocusGuard/actions/runs/38069047325), at commit `81b6732875950fc90df0ce92248724bced072402`, passed all four jobs:

- `testDebugUnitTest` (20 tests), `lintDebug`, `assembleDebug`, and `assembleRelease` succeeded. Tests include the strict-mode regression that overrides a previous temporary allowance. Debug and unsigned release APKs were uploaded as the `FocusGuard-debug` artifact (the artifact contains both variants).
- `connectedDebugAndroidTest` succeeded on the API 35 emulator. This verifies the Room instrumentation test; real-device accessibility blocking, DNS behavior, and third-party screen detectors still require the device checklist.
- `supabase test db` succeeded against a fresh local Supabase stack, including all 17 pgTAP account-isolation, settings-conflict, permission and deletion assertions.
- `local_signing_input` published the unsigned build-input ZIP. Its SHA256 matched GitHub's published digest before extraction and local signing.

Java 17, Gradle 8.11.1, and Android command-line tools are installed within the workspace. Gradle's wrapper-generation task ran successfully. Local SDK package installation encountered network errors; the successful Android builds ran on GitHub's runner.

The user selected the existing Supabase project `cuveoqwrmofuvkynsbxj` and requested local signing. The project was restored and the FocusGuard schema deployed. Its existing `profiles` table belongs to another application, so FocusGuard uses `focusguard_profiles`; cloud deletion removes only FocusGuard data and preserves shared authentication. All 17 hosted SQL isolation/conflict/permission/deletion assertions passed inside a rolled-back test transaction. Live email sign-in and multi-device room behavior still need integration/device testing. The public project URL and publishable key are in `cloud.properties`; no private backend key is included.

A private release keystore was generated outside source control, with its password protected by Windows DPAPI. The owner chose to keep signing local. No signing key or password was sent to GitHub. The final cloud-configured APK was signed locally and cryptographically verified with APK v2/v3 signatures. [v0.1.0](https://github.com/anandpanchal9929/FocusGuard/releases/tag/v0.1.0) is a published preview release at the tested commit; its [APK download](https://github.com/anandpanchal9929/FocusGuard/releases/download/v0.1.0/FocusGuard-v0.1.0.apk) returned HTTP 200. APK SHA256: `cea41fe303e9db473792e938961d1effd8d71e74c393f464c99feeb70d8855bd`.

The public auth settings endpoint returned HTTP 200 with email sign-up enabled and confirmation required. Anonymous session-table reads and settings-RPC calls returned HTTP 401 permission denied. These probes do not replace authenticated Android integration tests. No download website has been deployed. Follow `docs/DEVICE_TESTS.md` on an actual Android device before treating this preview as production-ready.

