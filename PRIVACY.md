# FocusGuard privacy policy

Effective 10 October 2026. This policy describes the current development version; deployed operators must supply their contact details before public production distribution.

Usage access provides package-level usage duration and device usage events. These statistics stay on the device and are not included in cloud uploads. Activity resumes are estimates of launches; unsupported unlock statistics are marked unavailable.

Accessibility checks foreground package names and known resource identifiers for optional short-video detection. It does not read, store or upload screen text, passwords, notifications or screenshots. App blocking uses an accessibility overlay. Android settings, emergency calls and permission controls remain accessible.

Focus history, app rules and preferences are stored in app-private Room/DataStore files. Authentication tokens are encrypted using Android Keystore. Android automatic app backup is disabled. The app does not include advertising, analytics or tracking SDKs.

If you configure Supabase, sign in and explicitly enable synchronization, new focus sessions are uploaded to your account. Supabase processes email authentication and stores the focus-session data. Joining rooms exposes your account UUID and chosen focus state to members of those rooms. Other users cannot read your private focus-session rows. Leaderboards contain self-reported focus totals for joined rooms. Offline and inactive participants may show stale status.

Optional DNS VPN filtering inspects domain questions on the device, returns blocked results locally and forwards unblocked system-DNS questions to Cloudflare 1.1.1.1. It does not retain a DNS log. This forwarding discloses requested domains to the upstream DNS provider. No paid filtering service is used. Private/encrypted DNS and direct IP access can bypass filtering. It does not inspect HTTPS page paths or other internet traffic. Android permits one VPN at a time.

Turn off synchronization to discard pending uploads. Sign out to clear local credentials. Delete local history in settings; it does not delete cloud history. Delete FocusGuard cloud data from the profile screen to remove FocusGuard records, including room ownership and memberships. This deployment shares a Supabase project with another application, so this action preserves the shared authentication account. Request deletion of that account through the project administrator. Export uses Android's file picker and only writes to your chosen destination.

No data is sold. This application is not a security product, parental-control bypass prevention tool or emergency access restriction tool.
