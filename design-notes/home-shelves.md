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

0. **Plan and switch** - this note, the setting, the switch and search row, a placeholder `ShelfHome`. (Nov 26)
1. **Model, no UI** - pure `ShelfHomeModel`: sections + Continue Watching + collections -> tabs -> shelves, keyed by
   catalog. `ShelfHomeModelTest`; its test filter in `windows-build.yml`.
2. **Navigation** - slash menu, shelf list, focused shelf area; D-pad, gamepad and phone remote; focus per tab; lazy
   loading of the focused shelf only. The hook moves below HomeScreen's data so it can pass the sections in.
3. **Strip view** - the focused shelf drawn with the existing row cards: a complete, compatible shelf home.
4. **Ledger view** - text rows, time left, end time; numbered style for ranked rows.
5. **Per-shelf view choice** - "View as…", automatic defaults, the fork-only presentation store.
6. **Spotlight view**. 7. **Wall view**. 8. **Logo strip view**.
9. **Time-aware details** - end times, fits before bedtime, series remaining.
10. **Mixed shelves** - the shelf builder, filters, de-duplication, paging until enough.
11. **Paper theme** - a light theme for the shelf home only.
12. **Tidy-up** - CHANGELOG, test filter, TESTING.md pass.

Each phase adds its checks to TESTING.md under "Shelf home".
