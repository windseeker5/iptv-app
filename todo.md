# TODO / Ideas Backlog

Working list of improvements and bugs to work through one at a time. For each item: discuss with user, clarify scope, then plan/implement.





- [x] Retrieve/cache EPG info for My TV (concurrent fetch + get_simple_data_table + 24h SQLite cache, shared with player); cache VOD/series details on-demand; strip provider title prefixes (e.g. "CA FR:")
- [x] My TV Guide: bug with info guide + prefix + timing — only 1 channel got a grey background (root cause: sequential EPG fetch loop)
- [x] App icon doesn't look right on Fire TV (square instead of circle) — fix adaptive icon shape
- [ ] Improve menu cosmetics: smaller logo, align left, shadow or glow on right edge
- [x] My Library: only show recordings when enabled in Settings AND a USB drive is attached
- [x] Settings: add "enable recording with external USB/SSD drive" toggle and setup flow
- [ ] Refine search: white background on search form; replace progress bar on the 4 KPI cards with expiration date
