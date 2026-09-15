# Next session checklist

## 1. First: install design skills / have references ready

The app works end-to-end but looks rough. Before any UI/UX polish work,
install/reference these instead of trial-and-error:

- **`compose-skill`** — https://github.com/aldefy/compose-skill — a Claude
  Code skill covering Compose topics including Android TV. Not currently
  installed in this environment.
- **`mobile-android-design`** skill (found via Claude Code marketplaces) —
  Material 3 / Jetpack Compose patterns, a possible complement to the above.

Reference material to browse for inspiration instead of guessing:

- Google's official TV Design Guidelines: https://developer.android.com/design/ui/tv
- TV Components page: https://developer.android.com/design/ui/tv/guides/components
- TvMaterialCatalog sample app (installable, browsable component catalog):
  https://github.com/android/tv-samples/tree/main/TvMaterialCatalog
- JetStream sample app (full reference TV streaming app):
  https://github.com/android/tv-samples/tree/main/JetStreamCompose
- **Google's official "TV Design Kit" in Figma** — a real TV-specific
  design system (Material 3-based: styles, themes, patterns, components) —
  the closest thing to a "shadcn for TV." Search Figma Community for
  "TV Design Kit" by Google, or find it linked from the TV Design
  Guidelines page above.
- **Dribbble's "ott" tag** (dribbble.com/tags/ott) — browsable gallery of
  real OTT/streaming TV app screens, good for visual bed-reading/
  inspiration rather than component code.
- **Fudge** (https://github.com/sergio11/fudge_tv_compose_library) — a
  pre-built Jetpack Compose component library specifically for TV
  (focusable buttons, lists, nav drawers) - worth a look as ready-made
  parts rather than building every component from scratch.

## 2. Later: USB drive recording (start/stop) — lower priority

- Attach a USB drive (e.g. via a hub) to the Chromecast with Google TV
  test device.
- Goal: a basic record/stop toggle that writes the live stream's raw
  bytes to a file on that drive — no transcoding needed, `.ts` is already
  playable as-is.
- Two things to keep in mind from the brainstorm that led here:
  - Recording is a *second* connection to the same provider stream as
    playback — if the provider account only allows one connection,
    recording will conflict with simultaneously watching. Fine for now —
    record without watching if needed.
  - USB storage was chosen over SMB/NFS specifically because Android has
    native file-write support for USB storage, whereas SMB needs an extra
    library (`jcifs-ng`) and NFS is poorly supported on Android/JVM — USB
    avoids that entirely for a first version.

## 3. Idea: in-player translucent left-side menu (doesn't stop playback)

While watching a channel, pressing Left should slide in a translucent
menu over the video (audio/video keep running) instead of navigating away
to a full screen - roughly: Left = open the existing rail (Search /
Categories / Settings / Favorites) as an overlay on top of the still-
playing video; pressing Left again on a highlighted item drills into a
second nested translucent panel (e.g. into Categories' channel list)
without ever leaving the video.

Difficulty assessed tonight:
- **Easy piece**: a translucent panel over the video without interrupting
  playback - we already do exactly this technique for the info overlay
  (PlayerScreen.kt) and already have the rail component (SideRail.kt) to
  reuse. A single-level version of this is mostly wiring, not new
  capability.
- **Bigger piece**: true nested/stacked levels of menu that never leave
  the video is a real change to how navigation works in this app - today
  each `Screen` fully replaces the page. Supporting this means the video
  becomes a permanent background layer with menus stacking on top of it,
  rather than the current "one screen at a time" model. Doable, not a
  rewrite, but a dedicated chunk of work on its own - don't bundle it in
  as a small tweak.
