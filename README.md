# iptv-tv-app

An open-source, self-hosted-friendly IPTV player for Google TV (Chromecast
with Google TV / Android TV boxes), built with Kotlin and Jetpack Compose
for TV.

Not tied to any single provider: each user enters their own Xtream Codes
(or M3U) credentials after installing. Credentials stay on-device — this
app makes no calls to anything except the provider you configure.

See [`docs/xtream-api.md`](docs/xtream-api.md) for the Xtream Codes API
contract this app talks to.

## Status

Minimal Compose-for-TV project scaffolded — a placeholder screen showing a
focusable list of fake channels, to validate D-pad navigation before any
real channel/EPG logic is wired in.

Next step: open this folder in Android Studio, let it sync (it will offer
to install any missing SDK platform/build-tools and create the Gradle
wrapper), then run on a real Google TV device over `adb connect` and
confirm D-pad up/down/select actually moves focus between the cards.

## Requirements

- Android Studio (installed)
- A Google TV device on the same network, with Developer Options + ADB
  debugging enabled, for `adb connect <tv-ip>:5555`
