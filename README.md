# Pulse — Native Android (Kotlin)

The native Kotlin rewrite of **Pulse**, a realtime status-sharing + messaging app.
Same Supabase backend as the web app — this is a from-scratch **Jetpack Compose**
client with no Capacitor/WebView.

> This is the second client. The web/PWA client lives in the sibling repo and
> shares the exact same Supabase project, tables, RPCs and edge functions.

---

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.4 |
| UI | Jetpack Compose (Material 3, BOM 2026.08) |
| Navigation | Navigation Compose |
| Backend | Supabase via **supabase-kt 3.7.0** (Auth, PostgREST, Realtime, Storage) |
| HTTP | Ktor client (Android engine) |
| Serialization | kotlinx-serialization |
| Notifications | Firebase Cloud Messaging (data-only, same push flow as web) |
| Sign-in | Email/password + Google Sign-In (play-services-auth) |
| Build | AGP 8.13, Gradle wrapper, CI via GitHub Actions |

## Requirements

- Android **minSdk 26** (Android 8.0), target/compile SDK 36
- Java 17 (local builds) / Java 21 (CI)
- A Supabase project with the **Pulse schema** (see the web repo's
  `supabase_setup.sql`) and the push-notification edge functions
- A Firebase project with the Android app registered (package
  `com.pulse.statusapp`) — `app/google-services.json` is required

## Project layout

```
app/src/main/java/com/pulse/statusapp/
├── MainActivity.kt              # Activity + edge-to-edge
├── PulseApp.kt                  # Application: notification channels, FCM token
├── data/
│   ├── Models.kt                # @Serializable models for all tables
│   ├── PulseClient.kt           # Supabase client singleton (auth, postgrest, realtime, storage)
│   ├── AuthRepository.kt        # Email + Google sign-in, session, resend
│   ├── ProfileRepository.kt     # Profile, username (mandatory onboarding, rename cooldown)
│   ├── ConnectionsRepository.kt # Friends, invites, unread badges
│   ├── MessagesRepository.kt    # DMs, receipts, reactions, typing, delete, search + realtime
│   └── NotificationsRepository.kt # Push endpoint registration
├── notif/
│   └── PulseFirebaseMessagingService.kt  # Data-only FCM → local notification
└── ui/
    ├── PulseRoot.kt             # Nav graph: Auth → Onboarding → Dashboard → Chat / Settings
    ├── theme/Theme.kt           # Material 3 dark theme (Telegram-style blue-gray)
    ├── auth/                    # Sign in (email + Google)
    ├── onboarding/              # Mandatory username setup
    ├── dashboard/               # Connections, live statuses, unread, invites, status update
    ├── chat/                    # Messages, receipts (✓✓), reactions, typing, delete, search
    └── settings/                # Profile, username change (cooldown), deactivate/delete
```

## Build

```bash
# Local (uses fallback Supabase URL; add keys via gradle properties for full app)
./gradlew assembleDebug

# With real project keys
./gradlew assembleDebug \
  -PSUPABASE_URL=https://<project>.supabase.co \
  -PSUPABASE_ANON_KEY=<anon-key>
```

## CI / APK

Push to `main` → GitHub Actions builds `app-debug.apk` and uploads it as an
artifact (30-day retention). Configure these repo secrets:

| Secret | Purpose |
|---|---|
| `SUPABASE_URL` | Supabase project URL |
| `SUPABASE_ANON_KEY` | Supabase anon (publishable) key |

**Before first build:** put your Firebase `google-services.json` in
`app/` and replace `REPLACE_WITH_WEB_CLIENT_ID` in
`app/src/main/res/values/strings.xml` with your Google OAuth **Web** client ID
(found in the Google Cloud console → Credentials).

## Feature parity with the web app

- Realtime DM delivery (sender + recipient both receive inserts)
- Read receipts (✓✓) synced live via realtime + a fallback receipt-sync poll
- Reactions, message deletion, typing indicators — all live
- Status updates with emoji + photo/video, live on the dashboard
- Username is mandatory; renamed only from Settings; 2 changes/week cooldown
- Unread chat badges on the dashboard, updated live
- Push notifications (data-only FCM → local notification) for DMs and statuses
- Account deactivation & deletion with confirmation
