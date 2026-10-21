# LumiMusic

The Android edition of [Lumisound](https://github.com/HollowHeavenXR/Lumisound) — same
account, same bridge, same cloud library.

LumiMusic is not a separate service with an import button bolted on. It talks to the
**same `ios-bridge` backend** Lumisound does, with the same `/auth/*` session tokens and the
same per-user cloud storage, so:

- **Cross sign-in.** Sign in with the username and password you already use on iPhone. No
  linking step, no LumiMusic account, no migration. An account created here works in
  Lumisound too.
- **Your cloud tracks play here.** Everything in your personal server library
  (`/user/music`) is browsable and streamable, including Lumisound-locked `.lms` tracks,
  which no other Android player can open at all — LumiMusic unmasks them as they stream
  (see `LumisoundLockDataSource`).
- **Your cloud data comes with you.** Favorites, playlists, play history and the shared
  settings row are pulled down by **Settings → Import cloud data**, and every play logged
  here counts toward the same history, scrobbles, stats and achievements as a play on iOS.

## Status

Milestones 1, 2 and 3 are built: account + cloud import + cloud playback, the device's own
library, queue/shuffle/repeat/speed, offline downloads, favorites and playlist editing,
the equalizer, and a full diagnostics/telemetry stack -- and now the breadth surface:

- **Home dashboard** -- Aria's daily pick, Weekly Mix, Discover Mix, Continue Listening,
  On This Day, recently played, what's trending with other listeners, and your listening twin.
- **Search and stream YouTube and SoundCloud** through the bridge, with play next / queue /
  **radio** from any result, trending and recent searches, and autocomplete.
- **Synced lyrics** in Now Playing (tap a line to seek), plus a **sleep timer**.
- **Friends** -- who's online and what they're playing right now, requests, an activity
  feed, people search, profiles with badges and a Music Match score.
- **Podcasts** -- search, Apple's trending chart, follow/unfollow, episodes, and progress
  that resumes where you left off on iPhone or Android.
- **Stats, Rewind and Achievements** -- totals, streaks, a year-long listening heatmap,
  monthly/yearly recaps you can share, and Lumisound's full badge set.
- **Inbox and scrobbling** -- notifications, and Last.fm / Libre.fm / ListenBrainz status
  with a ListenBrainz token field and an on/off switch.
- **Whole-library downloads** -- one switch keeps every cloud track on the phone, locked
  ones included, filed as Artist/Album with a cover and a metadata file beside each.
- **Gallery background** -- the photos Lumisound on iPhone backs up for its background
  import on their own and play as a slideshow behind every screen, with the iPhone's
  opacity, blur, timing and transition.

The UI is the "Aura" design: the whole app glows in the colours of whatever is playing, with a
floating Orbit dock whose centre is the playing record -- see the roadmap's redesign section.
It keeps Lumisound's own `AppTheme` tokens — see the visual design
section of the roadmap for what is matched and what is not. Signed APKs are attached to every
tagged release. See
[docs/ROADMAP.md](docs/ROADMAP.md) for what is built, what is deliberately out of scope,
and what comes next.

## Building

No Android Studio required; CI builds every push.

```bash
./gradlew assembleDebug
```

Two optional, gitignored files:

| File | What it's for |
|---|---|
| `secrets.properties` | `lumiBridgeApiKey=…` — the shared bridge key for the bridge's legacy yt-dlp-backed routes. Unset just means no key is sent. |
| `keystore.properties` | `storeFile` / `storePassword` / `keyAlias` / `keyPassword` for a signed release build. |

Neither is needed to build, run, or sign in.

## Architecture at a glance

```
audio/         Equalizer + the audio session id the effects chain binds to
bridge/        Retrofit APIs, auth interceptor, token store, URL builders
cloud/         CloudImportService — pulls account data into the local mirror
lyrics/        LRC parsing and the lyrics cache
social/        Presence heartbeat for friends
data/db/       Room mirror of server state + the device library and offline downloads
diagnostics/   Logger, telemetry upload, crash reporter, hang watchdog, HTTP metrics
download/      Offline copies of cloud tracks (stored exactly as the server sent them)
library/       MediaStore scan of the device's own music
playback/      Media3 session service, lock-aware DataSource, play-history logger
ui/            Compose screens
```

Dependencies are wired by hand in `AppContainer` — one app-scoped object of lazy
singletons, no annotation-processed DI.

## License

See [LICENSE](LICENSE).
