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
- [ ] Clean the APK server folder: keep only files that are actually used
- [ ] BUG: on Fire TV, exiting the app leaves the live stream audio still playing
- [ ] My TV page (TV guide) takes too long to load — need a strategy to fix that
- [ ] Remote control navigation overhaul — expected behavior per section:
      1. App start goes to full-screen default stream.
      2. Back (from full-screen stream) resizes/reduces the stream to a smaller view while continuing to play video/audio, showing the TV guide EPG behind/around it, with the EPG highlighting the channel currently being watched. Back again (from that reduced view) shows the menu over the guide.
      3. Whenever the menu is open, Back exits the application and stops the audio (currently buggy — audio keeps playing on exit, see FireTV bug above).
      4. In full-screen live stream: OK shows the info viewer overlay; only Back triggers the EPG guide (Left/Right do not). In the EPG guide view: Left/Right navigate earlier/later programs (time), Up/Down switch channel, and OK goes to full-screen.
