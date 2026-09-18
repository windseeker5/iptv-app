# TODO / Ideas Backlog

Working list of improvements and bugs to work through one at a time. For each item: discuss with user, clarify scope, then plan/implement.





- [x] Retrieve/cache EPG info for My TV (concurrent fetch + get_simple_data_table + 24h SQLite cache, shared with player); cache VOD/series details on-demand; strip provider title prefixes (e.g. "CA FR:")
- [x] My TV Guide: bug with info guide + prefix + timing — only 1 channel got a grey background (root cause: sequential EPG fetch loop)
- [x] App icon doesn't look right on Fire TV (square instead of circle) — fix adaptive icon shape
- [x] Improve menu cosmetics: smaller logo (-10%), left/right padding around items, narrow dark gradient scrim on the right edge (logo stays centered - confirmed fine as-is)
- [x] My Library: only show recordings when enabled in Settings AND a USB drive is attached
- [x] Settings: add "enable recording with external USB/SSD drive" toggle and setup flow
- [x] Refine search: center the "preparing search" loading indicator on screen; replace progress bar below KPI row with a 4th KPI tile showing expiration date, with a thin ratio bar inside the tile instead of tinting the whole card
- [x] Refine search: white background on search form — tried, reverted at user's request (didn't read well); kept the original dark surfaceContainerLow pill
- [x] Search engine wasn't matching best-practice standards: accent-insensitive matching (NFD-normalized name_normalized column, "elections" now matches "Élections") and multi-word AND matching (each space-separated word now an independent condition, so "ncaa hockey" matches names containing both words in any order, not one exact contiguous phrase)
- [x] Player info overlay (OK button): title, description, and the now/next program rows are too large and take up too much of the screen - reduce text sizes so more of the video is visible
- [x] Player info overlay "Hold OK to Record" hint: remove the red "Record" color, make the whole line the same light gray as the rest of the hint text; make the "OK" pill a fully round button with a black "OK" label instead of the current shape
- [x] Favorite star (e.g. seen adding a channel found via search, like Hockey/NCAA) shows before the channel name — should be after/at the end of the name instead — fixed in `ChannelRow.kt` (star now trails the name); also added a small green dot marker (reusing the EPG guide's existing "tuned" indicator color) after the name for the default channel, verified on real hardware for both the star and the default dot
- [x] Clean the APK server folder: keep only files that are actually used (removed stale `__pycache__/`; every other file — `app.py`, `templates/`, `data/version.json`, `Dockerfile`, `docker-compose.yml`, `requirements.txt`, `README.md` — is actively used by the Flask serving app)
- [x] BUG: on Fire TV, exiting the app leaves the live stream audio still playing — fixed by making app-exit explicit (release the shared live player, then `finishAndRemoveTask()`) instead of relying on the OS's undocumented default back behavior; verified on Chromecast with Google TV (audio decode stops immediately on exit) — **still needs a real-device re-check on the Fire Stick**, since that's the hardware the bug was originally seen on
- [x] My TV page (TV guide) takes too long to load — root cause found: the guide composed the whole week of programs for every channel (hundreds of boxes + ~336 time labels), blocking the TV's main thread ~18s. Now only the on-screen part of the timeline is composed. Measured Back-to-guide on a release build: 18.6s -> ~0.3s (debug build ~3-5s, debug is always much slower on this TV)
- [x] Remote control navigation overhaul — REWORKED 2026-09-18 into ONE screen after the first version was rejected (guide took ~5s, showed a channel logo instead of the stream, Left still opened the menu):
      - Live TV is now one screen: Back shrinks the same video into the guide's top-left slot (a resize, not a screen swap); OK expands it back.
      - In the guide: Left/Right scroll time (no menu), Up/Down change channel and the stream really switches (quick scrolling only switches once you pause), Back opens the menu, Back in the menu exits and stops the audio.
      - Verified on the Chromecast (release build): Back->guide ~0.3s; Right/Left scroll; Down retunes the stream (checked via log); menu opens. Not verifiable by screenshot: the live picture itself in the small slot (this TV's screenshots cannot capture video layers) — needs the user's eyes.
