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
- [ ] Favorite star (e.g. seen adding a channel found via search, like Hockey/NCAA) shows before the channel name — should be after/at the end of the name instead
- [x] Clean the APK server folder: keep only files that are actually used (removed stale `__pycache__/`; every other file — `app.py`, `templates/`, `data/version.json`, `Dockerfile`, `docker-compose.yml`, `requirements.txt`, `README.md` — is actively used by the Flask serving app)
- [x] BUG: on Fire TV, exiting the app leaves the live stream audio still playing — fixed by making app-exit explicit (release the shared live player, then `finishAndRemoveTask()`) instead of relying on the OS's undocumented default back behavior; verified on Chromecast with Google TV (audio decode stops immediately on exit) — **still needs a real-device re-check on the Fire Stick**, since that's the hardware the bug was originally seen on
- [ ] My TV page (TV guide) takes too long to load — need a strategy to fix that
- [x] Remote control navigation overhaul — implemented and verified on real hardware (Chromecast with Google TV):
      1. App start goes to full-screen default stream. ✅ unchanged, still works.
      2. Back (from full-screen live) reduces the stream to a smaller view over the EPG guide, continuing playback via one shared ExoPlayer instance (no reload), with the guide highlighting the channel just watched. Back again shows the menu over that reduced guide. ✅ verified.
      3. Back while the menu is open exits the app and stops audio — now deterministic (see Fire TV bug above), verified: audio decode stops immediately on exit. ✅ verified on Google TV; Fire TV re-check still pending.
      4. In full-screen live, OK still shows the info overlay (unchanged); only Back reduces to the guide (Left/Right are now no-ops in full-screen live). In the reduced guide: Left/Right scroll the timeline earlier/later, Up/Down change the highlighted channel and retune the shared player live, OK expands back to full-screen. ✅ verified.
      - Known limitation: the reduced/embedded view shows the channel's icon instead of a live video thumbnail — a live video hot-swap (both SurfaceView and TextureView approaches) caused a real ANR on this hardware (Amlogic STB), so it was reverted; audio continuity was kept since that's the part that matters for the "keep listening" requirement. Revisit only with real hardware budget.
