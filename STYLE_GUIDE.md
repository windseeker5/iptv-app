# iptv-tv-app — Style Guide

This is the single source of truth for visual design in this app: color, typography,
spacing, and component states. It follows Google's **Material Design 3 for TV**
guidance (developer.android.com/design/ui/tv) as closely as possible. The only
deliberate departure from stock Material is the seed color: a Unix-terminal green.
Everything else — grid, type scale shape, focus system, component states — follows
Google's published rules or a documented, reasonable extrapolation where Google
doesn't publish exact numbers.

Every section below is tagged:
- **[Google spec]** — value taken directly from Android TV / Material 3 docs.
- **[App-defined]** — Google doesn't publish an exact number for TV; this is our own
  value, derived from the closest published rule (usually the M3 mobile token,
  scaled for 10-foot viewing).

"10-foot UI" refers to *viewing distance* (a couch, ~3m from the screen), not screen
size — it applies the same whether the display is 40" or 65".

---

## 1. Principles **[Google spec]**

- **10-foot UI**: design for ~3m viewing distance — larger text, larger touch
  targets, lower content density than mobile.
- **D-pad first**: every interactive element must have a clear, high-contrast
  focused state. There is no touch input to fall back on.
- **Content-first**: minimize steps between launch and playback; posters/art carry
  the UI, chrome stays out of the way.
- **Dark-theme-first**: TV viewing is typically a darkened room; the app ships a
  single dark scheme (no light theme) for a cinematic feel and better contrast.

---

## 2. Color system

### 2.1 Seed color

Primary seed: **terminal green**, `#33FF33`-family, tuned to Material's tonal-palette
tone 50 for our green hue (`#06F906`, hue 120°). **[App-defined seed, app-defined
ramp]** — Google's TV docs specify *how* to build a tonal system (5 key colors → 13
tones each → roles), not *which* color to use. We generated the ramp below by hand
using the same tone-ladder shape M3 uses (tones 0–100, desaturating toward the
extremes) rather than the real M3 HCT algorithm — visually equivalent, close enough
for hand-authored Compose color tokens.

### 2.2 Primary tonal ramp (hue 120°)

| Tone | Hex | Typical role |
|---|---|---|
| 0 | `#000000` | — |
| 10 | `#0A290A` | primary container (dark) |
| 20 | `#0D590D` | on-primary (text on tone-80 primary) |
| 30 | `#0B8E0B` | primary container border |
| 40 | `#0AC20A` | primary (light-theme only, unused) |
| 50 | `#06F906` | **vivid accent** — focus glow, outline, active/live indicators |
| 60 | `#3DF53D` | — |
| 70 | `#71F471` | — |
| **80** | **`#A6F2A6`** | **primary (dark theme)** — filled buttons, active nav item, selected tab |
| 90 | `#D7F4D7` | primary container (on dark surfaces) |
| 95 | `#EDF7ED` | — |
| 99 | `#FCFDFC` | — |
| 100 | `#FFFFFF` | — |

Two greens are in play, both from the same ramp, used for different jobs:
- **Primary `#A6F2A6`** (tone 80) — used wherever green sits *behind* or *as* body
  text/icons (buttons, active nav), because it meets contrast requirements against
  the dark surface and against dark text placed on top of it.
- **Vivid accent `#06F906`** (tone 50) — used only for non-text decoration: the
  focus glow/outline ring, live-badge dot, progress-bar fill. This is the color that
  actually reads as "terminal green" (`#33FF33`-class phosphor green); it's kept off
  of text because at tone 50 it fails contrast for small text on black.

### 2.3 Secondary ramp (muted green-gray, hue 120°, 20% sat)

| Tone | Hex | Role |
|---|---|---|
| 20 | `#293D29` | secondary container (dark) |
| 80 | `#C2D6C2` | secondary (dark theme) — filter chips, less-prominent controls |
| 90 | `#E0EBE0` | on-secondary-container |

### 2.4 Tertiary ramp (cyan accent, hue 190°, 55% sat)

Used sparingly for contrast against the green-dominated UI — e.g. an "on now" tag,
info-overlay accents, anything that would disappear if it were also green.

| Tone | Hex | Role |
|---|---|---|
| 20 | `#17464F` | tertiary container (dark) |
| 80 | `#B0DFE8` | tertiary (dark theme) |

### 2.5 Neutral / surface ramp (hue 120°, 6% sat — faint green undertone)

| Tone | Hex | Role |
|---|---|---|
| 0 | `#000000` | true black — safe to use behind edge-to-edge video |
| 6 | `#0E100E` | **surface** (base background) |
| 10 | `#181B18` | surface container low |
| 12 | `#1D201D` | surface container |
| 17 | `#292E29` | surface container high |
| 22 | `#353B35` | surface container highest — card default state |
| 90 | `#E4E7E4` | on-surface (primary text on dark surfaces) |
| 60 | `#939F93` | on-surface-variant (secondary text, hints) |

### 2.6 Neutral-variant / outline ramp (hue 120°, 12% sat)

| Tone | Hex | Role |
|---|---|---|
| 30 | `#435643` | outline (dividers, unfocused card border) |
| 60 | `#8DA58D` | outline-variant |

### 2.7 Error

Standard Material 3 error role, unchanged — errors must never be color-coded green
or confusable with "live"/"selected" states. **[Google spec: use standard M3 error
tones, do not brand them.]** Use M3's default dark-theme error tones: `error` =
`#F2B8B5`, `onError` = `#601410`, `errorContainer` = `#8C1D18`.

### 2.8 Elevation via tonal surfaces **[Google spec]**

Google's focus-system guidance: elevation is expressed by *shifting the surface tint
level*, not by changing background hue arbitrarily. Use the surface ramp tones 6 →
10 → 12 → 17 → 22 for elevation levels 0–4 (e.g. screen background → row background
→ default card → focused card → dialog/overlay).

---

## 3. Typography

### 3.1 Typeface **[Google spec + app-defined]**

**Roboto**, system default. Google's TV typography guidance explicitly calls for
sans-serif with thick strokes and open counters for legibility at distance, and
allows brand typefaces only if they preserve that legibility. Per direction, we are
**not** switching to a monospace/terminal font — the green color carries the theme;
the type stays maximally legible from the couch.

### 3.2 Type scale **[App-defined — scaled from M3 mobile tokens]**

Google's TV typography page names the same 5 role families as mobile M3 (Display,
Headline, Title, Body, Label) but does not publish TV-specific sp values, pointing
instead at the M3 token scale. We scale each mobile token by **~1.4×** (the ratio
commonly used by Google's own Compose-for-TV sample apps for 10-foot legibility) and
round to clean sp values.

| Role | Size / Line height (sp) | Weight | Use |
|---|---|---|---|
| Display Large | 80 / 96 | Regular | Splash / hero title only |
| Display Medium | 64 / 72 | Regular | Rarely used |
| Headline Large | 44 / 52 | Regular | Screen titles ("Favorites", "Search") |
| Headline Medium | 40 / 48 | Regular | Section headers ("Live TV", "Movies") |
| Headline Small | 34 / 42 | Regular | Sub-section headers |
| Title Large | 30 / 38 | Medium | Card/row titles, dialog titles |
| Title Medium | 22 / 28 | Medium | List item primary text |
| Title Small | 20 / 26 | Medium | Secondary card labels |
| Body Large | 22 / 30 | Regular | Descriptions, EPG synopsis |
| Body Medium | 20 / 26 | Regular | Default body / EPG channel+time rows |
| Label Large | 20 / 26 | Medium | Button labels |
| Label Medium | 16 / 22 | Medium | Chips, badges, nav labels |
| Label Small | 16 / 20 | Medium | Timestamps, minor metadata — use sparingly, this is the legibility floor |

Never go below Label Small (16sp) anywhere in the app.

---

## 4. Layout & spacing **[Google spec]**

### 4.1 Canvas & safe zone

- Design baseline: 960×540dp (scales cleanly to 1080p assets), 16:9.
- Safe margins (overscan): **48dp horizontal, 24dp vertical**. All primary,
  interactive content must stay inside this margin. Full-bleed backgrounds/art may
  extend to the edge.

### 4.2 Grid

- 12 columns, 52dp column width, 20dp gutters, 4dp vertical baseline unit.
- Card width by row density (use whichever matches a given row's item count):

| Cards per row | Card width |
|---|---|
| 1 | 844dp |
| 2 | 412dp |
| 3 | 268dp |
| 4 | 196dp |
| 5 | 124dp |

### 4.3 App screen → grid mapping **[App-defined]**

| Screen / component | Row density |
|---|---|
| `ChannelRow.kt`, `ChannelListScreen.kt` | list style (not card-grid) — full-width rows, safe-margin padding |
| `VodTiles.kt`, `MyVodScreen.kt`, `SeriesEpisodesScreen.kt` poster grids | 5-card (124dp) poster tiles |
| `SearchScreen.kt` results | list style (not card-grid) — type indicator, title, "Now: `<title>`" subtitle for live results; matches Favorites/Channel-list row pattern |
| `FavoritesScreen.kt` | 4-card (196dp) |
| `CategoryListScreen.kt` | list style |

---

## 5. Focus & interaction states **[Google spec + app-defined recipe]**

Every focusable element needs distinct **default / focused / pressed / disabled /
selected** states. Google's focus-system page gives three composable techniques
(scale, glow/elevation, outline) and explicitly says to mix them per context, without
prescribing exact numbers for every case, or any animation timing. This app's
concrete recipe:

| State | Treatment |
|---|---|
| Default | Surface container tone (2.5), no scale, no outline |
| Focused | Scale **1.05×** [Google range: 1.025–1.1×] · outline **2dp** width, **2dp** inset, color = vivid accent `#06F906` · glow: 8dp diffused shadow, same color at 40% opacity · surface tint steps up one tonal level — briefly tried 1dp after a "border looks too big" report on real hardware, but a pixel-level check showed the outline itself was already correctly thin; the bulk was the glow's soft bleed, not the line. 1dp also under-serves 10-foot viewing (TV's whole reason to use a visible outline, not a hairline). Reverted to 2dp. |
| Pressed | Same as focused, scale reduced to **1.02×**, glow opacity raised to 60% |
| Disabled | Surface tone unchanged, content opacity **38%** (M3 default disabled-content opacity), no focus ring possible |
| Selected (persistent, e.g. active nav item, current tab) | Neutral elevated surface `#232823` (not a green fill — validated against the side-nav mockup, a solid green fill read as "ugly"/alert-like) + bold on-surface text, icon tinted primary tone-80 `#A6F2A6`. Survives focus moving elsewhere; focus glow (above) still layers on top when a selected item also has focus. |

Animation: **[App-defined, Google doesn't specify]** 150ms scale/opacity transition,
standard Material easing (`FastOutSlowInEasing`).

**Don't** (from Google's guidance, restated for this app):
- Don't indicate focus with color/tint change alone — always pair with scale and/or
  outline, since color-only cues fail for color-blind users and low-quality TV
  panels.
- Don't change an element's *background hue* for elevation — shift the neutral
  surface tone instead (§2.8).
- Don't apply glow + outline + scale all at max intensity simultaneously — pick the
  combination that fits the element size (small chip vs. large hero card).

**Exception: full-width list rows don't scale.** `ChannelRow`/`VodRow`/
`SeriesRow` (§6.4, §6.5, §6.6) use `appRowCardScale()`/`appRowCardColors()`
(theme/Focus.kt) instead of the standard `appCardScale()` - focus is
background lift + outline + glow only, no scale transform. A row already
near screen-width growing 1.05-1.1x on focus reads as a huge jump, not a
subtle lift; the standard scale recipe stays the default for everything
else (pills, icon buttons, poster/card grids).

**D-pad `Left` and the side rail** — `WithRail` (`SideRail.kt`) wraps every
screen's content and intercepts `DirectionLeft` at the root via
`onPreviewKeyEvent` (fires before any descendant, so a text field or a
horizontal row never gets first crack at the key). It tries a normal local
focus move (`FocusManager.moveFocus(FocusDirection.Left)`) first; only when
that finds no focusable neighbor does it summon the side rail. This is
general infrastructure for any future horizontal row - every single-column
screen in the app today (which has no horizontal neighbor) keeps the
original "`Left` always opens the rail" behavior unchanged. §6.4's search
history ended up single-column anyway (see §6.4), so it doesn't currently
exercise the local-move path, but the fallback-to-rail behavior it relies on
is the same code path.

**Selecting a rail item always returns focus to content, even a no-op
selection.** Picking a rail item calls `onSelectRail`, which changes
`screen` state in `MainActivity.kt` - and when the screen actually changes,
the whole `WithRail` instance remounts fresh (rail defaults to hidden), so
focus incidentally ends up back in content. But picking the *current*
screen's own rail item (e.g. pressing OK on "Search" while already on
Search) leaves `screen` unchanged: no recomposition, nothing ever moves
focus out of the rail, and it's left stuck open - confirmed bug, previously
only `DirectionRight`'s dedicated handler actually closed the rail. Fix:
`WithRail` wraps `onSelectRail` so every selection - not just a real
navigation - explicitly calls `contentFocusRequester.requestFocus()`,
matching what the `DirectionRight` key handler already did.

---

## 6. Components — mapped to this app's screens **[App-defined mapping of Google's component list]**

| Google TV component | This app's file(s) | Notes |
|---|---|---|
| Navigation Drawer | `SideRail.kt` | Persistent left rail; selected item uses "Selected" state from §5; icon + Label Medium text; see §6.2 for the approved recipe |
| Lists | `ChannelRow.kt`, `VodTiles.kt` (`VodRow`/`SeriesRow`), `ChannelListScreen.kt`, `CategoryListScreen.kt`, `SearchScreen.kt`, `FavoritesScreen.kt`, `MyVodScreen.kt` | Full-width rows, Title Medium primary text, Body Medium secondary (channel number, "now playing"); short press plays/opens, long press (native `Card` `onLongClick`) opens `RowActionsMenu.kt` |
| Cards | `VodTiles.kt`, `MyVodScreen.kt`, `SeriesEpisodesScreen.kt`, `FavoritesScreen.kt` | Poster grid cards (`PosterCard` in `VodTiles.kt`) show art only, no title overlay — tried a scrim + Title Small overlay first, dropped per explicit direction ("remove the text... I don't need that for movie and series") since the artwork alone is enough to recognize a title in the grid. `SeriesEpisodesScreen.kt`'s episode cards are a separate text-card variant (§6.6) since episodes have no per-item artwork. |
| Context menu | `RowActionsMenu.kt`, `RecordingDurationMenu.kt` | Long-press popup, generalized to a plain `List<MenuAction>` (label, `onClick`, `isDestructive`) rather than hardcoded favorite/default slots — lets `RecordingRow`'s "Remove from Recordings" be a real destructive action instead of a fake favorite toggle. Centered on screen (`Popup(alignment = Alignment.Center)`) over a 60%-black scrim, so it lands in the same predictable spot regardless of which row/how close to an edge triggered it — an earlier version had no alignment at all and rendered wherever Compose's default popup placement happened to land ("placed weirdly"). **No container background** — items float directly on the scrim, `Arrangement.spacedBy(8.dp)` apart, inside a fixed `width(340.dp)` `Column` (not `widthIn(min=...)` — a `fillMaxWidth()` item inside an unconstrained `Column` forces the whole `Column` to claim the full screen width instead of shrinking to content, confirmed on-device). An earlier version wrapped the items in a surface-container-highest `#353B35` box, which just read as an ugly grey box padded around already-styled buttons — removed. Items: `fillMaxWidth()` `Card`s using the real §5 focus recipe **minus scale** — `appCardBorder()`/`appCardGlow()` plus `appRowCardScale()` (not `appCardScale()`), the same "no scale-up on focus" exception full-width rows use (§5): a focused menu item growing larger than an unfocused one is the identical bug already fixed for list rows, just recurring here. An earlier version left items fully unstyled (library defaults) before that, which is what actually produced the "two different-size grey squares" look despite this row's older text claiming otherwise. Destructive actions render their label in the Error tone (`#F2B8B5`, §2.7). Dismissed via Back or picking an item; the focus-immediately + 500ms grace-window dismissal logic (absorbing the stray key-up from releasing a physical long-press) is unchanged and duplicated intentionally across both files rather than shared, per their own code comments. |
| Featured Carousel | *(not yet built)* | Reserved for a future home-screen hero row; full-width per §4.2 "1 card" |
| Immersive List | *(not yet built)* | Candidate for a future "Continue Watching" row |
| Buttons | `PlayerScreen.kt` controls, `SettingsScreen.kt` actions, dialogs | Filled = primary tone-80 fill / tone-20 text; Outlined = for secondary actions, outline-ramp tone 30 border |
| Tabs | *(not currently used — SideRail covers top-level nav)* | If added later (e.g. Live/Movies/Series switcher), stick to top, Label Large text, selected state per §5 |

### 6.1 Player overlay (`PlayerScreen.kt`)

**Approved visual recipe** — validated against a user-supplied reference mockup
(https://claude.ai/artifact/8zMP1RLmCz257xqzuUKmpz), iterated to final. This is the
canonical layout for the "info overlay" shown on OK/select while watching live TV
or VOD; implement it as spec'd here, not as a fresh design pass.

**Layout, top to bottom:**
1. **Top row** (inside the safe margin, §4.1): channel name top-left (Title
   Large), current date + time top-right (**Title Medium** — bumped up from Body
   Medium after an on-device pass found the clock too small to read from the
   couch). Both sit over the extended scrim (see point 5) plus a text-shadow.
2. **Program row**: channel/provider logo as a **rounded-square card** (not a
   circle — border-radius ≈ 14% of tile size), **top-aligned** with the title
   (`align-items: flex-start`, not `center` — the logo's top edge lines up with the
   title's cap-height). Title (Headline Small) + a 1–2 line description/synopsis
   when the provider supplies one (Body Large, `-webkit-line-clamp: 2` to avoid
   runaway text), in a column next to the logo.
3. **Progress bar**: track = **on-surface-variant `#939F93`** (a real neutral
   grey) — an earlier version used outline tone 30 (`#435643`), a dark muted
   green that blended into the scrim and read as "not working" on real
   hardware. Fill + handle = vivid accent `#06F906` with a soft glow (per
   §2.2 — this is the one place the vivid accent legitimately sits near
   text-adjacent UI, since it's a non-text indicator). Leave **generous
   vertical space** above and below the bar (≈2.5–3% of frame height) — it
   must not feel crammed against the program info above or the status lines
   below.
4. **Status block, two lines, directly below the progress bar** (not mixed into the
   program row above it):
   - **Line 1 (current)**: time range — duration — channel/quality tag, followed by
     the HD / frame-rate / audio badges. Put a clear gap (~1cqw / a few dp) between
     the channel tag and the first badge — they read as two different kinds of
     information and must not crowd together. **Deviation**: this app's
     `PlayerScreen.kt` only renders time range + duration — the Xtream API never
     returns channel-tag/quality/HD/frame-rate/audio metadata, so those aren't
     fabricated.
   - **Line 2 (next up)**: time range + next program title, de-emphasized
     (on-surface-variant `#939F93` vs. on-surface `#E4E7E4`), **no "Next" label** —
     its time range must start at the exact same horizontal position as line 1's
     time range, so the two lines form one aligned grid the eye can scan down.
   - **Record hint, right-aligned on its own line below the status block**:
     "Hold **[OK]** to **Record**" — "OK" sits in a rounded pill (`surface
     container highest` background, fully rounded) so it reads as a pressable
     button, not a label; "Record" is a **dedicated recording red**
     (`ScreenColors.RecordAccent`, `#FF453A`) — deliberately not the same value
     as the Error role (`#F2B8B5`), since recording and error/destructive states
     are different concepts and must stay visually distinct.
5. Scrim: **one gradient spanning the full screen height**, dark at both the top
   (behind the channel name/clock) and bottom (behind the program-info block),
   transparent through the middle so video stays the visual focus. Originally
   scrimmed only the bottom block with a text-shadow covering the top row, but
   that wasn't enough contrast against busy live content (news tickers, lower
   thirds) — extending the same gradient upward fixed it without adding a
   second, separate layer.

**Explicit deviation from the user's original hardware reference**: progress bar
uses terminal green (`#06F906`), not the reference device's blue.

**Not part of this screen**: no bottom row of channel-shortcut tiles (the reference
photo's TV-guide/History/channel-icon row) — this app has no such feature.

- Play/pause and transport buttons (when shown): circular, Label Large icon size
  ≥ 32dp touch-equivalent target. Positioned with a slight upward vertical bias
  (not dead-center) so it doesn't overlap the program title text below it — a
  plain OK-triggered indicator, not a focusable/scaling target, since it's
  reached only via the root's own OK-key handling, not D-pad navigation.

### 6.2 Side navigation (`SideRail.kt`)

**Approved visual recipe** — validated against a user-supplied reference mockup
(https://claude.ai/artifact/EX4zdqX4ZBDXWqs8oAGzy2), iterated to final.

- **Logo**: the app icon (`app-logo.png`), round-cropped (`border-radius: 50%`,
  `object-fit: cover`), centered at the top of the rail. **No wordmark/text next to
  it** — the mark stands alone.
- **Menu, top to bottom**: Search, **My TV**, **My Librairie** (VOD/series the user
  has saved), **All** (all categories) — then a flexible spacer — **Settings**
  pinned to the bottom. No divider line above Settings (tried, rejected — reads as
  visual clutter). No Recordings, no My List — not features this app has.
- **Rail background**: one surface tone lighter than the content area behind it
  (`#181B18`, §2.5 tone 10, vs. a near-black `#050605` content backdrop) — enough to
  read as a distinct panel without going full black. Not flat black, not a color tint.
- **Selected item**: see the updated "Selected" row in §5 — neutral surface highlight
  + green-tinted icon, **not** a solid green fill.
- **Focus** (hover/D-pad): standard §5 recipe — scale + outline + glow in vivid
  accent `#06F906` — layers on top of the selected state when applicable.
- Search has no "selected" state of its own (it opens a screen, not a persistent
  section) — de-emphasized with on-surface-variant color until focused.

### 6.3 My TV guide (`FavoritesScreen.kt` — the app's home screen)

*Correction: this was originally mislabeled as `ChannelListScreen.kt` — that file is
a different, plainer screen, see §6.4b below.*

**Approved visual recipe** — validated against a user-supplied reference mockup
(https://claude.ai/artifact/7qbbtXWvz3rSC5AmKupzJL), iterated to final. Reached from
the side nav's **My TV** item (§6.2) — "My TV" and "Favorites" are the same concept:
this screen shows only the user's favorited channels as an EPG grid. Layout is a
near-1:1 match to the user's reference photo of their current TV box's guide, with
only color and depth changed:

- **Top preview block**: video preview thumbnail (**rounded corners**, ≈1.4cqw
  radius — explicitly not square, tried square-ish first and it read as wrong),
  program title, time range + duration, a multi-line description/synopsis, favorite
  star and channel tag in the top-right.
- **Guide header**: current date/time on the left, hourly timeline labels across the
  top, aligned to the grid columns below.
- **Grid rows**: channel number, channel logo (rounded-square, consistent with the
  player-overlay logo treatment in §6.1), channel name, then program cells across
  the timeline.
- **Channel numbers are discreet by default**: small, dark gray (`#5B635B`) — they
  must not compete visually with channel names/programs. They light up vivid green
  (`#06F906`) **only on the row currently under D-pad navigation** — this is a
  per-row focus effect (row background tints faintly green, number + name brighten),
  not a static color.
- **"Now" line**: a **1px** vertical line (not thicker, no glow) in accent green
  (`#3DF53D`, §2.2 tone 60), with a small dot marker at its top end.
- **"Currently playing" cell** (the tuned channel's current program): a **plain
  gray** highlight (`#292E29`, §2.5 tone 17) — explicitly **not** the green focus
  treatment. This distinguishes "what's airing now" (a status, always gray) from
  "what row you're navigating to" (a focus interaction, green) — don't conflate the
  two even though both are highlights in the same grid.
- **Tuned-channel indicator**: the play-triangle + channel name on the row matching
  what's actually playing renders in accent green (`#3DF53D`), same family as the
  "now" line — this is a status indicator, unrelated to D-pad focus.
- **Background**: darker than the user's reference photo — near-black
  (`#0A0C0A`), per explicit request ("a bit darker for the background").

**Explicit deviation from the reference**: primary accent (channel numbers' hover
color, date/time, "now" line, tuned indicator) is terminal green, not the reference
device's blue. Everything else — layout, proportions, information hierarchy — is
intentionally as close to identical as possible; this was the user's explicit
instruction ("exact, exact, exact, exact same design").

### 6.5 All → channel list (`CategoryListScreen.kt`, `ChannelListScreen.kt`)

**Approved visual recipe** — proposed (no reference photo, loosely informed by the
middle column of the earlier side-nav reference photo), validated against
https://claude.ai/artifact/Do7THdbuLMM8m6wASoN6c7, iterated to final. Two connected
screens reached from the side nav's **All** item (§6.2):

- **All** (`CategoryListScreen.kt`): single-column list of categories (§6 "Lists"
  row pattern), a chevron on the right signaling "this row drills into another
  screen" — distinct from channel rows, which play/toggle instead of navigating
  deeper. **Implemented deviation from the original proposal**: no synthetic
  "Favorites pinned first" / "All channels" rows, and no quality-tag line or
  channel count — `LiveCategory` (`XtreamApi.kt`) carries only an id and a name,
  and the side rail's own **My TV** item already covers "favorites" as a separate
  screen; fabricating a count or a tag with no backing data would violate the
  app's "never fabricate" rule (see §6.5's subscription meter, §6.6's in-progress
  indicator). Revisit if a bulk categories-with-counts endpoint or a quality-tag
  field is ever added.
- **Channel list** (`ChannelListScreen.kt`): reached after picking a category. Row =
  channel logo (rounded-square) + name + "Now: <program>" subtitle + a favorite-star
  toggle (filled primary tone-80 when favorited, outline `#5B635B` otherwise).
- **Back navigation**: a circular icon button top-left next to the screen title —
  replaces the current implementation's bottom "Back to categories" `Card`. Keeps
  the same explicit D-pad-focusable affordance the code comment explains is
  necessary (Back isn't always reliably mapped), just moved to the conventional TV
  back-button position instead of the end of the list.
- Row focus/hover: standard list-row treatment (background lifts to
  surface-container-low `#181B18`, outline + glow in vivid accent `#06F906`), same
  as every other list in the app.

### 6.4 Search (`SearchScreen.kt`, `SearchHistoryStore.kt`)

**Approved visual recipe** — validated against a user-supplied reference mockup
(https://claude.ai/artifact/D3nBfZWjBZKfRyN8FHcwSE), iterated to final.

- **KPI row above the search field** — three static stat tiles (surface-container
  fill, rounded 10dp, 14dp/8dp padding), showing the live totals of what's actually
  indexed and searchable right now: **Live Channels**, **TV Shows**, **Movies**
  (`SearchScreen.kt`'s `syncedLiveCount`/`syncedSeriesCount`/`syncedVodCount`, already
  the running totals `MainActivity.kt`'s sync callbacks maintain). Number in primary
  tone-80 (`#A6F2A6`, Title Large), caption below in muted on-surface-variant
  (Label Small). **App-defined** — not part of the original reference mockup.
  Display-only, not a focusable D-pad stop. Deliberately compact (shrunk from an
  initial Headline Small/20dp-padding pass, on feedback that the KPI row + the
  subscription progress line together were eating ~30% of the screen for what's
  purely informational) — this strip plus the progress line below it should read
  as a compact status bar, not a hero section.
- Full-width search field only — **no mic/voice-search icon, no settings gear**.
  This screen doesn't navigate to Settings, and voice input is handled by the
  remote, not the app.
- Content margin: **64dp horizontal / 32dp vertical** — a step past the §4.1
  baseline safe margin (48dp/24dp), on explicit user feedback that the baseline
  still read as too tight on this screen specifically.
- "Historique des recherches" / "Search History" header row, with a delete/trash
  icon right-aligned in the same row (clears history).
- Past searches render as small pills, **one per row in a single vertical
  column** (not a wrapping horizontal row), most recent search at the top,
  oldest at the bottom — matching the order `SearchHistoryStore` already
  returns. Each pill wraps its own text width (not full-width) so it still
  reads as a small button, not a banner.
  - **Deviation from the reference mockup**, which showed a wrapping pill
    row: `WithRail` (`SideRail.kt`) already generalizes D-pad `Left` to try a
    local focus move before opening the side rail (see §5), which works in
    simulated-input testing, but real-remote `Left`/`Right` between pills
    didn't feel reliable enough on hardware. A single column sidesteps the
    question entirely — only `Up`/`Down` are meaningful, and `Left` from any
    pill predictably opens the rail, same as every other list in the app.
  - Pill fill is quiet surface-container grey at rest, matching the reference
    mockup — **not** a permanent green fill. Vivid accent green appears only
    as the §5 focus outline/glow, the same "selected" signal every other
    focusable in the app uses. (An earlier version tried a permanent green
    "Filled button" fill; reverted on explicit feedback that it read as
    "ugly" — the focus-only outline is both correct per the mockup and
    consistent with the rest of the app.)
  - **Focus loss on selection**: picking a pill (or clearing history) blanks
    `query`/`history`, which removes the whole history section — including
    the very item that had focus — from composition on the same frame. Left
    to Compose's default focus-loss recovery, this was observed to leave
    focus in an unpredictable place (the side rail popping open over the
    results underneath, requiring an extra `Right` press to dismiss). Fix:
    both the pill's and the trash icon's `onClick` call
    `focusRequester.requestFocus()` (returning focus to the search field,
    which never leaves composition) *before* triggering the state change
    that hides the section, so a focused node is never left dangling.
- Focus state on the field and each history pill: standard §5 recipe (scale +
  outline + glow, vivid accent `#06F906`) — the focus outline is drawn with
  the pill's own fully-rounded shape, not the app-wide default 8dp-corner
  card shape (which would draw a near-square outline over a round pill).
- The trash icon button's focused colors are set explicitly (a neutral dark
  elevated tone, not the `IconButtonDefaults` default focused container of
  near-white `colorScheme.onSurface`) and its `Icon` has no hardcoded tint
  (inherits `LocalContentColor` from the button instead) — the combination
  of a hardcoded white tint and the library's white focused-container default
  made the icon invisible (white-on-white) when focused.

**Search results** — proposed (no reference photo; built from the tokens/patterns
already agreed elsewhere in this doc), validated against
https://claude.ai/artifact/9rg31dK8KcYY54TM9Wq1F3, iterated to final.

- Results group by content type (Live TV / Movies / Series) under a **Title
  Large** section header (shrunk from Headline Small - 34sp read as too large
  next to compact result rows and the KPI strip above).
- Each section is a **single-column list — one result per line**, not a poster
  grid and not a horizontal shelf. Reuses the row pattern from §6 "Lists" (small
  thumbnail + title + subtitle) rather than inventing a new card layout.
  - **Why not a grid or a shelf**: result count is open-ended. A horizontal shelf
    forces long D-pad scrolling sideways once a section has more than a handful of
    hits; a wrapping multi-column grid still puts several items side by side per
    row, which reads the same way. A single column growing straight down is the
    better D-pad ergonomic — validated by testing both alternatives with the user
    before landing here.
  - Thumbnail: 16:9 for live-channel rows (72dp wide), 2:3 poster for
    movie/series rows (42dp wide) — matches each content type's real artwork
    shape, sized down from an initial pass (96dp/56dp) that made rows taller
    than necessary.
  - Row content, left to right: thumbnail, then title (**Title Small**,
    on-surface) + subtitle (**Label Medium**, on-surface-variant) stacked and
    given `weight(1f)`, then the type tag (Live rows only) trailing at the
    **far right** of the row — matches the mockup; an earlier pass placed
    the tag immediately after the thumbnail, ahead of the title, which
    didn't match and read as dated/cluttered. Row padding is 16dp
    horizontal / 10dp vertical (down from an initial flat 24dp all around,
    then 20dp/14dp - still too tall/bulky next to the mockup's tighter row
    height; title text also stepped down from Title Medium after the same
    feedback).
- **Not yet implemented**: the mockup's movie-row synopsis + provider rating.
  Xtream's VOD **list** endpoint (what populates search results) doesn't
  return a synopsis or rating field — only a separate per-title
  `get_vod_info` call does, which isn't fetched for every search result.
  Revisit if/when per-result detail fetching is added; don't fabricate
  placeholder text/scores in the meantime.
- Row focus/hover: background lifts to surface-container-low `#181B18` +
  outline/glow in vivid accent `#06F906` — but **no scale-up**, unlike every
  other focusable card in the app. `ChannelRow`/`VodRow`/`SeriesRow` use
  `appRowCardScale()`/`appRowCardColors()` (theme/Focus.kt) instead of the
  standard `appCardScale()`: a full-width row growing 1.05-1.1x on focus (both
  the Card library default *and* the app's own §5 scale recipe) read as a
  huge, jarring jump on something already near-screen-width - matches the
  reference mockup's row-focus CSS, which only ever does background + outline
  + glow, never a transform. This is a deliberate, explicit exception to §5
  for full-width rows specifically; small elements (pills, icon buttons, KPI
  tiles if made focusable) keep the standard scale recipe.

### App-defined: subscription progress line

Not from any reference mockup — added on explicit request, since this app is
tied to one specific provider whose accounts run on a fixed
subscription term with no auto-renewal.

- A thin (4dp) rounded progress line between the KPI row and the search
  field, only rendered when the provider's account info includes **both**
  a creation date and an expiration date (`XtreamApi.getAccountInfo()`,
  the bare `player_api.php` call's `user_info.created_at`/`user_info.exp_date`)
  — some providers omit `exp_date` for lifetime accounts, and there's no
  meaningful fraction to draw without a start date. Never fabricate a
  fallback duration.
- Fill fraction = `(now - createdAt) / (expiresAt - createdAt)`, clamped to
  `[0, 1]`. Track: surface-container grey. Fill: vivid accent `#06F906` —
  reusing the app's one existing "progress" color (§6.1 player scrub bar,
  §6.6 in-progress poster indicator) rather than inventing a second one.
- The only text on the line is the formatted expiration date
  (`"Expires MMM d, yyyy"`), trailing — no percentage, no "Subscription"
  label, no icon. Deliberately minimal per explicit direction.

### 6.6 My Librairie (`VodTiles.kt`, `MyVodScreen.kt`, `SeriesEpisodesScreen.kt`)

**Approved visual recipe** — proposed (no reference photo), validated against
https://claude.ai/artifact/CexgnsEMWGN9hMxZrhYxTz, iterated to final. Reached from
the side nav's **My Librairie** item (§6.2) — the user's saved VOD and series.

- **Poster grid, not a list** — the opposite call from §6.4 search results, and
  deliberately so: this is a browse screen where recognizing artwork is the point,
  not scanning text, so it uses the **5-card poster density** already specced in
  §4.2/§4.3 (2:3 posters, 124dp width) rather than the single-column row pattern.
- Grouped into **Movies** and **Series** sections (Headline Small headers), each a
  wrapping grid that grows downward as the library grows — same vertical-growth
  principle as search results, grid instead of list only because the content type
  calls for it.
- **All sections that exist must be visible at rest, without scrolling**, when the
  library is a typical size — don't let one section's height push another
  completely below the fold on first load. Tried a 2-row Movies section that hid
  Series entirely; fixed by capping the visible rows so both sections show
  immediately.
- **In-progress indicator**: a thin vivid-accent (`#06F906`) progress bar overlaid
  near the bottom of a partially-watched poster — reuses the exact color convention
  from the player overlay's progress bar (§6.1), so green consistently means
  "playback position" everywhere in the app, never anything else. **Not
  implemented** — the app has no watch-position tracking for VOD/series yet, and
  per the app's "never fabricate" rule (§6.5's subscription meter) this stays a
  design intent, not a fake/random bar, until real playback-position data exists.
- Card focus: standard §5 recipe (scale + outline + glow, vivid accent `#06F906`),
  same as every other card in the app — **not** the row exception (§5), since
  poster cards are a real grid, not full-width rows.
- **Recordings** aren't part of the approved mockup (no reference photo covers
  them, and `SeriesEpisode`/recorded files carry no poster art to grid). Kept as
  a third section below Movies/Series, styled to match (Headline Small header +
  count) but rendered as the existing full-width row list, not a poster grid.
- **Poster cards show art only, no title overlay** — tried a bottom-scrim +
  Title Small overlay first (matching the original mockup), removed per explicit
  direction: the artwork itself is enough to recognize a saved title, text on top
  of it didn't add anything.
- `SeriesEpisodesScreen.kt` (the episode picker, reached by opening a series) was
  redesigned around a real request instead of the original mockup, which didn't
  cover this screen: **poster + title + synopsis header** at the top (series-level
  `plot` from `get_series_info`'s root `info` object, newly parsed in
  `XtreamApi.getSeriesEpisodes` → `SeriesDetails`), then each season as a heading
  followed by a **3-card (268dp) grid** of episode cards — wider than the 124dp
  poster density since each card carries text, not art (episodes have no
  per-item artwork). Each card shows `E<n> · <title>` plus that episode's own
  synopsis (`SeriesEpisode.description`, already parsed, previously unused in
  the UI) **only when the provider actually supplies one** — many providers
  return it blank, and the card just shows the number/title in that case, never
  a fabricated line.
- **Type sizes cut one step down the scale** after a "titles are too big" report
  on real hardware — series name: Headline Large → **Headline Small**. Season
  heading: Headline Small → **Title Large**. Episode card text: Title Small →
  **Label Medium** (16sp, the app's type floor from §3.2 — can't go smaller).

### 6.7 Settings & forms (`SettingsScreen.kt`)

**Approved visual recipe** — built directly from `SettingsScreen.kt`'s real
structure (not guessed), validated against
https://claude.ai/artifact/A9QVVdK5QYJNdKpMR81pmv, iterated to final.

- **Layout, top to bottom, matching the code's actual section order**: App Update
  → IPTV Provider Setup (Server URL / Username / Password / Save) → Error Log, each
  separated by a plain 1px divider (`#2A2F2A`). App Update comes first because the
  code gives it initial D-pad focus whenever an update is available.
- Single narrow column (not full width) — a form doesn't need edge-to-edge width,
  and capping it keeps label/value line lengths comfortable to scan from the couch.
- Text inputs / toggles use the same focus recipe as buttons (§5); no separate
  "text field focus" style — D-pad users interact with the whole field as a unit,
  not a text caret.
- **Update card**: outlined in plain gray (outline tone 30, `#435643`), **not**
  green — green is reserved for focus/action, not passive emphasis/callouts.
- **Update and Save buttons**: primary tone-80 fill (`#A6F2A6`) — the two
  "commit" actions on this screen, styled identically to any other primary button.
- Section headers: Headline Small. Field labels: Title Small. Helper/error text:
  Label Medium, error text uses the standard M3 error role (§2.7), never green —
  the error log entries specifically must never read as "green = good."

---

## 7. Iconography **[App-defined, derived from focus-target sizing]**

- Icons sized 24dp / 32dp / 40dp steps, matching the sp jumps in §3.2 so icon+label
  pairs stay visually balanced.
- Nav rail and player-control icons: minimum 32dp, since these are the most
  frequently D-pad-targeted elements in the app.
- Icon color: `on-surface` (tone 90, `#E4E7E4`) by default; switches to primary
  tone-80 or vivid accent only when the icon itself communicates state (live dot,
  favorite/heart, selected nav item) — not decoratively.

---

## 8. Do / Don't summary

- **Do** keep every interactive element's focused state legible against any
  poster-art background — glow + outline together handle this; color alone doesn't.
- **Do** keep all primary content inside the 48dp/24dp safe margin (§4.1).
- **Do** default to Body Medium (20sp) or larger; treat Label Small (16sp) as a
  floor, not a default.
- **Don't** introduce a light theme — this app is dark-theme-only, per Google's TV
  guidance and the cinematic-viewing rationale.
- **Don't** use the vivid accent green (`#06F906`) as a text color on small text —
  use primary tone-80 (`#A6F2A6`) for that; the vivid tone is for glow/outline/
  progress/live-indicator use only.
- **Don't** introduce a monospace/terminal font, scanline effects, or other literal
  "hacker UI" decoration — the green seed color is the entire terminal reference;
  everything else stays standard Material 3 for TV.

---

## Open decision for review

The exact green hex (`#06F906` vivid accent / `#A6F2A6` primary) is a first pass. If
you want a different exact shade of "terminal green," tell me the hex (or a
reference like "classic amber-monitor green" vs. "Matrix green" vs. "P1 phosphor
green") and the whole tonal ramp in §2.2–2.6 gets regenerated from that new seed —
nothing else in this doc changes.
