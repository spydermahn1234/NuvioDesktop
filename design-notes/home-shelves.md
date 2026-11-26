# Shelf home: a Kodi-style home for Nuvio

Nick, Nov 26 2026. Mockups: the design canvas "Nuvio home — Kodi-style mockups" (Hub, Films Spotlight on the Paper
theme, Tonight Ledger, Series Logo strip, Library list and info panel, Shelf builder).

## Why

The classic home is a feed: a big backdrop and a stack of rows ("Trending", "Netflix best of this week", collections),
every row fighting for attention. The Kodi skins Nick likes are a library: one thing in focus, type doing the work,
practical facts ("ends 8:10 PM"), and a choice of how to look at each shelf. Direction chosen (Nov 26): a **hybrid**:
tabbed shelves along the top, a switchable view per shelf; keep user-built filter shelves, time-aware details, a
Paper (light) theme and a Ledger list view.

## The rule: rows are data, not layout

Nothing about what is shown changes. `HomeRepository` still builds `HomeUiState.sections` (one `HomeCatalogSection`
per row, with its key, title, items and paging), Continue Watching and the collections exactly as now, from the same
home catalog settings, addons (AIOMetadata's catalogs included) and user collections. The shelf home only changes how
those rows are drawn and moved between. So:

- the classic home's row order, hidden rows and collection order are the shelf home's too;
- a catalog AIOMetadata adds, renames or reorders just shows up (keyed by catalog, never by position);
- collections keep their upstream JSON and Supabase sync; nothing the fork adds is written into them.

## The switch (upstream keeps its design)

- `ShelfHomeSettings.enabled` in its own profile-scoped store `nuvio_shelf_home`
  (`features/home/shelves/ShelfHomeSettings.kt`, desktop actual `ShelfHomeStorage.desktop.kt`). **Off by default.**
  Not in `HomeCatalogSettings`: that payload is upstream's and synced.
- Settings > Home screen > **Shelf home** (beside Number catalog rows), with a search row `home-shelf-home`.
- One `fork:shelf-home` branch at the top of `HomeScreen` hands Normal mode to `ShelfHome`; Search, Library,
  Discover and catalog pages stay classic. Off: the classic home runs untouched and none of the new code loads data.
- Everything else lives in `features/home/shelves/**`, listed in CLAUDE.md's fork-UI list.

## Shape

- **Top menu (slash menu):** `Tonight / Home / <each pinned collection> / Library`. Home's shelves are the classic
  home's rows, in order. A collection's shelves are its folders.
- **Inside a tab:** a list of shelf names on the left, the focused shelf on the right in its view. Up/down changes
  shelf, left/right moves through it; focus remembered per tab. Only the focused shelf loads more pages.
- **Views:** Strip (the existing row cards), Ledger (text rows with time left and end time; numbered for "Top 10" /
  "best this week"), Spotlight (coverflow, credits in caps), Wall (dense grid), Logo strip (clearlogos).
- **Per-shelf view:** "View as…" on a long press; automatic defaults by kind of row. Stored in the fork's own store,
  keyed `addonId:type:catalogId` (folder id for collection folders), never in collection JSON.
- **Time-aware:** end times from `MetaPreview.runtime`; "fits before bedtime" from Tonight's bedtime
  (`TonightSettingsRepository`); episodes and time left for series.
- **Mixed shelves ("The Hang"):** several catalogs plus filters (runtime, unwatched, ends before bedtime), merged and
  de-duplicated by IMDb id; pages keep loading until the shelf has enough (capped); a catalog's `genre` extra is used
  where it offers one.

## Phases (about one session each)

0. **Plan and switch** - this note, the setting, the switch and search row, a placeholder `ShelfHome`. (Nov 26, done)
1. **Model, no UI** - pure `ShelfHomeModel`: sections + Continue Watching + collections -> tabs -> shelves, keyed by
   catalog. `ShelfHomeModelTest`; its test filter in `windows-build.yml`. (Nov 26, done: Home = Continue Watching,
   Up next, Upcoming, then the classic rows in order with an unpinned collection as one shelf of its folders; each
   pinned collection is its own tab with a shelf per folder; Library last. The hook moved below HomeScreen's data and
   the placeholder lists the tabs and shelves.)
2. **Navigation** - slash menu, shelf list, focused shelf area; D-pad, gamepad and phone remote; focus per tab; lazy
   loading of the focused shelf only. The hook moves below HomeScreen's data so it can pass the sections in.
   (Nov 26, done, with the Strip view folded in: catalog shelves use the classic `HomePosterCard`, Continue Watching
   and folders `NuvioPosterCard`; the focused title's details above the shelf; tab and shelf remembered for the
   session in `ShelfHomeMemory`; more pages asked for only by the focused shelf.)
2b. **The mockups' frame** (Nov 26, after Nick's first look: "it looks nothing like the mockup"): the focused
   title's backdrop full-bleed behind everything with left and bottom scrims; slash menu top left; clock top right
   (hour heavy, minutes light; Tonight's 12-hour setting); the tab's shelves in a dark right-hand panel, Kodi
   list-and-info style, the focused row filled with the accent; on the left a fixed-height type block (shelf name and
   count in spaced caps, "Name / YEAR / LENGTH" with accent slashes, a spaced credit line, "starts now, ends 9:30 PM" or
   "finish by 8:41 PM", two lines of description) above the shelf's cards. Times from `ShelfTime` (tested).
3. **Strip polish** - a pinned collection's folder shelves show the folder's titles (not just the folder card);
   Continue Watching's long press and source shortcut; the classic card sizes and landscape setting checked.
   (Nov 26, done in one go with 4-6 by parallel helper sessions, integrated together: `ShelfFolderItems.kt` loads a
   folder's titles through the same resolvers as the folder page but in its own per-folder session cache (the folder
   page's repository holds one folder at a time, so it is not reused), merging sources round-robin with a short hold so
   cards never move; Continue Watching's long press is passed through. Every view implements the `ShelfViewState`
   contract in `ShelfCards.kt`; the Strip is `ShelfStripView` in ShelfHome.kt. The source shortcut is still to do.)
4. **Ledger view** - text rows, time left, end time; numbered style for ranked rows.
5. **Per-shelf view choice** - "View as…", automatic defaults, the fork-only presentation store.
6. **Spotlight view**. 7. **Wall view**. 8. **Logo strip view**.
   (Nov 26: 4 Ledger (`ShelfLedgerView.kt`), 5 the per-shelf choice (`ShelfPresentation.kt`, one "views" key in the
   shelf home's store; `ShelfViewPicker.kt` on Menu / V / hold OK / right-click on a shelf name) and 6 Spotlight
   (`ShelfSpotlightView.kt`, a fixed focused slot with posters sliding through `graphicsLayer`) done. Defaults:
   Continue Watching, Up next, Upcoming and ranked titles (Top N, best of, most watched) -> Ledger; the rest Strip.)
   (Nov 26, second parallel batch: 7 Wall (`ShelfWallView.kt`, fixed column count from the width) and 8 Logo strip
   (`ShelfLogoStripView.kt`, clearlogos in a fixed slot, names in big capitals without one) done; network and
   streaming-service rows default to Logos.)
9. **Time-aware details** - end times, fits before bedtime, series remaining.
10. (Nov 26, done: `ShelfMixes.kt` (model, rules, codec, `ShelfMixRepository` under "mixes"), `ShelfMixEditor.kt`
   (the builder overlay), `ShelfSource.Mix`, "+ New shelf" at the end of Home's panel and "Edit this shelf" in the
   view picker; a focused mix pages its sources until it has 24 titles or five pages each, more as the user scrolls.)
    **Mixed shelves** - the shelf builder, filters, de-duplication, paging until enough.
11. (Nov 26, done: `ShelfTheme.kt` palettes (Dark = the previous exact values; Paper warm paper, ink 1A1A1A, muted
   ink darkened from the mockup's 8A857C to 4A4740 / 6B665E for 4.5:1, accent darkened for text when pale, no
   backdrop), `ShelfThemeRepository` under "theme", `ProvideShelfPalette` around ShelfHome, "Shelf home look" in
   Settings. Known risk: the classic cards' own label colours and white clearlogos on Paper.)
    **Paper theme** - a light theme for the shelf home only.
12. **Tidy-up** - CHANGELOG, test filter, TESTING.md pass.

Each phase adds its checks to TESTING.md under "Shelf home".
