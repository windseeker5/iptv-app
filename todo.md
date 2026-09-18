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
- [ ] Player info overlay (OK button): title, description, and the now/next program rows are too large and take up too much of the screen - reduce text sizes so more of the video is visible
- [ ] Player info overlay "Hold OK to Record" hint: remove the red "Record" color, make the whole line the same light gray as the rest of the hint text; make the "OK" pill a fully round button with a black "OK" label instead of the current shape
