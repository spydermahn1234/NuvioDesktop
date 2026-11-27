# Shelf home v3: what's next (plan, Nov 27 2026)

Mockups: the canvas "Nuvio home — Kodi-style mockups", page "v3 · What's next" (eight boards, approved by Nick on
Nov 27: "Happy with all let's plan this"). Builds on the fan-art home (`design-notes/home-shelves.md`, "Fan-art first").

Everything stays behind the Shelf home switch (off by default, so upstream keeps its home), and each new behaviour
has its own switch where it costs something (network, sound, lights). Fork-only UI: add every new file to CLAUDE.md's
fork-UI list, `fork:shelf-home` markers in upstream files, checks in TESTING.md and a CHANGELOG entry per phase.

## What already exists (so each phase mostly joins things up)

| Need | Already in the app |
| --- | --- |
| Textless backdrops, several per title | `TriviaArtwork.forTitle` (pre-show): the addon's background, then TMDB's extra textless fanart, up to 10, cached |
| Colour from a picture | `kmpalette` (`rememberDominantColorState`), used by the details page's Dominant colour background |
| Trailer behind the art | the home hero's trailer: `HomeHeroTrailerSurface.desktop.kt`, `rankHeroTrailers`, `HomeHeroTrailerManualTrigger` |
| Theme tunes | `features/themesong/**` (`ThemeSongRepository`, `ThemeSongPlayer`, `ThemeSongSettings` with volume) |
| Lights | `LightsController` (WiZ, Govee, Smart Life scenes per content), the beat lights' WiZ `setPilot` sender |
| Seasons and episodes | `MetaDetails.videos` (`MetaVideo`), progress per episode in `WatchProgressRepository` |
| Cast with photos | `HeroCastMember(name, photo, role, tmdbId)` from `fetchHeroSummary`; a person's works in `TmdbMetadataService` |
| Collections | `TmdbMetadataService.fetchCollection` (`belongs_to_collection`) |
| Bedtime | `TonightSettingsRepository.bedMinuteOfDay`; `ShelfTime.endsAt` |
| Phone remote screens | the Tonight and Live TV mirrors (`RemoteTonight.kt`, `RemoteLiveTv.kt`) are the pattern |

## Phases

Each phase is one helper's work in its own new file(s), with a small hook in `ShelfHome.kt` / `ShelfInfoOverlay.kt`.
The lead merges hooks so two helpers never edit the same lines.

### Batch 1 (the art and Info; the base for the rest) — built Nov 27 by four parallel helpers, merged and pushed

1. **Art set (boards 1–2).** `ShelfArt.kt`: per title, an ordered list of backdrops: textless first (TMDB extras via
   the same path as `TriviaArtwork`, moved to a shared helper rather than copied), the addon's background after, the
   blurred poster last. Fetched only for the focused title (and the next one), cached per session. While a title is
   held, the next backdrop cross-fades in every 8 s; "ART n OF m" in the row header when m > 1; the gallery uses the
   set. Setting: **Textless art first** (on).
2. **Accent from the art (board 2).** `ShelfAccent.kt`: one colour from each backdrop with kmpalette (vibrant, else
   dominant), lifted until it reads on the scrim (contrast ≥ 3:1 against #06090A), eased over 600 ms. Feeds the
   slashes, fact dots, focus outline, trailer line and Shelves underline through `LocalShelfPalette`. Paper keeps its
   own accent. Setting: **Colour from the art** (on).
3. **Seasons and episodes in Info (board 5).** `ShelfInfoSeasons.kt`: for a show, the season strip and the episode
   list (number, name, length, watched / time left with a progress line), opened on the season and episode you are
   up to. Up/Down move through episodes, Left/Right change season, OK plays that episode; Play reads
   "Resume S4 E2" / "Play S4 E3" and plays it through `RemotePlayerHub.playEpisode` (this replaces the "Play on a show
   opens its page" guess). The source pill stays beside Play.
4. **Time of day (board 8).** `ShelfEvening.kt`: from a chosen time (default 21:00) a "Fits before bed" shelf leads
   Home: titles from your Home rows whose length ends before bedtime, ending soonest last; each card shows "ENDS 22:53";
   the title block adds "37 min to spare"; "BED AT 23:30 · 2H 20M" under the clock; the scrims warm (a 6% amber wash
   rising to 12% in the last hour). Built like a mix (`ShelfMixRules`), so paging and watched-filtering are reused.
   Setting: **Evening home** (on) with its start time.

### Batch 2 (motion, sound, light, more Info)

5. **Trailer as backdrop (board 1).** `ShelfTrailer.kt`: after 4 s still on a title in Browse, the art fades into
   the hero trailer, always muted, with a 4 dp progress line along the top in the accent and "● TRAILER · MUTED" under
   the clock. Any key, the gallery, Info or Shelves stops it; it never plays during playback or with the window
   hidden. Reuses the hero's trailer surface and ranking. Setting: **Trailers behind the art** (off until tried).
6. **Cast and collections in Info (board 6).** `ShelfInfoPeople.kt`: the cast as photo circles (initials until
   loaded); focusing one shows "More with <name>" as a row of their titles, OK opens it as a temporary shelf on the
   home; for a film in a collection, "PART 2 OF 3 · <collection>" with the parts, watched marks and "3h 21m for part 3".
   Keys: Up from the buttons reaches the series, then the cast.
7. **Lights follow the art (boards 3–4).** `ShelfArtLights.kt`: while browsing the shelf home (and in the gallery),
   the chosen bulbs take the accent colour from phase 2: a 2 s fade, only after the art has settled for 1 s, capped at
   40% brightness, nothing while scrolling fast. Playback hands back to Lights out per content as now; leaving Home
   restores the room. WiZ via `setPilot` (as the beat lights), Govee via its provider. Settings > Lights >
   **Follow the home's art** (off), which bulbs, brightness cap. "● LIGHTS · <colour name>" under the clock.
8. **Theme tunes (board 4).** `ShelfThemeTunes.kt`: on a show focused for 1.5 s, its theme plays quietly through
   `ThemeSongPlayer` (the theme-song volume setting), fading in over 1 s and out when you move on; never with a
   trailer's sound or during playback. "♪ THEME · QUIET" under the clock. Setting: **Theme tunes on the home** (off).

### Batch 3 (the phone)

9. **Phone mirror (board 7).** HOME / HOME_DO lines in `PhoneRemoteProtocol.kt` (current row, its posters, the
   focused title and backdrop; move, pick, play, info, shelves, view) and SHELVES (the grouped rows); the app gets a
   Home screen like Tonight's mirror plus Shelves as a searchable list; demo TV and `remote-android/README.md` too.
   The protocol is duplicated in `RemoteClient.kt`: change both sides together.

## Risks and how they are handled

- **Network per focus move** (art sets, trailers, cast): only the focused title and its neighbour are fetched, after
  the 250 ms art wait; everything cached for the session.
- **The trailer surface is one native player**: the shelf home takes it only when the hero is not mounted (it is
  not, with Shelf home on), and releases it on any key.
- **Lights from a picture can look garish**: the same contrast-lifted accent as phase 2, capped brightness, and
  no change more often than every 2 s.
- **Sound by surprise**: trailers always muted; theme tunes off by default and never over other sound.
