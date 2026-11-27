# EON Mobile TV Player — Living Spec

## What the app is
Android IPTV player (Kotlin, Jetpack Compose Material3, Media3 ExoPlayer). UI language: **Bulgarian**.
Package: `com.example.eonmobiletv`. minSdk 26, target/compile 35, JVM 17.

## Data model (`model/EonModels.kt`)
- `TvChannel`: id, number, name, streamUrl, logoUrl, groupTitle, tvgId, isFavorite, catchupSource, catchupDays, currentProgramme, nextProgramme
- `EpgProgramme`: title, startTime/endTime (Instant), categoryTag, description + `progressFraction(now)`
- `CatchupRequest`: channel + resolved streamUrl + programmeTitle
- `PlayerState` / `TrackOption` (player pkg): buffering/playing/retry state; track name/detail/selected/TrackGroup

## Key flows
1. **Playlists**: M3U/M3U8 from URL or SAF file picker → `M3uParser.parse` (attrs: tvg-id, tvg-logo, group-title, catchup, catchup-days, catchup-source) → saved to DataStore; demo playlist on first run.
2. **EPG**: XMLTV / .xml.gz from URL or file → `XmlTvParser` (keyed by lowercase tvg-id) → JSON cache (`EpgCacheStore`) → WorkManager `EpgRefreshWorker` refreshes every 6h when an external EPG URL is set.
3. **Playback**: ViewModel `activeChannel` (+ `playTick`) → `EonPlayerController.playChannel(channel, url?)`; auto-retry on error (uses last URL); catch-up playback overrides URL via `CatchupHelper.buildUrl` (placeholders ${start}/${end}/${duration}/{utc}/{lutc}/(start)/(end)/`${start|Y-m-d H:M:S}`, else appends ?utc=&lutc=).
4. **Guide (ТВ Програма)**: mode chip in list pane; expandable per-channel day schedule with badges НА ЖИВО / ИЗЛЪЧЕНО / ПРЕДСТОИ; past programmes play via catch-up (History icon).
5. **Extras**: number-pad channel entry (Dialpad icon), sleep timer (ViewModel endsAt → MainActivity LaunchedEffect pauses player), audio/subtitle track selection via Media3 TrackSelectionParameters, PiP (button → enterPictureInPictureMode, aspect 16:9; manifest supportsPictureInPicture), fullscreen landscape + brightness/volume vertical gestures + double-tap seek ±10s (middle third toggles fullscreen).

## State ownership
- `EonTvViewModel`: channels, EPG map, filters, activeChannelId, playTick, sleepTimerEndsAt, catchupRequest, alerts. Favorites + cached M3U + EPG URL in DataStore (`EonPreferencesRepository`).
- `MainActivity`: owns `EonPlayerController` (own coroutine scope), fullscreen/PiP state, file pickers, dialog visibility, brightness/window.

## Design system (`ui/theme/EonTheme.kt`)
Page #0D1117, Card #161B22, Active #10232E, Border #21262D, Accent cyan #00D4E6, Live red #FF3B4E, Favorite gold #FFB800. Player overlay: fade 220ms / exit 280ms, CubicBezier(0.16,1,0.3,1); live dot pulse 2.2s. Touch targets ≥48dp; `Modifier.testTag("...")` on key controls.

## Build
- GitHub Actions `.github/workflows/build-apk.yml` (Build EON TV Debug APK): temurin 17 + gradle/actions/setup-gradle cache + SDK 35 → `assembleDebug` → artifact `eon-tv-debug-apk`.
- Local (this pod, arm64): JDK 17 (apt) + SDK at /root/android-sdk; `sdk.dir=/root/android-sdk` in gitignored `local.properties`. AAPT2 is x86_64-only → run under qemu-user-static via `android.aapt2FromMavenOverride=/root/aapt2-override/aapt2` (wrapper in /root/.gradle/gradle.properties, NOT committed). Verified: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
