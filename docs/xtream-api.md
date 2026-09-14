# Xtream Codes API contract

Reference for this app's networking layer. Extracted from the sibling
`iptv` repo's Python implementation (`iptv_tui/domain/iptv_provider.py` /
`iptv_tui/domain/config.py`) — not reused as code, since that project is
Python and this one is Kotlin, but the API shape is identical.

- **Base URL**: provider server, e.g. `http://host:port`. This app asks for
  it in a settings screen rather than an env var.
- **Auth**: plain `username`/`password` query params on every call. No
  headers/tokens.

## Player API

`GET {server}/player_api.php?username=..&password=..&action=..`

- *(no action)* → account/user info (`user_info.status`, `exp_date`,
  `max_connections`)
- `action=get_live_categories`
- `action=get_live_streams`
- `action=get_vod_categories`
- `action=get_vod_streams`
- `action=get_series_categories`
- `action=get_series`
- `action=get_series_info&series_id=<id>` → episodes by season
- `action=get_short_epg&stream_id=<id>&limit=<n>` → short EPG (`epg_listings`)
- `action=get_simple_data_table&stream_id=<id>&limit=<n>` → fallback EPG,
  same shape, sometimes base64-encoded title/description

## Bulk EPG

`GET {server}/xmltv.php?username=..&password=..` → full XMLTV guide
(standard `<programme channel= start= stop=><title>/<desc>`).

## Playback URLs (no API call, direct path)

- Live: `{server}/live/{username}/{password}/{stream_id}.ts`
- VOD: `{server}/movie/{username}/{password}/{stream_id}.{container_extension}`
- Series episode: `{server}/series/{username}/{password}/{episode_id}.{container_extension}`

## Notes

- No rate limiting is enforced by the provider API in any documented way.
  The Python client does a warm-up `GET {server}` with browser-like headers
  before real calls, because some providers sit behind Cloudflare and
  reject "cold" requests — worth replicating in the Kotlin HTTP client.
- Credentials appear directly in stream URLs. Never log full stream/playback
  URLs.
