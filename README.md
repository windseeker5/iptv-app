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

Early setup — no app code yet. Next step: create the initial Compose-for-TV
project in Android Studio and validate D-pad navigation on real hardware.
