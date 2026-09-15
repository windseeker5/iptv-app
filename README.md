# iptv-tv-app

A free, open-source IPTV player for Google TV (Chromecast with Google TV /
Android TV boxes), built with Kotlin and Jetpack Compose for TV.

This is a personal learning project — a way to actually learn Kotlin,
Jetpack Compose, and how to build a real Google TV app, by replacing a paid
app (TiviMate) I was already using. It's not trying to be a polished
commercial product; expect rough edges.

Not tied to any single provider: each person enters their own Xtream Codes
credentials after installing. Credentials are stored only on-device — the
app never talks to anything except the provider you configure.

## What it does

- **Live TV**: browse by category, or jump straight to your saved
  favorites ("My Channel" — the app's home screen).
- **VOD (movies) and series**: search finds all three content types at
  once (live channels, movies, TV series); series open an episode picker.
- **Save-for-later**: star a live channel into "My Channel", or a movie/
  series into "My VOD".
- **Search history**: past searches show up as re-clickable pills, with a
  one-tap clear.
- **Default channel**: pick one favorite to auto-launch into on app start.
- **In-player browsing**: while something is playing, press Left on the
  remote to open a translucent menu over the video — browse My Channel, My
  VOD, or Categories without interrupting playback, then jump straight
  into something new.
- Full D-pad/remote navigation throughout — no touchscreen assumed.

See [`docs/xtream-api.md`](docs/xtream-api.md) for the Xtream Codes API
contract this app talks to.

## Requirements

- [Android Studio](https://developer.android.com/studio)
- A Google TV / Android TV device (physical device recommended — remote/
  D-pad behavior doesn't reliably match an emulator) with Developer Options
  and wireless debugging enabled
- An Xtream Codes IPTV subscription (server URL, username, password) to
  actually watch anything

## Getting started

### 1. Clone and open

```bash
git clone git@github.com:windseeker5/iptv-app.git
cd iptv-app
```

Open the folder in Android Studio and let it sync — it will offer to
install any missing SDK platform/build-tools automatically.

### 2. Connect your TV device

On the TV: **Settings → System → Developer options** (enable it first by
clicking the build number 7 times under About) → turn on **Wireless
debugging** and open it to see a pairing screen.

From a terminal, with `ANDROID_HOME` pointing at your SDK
(`~/Library/Android/sdk` on macOS, `~/Android/Sdk` on Linux):

```bash
adb pair <tv-ip>:<pairing-port> <6-digit-code>   # shown on the pairing screen, one-time
adb connect <tv-ip>:<connect-port>                # different port, shown on the main wireless-debugging screen
adb devices                                       # confirm the TV shows up as "device"
```

If the TV drops out of `adb devices` later (e.g. after a rebuild restarts
the adb daemon), just re-run `adb connect <tv-ip>:<connect-port>` — no need
to re-pair.

### 3. Build and install

From Android Studio: hit Run with your TV selected as the target device.

Or from the command line:

```bash
export ANDROID_HOME=~/Android/Sdk   # adjust for your OS
./gradlew installDebug
adb shell am start -n com.kdresdell.iptvtv/.MainActivity
```

### 4. Configure your provider

On first launch, the app asks for your Xtream Codes server URL, username,
and password. Enter them once — they're saved locally on the device.

## Making changes

The app is plain Kotlin + Jetpack Compose, organized as one flat package
(`app/src/main/java/com/kdresdell/iptvtv/`) — one file per screen/concern
(e.g. `SearchScreen.kt`, `PlayerScreen.kt`, `XtreamApi.kt`). After editing,
rebuild and reinstall with `./gradlew installDebug` and relaunch on the TV
to see the change.

### If you're using Claude Code

This project was built with [Claude Code](https://claude.com/claude-code).
Two skills make it noticeably better at Compose-for-TV and visual design
work in this repo — worth installing if you're picking this project up
with Claude Code yourself:

```bash
claude plugin marketplace add aldefy/compose-skill
claude plugin install compose-expert@aldefy-compose-skill

claude plugin marketplace add hamen/material-3-skill
claude plugin install material-3@material-3-skill
```

- **compose-expert** ([aldefy/compose-skill](https://github.com/aldefy/compose-skill)) —
  Jetpack Compose mechanics: state, navigation, performance, and a
  dedicated Android TV / D-pad reference.
- **material-3** ([hamen/material-3-skill](https://github.com/hamen/material-3-skill)) —
  the Material Design 3 system itself: tokens, components, theming, and
  dynamic color, so UI work follows real MD3 guidance instead of guesses.

Both activate automatically when relevant — no need to invoke them by name.

## Status

Actively developed, core features working end-to-end on real hardware.
Distributed as a sideloadable APK, not on the Play Store.
