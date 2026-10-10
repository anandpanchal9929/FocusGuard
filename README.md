# FocusGuard

Native Android screen-time controls and focus sessions. Kotlin, Jetpack Compose Material 3, Room, DataStore, Coroutines, WorkManager and optional Supabase. Original shield branding. Android 8+ (API 26).

## Current delivery status

This is an implementation under verification, not a completed production release. See `STATUS.md` for executed checks and limitations. Do not rely on experimental screen detectors or DNS filtering until device testing is complete.

## Build

Install Java 17 and Android SDK platform/build-tools 35. The included Gradle wrapper pins Gradle 8.11.1. On macOS/Linux make gradlew executable, then run:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The installable development APK is at `app/build/outputs/apk/debug/app-debug.apk`. It is debug-signed and is separate from the production signing identity. GitHub Actions uploads this APK after successful verification. Instrumentation and Supabase pgTAP tests are separate CI jobs. CI execution is not implied by the presence of workflow files.

Pinned build stack: AGP 8.9.2 / Gradle 8.11.1 / Kotlin 2.1.20 / KSP 2.1.20-1.0.32 / Java 17 / API 35. This conservative mutually compatible stack is intentionally pinned rather than using moving dependency versions. Official compatibility: https://developer.android.com/build/releases/agp-8-9-0-release-notes.

## Install and activate

1. Download a verified APK from Releases, or a successful Actions artifact while development continues.
2. Allow installation from the app used to open the APK in Android settings, then install.
3. Open FocusGuard and read the permission disclosure. Enable usage access for statistics and daily limits.
4. Enable the FocusGuard accessibility service for blocking. Some Android versions require allowing restricted settings from the app-info menu for sideloaded applications.
5. Select apps and an active profile. Configure focus-only, always, daily-limit and/or weekday schedule rules. Start a focus session and verify a selected app is blocked.
6. Screen-level Reels/Shorts detection and system-DNS filtering are separately opt-in. Test both on the actual device.

Accessibility can be revoked, the app can be uninstalled, and emergency/system settings remain accessible. Strict mode only restricts controls inside FocusGuard. OEM power controls may stop services. Force-stop prevents background work until the app is reopened. A persisted timer restores when the app reopens, but no app can continue executing while force-stopped.

## Cloud setup (optional)

This deployment uses the restored project `cuveoqwrmofuvkynsbxj` with public client configuration in `cloud.properties`. Both FocusGuard migrations are deployed. `focusguard_profiles` avoids the other application's existing `profiles` table. FocusGuard cloud-data deletion preserves the shared sign-in account. All 17 hosted SQL tests passed; actual email sign-in and multi-device rooms still need integration testing. For a new installation, apply every migration in the migrations directory.

Create a Supabase Free project, enable email authentication and apply `supabase/migrations/202610100001_initial.sql`. For reproducible local development install the official Supabase CLI and Docker, then run:

```sh
supabase start
supabase db reset
supabase test db
```

For a hosted project, `supabase link --project-ref YOUR_REF` then `supabase db push`. Configure only the public project URL and public publishable/anon key:

```sh
export SUPABASE_URL=https://YOUR_REF.supabase.co
export SUPABASE_ANON_KEY=YOUR_PUBLIC_KEY
./gradlew assembleDebug
```

On Windows set those environment variables in PowerShell with `$env:SUPABASE_URL` and `$env:SUPABASE_ANON_KEY`. Never embed a service-role key. Tokens are encrypted using an Android Keystore AES-GCM key. Signing out or switching accounts clears pending operations to prevent cross-account uploads. Raw usage stays local.

Focus history is append-only on upload, with stable IDs, durable transactional outbox storage, conflict-ignore duplicate submissions and retry after network failure. Sync is opt-in for **new** sessions, every approximately 15 minutes on connected devices. Cloud restore merges session IDs. The UI must be signed in before enabling sync. Optional settings/app-rule sync has a separate consent switch and a durable queue. Conflicts resolve to the newest client edit timestamp; server rejects clocks more than five minutes ahead. Sync is deferred during strict sessions. Keep device clocks accurate.

Rooms support creation, long invite codes, joining, owner starts, participant status, Realtime updates with periodic recovery polling and a self-reported focus leaderboard computed by a server trigger. Individual timers run offline. Participant status can be stale offline. Leaderboard values are self-reported, not tamper-proof. Friend requests can be sent and accepted using account UUIDs.

## Signing and free GitHub release

The owner selected local signing. Keep the private release keystore outside the repository and maintain an encrypted offline backup. Do not upload the key or password to GitHub. CI builds unsigned release inputs; the signed APK is uploaded separately after local signature verification.

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Run unit tests, lint, emulator tests and device blocking checks.
3. Commit/push to main. Run the Android verification workflow manually to publish a clearly labeled build-input ZIP, or download the unsigned release APK from its Actions artifact.
4. Sign using `scripts/sign-local.ps1` and the private local keystore. Verify the signature and calculate SHA256. Create a release tagged at the tested commit and upload `FocusGuard-v0.1.0.apk` plus `SHA256SUMS.txt`. The tag workflow verifies source and builds unsigned artifacts; it does not receive the private signing key.
5. Install updates with the same application ID and signing key. Android rejects an update signed with a different key.

Public standard GitHub Actions runners and public Releases require no paid hosting. Signed APK publication is pending local retrieval/signing. A download website is optional and has not been deployed.

## Architecture and limitations

Packages separate `core`, `data`, `usage`, `blocking`, `focus`, `web`, `network` and `sync`. App-scoped dependency construction supplies repositories to the Android ViewModel, Room DAO and services. UI state is exposed using StateFlow. WorkManager is used for synchronization, never realtime app blocking.

See `PRIVACY.md`, `STATUS.md`, and `docs/DEVICE_TESTS.md`. There is no fabricated usage data, simulated online session, or fake release URL.

