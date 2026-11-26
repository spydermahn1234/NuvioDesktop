# Testing checklist

What has been tried on the TV, and what is left. Nick tests; Claude keeps this file.

**Rules (for every Claude session on this branch, see CLAUDE.md):**

- A feature's checks go in here in the same commit as the feature, under its area, as `- [ ]` lines. Each check is one
  thing to try on the TV, written so it can be answered Works / Broken / Not tried.
- The CHANGELOG entry may still say "Not yet tried", but the checks must be here.
- Results come from Nick, usually as clickable Works / Broken / Not tried questions, or from the **Checks** tab of Nuvio
  Remote (Nov 16), which writes them in here itself (a commit "TESTING: N results from the phone") or shares them as
  ready-made lines to paste. Record them as:
  - `- [x] check (2026-11-08)` when it works
  - `- [ ] **BROKEN** check (2026-11-08): what Nick said` when it does not (the phone adds the time,
    `(2026-11-16 21:04)`, to find the moment in nuvio.log)
  - `- [~] check (2026-11-08): works, but what Nick said needs doing` for "works, with a follow-up"
- At the start of a session, offer the open checks (unticked, oldest first) as clickable questions.
- **Must test** (Nov 22): `- [ ] (must) check` marks the few checks that matter most (re-checks of BROKEN lines,
  the core of each new feature, a sanity check after a merge). The phone asks them first, in a Must test section, and
  sessions offer them first. Tag one or two per new feature, not every line; keep the tag when recording a result.
- `# Archived (not testing)` at the bottom holds checks set aside by Nick. Nothing below it is asked; move a line back
  up to test it again.
- A fix for a broken or follow-up line adds a fresh `- [ ]` re-check under it rather than ticking it.

## Phone remote

- [x] Pairing with the code; D-pad, OK, Back, play/pause and typing into search drive the TV (2026-11-08)
- [x] Now playing on the phone (cover, seek bar), with lock-screen and notification controls (2026-11-08)
- [x] Send to Nuvio: a YouTube or YouTube Music link from the phone's share menu plays on the TV (2026-11-08)
- [x] Movies & TV tab: library posters show; tapping one opens its details on the TV (2026-11-08)
- [x] Watch tab sometimes lists the Torbox cloud files as "Your library" (no section chips, about 250 titles), and tapping one fails on the TV; other times the real library (My Shows, My Movies..., about 760) (2026-11-09) - fixed (2026-11-09)
  - [x] Re-check after the fix: the Watch tab always ends up with the real library and its section chips (it keeps asking for up to half a minute after Nuvio starts, and again when the tab is reopened); Torbox files are no longer in it (2026-11-09)
- [x] Music tab: albums and artists show; Play / Shuffle / Add to queue and an artist's Shuffle all / Radio play on the TV (2026-11-08)
- Remote tab bottom row of five keys: dropped, the glass redesign replaced that layout (2026-11-08)
- [x] The big key is the only play/pause: it shows Pause while playing and Play while paused, and matches the TV (2026-11-08)
- [x] Long titles and artists show in full on the film card and the music screen (they scroll slowly) (2026-11-08)
- [x] The seek bar is thin with a round marker; tapping or dragging it seeks the TV once, on release (2026-11-08)
- [x] Glass redesign: the app wears the TV's theme (change it in Nuvio's Settings and the phone follows within a second), and opens in that colour next time (2026-11-08)
- [x] Remote while browsing: touchpad, swipe moves one tile per short swipe and keeps going on a long one, tap is OK; Back, Home, Search (opens the TV's search and the phone keyboard), More (2026-11-08)
- [x] Moving around Nuvio's Settings from the phone, fixed (2026-11-09); was broken (2026-11-08): neither the touchpad nor the D-pad moves there, and a controller does not either, so Settings ignores the arrow and OK presses the pad layer sends
  - [ ] **BROKEN** Re-check after the first fix (2026-11-08): still broken
  - [ ] **BROKEN** Re-check after the second fix (2026-11-09): nothing highlighted and Down seemed to do nothing, with all three inputs. Focus was moving (OK after two Downs opened Screensaver); Settings simply draws no focus ring
  - [ ] **BROKEN** Re-check after the third fix (2026-11-09): no ring
  - [x] Re-check after the fourth fix: a ring in the theme's focus colour sits on the open category when Settings opens, and moves with the phone's touchpad and D-pad, a controller's D-pad and the keyboard's arrows over categories, rows, switches and buttons; OK opens or toggles what has the ring; the ring hides when the mouse moves (2026-11-09)
- [x] Touchpad / D-pad switch (the tag in the panel's corner, or More) is remembered after closing the app (2026-11-08)
- [x] Remote during a film: Now playing card, Subtitles and Audio tiles show the current track and tap to the next, back 10s / play-pause / ahead 10s, Back / Sources / More (2026-11-08)
- [x] Remote during music: cover, title, artist, seek, previous / play-pause / next; the Touchpad tag switches to the pad and back (2026-11-08)
- [x] More sheet: Mute, Vol −, Vol +, Type, D-pad/Touchpad, Other PC (2026-11-08)
- [x] Watch and Music tabs in the glass style: header label, glass search, pill chips, section labels with counts, glass album sheet (2026-11-08)

### Phone remote: Dark mode and the demo TV (phase 2)

- [x] Dark mode: the Dark key (film or music screen, or More) turns the phone black and dims it (Medium, 15%, to start), hides the status and navigation bars, and the screen stays on (2026-11-09)
- [x] Dark mode hints (tap, swipe, hold) show for a few seconds and fade; Back brings them back instead of leaving (2026-11-09)
- [x] Dark mode brightness: Low / Medium / High under the hints (Back shows them again) changes the phone's brightness at once, is kept for next time, and tapping one does not also pause the film (2026-11-09)
- [x] Dark mode, a film: tap is play/pause, swipe left/right is 10 s back/ahead per step with a tick each, swipe up/down is volume; each shows faintly in the middle (2026-11-09): tap works, but no swipe does anything, in any direction - fixed (2026-11-09)
  - [x] Re-check after the fix: swipes left/right (10 s, or songs for music) and up/down (volume) work, each with a tick; hold for a second leaves (2026-11-09)
- [x] Dark mode, music: swipe left/right is the previous/next song (one per swipe) (2026-11-09)
- [x] Dark mode: holding a finger anywhere for a second draws a faint ring and leaves; brightness and the bars come back (2026-11-09)
- [x] Demo TV: Try the demo TV (on the PC list) asks for the code 123456, then connects as "Demo TV" and stays in the paired list (2026-11-09)
- [x] Demo TV: the "Demo TV received" strip shows each key, swipe and typed text (2026-11-09, from Nick's screenshots)
- [x] Demo TV: More > Demo TV plays a film or an album, stops it, and changes the theme (the app follows) (2026-11-09)
- [x] Film screen with the demo strip (2026-11-09, from Nick's screenshots): the D-pad / touchpad panel is squeezed down to its title bar, with no pad left to use - fixed (2026-11-09)
  - [~] Re-check after the fix (2026-11-09, from Nick's screenshots): the keys stay whole at the bottom, but the pad was still pushed off the screen
  - [x] Re-check after the second fix: the film screen has no pad on it any more; the Touchpad tag in its header (as on the music screen) swaps to the full-size pad and Now playing swaps back; Sources brings the pad up by itself (2026-11-09)
- [x] Music screen (2026-11-09, from Nick's screenshot): the Back / Search / Dark / More keys are cut in half by the tab bar - fixed (2026-11-09)
  - [x] Re-check after the fix: the cover shrinks to fit, and the keys show whole above the tab bar (2026-11-09, from Nick's screenshot)
- [x] Demo TV: Watch and Music tabs show the pretend library (with photos when online); opening a title plays it after 2 s; an album plays, shuffles, next and previous work (2026-11-09, from Nick's screenshots)
- [x] Demo TV: the lock-screen controls show the pretend film or song and work (2026-11-09)

### Phone remote: Continue watching (phase 3)

- [x] The Watch tab opens with a Continue watching row (landscape pictures, a progress line, the episode and time left), the same titles as Nuvio's Continue Watching (2026-11-09)
- [x] The "Opening … on the TV" note shows at the top of the remote, clear of the keys (it covered them at the bottom) (2026-11-09)
- [x] Tapping one opens a sheet: poster, title, episode, how far in, and the source it last played from (2026-11-09)
- [x] Resume on the TV plays it straight away from the same source, from where it stopped (with a film already playing, that one closes first) (2026-11-09)
- [x] Resume on a title whose last link no longer plays opens its source list on the TV instead (2026-11-09)
- [x] Details on the TV opens the title's page on the TV (2026-11-09)
- [x] Demo TV: Resume plays a pretend title from its saved place (2026-11-09, from Nick's screenshots)

### Phone remote: QR pairing (phase 4)

- [x] Nuvio's Settings › Keyboard shortcuts › Phone remote shows a QR code ("Or scan to pair") under the pairing code, while Phone remote is on (2026-11-09)
- [x] In the app, "Scan the TV's code" on the PC list opens Google's scanner (no camera permission asked); scanning the TV's code pairs and connects with no typing (2026-11-09)
- [x] The phone's own camera app, pointed at the code, offers to open Nuvio Remote, which then pairs (2026-11-09)
- [x] "Scan the TV's code instead" on the code screen works the same (2026-11-09)
- [x] New code on the PC changes the QR code too; an old QR then says the code isn't right (2026-11-09)
- [x] Demo TV: scanning the demo QR picture (sent in the chat) pairs with Demo TV (2026-11-09)

### Phone remote: pick the source on the phone (phase 5)

- [x] When a film or episode's source list opens on the TV, the remote shows the same list in the same order: name, addon, size, an Instant tag for cached ones, and the file details (2026-11-09)
- [x] It fills in as the TV finds sources ("Finding sources… N so far" with a thin bar), then shows the total (2026-11-09)
- [x] Tapping a source plays that one on the TV; greyed-out rows (the ones the TV greys) do nothing (2026-11-09)
- [x] The Instant tag at the top shows only the cached sources (2026-11-09)
- [x] The header tag (or the Pad key) swaps to the touchpad and back, for anything else on the TV's list (2026-11-09)
- [x] Sources during a film (the player's sources panel) shows its list on the phone too (2026-11-09)
- [x] Back closes the list on the TV and the phone goes back to the remote (2026-11-09)
- [x] Demo TV: opening a title shows four pretend sources (the last greyed); picking one plays it (2026-11-09)

### Phone remote: search everything and voice search (phase 6)

- [x] Watch tab: typing a search (and pausing) adds "From your addons" under your library's matches: what Nuvio's Search tab finds, with posters, filling in while it searches (2026-11-09)
- [x] Tapping a result opens its page on the TV, and the phone says its name (2026-11-09)
- [x] The mic in the Watch and Music search boxes (while empty) opens the phone's voice typing; what you say becomes the search (2026-11-09)
- [x] Remote tab: Search, then the mic beside the text box, types what you say into the TV's search box (2026-11-09)
- [x] A search on the phone while the TV is on its own Search tab does not upset it (2026-11-09)
- [x] Demo TV: searching "signal" shows The Long Signal and two pretend catalog results (2026-11-09)

### Phone remote: wake the PC (phase 7)

- [ ] Once connected with this Nuvio, the remote remembers the PC's network card (no setting needed on the phone)
- [ ] With the PC asleep, the remote shows "Asleep or off?" with a Wake tag; Wake sends the wake-up, shows "Waking it up", and connects once the PC is up
- [ ] On the PC: Wake on Magic Packet on for the network card (Device Manager) and Wake-on-LAN in the BIOS, as Settings › Phone remote now says
- [ ] If it does not wake within about 90 s, the remote says so
- [ ] Demo TV: More › Demo TV › Sleep drops the phone; Wake brings it back after a few seconds

### Phone remote: music queue and lyrics (phase 8)

- [x] Music screen keys are Back, Queue, Lyrics, Dark, More (Search is on the pad view now) (2026-11-09)
- [x] Queue opens a sheet of the TV's queue (cover, title, artist, length), the playing song highlighted and scrolled to (2026-11-09)
- [x] Music tab (2026-11-09): tapping an album only plays the whole album; there is no way to play one of its songs - fixed (2026-11-09)
  - [x] Re-check after the fix: the album sheet lists the album's songs under Play / Shuffle / Add to queue; tapping one plays the album from that song on the TV (2026-11-09)
- [x] Tapping a song in the queue plays it on the TV; Play next and Remove (on songs still to come) change the queue, and the sheet shows the change (2026-11-09)
- [x] Lyrics (or a tap on the cover) shows the song's lyrics in the cover's place; timed ones follow the song with the sung line bright and kept in view (2026-11-09)
- [x] Lyrics follow to the next song by themselves; a song with none says so (2026-11-09)
- [x] Lyrics on the phone look like the stage's Loud style: each line big in its own face and colour, words outlined and landing as sung (on the beat for word-timed songs); the next line small underneath (2026-11-09): works, but the words were too big: the next line went off the box and the words ran under the List tag - fits and shows whole lines after the third fix (2026-11-09)
  - [~] Re-check after the fix (2026-11-09): fits now, but long lines were cut short with … instead of shown whole
  - [~] Re-check after the second fix (2026-11-09): whole lines now, but the last row of a two- or three-row line was cut off at the bottom ("ALL FOR NOTHING", "DID YOU REALLY WANT ?")
  - [x] Re-check after the third fix: every row of a long line is fully visible, none cut at the bottom, and the next line still shows under it (2026-11-09)
- [x] The List / Loud tag in the lyrics' corner switches to the scrolling list and back (2026-11-09)
- [x] Demo TV: an album's queue and pretend timed lyrics (2026-11-09)

### Phone remote: a show's episodes on the phone

- [x] Watch tab: tapping a show (not a film) opens its sheet on the phone with season tags and the episodes (picture, number, title, how far watched), instead of only opening it on the TV (2026-11-09)
- [x] It opens on the first season with an episode not yet watched through (2026-11-09)
- [x] Tapping an episode plays it on the TV as its details page would: its source list comes up on the remote to pick from (2026-11-09)
- [x] Details on the TV in the sheet still opens the show's page on the TV; a film still opens straight on the TV (2026-11-09)
- [x] Demo TV: Harbour Lights shows two seasons of six episodes, the first three watched (2026-11-09)

### Phone remote: add-on subtitles

- [x] The Subtitles tile shows the add-on subtitle's name (English (SubSens…) once one is on (2026-11-09)
- [x] Hold the Subtitles tile: a sheet lists Off, the file's own subtitles, then each add-on's (under its name), the one in use marked "On"; with none yet it says it is asking and fills in as the add-ons answer (2026-11-10)
- [x] Tapping a row in that sheet switches the TV's subtitles to it (an add-on one loads and shows), the tile follows, and Off turns them off; a quick tap on the tile still cycles to the next (2026-11-10)
- [x] Scrolling the sheet's list does not pick a row by accident (2026-11-10)
- [x] Demo TV: hold Subtitles shows a list of six; picking one changes the tile (2026-11-16)
- [x] Hold the Audio tile: a sheet lists the film's audio tracks (the one playing marked "On"); tapping one switches the TV's audio and the tile follows; a quick tap still cycles to the next (2026-11-10)
- [x] The audio list leaves out commentary and audio-description tracks, as the TV's own audio panel does (2026-11-10)

- [x] A film with no built-in subtitles (the screenshot case): once the add-on subtitles are found, the Subtitles tile reads "Off" instead of "Tap for the next"; tapping it picks the first add-on subtitle and the tile shows its name; further taps go through the rest (2026-11-16)

### Phone remote: Sources cards

- [x] Open a film's or episode's source list on the phone: each source is a card with the add-on's name on top, then badges for resolution (4K, 1080p), HDR or Dolby Vision, codec (HEVC, H.264) and size (2026-11-10)
- [x] The raw release name sits on one muted line under the badges; holding the card shows all of it, holding again hides it (2026-11-10)
- [x] A quick tap on a card still plays that source on the TV (and holding does not play it); greyed cards still do nothing (2026-11-10)
- [x] The filter row under the title (All, Instant, 4K, HDR, Dolby Vision) only offers filters that match something; tapping one narrows the list, tapping it again (or All) shows everything (2026-11-10)
- [x] A source whose name says nothing readable shows its name as before, not an empty card (2026-11-16)
- [x] Demo TV: the first source shows 4K, Dolby Vision and HEVC; the second shows 1080p, HDR10 and H.264 (2026-11-16)

### Phone remote: film extras, music extras and the More sheet

- [x] Film screen: top panel, Subtitles and Audio tiles, play buttons, volume slider and Skip / Next episode (when shown) fit without scrolling on your phone; a smaller phone may scroll a little (2026-11-10)
- [x] Under the play button a slim volume slider changes the TV's volume, and the number follows (2026-11-10)
- [x] More > Playback has Speed chips on a film (0.75x to 2x); tapping one changes the film's speed on the TV and the chip lights; the TV's own speed panel agrees; Speed is not shown for music (2026-11-10)
- [x] During a show's intro (or recap, credits) a "Skip intro" (or "Skip credits", "Skip recap") button shows above the card; tapping it jumps the TV past it; it disappears once the part is over (2026-11-16)
- [x] On a show with a next episode, "Next episode" shows and tapping it starts that episode on the TV; on a film it is not there (2026-11-16)
- [x] More > Playback > Sleep: 15 / 30 / 60 min starts a timer and the row says "N min left"; when it ends the TV (or music) pauses; Off cancels it (2026-11-10)
- [x] Music screen: a row of Shuffle, Repeat, Like and Radio under the play button. Shuffle and Repeat (off, all, one) change the TV's queue and light up; Radio starts a radio from the playing song (2026-11-10)
- [x] Like shows only when signed in to YouTube Music on the TV; tapping it fills or empties the heart and the TV's heart agrees (2026-11-10)
- [x] More sheet is in three groups (Playback with volume and sleep, Display with pad and dark mode, Device with Type and Other PC) and scrolls on a small phone (2026-11-10)
- [x] Demo TV: the film shows "Skip intro" for its first minute; speed, sleep, shuffle, repeat and like all answer (2026-11-16)

### Phone remote: Tonight on the phone (Nov 10)

Needs this Nuvio and the new NuvioRemote app together.

- [ ] With the Tonight picker on the TV, the phone's Remote tab says "Choosing tonight" and shows Three Questions and Three Cards as two big buttons (the one used last marked LAST TIME)
- [ ] Tapping Three Questions starts it on the TV; the phone shows the same question, "Question 2 of 3", "214 → 41 left" and two big answers
- [ ] Tapping an answer answers it on the TV; "Either is fine" and "Back a question" do what Down and Back do on the TV; the buttons never move between questions
- [ ] After the questions, the three cards show on the phone with their posters, SAFE / NEW TO YOU / WILDCARD, the reason and "ends 23:06"; tapping a card plays it on the TV, Details opens its page
- [ ] Reshuffle on the phone deals three new cards once, then says "No reshuffles left"
- [ ] The header tag swaps the Tonight mirror for the pad and back (Nov 11: the modes are mirrored too, see below)
- [ ] Choosing something with the TV remote while the phone is open keeps the phone in step
- [ ] Once a title starts playing, the phone switches to the film screen as usual

### Phone remote: every Tonight mode on the phone (Nov 11)

Needs this Nuvio and the new NuvioRemote app together.

- [ ] The phone's Tonight launcher lists all eight ways to choose in the TV's order (the one used last marked LAST TIME); tapping any of them opens it on the TV and on the phone
- [ ] Coin Toss: the phone shows the picked title and "Plays by itself in 7 s" counting down; Not this tosses again on the TV, Play now plays, Hold stops the clock (and Start the clock restarts it), Its page opens the page; the last three noes and the lean show under the buttons
- [ ] Knockout: the phone shows "Round 1 of 3" and the two titles; tapping one keeps it on the TV; Undo undoes the last pick (and on the first match goes back to the launcher); "A 20 second taste of each" plays the tastes on the TV and the phone says which; the winner shows with its countdown and Play now
  - [ ] **BROKEN** (2026-11-11): after a pick the names changed but the posters stayed the old ones
  - [ ] (must) Re-check: after each pick both posters on the phone change with the names (and the same for Three Cards' reshuffle and Coin Toss' next title)
- [ ] Channel Flick: the phone shows the channel playing, "CH 3 of 40", and the next three; Never show me this, Play it, Save for later and Next channel do what Left, OK, Up and Right do on the TV; the note ("… saved for later") shows on the phone too
- [ ] Ends By: the phone shows "Bed at 23:30" with −15 and +15 (they move bedtime on the TV too) and the list, the ones past bed faded; tapping a title plays it
- [ ] The Pile: tapping a title plays it; Let go asks "Let go for good?" and a second tap takes it off the pile on the TV
- [ ] The Map: dragging on the phone's touchpad moves the lens on the TV; the title under the lens (and what is nearby) shows above the pad, a small square shows where the lens is, a tap plays the title under the lens
- [ ] Back on the phone does what Back does on the TV in every mode, and the buttons never move as titles change
- [ ] With an older phone app, the modes still show "… is on the TV" and the two-button launcher still works

### Phone remote: long-press menus on the Watch tab (Nov 11)

Needs this Nuvio and the new NuvioRemote app together. Hold anything for about half a second.

- [ ] Holding a library poster buzzes and opens a glass sheet with its poster and name straight away; the rows fill in a moment later without anything jumping
- [ ] A film's sheet: Mark as watched / unwatched, Add to or Remove from library, Choose a source on the TV, Add to a playlist, Not interested, Details on the TV (plus Start from the beginning when it is half-watched, Add to all libraries with Trakt or another library connected, and Start a rewatch with Simkl rewatches on and the film watched)
- [ ] Mark as watched on a poster: the phone says so, the green tick appears on the poster, and the TV's Library shows it watched (and Trakt/Simkl get it as they do from the TV)
- [ ] Mark as watched on a show's poster marks every released episode (the TV's toast says how many); Mark as unwatched clears them
- [ ] Remove from library takes the poster off the phone's list and the TV's Library; Add to library on a search result adds it
- [ ] Holding a Continue watching card: Resume, Start from the beginning, Choose a source, Mark episode (or film) as watched, Add to a playlist, Details, Remove from Continue watching; Remove takes the card off the phone and the TV
- [ ] Start from the beginning plays it from 0:00 on the TV; Choose a source opens the TV's source list (and the phone's Sources cards)
- [ ] In a show's season list, holding an episode: Mark as watched, Mark earlier episodes as watched, Mark Season N as watched, Choose a source, Recap the story so far (with AI recaps on), Add episode / Add Season N to a playlist; the episode list ticks over without closing the show sheet
- [ ] Holding a season tag (shows with more than one season): Mark Season N as watched, Mark earlier seasons as watched, Recap before Season N, Add Season N to a playlist, Details
- [ ] Add to a playlist opens a picker on the phone with the TV's playlists and New playlist; adding says "Added ... to <playlist>" and the TV's playlist has it. A whole show from its poster offers Every season or one season first
- [ ] Recap shows "Writing the recap…" then the recap text on the phone (scrolls if long); with recaps off it says so
- [ ] Not interested on a poster: the title no longer appears in Discover on the TV
- [ ] Demo TV: holding a poster, card, episode or season opens the sheet; marking an episode watched ticks it; the playlist picker and the pretend recap appear

### Phone remote: the "Ends at" clock on the film screen (Nov 11)

Needs the new NuvioRemote app (no change on the PC).

- [x] Playing a film: the gap between the volume slider and Back / Sources / Dark / More is now a glass card with "ENDS AT" and the finishing time in big type (in your phone's 12 or 24 hour format), and the film's length ("Runs 1h 50m") under it (2026-11-16)
- [x] The time is right: it matches the clock plus the time left on the seek bar, and jumps at once when you seek or skip 10 s (2026-11-16)
- [x] Pause: the label reads "Ends at · paused" and the time creeps forward with the clock (it is when it would end if you played now) (2026-11-16)
- [x] Speed 1.5x (More sheet): the finishing time comes forward and "1.5x" shows on the line under it; the sleep timer shows "Sleep in N min" there too (2026-11-16)
- [x] On a show, Next episode sits in the card's bottom row; during an intro, recap or credits Skip appears beside it. The big time does not move when they come and go (2026-11-16)
- [x] Nothing on the film screen scrolls on your phone; the card fills the gap with no blank space under it (2026-11-16)
- [ ] **BROKEN** (2026-11-11): on a show the screen scrolled and the card was cut off under the keys
  - [ ] **BROKEN** Re-check (2026-11-11): no scroll, but the time was tiny and Next episode filled the card: on a show (taller top panel) nothing scrolls; the card shrinks to fit and its big time gets smaller, with Next episode fully visible inside it
  - [~] Re-check: on a show the card lays out sideways: "ENDS AT" and a big time on the left, Next episode on the right (Skip stacked above it during an intro or credits); nothing scrolls, and the time keeps its size when Skip comes and goes (2026-11-11): works, but the time was cut off ("00:1" for 00:16)
    - [x] Re-check: the whole time shows on a show (try a late one like 00:16 and a wide one like 23:58); it shrinks a little if it has to, never cut off (2026-11-16)

### Phone remote: music search with YouTube Music (Nov 15)

Needs the new Nuvio on the PC (build.cmd) and the new NuvioRemote app.

- [ ] Music tab: typing two letters or more (or the mic) shows Artists, Songs and Albums; your library's matches come at once and YouTube Music's fill in a moment later ("Searching YouTube Music on the PC…" meanwhile)
- [ ] Artists and albums already in your library carry a LIBRARY tag, and are not listed twice
- [ ] A library artist opens its page on the phone; an artist not in the library opens a sheet with Radio on the TV, which plays their radio
- [ ] Tapping a song: Play on the TV plays it (a Video row plays as the video), Play next and Add to queue join the queue, Start radio plays its radio
- [ ] Tapping any album (in the library or not) opens the album sheet with its songs; Play, Shuffle, Add to queue and a song all play on the TV
- [ ] Clearing the search goes back to the Albums / Artists view
- [ ] With an older Nuvio on the PC: after about 20 s it says the PC didn't answer, and the library matches still show
- [ ] Demo TV: a music search shows the demo albums with LIBRARY tags plus one "(Live)" album without; a song plays

### Phone remote: updates itself (Nov 15)

Install this build once by hand from the NuvioRemote release; from then on the app updates itself. No change on the PC.

- [ ] More › Device shows an Update key and, under it, "Build N" (the build installed); Update checks at once and says "up to date"
- [ ] After a newer build is published, opening the app (or Update in More) downloads it, and a card at the top says "Update ready · Nuvio Remote build N" with Later and Install
- [ ] Install, the first time: Android asks to allow Nuvio Remote to install apps; after allowing and coming back, the install carries on and Android's confirm screen appears; the app updates and keeps the paired PCs
- [ ] The next update installs with no "allow" step (on Android 12 and up, often no confirm screen at all)
- [ ] Later hides the card until a newer build; More › Install still installs it

### Phone remote: Checks, TESTING.md on the phone (Nov 16)

Needs the new Nuvio Remote build (it updates itself); nothing changes on the PC. A fourth tab, **Checks**, beside Music.

- [ ] The Checks tab reads TESTING.md from GitHub: "N open" under the title, then one glass row per area with its open count, in the file's order
- [ ] Tapping an area opens its first unanswered check as a card: the check in large type, "Re-check of" with the broken line above it for a re-check, and "Getting there" with where to turn the feature on; "1 of N" and the arrows step through
- [ ] Works moves to the next check at once; Not tried moves on and writes nothing; after the last check the area list comes back
- [ ] Broken and Works, but… open a sheet to type or say (the mic) what happened; Save answer moves on, and the line under the card says what was answered (tap it to take the answer back)
- [ ] Answers survive closing the app; the header says "N answered, not saved yet"
- [ ] Without a token, Share N opens the share sheet with the answers as TESTING.md lines under their headings; pasted into a Claude chat, Claude can record them
- [ ] GitHub opens the token sheet; "Make a token on GitHub" opens the fine-grained token page; a token with Contents read and write on NuvioDesktop is saved, and the tag turns to Save N
- [ ] Save N commits to Spyder ("TESTING: N results from the phone"): the lines are ticked as the header says (Broken with the time), the answers leave the phone, and the list refreshes with fewer open
- [ ] A wrong or expired token says so in plain words, and the answers stay on the phone
- [ ] (must) Must test (Nov 22): the Checks tab lists "Must test" first, with the (must) checks from every area; each card names its area above the check, and "(must)" is not shown in the text
- [ ] Answering a check under Must test ticks it in its own area too, and Save writes it to its own line in TESTING.md, tag kept
- [ ] Nothing from the "Archived (not testing)" part at the bottom of TESTING.md is asked, and the open count at the top no longer counts it

## Music playback

- [x] Gapless: songs in an album run straight into each other (2026-11-08)
- [x] Volume is right from the first second, no jump a few seconds in (2026-11-08)
- [x] Constant volume for music: no gain riding during a song (2026-11-08)
- [ ] Constant volume for trailers: check they get the same treatment as music (Nick, 2026-11-08)
- [x] Lossless: an album playing from YouTube Music switches to the addon's lossless copy; tiles show the Lossless badge (2026-11-08)
- [x] Calm lyrics stay in order without jumbling (2026-11-08)
- [x] After the upstream merge (mpv 0.42, FFmpeg 9.0.2): gapless, steady volume and the Beat Grid all still work (2026-11-09)

## Mini player

- [x] Open dock: cover-tinted seek bar, beat dot, Radio tag, "Keep going?" inside the card (2026-11-08)
- [x] Heart likes the song on YouTube Music, and un-likes on a second click (2026-11-08)
- [x] Quality pill, Previous, times at both ends of the bar, Up next with Play next / Remove, Shuffle, Repeat, Clear (2026-11-08)

## Radio

- [x] "Keep going?" near a radio's end, and Keep going adds more songs (2026-11-08)
- [~] Stations and mixes play from search; Start radio on songs and artists (2026-11-08): works, but genre keyword
  detection needs to be better. Plan: remove the Ladder and move genre into search. Built (2026-11-09), see Music home
  and search below.

## Shelf

- [x] Three-panel browser fits one screen; Artists A to Z and All albums work with the remote and the mouse (2026-11-08)
- [x] Smart shelves (New from your artists, Recently played, Most played, Gathering dust) fill from the play log (2026-11-08)
- [x] Liked Music, Unplayed and Listen again shelves fill and play (2026-11-08)
- [x] Liked Music shows your liked songs as their own rows after the card (2026-11-08)
- [x] Play history: Import from YouTube Music and Clear (2026-11-08)
- [x] YouTube Music as you: your own mixes, playlists and Liked Music (2026-11-08)

## Music home and search

- [x] Sentence chips, the narrow row, Remix / Live panels (2026-11-08)
- [x] Top-match morph, the action row and More menus (2026-11-08)
- [x] More menu on songs and releases (right-click, Menu key, hold OK), with Play next (2026-11-08)
- [x] The Ladder is gone from the Music tab: no Ladder pills beside the search kinds, no "Ladder starters" or "Continue your ladder" on the old search's start screen, no "Ladder from here" or "Climb into" buttons in the preview pane (2026-11-10)
- [x] Stage: tapping a genre chip closes the stage, opens the Music tab and searches that genre (the genre card shows) (2026-11-10)
- [x] Type a genre (shoegaze, post punk, trip hop): a genre card leads Top Match, with "IN YOUR LIBRARY" artists and "TOP ARTISTS" under it; OK on the card plays a radio or playlist for it (2026-11-10)
- [x] Spelling and word order are forgiven: "postpunk", "hip hop music", "rock indie", "punks" all show the card (2026-11-10)
- [x] Moods, activities and decades: "chill", "workout music", "90s", "90s rock" show a card (and "90s rock" plays something sensible) (2026-11-10)
- [x] An artist wins a clash: with an artist as the top match (try "Soul" or "Dance" if you own one), the artist stays Top Match and the genre card sits second (2026-11-10)
- [ ] With the offline MusicBrainz copy installed, a niche tag the hand list lacks (try "vaportrap", "witch house") still gets a card; without the copy it does not
- [x] Tapping a TOP ARTISTS row searches that artist (2026-11-10)

## Artist page

- [x] Photo hero, three panels, Essentials and Discography, six buttons on one row (2026-11-08)

## Album page

- [x] Lineage fills from open data (Wikidata, YouTube Music) instead of waiting on the AI (2026-11-08)
- [~] The Story shows text even when the AI key is out for the day (2026-11-08): works, but it only says "From
  Wikipedia", so it is not clear whether the Gemini / Google Search backup ever answered. Needs a way to tell which
  source wrote the story.
- [x] Album intro uses YouTube Music's own Wikipedia blurb (2026-11-08)
- [x] Who made it: credits for most albums, without waiting on the AI (2026-11-08)
- [x] Sonic Map and Your History tabs (2026-11-08)
- [x] Cut to fit (2026-11-08)
- [x] Albums with two editions (Ren, Vincent's Tale) stay on one (2026-11-08)
- [ ] **BROKEN** Song by song > Other versions finds the clean or explicit edition (2026-11-08): not all of the time,
  needs work
  - [ ] (must) Re-check after the fix (Nov 20): on an explicit album with a clean edition (2001, or one whose clean titles are starred out like "F**k"), Song by song > Other versions lists "Clean edition" for every song, the starred ones included, and playing it plays the clean copy
  - [ ] On an album with deluxe or remastered editions as well, the Clean (or Explicit) edition is listed first, above the others
- [x] Edit pills: small card, intro, trivia and the song story edit on the album page and the Stage, and stick (2026-11-08)

## Music facts

- [ ] **BROKEN** The facts filler fills in the background within the AI key's limits (2026-11-08): on Groq's free tier
  it hits the limits within seconds
  - [ ] (must) Re-check after the fix (rolling-minute limit, 1773853, Nov 8): with a free Groq key and the filler on for half an hour, Settings > Music shows it filling and now and then "Keeping under the AI key's minute limit; next lookup in N s", never "Several lookups failed in a row", and a song story you open yourself meanwhile still loads
- [x] Share music facts as JSON; Fill album facts in the background (Settings > Music) (2026-11-08)

## Now Playing stage

- [x] Beat Grid drum rows read on the beat; Song Map has the right tempo and bar lines and fills the width (2026-11-08)
- [~] Beat Grid: beat one in the right place, e.g. Billie Jean (2026-11-08): much better, still needs work
- [x] Visualiser in the Liner Notes box (2026-11-08)
- Bigger album case on wide windows: dropped, the new screen replaced it (2026-11-08)

## Beat lights (Nov 22)

Settings > Lights out > Beat lights (music). Needs the WiZ bulb's IP address (your router's device list shows it).

- [ ] (must) **BROKEN** With the bulb's address in and Flash a light on the beat on, a four-to-the-floor track from the Music tab flashes the bulb on every kick drum, and not on the bass line or hi-hats (2026-11-22): the Beat Grid shows a kick on every beat (120 BPM), but the bulb pulses softly and misses runs of kicks for seconds at a time
  - [ ] (must) **BROKEN** Re-check with Flash style Strobe (the new default): the bulb goes hard on and off on every kick of a four-to-the-floor track; if not, copy the nuvio.log lines starting "BeatLights" (one every 10 s) into a chat (2026-11-22): not a hard on/off. The log shows every flash sent and every command answered by the bulb, no playhead jumps: the WiZ firmware fades every change, so WiZ cannot strobe hard
  - [ ] **BROKEN** Flash style Swap: the light flips on or off on every kick, never skipping a run of kicks (2026-11-22): soft, as Strobe (the bulb's own fade)
  - [ ] (must) Govee: Find Govee lights lists the Govee lights with LAN Control on (model and address)
  - [ ] (must) Govee: a picked light strobes hard on and off on every beat of a four-to-the-floor track (Flash on Every beat, Flash style Strobe)
  - [ ] Govee: Add a Govee light by address (its IP from the router) adds it ticked, and it flashes, when Find can't reach it from the PC's 5 GHz network
  - [ ] (must) Govee floor lamp: between flashes it stays dark (Strobe), instead of going back to warm white (2026-11-22 video: hard white flash, then warm white)
  - [ ] (must) Switchboard's Beat lights switch, with Movie DreamView running: within a few seconds the Govee lights strobe to Nuvio's music, without pressing All off first (2026-11-22: DreamView stayed in charge until All off)
  - [ ] Switchboard's Movie while beat lights run: beat lights switch off and Movie DreamView takes over
  - [ ] Govee: pausing the music (or turning beat lights off) hands the light back to what it was doing before
  - [ ] Govee: a light left unticked (the Sync Box's for Movie) is not touched
  - [ ] Flash style Dim: still drops to the resting brightness between kicks (as before)
- [ ] (must) Flash early by: after setting it once by eye, the flashes land on the kick, not after it (note the value that works)
- [ ] The bulb stops flashing when the music is paused, and starts again on play
- [ ] Seeking ahead or back in a song: no burst of flashes, and it carries on in time from the new place
- [ ] Flash on Beat one: one flash per bar, on beat one (try a song where the Stage's Beat Grid has the bar line right)
- [ ] Flash on Every beat: one flash per beat, steady through a breakdown with no drums
- [ ] The next song (also when it follows on gaplessly) flashes in time too
- [ ] Turning Flash a light on the beat off stops the flashing; the setting and address are still there after restarting Nuvio

## Live TV

- [x] Stalker portals log in, load channels and play (2026-11-08)

## Live TV: Decide for me (Nov 13)

Settings > Live TV > Decide for me (needs On Now). Four sections appear under Films: Three things, I have 25 minutes, Earlier today, Following.

- [ ] Three things shows three cards the same size: SAFE, STARTS SOON, WILDCARD, each with its channel; a card with nothing says so in words instead of vanishing
- [ ] SAFE is something on now from the channels you usually watch; STARTS SOON begins within half an hour; WILDCARD is a film from outside those; no two cards share a channel
- [ ] The Reshuffle tile changes the cards once; after that it says "used tonight" and does nothing until tomorrow (after 05:00)
- [ ] OK on a card goes to the channel (or its page, when it has not started)
- [ ] "I have N minutes" has five choices (15, 25, 45, 60, 90); OK on one selects it, the sidebar label follows, and the three rows change
- [ ] Nothing in the three rows runs over the minutes, except a dimmed tile that says how many minutes over (at most 10)
- [ ] Earlier today lists finished programmes from your lineup or usual channels that the provider still has, films and followed first; OK opens a page with Start from the beginning
- [ ] Earlier today explains itself when the provider keeps no catch-up
- [ ] On a programme's page "Follow ..." adds it; the Following section then lists the channels that have it next, soonest first, and a lineup channel before others
- [ ] A followed thing that is not in the guide is shown dimmed with "Not in the guide yet"; "Stop following" on a page removes it
- [ ] Everything in these sections works with arrows, OK and Back, and the mouse
- [ ] With Decide for me off none of these appear

## Live TV: Starting soon (Nov 13)

Settings > Live TV > Starting soon (needs On Now). It needs a playlist with a programme guide.

- [ ] Live TV's sidebar has "Starting soon" under Films, with a count; it opens three titled groups: Within 15 minutes, 15 to 30 minutes, 30 to 60 minutes
- [ ] Every tile is something that has not started yet, shows "in N min" at the top and its start and end below; nothing already on is there
- [ ] With Your 40 on, only the lineup's channels appear; with it off, any channel with a guide can
- [ ] OK on a tile opens its page with "Remind me when it starts" and "Switch to it at HH:MM" above "Go to the channel"
- [ ] After Remind me, the same page offers "Cancel the reminder" instead; cancelling brings the two choices back
- [ ] Two minutes before the start a glass card (bottom left, fixed size) says the programme and "BBC One · starts in 2 min" and goes after about 20 seconds
- [ ] While a channel is playing, the same text shows as a pill on the player instead
- [ ] "Switch to it" puts the channel on by itself at the start, from the Live TV tab and while something else is playing, and the card says "Switching to ..."
- [ ] Quit Nuvio with a reminder set and start it again before the time: the reminder still fires
- [ ] Start Nuvio after a reminder's time has passed by more than ten minutes: nothing fires
- [ ] A coming-up sport tile inside the hour shows the countdown at the top
- [ ] With Starting soon off, there is no section, no reminder actions and the sport tiles show clock times as before

## Live TV: Your 40 (Nov 13)

Settings > Live TV > Your 40 (off by default). Turn it on, open Live TV.

- [ ] Live TV opens on "Your 40", a numbered list: the channels you watch most and starred first, then channels from what the triage kept; the numbers run 1, 2, 3 from the top
- [ ] Leave and come back (or restart Nuvio): the lineup and its numbers are exactly as they were, nothing reshuffles itself
- [ ] Right from a channel's star reaches a "..." button; OK opens a card with Move up / Move down / Take out of Your 40 / Never again (no Move up on number 1, no Move down on the last)
- [ ] Move up and Move down swap the channel's number and the highlight stays on it
- [ ] "Take out of Your 40" removes it from the lineup only: it is still in All channels and its category
- [ ] On a channel in All channels or a category, the "..." offers "Add to Your 40" (not when the lineup is full at 40), and the channel gets the next number at the end
- [ ] "Never again" removes the channel from the lineup, the favourites, the On Now wall and every list; typing its name in the search box still finds it
- [ ] Settings > Live TV > "Bring back channels thrown out" shows them again; they are not back in the lineup
- [ ] In the player, typing 1, 2, 3 on the remote or keyboard switches to lineup channels 1, 2, 3 (not the provider's numbers); channel up/down from the lineup steps through it in order
- [ ] A channel with several copies (FHD, HD, SD) is one row in the lineup and plays the next copy when one fails
- [ ] Back and Escape close the "..." card; the mouse works too (click the "...", click an action)
- [ ] With Your 40 off, Live TV looks and numbers channels as before

## Live TV: Triage (Nov 13)

The sliders button in the Live TV header, beside Refresh.

- [ ] The Triage button opens "Which countries do you watch?": a grid of countries with flags and channel counts, biggest first, "No country marked" last, between two equal side panels (So far, What is left)
- [ ] Ticking countries moves the big number on the right ("1,280 of 12,014 channels") and the list on the left; Next is dimmed until one is ticked
- [ ] The second question lists only kinds your countries have, with sensible ones already ticked (not adult, shopping, religious, radio or 24/7 loops), and the count on the right follows
- [ ] The kinds are right for your playlist: no sport category filed under Entertainment and so on; if any are wrong, say which category names
- [ ] "Your first ten" offers channels from what is left, the ones you watch most first; ticking stars them (ten at most) and they are in Favourites afterwards
- [ ] After Done the sidebar has only the kept categories, All channels counts only those, and with On Now on the wall only draws from them
- [ ] Typing in the search box under All channels still finds a channel the triage left out, and it plays
- [ ] Favourites and Recently watched still show channels from countries or kinds you left out
- [ ] The whole thing works with arrows and OK (and the pad): the first press shows the highlight, Down from the last row reaches the buttons, Back goes to the previous question and leaves from the first without saving
- [ ] The Triage button is lit while a triage is in force; opening it again starts from your answers, and "Show everything" brings every category back
- [ ] The answers are still there after restarting Nuvio

## Live TV: On Now (Nov 13)

Settings > Live TV > On Now (off by default). Best tried in the evening with a match you follow under Live scores.

- [ ] OK on a film opens its page (picture, blurb, "Started 18:30 · 71 min in · ends 20:15"); Watch live plays the channel; Back (Esc or the pad's B) returns to the wall, not out of Live TV
- [ ] Start over: on a channel your provider records, the film page has "Start from the beginning" and it plays the film from its start, not live
- [ ] Start over: left running to the end, the recording carries on into the live channel instead of stopping or starting again
- [ ] Start over: the "Earlier on <channel>" lines on the page play that finished programme
- [ ] Start over: on a channel without catch-up the page says it can only be joined live and has no Start from the beginning
- [ ] All channels and the categories: a channel your playlist lists several times (FHD / HD / SD / backup) is one row marked like "FHD · 4 sources"; +1 channels and 4K copies are still their own rows
- [ ] No two different channels have been merged into one row; if any have, say which names
- [ ] Starring a merged row stars it once (Favourites shows one row); unstarring removes it
- [ ] A merged channel whose first copy is dead still plays: it moves to the next copy by itself within a few seconds (nuvio.log says "trying")
- [ ] Channel up/down in the player steps through merged channels (BBC One, then BBC Two), not through the copies of one

## Saving settings (hardening, Nov 10)

- [ ] Change a setting (e.g. a Music setting), then close Nuvio with the X and reopen it: the change is still there (stores now force their data to disk before the swap)
- [ ] After a normal day of use, nothing about settings, library, play history or music facts has reset on its own; if something has, say which (a zero-length store now falls back to its `.bak`, and the log says "is empty; recovering")
- [ ] Phone remote after the connection limit: the phone still pairs, reconnects after the screen was off, and a second phone can connect at the same time (the PC now refuses more than 16 open connections and gives a new one 30 s to pair)

## Artist page and Previously on (Nov 10)

- [ ] Artist page, Essentials: an artist whose YouTube Music list is short (Gang Starr had 5) now shows up to 10 songs; the extra ones come after YouTube Music's own, are real songs you can play, and Play Essentials plays them too
- [ ] Artist page, Essentials: an artist with a full list of 10 looks and plays exactly as before
- [ ] Previously on: pick up a half-watched episode of a show (Continue Watching or the phone's Resume) after more than your break (3 days by default): the "Previously on" cards show before it carries on from where you stopped
- [ ] Previously on: pick up a half-watched episode you left a few hours ago: no cards, it just resumes (with "Only after a break" off, every resume gets the cards)
- [~] Artist page, Story tab (third tab beside Essentials and Discography): a timeline of a few points (DEBUT, FIRST ALBUM, BEST KNOWN, COMEBACK, LATEST), each with the year, a line saying why, and the record from then; OK on a row opens that record; More menu works on a row (2026-11-10): works, but the years did not make sense (Jamiroquai: rows evenly spaced, "back after 7 years" from an album not on the line, 16 albums in the heading vs 17 on the right, a 2025 single with no cover as LATEST)
  - [ ] Re-check (Jamiroquai): a year line above the rows runs 1992 to 2017 to scale, a tick for each of the 8 studio albums, a dot per point, the decades marked, and the 2010 to 2017 gap dashed with "7-year break" over it
  - [ ] Re-check: the heading reads "8 studio albums · ... · 1992–2025" and the right panel's tile says "Studio albums 8" (reissues, deluxe editions, best-ofs and live albums no longer count)
  - [ ] Re-check: COMEBACK reads "Back 7 years after Rock Dust Light Star (2010)"
  - [ ] Re-check: LATEST is never a release with no cover that plays nowhere (Jamiroquai's 2025 single is gone; Automaton is the last point)
- [ ] Artist page, Story tab: nothing moves or resizes between artists (every row the same height, the record slot the same width, long titles cut); an artist with almost no dated releases says so instead of showing an empty panel

## Profiles and the phone app (Nov 10)

- [ ] Set something different in two profiles (Settings, Gamepad dead zone; Games, backdrop style; Playback, anime filler badges), then switch profile from the profile picker: each profile shows its own values straight away, without restarting Nuvio
- [ ] Phone remote: pairing still works after the update (scan the QR or enter the address and PIN), and it reconnects the next day without asking again
- [ ] Home, the movie/show page and the album page (JIT flag for the biggest screens): after about a minute of use, scrolling Home and opening a title or an album feels the same or smoother than before; say if Home hitches in its first minute, or Task Manager shows Nuvio's CPU staying high after it has settled

## Settings sidebar groups (Nov 24)

- [ ] (must) Open Settings on the PC: the sidebar is split under Sources, Home and look, Playback, Home theatre, More to watch, Account and System, and every page is still there
- [ ] With a controller, move down the sidebar from top to bottom: focus skips the headings and lands on every entry in turn
- [ ] Hide a whole group's entries (Categories > untick them): its heading goes too and no gap is left
- [ ] Long-press and drag one entry into another group: the headings disappear; drag it back and they return
- [ ] If the sidebar was reordered before this build, it keeps that order (no headings until each group is back together)

## Settings search

- [ ] Search "subtitle size", "outline", "blur" and "vertical offset": each result opens Player settings at the Subtitle rendering section
- [ ] Search "failover", "player ui scale", "controls layout", "seek bar handle": each lands on its section of Player or Streams settings
- [ ] Search "tvdb", "hero artwork", "proxy address", "links to prepare", "minimum score": each opens the right page at the right section
- [ ] Search "music addon link", "daily allowance", "export facts", "dim to", "theme song volume": each opens its page at the right section
- [ ] Search "mpv options", "low vram", "audio passthrough", "close to notification area": each opens its page (desktop only)

## Tonight picker (phases 0 and 1, Nov 10)

Turn it on in Settings > Random Play > Tonight picker > "Open on Tonight".

- [ ] **BROKEN** Back from Three Cards (2026-11-10): Esc on the keyboard did nothing (the Android remote's Back works); fixed the same evening (Back now acts on the key coming up)
  - [ ] (must) Re-check: the remote's Back (and a pad's B, the phone remote's Back, the mouse's back button) leaves every Tonight mode: cards and questions step back, the modes return to the launcher; on the launcher Back does what it did before
- [ ] Typing "Tonight" or "bedtime" in the Settings search finds the Tonight picker rows and opens Random Play at that section (also listed first in the Random Play page's sidebar)
- [ ] With the switch on, Nuvio opens on "How shall we choose tonight?" instead of the Home rows; with it off, Home is as before
- [ ] The launcher's eight tiles keep their size; the six unbuilt ones are dimmed with "COMING SOON", and OK on one says it is coming later
- [ ] The tile used last time has "LAST TIME" on it and is selected when the launcher opens
- [ ] "Just browse everything" goes to the normal Home rows; pressing Home again brings the picker back
- [ ] Up from the tiles reaches the bedtime chip; Left/Right on it move bedtime by 15 minutes, and the Bedtime slider in Settings matches
- [ ] Three Questions: the first question is always "A film, or a show?"; Left and Right answer at once, Down means "Either is fine", Back goes back a question
- [ ] The two questions after it differ from one night to the next, and a question is never one where every title would give the same answer
- [ ] The "214 → 41 titles left" count and the SO FAR chips follow the answers
- [ ] After the questions, three cards: SAFE (something you're watching or have seen), NEW TO YOU, WILDCARD; never the same title twice
- [ ] Each card shows the right poster and a sensible line under it (year, length, "ends 23:06" for films; "Next: S2 E4 · 30 min eps · 3 before bed" for shows)
- [ ] No film that would end well after bedtime is dealt (with bedtime still at least 20 minutes away)
- [ ] OK on a card plays it (a film from where you left it, a show's next episode); Down opens its page instead
- [ ] Reshuffle deals three different titles once; after that it says "No reshuffles left tonight" until tomorrow evening
- [ ] Three Cards straight from the launcher deals from everything that fits before bed
- [ ] Titles marked "Not interested" in Discover never appear
- [ ] A mouse works too: hovering a tile or card selects it, clicking plays or opens

### Tonight picker: the What to watch button and a way out (Nov 10 evening)

- [ ] A "What to watch" button (sparkle icon) sits beside Search in the top bar and the sidebar; it opens the Tonight launcher over Home, Library or Discover (from Music or Live TV it goes to Home first)
- [ ] Every Tonight screen has an ✕ in the top-right corner; clicking it closes Tonight and the rows behind come straight back
  - Nov 12: the ✕ is gone (user: "Just browse everything" is the way out); nothing to check here now
- [ ] Back on the launcher closes Tonight (Back inside a mode still steps back first)
- [ ] With "Open on Tonight" off, Nuvio opens on Home; with it on, Nuvio opens on Tonight once at start, and after closing it stays closed until the button is pressed
- [ ] The button can be moved in Settings > Appearance > Navigation order ("What to watch")
- [ ] Phone remote: a "What to watch?" button above the pad on the Remote tab opens Tonight on the TV, and the phone switches to the Tonight mirror

### Tonight picker: the launcher redesign with backdrops (Nov 12)

- [ ] Each of the eight launcher tiles shows a film or show backdrop, no icons, with the mode's name, its line and an arrow at the bottom left and LAST TIME at the top left; the eight backdrops are all different titles
- [ ] The backdrops fit the mode: The Pile shows something in your library you have never started, Ends By something that ends before bed
- [ ] The chosen tile glows all round (no bright border) in its own backdrop's colour: a green backdrop glows green; the glow fades as you move between tiles and lies over the gaps, not under the next tile
- [ ] The screen behind is a colour wash (no picture) of the chosen tile's backdrop colours, easing from one to the next; the header text stays readable over any colour; nothing on the screen moves or changes size
- [ ] Tonight fills the whole screen from the very top (no strip left empty for the top bar), and there is no ✕ on any Tonight screen
- [ ] The header reads "How shall we choose tonight?" ("tonight?" tinted) over "A little inspiration goes a long way.", with the day and time above it
- [ ] Before the titles have loaded (first open after start) the tiles are plain glass and fill in with backdrops when they arrive
- [ ] Under the key hint is a thin separator, and "Just browse everything →" (a rounded pill) sits straight beneath it rather than at the foot of the screen; Down from the tiles reaches it and OK closes Tonight
- [ ] The tiles' rounded corners are smooth, not stair-stepped, on every tile (with and without a picture)

### Tonight picker: the modes redesigned (Nov 12)

Every mode below has a "← Back" button in a strip under its content (hint line, separator, buttons), as the launcher does.

- [ ] Three Questions: the background is a colour wash; hovering an answer glows it in its colour (amber left, teal right) and shifts the wash toward what that answer would leave
- [ ] Three Questions: the two answer boxes mirror each other: "◀ LEFT" top left and "RIGHT ▶" top right, the left box's answer right-aligned and the right box's left-aligned; "Exciting" fits in its box
- [ ] Three Cards: the cards sit higher, the chosen card glows in its poster's colour (no accent border) and the background takes its colours
- [ ] Three Cards: Back, Its page and Reshuffle sit in a row under the cards; Down from a card reaches them (on Its page), Left/Right move along them, Up goes back to the card, OK presses one
- [ ] Knockout: the bracket on the left is small posters, eight down the first column, then four, two and the winner; the pair in play glows, losers fade back, unplayed slots are empty glass
- [ ] Knockout: the background mixes the two posters' colours; hovering a poster glows it; Undo last pick and Taste each work from the buttons
- [ ] Ends By: the rows fill the page down to the buttons (more than six on a big screen), the mouse wheel moves up and down the list, and the last hour label and BED are never cut off at the right
- [ ] Ends By: the chosen row glows in its title's colour and the background takes its colours; Bed earlier / Bed later / Play it buttons work
- [ ] Channel Flick: a "← Back" button sits at the left of the buttons and returns to the launcher
- [ ] Coin Toss: the poster and its panel are centred as one block, the poster glows, the background takes its colours, and the buttons (with Back) are in the strip below
- [ ] The Pile: a centred 3 x 3 grid of posters, each with its name and how long it has waited underneath; arrows and the wheel move (a row at a time with the wheel); the chosen poster glows and the background takes its colours
- [ ] Three Cards: the chosen card's glow hugs its rounded corners, with no faint square line round the poster at the corners
- [ ] Three Cards: SAFE / NEW TO YOU / WILD CARD are headings above each poster (brighter on the chosen one), not printed on the picture; the row of cards and the buttons below do not move
- [ ] Knockout: the bracket's posters have no glow; the pair in play has a thin edge in its own colour instead, and neighbouring posters never overlap
- [ ] Three Cards: moving from one card to the next, the old card's bloom fades all round, with no green or blue arcs left at its corners
- [ ] Three Cards: the button under the cards reads "Detail page" (Coin Toss's hint says "▼ opens the detail page")
- [ ] Knockout: the heading and its line sit where Three Cards' do, the two posters and "vs" are centred on the screen at the same height as Three Cards' cards, the buttons sit straight under them, and the bracket fills the left without touching the posters
- [ ] Settings > Random Play > Tonight picker: "Cards pick from the top" slides from 1 to 20 (5 to start); at 1 the same three cards come up every visit, at 15 or more they change from night to night
- [ ] Settings > Random Play > Tonight picker: turning on "Add Top picks for you", "Add Because you watched" and "Add More like your favourites" brings those Discover titles into Three Cards (a New to you card can say "Because you watched ..." or "A top pick for you"); all three are off to start
- [ ] With a Discover source on, Channel Flick shows the Discover reason ("BECAUSE YOU WATCHED ...") over those titles; Settings search finds the new rows by "top picks" and "pick from"
- [ ] The Pile: a small panel at the top right shows the chosen title's wait, name, length and genres; Down from the bottom row reaches Back / Finally watch it / Let it go, and Let it go needs pressing twice

### Tonight picker: Coin Toss and Knockout (phase 2, Nov 10)

- [ ] The launcher's Knockout and Coin Toss tiles are no longer "coming soon" and open their modes
- [ ] Coin Toss: one title with its poster, name and "ends 23:06" line, and a ring counting down from 10; doing nothing plays it
- [ ] Left tosses again with a new title and a fresh countdown; a vetoed title never comes back that evening
- [ ] The "What your no's said" panel lists the last three vetoes with a reason ("too long", "too heavy"...) and a lean ("leaning shorter and lighter"), and the next tosses follow it
- [ ] Up holds the countdown (the ring greys and shows II) and Up again restarts it; OK plays at once; Down opens the title's page
- [ ] Knockout: eight titles (fewer only if fewer fit before bed) in a bracket on the left; two at a time in the middle, with the pair highlighted in the bracket
- [ ] Left or Right keeps one; losers are struck through; three rounds, the last called "THE FINAL"
- [ ] Back undoes the last pick (Back with nothing picked goes to the launcher)
- [ ] The winner shows with a five-second ring and then plays; OK plays at once, Back reopens the final
- [ ] Clicking a poster with the mouse keeps it in Knockout
- [ ] **BROKEN** (2026-11-11): the winner (The White Lotus) opened "Failed to load: could not load details from any addon", though it opens fine from Home
  - [ ] (must) Re-check: a trending winner (one not in your library) plays; the same for Three Cards, Coin Toss, Channel Flick, Ends By and The Map, and for Down = details

### Tonight picker: Channel Flick and trailer tastes (phase 3, Nov 10)

- [ ] The Channel Flick tile opens trailers one after another in a large frame, with sound at the hero trailers' volume; music pauses while they play
- [ ] Under the frame: where the title comes from (FROM YOUR LIBRARY, TRENDING THIS WEEK, YOU'RE ON S2 E4), its name and "ends 23:06"; the frame and the lines under it never move
- [ ] When a trailer ends the next channel starts by itself; a title with no trailer says so briefly and is skipped
  - [ ] **BROKEN** (2026-11-12): every channel stuck on "Finding the trailer…"; YouTube answered every trailer lookup "Sign in to confirm you're not a bot"
  - [ ] (must) Re-check after the fix (signed-in fallback, ed0e022): Channel Flick plays trailers again while YouTube is asking for a sign-in; `nuvio.log` shows `TrailerSignedIn … trailer opened signed in` for the ones it rescued
  - [ ] The same while YouTube is asking for a sign-in: a new Home hero trailer, a details page trailer and the pre-show trailers play
- [ ] Right flicks to the next channel; the three UP NEXT pictures in the top corner match what comes next
- [ ] Left says "won't come up again" and the title is gone from Tonight and from Discover's rows (it is in Not interested)
- [ ] Up saves the title to your library ("saved for later") and moves on; OK plays the title
- [ ] Keys still work while a trailer plays (the trailer never takes the remote's focus)
- [ ] Knockout: Down plays a 20-second taste of the left title, Down again (or the end) the right one, then back to the posters; Left/Right still keep one while tasting; Back stops the tastes
- [ ] Three Cards: holding OK on a card plays its trailer in place of the cards; a quick OK still plays the title; Back returns to the cards

### Tonight picker: Ends By and The Pile (phase 4, Nov 10)

- [ ] Ends By opens on "It's 21:10. Bed at 23:30." with hour marks, a white NOW line and a BED line
- [ ] Films are bars from now ending in "ends 22:52"; shows are a block per episode that fits ("3 eps · 22:40") with one faded block past bed
- [ ] Rows that fit come first (the one that fills the evening best on top); ones that run past bedtime are faded at the bottom
- [ ] Up/Down move through the rows (six at a time; the list moves, rows never resize); Left/Right move bedtime and the bars and order update; OK plays the row
- [ ] The Pile lists library titles you have never started, newest on top, each spine with "added 3 days ago" ... "added 8 months ago"; older spines are dustier and fainter
- [ ] The chosen spine slides out and the panel shows how long it has waited, its poster, length and genres
- [ ] OK plays it; Left once asks "again to let it go", Left again removes it from the pile and from Discover's suggestions (Not interested)
- [ ] With nothing on the pile it says so instead of showing an empty panel

### Tonight picker: The Map and the AI (phase 5, Nov 10)

- [ ] The Map tile opens a field with no rows: LIGHT left, HEAVY right, SOMETHING NEW top, COMFORT bottom, and a ring (the lens)
- [ ] The lens starts over the kind of thing you keep; the arrows move it; titles near it grow and show their posters, the rest stay small and dim
- [ ] The side panel shows the title nearest the lens (picture, name, "ends 23:06", "Light, and new to you", up to two nearby titles) and its lines never move
- [ ] Light things (comedies, family) sit left and heavy ones (war, drama) right; things you're watching sit low, trending ones you've never seen high
- [ ] OK plays the title under the lens; clicking a small tile moves the lens to it, clicking the one under the lens plays it
- [ ] The bedtime chip at the top says "Ends before 23:30" and nothing on the field runs past it
- [ ] Settings > Random Play > Tonight picker > "Let the AI refine The Map": turning it on asks for AI consent if not given yet
- [ ] With it on and The Map open, the panel's footer counts titles "placed with the AI's reading of their tone" and some tiles shift sideways; a dark comedy moves right of a family comedy
- [ ] With it off, nothing is sent to the AI (no "Tonight map" lines in the log)


## Film player (from upstream, Nov 11)

- [ ] Subtitles carry over to the next episode: play an episode with your preferred subtitle on, let it go to the next one (or start the next from the list), and the same subtitle comes on by itself

## Live scores (Nov 11)

Settings > Live TV > Live scores (off by default). Best tried while Man Utd or a followed competition is playing.

- [ ] Settings > Live TV has a "Live scores" section with "Live score pop-ups" off; Settings search finds it by typing "live score"
- [ ] Turn it on, type "Man Utd" under "Add a favourite team": Manchester United is offered; Add puts it in the list with Remove
- [ ] "Follow: Football / Rugby / Fight nights" start empty; ticking Premier League and Six Nations keeps them after closing and reopening Nuvio
- [ ] "Show a sample" shows the card for the chosen look, with the chime (favourites only by default)
- [ ] Look: Corner card sits bottom right, Top ticker runs across the top, Big flash fills the centre for a favourite; none of them changes size with long names
- [ ] A Man Utd match: kick-off, each goal (with the scorer; "pen" or "own goal" when it is one), half-time and full-time pop up within about 20 seconds of the TV coverage, and nothing pops for what had already happened when Nuvio was opened mid-match
- [ ] A red card, a missed penalty and a VAR decision ("Goal ruled out") in a Man Utd match pop up
- [ ] A followed competition's match that is not a favourite pops up too, as the corner card (no big flash, no chime by default)
- [ ] Over a playing film, an episode and a Live TV channel the card shows over the video (not hidden behind it), then fades away; the remote still controls the player while it is up
- [ ] On the music stage and in menus the card shows and does not move anything on screen
- [ ] Rugby: a followed match's tries, penalty goals and yellow or red cards pop up; conversions do not pop on their own
- [ ] Fight nights (UFC, boxing): a card when the night starts and one with the main event's result
- [ ] "Pop up for": unticking a group (for example Red cards) stops those cards
- [ ] Phone remote: with "Notify the phone remote" on and the app connected, a Man Utd goal arrives as a notification ("GOAL · Man United 1 – 0 …"); the next event replaces it rather than stacking. On Android 13+ the app asked to allow notifications on first start
- [ ] The Status row says what is being watched ("Watching 1 live match", "No followed matches today")
- [ ] Optional: with an API-Football key saved, nothing changes while ESPN works (nuvio.log tag LiveScores shows no api-sports calls)

## AIOStreams and AIOMetadata inside Nuvio (Nov 11)

- [x] Settings > Addons shows "AIOStreams and AIOMetadata on this PC" with the switch off; the Nightly includes the servers (the switch is not greyed out with "This build does not include the servers") (2026-11-11)
- [x] Turning the switch on: within about a minute AIOStreams, AIOMetadata and Cache (Redis) all say "Running at http://127.0.0.1:…" (2026-11-11)
- [x] Windows Firewall does not ask about node.exe or redis-server.exe (2026-11-11)
- [x] "Open configure page" on AIOStreams opens its configure page in the browser; add TorBox there, save, and copy the manifest address it gives (2026-11-11)
- [x] Paste that address into "Install an addon address" and press Install: it says "Installed …" and the addon is in the Addons list; a film's sources come from it (2026-11-11)
- [x] Enter a TMDB key (and TVDB if you have one) and press "Save and restart": both servers restart and come back Running (2026-11-11) (TMDB; the TVDB key was rejected as invalid, left for now)
  - [x] TMDB key: the AIOStreams configure page said "Failed to validate TMDB API Key/Access Token ... 401" on Create (2026-11-11). Nuvio now tells the 32-character API key from the long Read Access Token (starts eyJ) and passes each where it works; with the API key in Nuvio and nothing in the configure page's Metadata TMDB fields, Create succeeds (2026-11-11)
- [x] "Open configure page" on AIOMetadata opens its page; set up catalogues, save, install its address the same way; Home shows its catalogues and details pages fill (2026-11-11)
- [ ] Close Nuvio: Task Manager shows no node.exe or redis-server.exe left running; start Nuvio again and the servers come back on their own, with the same addresses (installed addons still work)
  - [ ] **BROKEN** (2026-11-11): on start the two local addons showed "Unavailable, Failed to connect to /127.0.0.1:4734x" until refreshed by hand, because Nuvio loads its addons before the servers answer. Re-check: start Nuvio and leave Settings > Addons alone; within about half a minute both show as loaded and Home has their rows (nuvio.log has "Server on port … is ready: loading its addon again")
    - [~] (2026-11-11): AIOStreams came back by itself; AIOMetadata's reload got "Request failed with HTTP 503" (it answered /health/live while still initialising) and needed a manual refresh. Re-check: after a start both load without touching them
      - [ ] (must) Re-check after the /health/ready fix: start Nuvio and leave Settings > Addons alone; AIOStreams and AIOMetadata both load by themselves, with no manual refresh
- [ ] Memory: with both running for an evening, Task Manager's node.exe processes stay around 1.3 GB in total, and Nuvio stays smooth
- [ ] An in-app update while the servers are on installs cleanly (no "file in use" error)
- [ ] Turning the switch off stops all three (no node.exe or redis-server.exe left)
- [ ] PC build (D:\NuvioBuild\build.cmd, Nov 11): after a build the setting no longer says "This build does not include the servers"; the first build after a bundle change also builds the bundle (10 to 20 minutes, "Local addons: bundle ready" in build.log), later ones say "Local addons: bundle up to date" and keep the servers

## Top bar search predictions (from upstream, Nov 11)

- [ ] Typing in the top bar's Search (films and shows): the panel under the box swaps the recent searches for predictions, recent searches that match first (clock icon), then TMDB titles (film icon), with the typed part in bold
- [ ] Enter with nothing highlighted searches exactly what was typed
- [ ] Down/Up walk the predictions and put each one in the box; Up from the top one gives back what was typed; Enter runs the one shown
- [ ] Tab puts the top prediction in the box without searching; Tab again moves to the next and wraps round; Tab never jumps to Discover from the search box
- [ ] Mouse: hovering a prediction lights it; clicking runs it; "Clear history" shows only under the recent searches (empty box)
- [ ] Escape hides the panel first (and gives back the typed text if you were walking it); a second Escape leaves the search
- [ ] The same panel works on the Music tab (music note, YouTube Music completions), YouTube tab (YouTube autocomplete) and Live TV (channel names)
- [ ] The search box no longer has the Discover (compass) button; Tab outside search still opens Discover

## Music search rows, seek bar and strings (from upstream, Nov 12)

The "Music search" lines are upstream's search, which this fork shows only while Settings > Music > New music home is **off** (with it on, the music home searches as before). Upstream's album page, artist page and Stage changes of Nov 11-12 were not taken (see CHANGELOG, Upstream merge of Nov 13), so their checks are not listed here.

- [ ] Seek bar (Stage, dock, mini player): a drag or click lands where you let go, also after the song has changed a few times
- [ ] Music search waits for Enter, like the films and shows search: typing shows predictions only, the Library stays until Enter; a voice search or a genre chip on the Stage still searches straight away; clearing the box goes back to the Library
- [ ] Music search hero (TV Mode / Adaptive): more results have a description, each its own: an artist shows their story (saved artist's, else TheAudioDB, else Wikipedia); an album or single its own (TheAudioDB, else its Wikipedia page); a song or video the song's own; none of them falls back to the artist's story
- [ ] Music search hero: an artist result shows their name logo in the logo slot like a film's (when one exists); songs and albums keep artwork with the name under it
- [ ] TV Mode hero on Home: the logo and description sit a little higher and the description's last lines are no longer hidden under the first shelf row
- [ ] Album page: with a white or very bright cover, the left side stays dark enough that the left panel and its text read clearly
- [ ] Settings > Music > Word-by-word lyrics: descriptions read "the song's title" with no backslashes (also the Gemini and Last.fm key descriptions)
- [ ] Settings > Playback: the custom shader paths and custom mpv options hints show "e.g." and each example on its own line
- [ ] The P2P consent text (first P2P stream) shows its paragraphs and bullet points on separate lines
- [ ] French and the other languages: no stray backslash before apostrophes or quotes
- [ ] Settings > Music > Music facts: the daily token limit is a dropdown (50k to 600k), like the app's other choices
- [ ] Music search in TV Mode: the logo and description sit clearly higher above the shelf, and a long description shows up to about nine lines before it starts scrolling
- [ ] TV Mode hero on Home is back where it was (the column a little lower, short synopses no longer look stranded); music search keeps its higher column
- [ ] Music search hero, songs: several songs by one artist each get a different artist photo behind them (when the artist has more than one); a song with no artist photo gets a gradient of its cover's colours, not a small picture blown up
- [ ] Music search hero, albums and singles: a large cover (1000 px or more) stands whole and uncropped at the right of the hero, full height, its left edge fading into a gradient of its own colours behind the text; a smaller cover gives the gradient alone
- [ ] Music search hero, albums: when TheAudioDB has disc art, the disc peeks out to the right of the cover
- [ ] Music search hero, facts line under the genres: a song shows e.g. "2013 · 128 BPM · No. 3 of CHVRCHES's most played · Explicit"; an album e.g. "20 Sep 2013 · Virgin · 12 tracks · 49 min · ★ 8.4"; a live or remix result is not given the studio version's facts
- [ ] Music search hero, videos: the picture beside the name is the video's own large thumbnail
- [ ] Music search: browse quickly through many results; descriptions, facts and photos keep filling in (no stretch where everything comes back empty); people-panel photos may fill in a little later when browsing fast
- [ ] Music search hero, album gradients: brighter than before, still dark enough for the text
- [ ] Music search hero, disc: shows for more than the first result: albums with "(Deluxe)" style titles, and songs (their album's disc), when TheAudioDB or fanart.tv has disc art
- [ ] Music search guesses what you want: "Eminem" puts Artists first; "Eminem without me" puts Songs first with that song leading the row; "Eminem encore" puts Albums first with Encore leading; an ordinary search keeps the usual Songs, Albums, EPs, Singles, Artists, YouTube order
- [ ] "Against the current gravity": the Gravity (The Acoustic Sessions Volume II) record shows in the EPs row (YouTube Music calls it a single) and EPs come first, ahead of the song Gravity; a release YouTube Music calls a single but your library (saved artist) knows as an EP is always in EPs
- [ ] When an EP and a song (or single) share the search's title, EPs come first; an album of that name still beats them
- [ ] Music search rows put well-known artists ahead of near-unknown uploads among equally good matches: "love is dead" shows CHVRCHES' album before Gofun's (7 fans); rows don't reshuffle after they appear (they may take up to about 1.5 s longer to show)
- [ ] Music search: move quickly off an album before its details load, then come back: it fills in (description, genres, facts) instead of staying with the plain cover and no words (Kerli's Love Is Dead in "love is dead" was one)
- [ ] Artwork that was half grey (Momento's Love Is Dead (Extended Mix) and the other song tiles in "love is dead") shows whole after its next load, in the row and in the hero; posters and other artwork across the app are no longer kept when a download is cut short

## Live TV: Brakes (Nov 13)

Settings > Live TV > Brakes (off by default).
- [ ] With Brakes on, the switch's text shows a week line ("0 min watching, 0 channel changes, 0 programmes seen to the end"); after an evening of watching it counts up
- [ ] On a channel, press channel up five times quickly: a glass card "That's a lot of flicking" appears over the video with Keep flicking and Turn Live TV off; arrows and Enter pick, Escape dismisses
- [ ] Keep flicking: the card goes and does not come back that night, however much you flick
- [ ] Turn Live TV off leaves the player
- [ ] Stay on one programme for 15 minutes or more: at its end a card shows its title with Stop here (focused) and One more; Stop here leaves the player, One more carries on
- [ ] Flicking past channels quickly never shows the end card
- [ ] With Brakes off none of this appears and the week line is not shown as counting

## Live TV: Running order and Just ask (Nov 13)

Settings > Live TV > Running order and Just ask (needs On Now). Two sidebar items appear: Tonight's running order, Just ask.
- [ ] Tonight's running order shows up to six numbered programmes with start times, in order, and the bedtime from What to watch in its heading
- [ ] None of them runs past bedtime; with a very early bedtime the screen says nothing fits
- [ ] Approve: the button becomes "Approved"; Starting soon's reminders list does not need to be on, and at the first slot's start Nuvio switches to its channel by itself, and again at each later start
- [ ] Selecting Approved cancels them; no switching happens
- [ ] Just ask with an empty search box tells you what to type
- [ ] Type "something funny, half an hour": one answer tile marked THE ANSWER, a "Why this" tile and Not that
- [ ] Not that shows a different answer; after the last it wraps round to the first; editing the text starts again at the first
- [ ] "a film, an hour" offers a film that fits; "any bond film" finds a title with bond in it, if one is on
- [ ] A line nothing fits says "Nothing fits" and no button
- [ ] With the switch off neither item appears

## Live TV: Overnight weeding (Nov 13)

Settings > Live TV > Overnight weeding. It works only between 02:00 and 05:00 with the app running and nothing playing, so these need a night.
- [ ] Leave Nuvio open overnight with the switch on and Your 40 set up; in the morning Settings > Overnight weeding says "Now marked dead: 0" if all your channels work
- [ ] The log of the night (Windows build log) shows no flood: one channel at a time, a few seconds apart
- [ ] A channel you know is dead (change its stream in the provider, or add a broken test channel to the lineup) is still in Your 40 after one and two nights, and gone after the third; "Now marked dead" counts it
- [ ] "Bring back channels thrown out" returns it
- [ ] Starting a channel in the night stops the weeding until the next quiet check
- [ ] With the switch off nothing is opened at night

## Live TV: Stage (Nov 14)

Settings > Live TV > Stage. Live TV opens on Three things with a strip of stages under the heading.
- [ ] Every stage has the centred heading, the strip, and either two equal side panels round a centre or three equal columns; nothing changes size as you move or as the guide loads
- [ ] Up from the top of a stage goes to the strip; left and right there change stage as you go; down comes back; Back goes to the strip
- [ ] All channels (end of the strip, OK) opens the classic Live TV; its "Stages" sidebar item brings the stages back
- [ ] Three things: three cards the same size, films with a backdrop, others with the channel logo; OK plays one that is on, tunes in for one that starts soon; Deal again works once a night
- [ ] **BROKEN** Starting soon: the chips narrow the departures; the right panel follows the highlighted row; Tune in at switches to the channel at the start; Go to ... now plays it (2026-11-15): Tune in at did nothing while another channel was playing
  - [ ] (must) Re-check after the fix (the channel is now changed inside the playing player): Tune in at on Starting soon, keep watching another channel, and at the start time it switches; the same while a film plays (the film closes and the channel opens)
- [ ] I have N minutes: the dial changes the three columns; nothing runs over except dimmed rows that say how far
- [ ] Tonight: the running order ends before the bedtime chip; Swap one replaces the highlighted slot; Approve and start switches at each start; Approved: cancel and Not tonight undo it
- [ ] Earlier today: OK or Play from the start plays a programme from its beginning (Xtream catch-up)
- [ ] Following: type a name in the box and press Enter to follow it; Follow on a suggestion; hold OK on a followed one to stop; one on now plays, one later sets a reminder
- [ ] Just ask: type "something funny, half an hour" (or use the mic): one answer with artwork, why this on the right, Not that for the next; the line is kept under Asked before
- [ ] Your 40: the numbered grid with a progress bar per channel; OK plays; type 1 2 to go to 12; hold OK for move / take out / Never again; Redo the triage opens the questions

## Live TV: Stage v2 (Nov 14)

Settings > Live TV > Stage. The first start after updating reads the guide again (it takes a little longer once).
- [ ] The whole stage glows softly in the colours of the highlighted programme's picture, and the glow fades over as the highlight moves; nothing moves or changes size
- [ ] Three things: each card has a picture (film backdrop, series art or the channel logo) with LIVE / STARTS / FILM marks, chips (genre, year, length, episode), three lines of description, why it was picked and a button
- [ ] No 24/7 loop channel (SpongeBob, American Pie, Luke Wilson), radio or Teleshopping appears in Three things, Starting soon, minutes, Tonight or Just ask unless it is on Your 40; the stages say how many were hidden
- [ ] Titles show without "- S02E01" and without ᴺᵉʷ / ᴸⁱᵛᵉ; those show as badges instead
- [ ] Starting soon, Earlier today: the right panel shows a picture, marks, a description and what comes on after
- [ ] I have N minutes: rows have pictures; things on now have LIVE and a progress line
- [ ] Tonight, opened in the morning: plans from 18:00, the ribbon shows the evening up to bedtime, Starts chips (Now, 18:00, 20:00) change it, and something that runs past bed is shown dimmed as left for another night
- [ ] Your 40: the band at the top shows the highlighted channel's programme with its picture and what is next; tiles show the logo, what is on and progress; placeholder logos ("Content not viewable") are replaced by the channel name
- [ ] Just ask: the three example lines under the answer fill in the question; the answer card shows the picture, marks and description

## Live TV: stages on the phone remote (Nov 14)

Needs the new Nuvio Remote build (android-remote workflow) and Settings > Live TV > Stage on.
- [ ] Opening Live TV on the TV: the phone's Remote tab shows the same stage (heading, the strip of stages as chips, the programmes as cards with a picture, a LIVE slot and a progress bar)
- [ ] Tapping a stage chip changes the stage on the TV; changing it on the TV changes it on the phone
- [ ] Three things: tapping a card does what OK does (watch, or tune in at its start); Deal again works once a night
- [ ] I have N minutes: the minute chips change the board; Tonight: Approve and start / Not tonight work from the phone
- [ ] Your 40: tapping a channel plays it; Following: Remind me on something not yet on sets the reminder
- [ ] The header tag swaps to the pad and back; All channels opens the classic list and the mirror goes away
- [ ] Leaving Live TV (or starting something) puts the phone back on its usual screens

## Live TV: From the start, on demand (Nov 14)

Settings > Live TV > From the start, on demand (and Stage) on; add-ons with TorBox (or another source) set up.
- [ ] OK on a film that started more than 3 minutes ago asks: From the start, on demand / Join live, N min in / Close; it says what TMDB found
- [ ] From the start plays the film from 0:00 through your add-ons (the source they pick), not the live channel
- [ ] It starts playing by itself, with no source list, even with Settings' auto-play set to choose manually
- [ ] Friends (S3 E22 in the description, not the guide's episode field) plays S3 E22 from the start instead of opening the series page
- [ ] An episode with no numbers but a description starting with its title ("The One With ...") finds and plays that episode
- [ ] An episode with S/E numbers in the guide plays that episode from the start; one without opens the series' page
- [ ] A programme TMDB doesn't know says so and offers only Join live; news, sport and Live-marked programmes join live straight away
- [ ] Up/Down, OK and Back work on the choice; tapping a card on the phone mirror opens the same choice on the TV
- [ ] On Now (stages off): a film or episode well under way opens its page with "From the start, on demand" (it says what TMDB found) above Watch live
- [ ] The classic channel list, Your 40 and the guide: picking a channel whose programme is well under way opens the same page; a channel with news, sport or nothing worth it just plays

## Live TV: Just ask finds more (Nov 14)

- [ ] "news, ten minutes" answers with a news programme on now (Live: dip in), even when the guide has no categories
- [ ] "the football" answers with sport, a football match first when there is one
- [ ] Asking for more time than anything fits ("a film, 15 minutes") gives the closest and says "Nothing fits 15 minutes, so this is the closest"
- [ ] When truly nothing fits, Why this shows what it looked for

## Live TV: Reminder card (Nov 14)

Set a plain reminder (Remind me, not Tune in at) on something starting in a few minutes.
- [ ] Two minutes before, away from the player: the card bottom left asks Switch now / At the start / Not now, with At the start highlighted; left and right move, OK picks, Back is Not now; the screen behind keeps its place once it closes
- [ ] At the start: nothing happens until it starts, then Nuvio switches to the channel
- [ ] Switch now goes to the channel straight away; Not now (or leaving it 20 seconds) just closes it
- [ ] While watching something: the same question appears on the player's HUD, and the answers work the same
- [ ] With Brakes on, a programme reached through Switch now or At the start gets the end card

## Live TV: Just ask with your AI (Nov 14)

Needs an AI key under Settings > Discover. Settings > Live TV > Let the AI answer Just ask.
- [ ] Turning the switch on without having agreed before shows the AI privacy dialog; agreeing turns on only this switch
- [ ] Just ask: type "something to cheer me up" and leave the box: "Asking your AI" shows under the answer, then the label reads ONE ANSWER · FROM YOUR AI with the AI's reason in Why this
- [ ] Not that steps through the AI's picks (up to three), then on to the rules' answers
- [ ] With no key (or the switch off) Just ask answers by the rules as before; if the AI fails, it says so and shows the rules' answer
- [ ] Nothing on the Ask stage moves or changes size while the AI is asked

## Live TV: Brakes finished (Nov 14)

Settings > Live TV > Brakes and Stage on.
- [ ] Five channel changes inside two minutes: the card has three answers (Keep flicking, Pick something for me, Turn Live TV off) on one line each; Pick something for me leaves the player for Live TV's Three things stage
- [ ] With Stage off, the zap card still has two answers
- [ ] A programme picked on a stage (or switched to by a reminder, or tuned in to before it was 10 minutes in) and watched 15 minutes: the end card shows as it ends
- [ ] Landing on something half-way through while flicking and staying: no end card at its end
- [ ] Approve Tonight's running order and watch it through: as the last slot ends the card says "That's tonight" with Stop here focused
- [ ] Your week is on the strip of stages (only with Brakes on): tonight's numbers on the left, seven equal bars (tonight highlighted) with changes under each, the week's totals with changes an hour on the right; nothing moves or changes size

## Live TV: smarter picks (Nov 14)

Settings > Live TV > Stage. Watch a few programmes on Live TV first (each one at least 5 minutes; some to the end).
- [ ] After watching the same programme a few times, mostly to the end, Three things' SAFE card says "You finished 4 of the last 5" (or similar) before "Your usual at this hour"
- [ ] Following: a programme you watched 3 or more times in three weeks (and do not follow) is listed under "You keep watching" with how often you saw it, at its next airing
- [ ] Just ask: a programme you usually finish comes first and says "you usually finish it"; its reasons include "You usually finish it"
- [ ] Where the guide marks a new episode it is picked above an episode you have already seen; Tonight and Earlier today favour programmes with a picture and a description
- [ ] Settings > Live TV > Forget what I watched clears it (the row says how many programmes it learned from, and goes away after)

## Phone remote: poster wallpaper (Nov 14)

- [x] Remote › More shows a Lock screen row: Poster off/on, My picture, and a line saying what it does (2026-11-14)
- [x] With Poster on, start a film on the TV: within a few seconds the phone's lock screen shows its poster, whole and centred on a blurred copy (home screen unchanged) (2026-11-14)
- [ ] An episode of a show: the lock screen shows the show's poster; the next episode does not make it flicker
- [ ] Music on the TV: the lock screen shows the album cover; a new album changes it
- [ ] Pausing keeps the poster
- [ ] My picture: pick a photo; about 20 seconds after stopping playback on the TV, that photo is back on the lock screen
- [ ] Without My picture: after stopping, the lock screen goes back to matching the home screen
- [ ] Turning Poster off while something plays puts the normal picture back straight away
- [x] The poster is sharp, not blurry, on the phone's screen (2026-11-14); layout and darkness kept as they are (the clock and media card sit over it)

## Live TV: triage languages and countries (Nov 15)

- [ ] Redo the triage: countries written out ("Germany | TV", "Costa Rica | TV", "Czech | TV") have their own tiles, not "No country marked"
- [ ] A second question asks about languages; with United Kingdom and United States ticked only English starts ticked
- [ ] With English only: Univision, Telemundo and ESPN Deportes are gone from the US categories, and an all-Spanish US category leaves the sidebar
- [ ] The sidebar counts and All channels match what is left; search still finds a Spanish channel
- [ ] Three things never offers a channel from a country or language the triage left out (the Wildcard included)

## Live TV: Tonight never runs dry (Nov 15)

- [ ] On Tonight, press Swap ten times or more: the running order keeps filling, never stopping half way
- [ ] A programme swapped out comes back only after the others are used up

## Live TV: learned teams (Nov 15)

- [ ] After watching three matches of one team in six weeks (two to the end), Following shows "Follow <team>?" under Learned from what you watch
- [ ] OK follows it and the toast says it was added to Live Scores; the team is in Settings > Live scores favourites
- [ ] Hold OK on it: it goes and is not offered again
- [ ] A team followed as "Manchester United" also finds "Man Utd v ..." listings

## Live TV: sport badges and merged copies (Nov 15)

- [ ] Sport tiles show the competition and round on top, "Team v Team" as the title and both badges beside it
- [ ] Premier League, rugby, NHL and NBA teams get badges; teams not found show their initials, never a wrong sport's badge
- [ ] A boxing live-score tile shows the fight night on top and the fighters' names (with photos where found)
- [ ] A match listed twice (once with "ᴸⁱᵛᵉ") is one tile, with "also on" when the other channel has another name
- [ ] On the stages, a fixture with no picture shows the two badges instead of the channel logo

## Live TV: reminder banner (Nov 15)

- [ ] A reminder comes as a solid banner at the top centre: picture, title, channel, a countdown and a filling bar; nothing shows through it
- [ ] Left, right and OK pick Switch now / At the start / Not now; Back is Not now
- [ ] A reminder due while the triage questions are open waits and shows when they close

## Live TV: more views (Nov 15)

- [ ] Map: blocks by kind; OK opens one into tiles with the highlighted one on the right; Back zooms out
- [ ] For you now: big tiles are what you would pick first; OK plays or tunes in
- [ ] River: lanes by kind with the now line; left/right moves along a lane; OK on a later one sets a switching reminder
- [ ] Picture wall: tiles turn from art to real stills one by one ("waiting", then minutes); none are taken while a channel plays
- [ ] Tonight grid: darker cells where more starts; OK on a cell lists them on the right; OK on one plays or tunes in
- [ ] Dial: up/down flip one channel at a time with two peeking above and below; typing a number jumps there; OK plays
- [ ] The phone remote shows For you now, Dial and Picture wall as lists, and says the other three are on the TV

## Live TV: subtitles for films on live channels (Nov 15)

- [ ] Settings > Live TV has "Subtitles for films on live channels"; Settings search finds it
- [ ] **BROKEN** With it on, tune to a film (Jaws on Sky Cinema): the player's subtitles list shows add-on subtitles (Subsense) within about half a minute (2026-11-15): 21 Jump Street and Mission: Impossible showed nothing
  - [ ] (must) Re-check after the fix (films the guide does not mark as films are now recognised by their channel or category, so they are not looked up as the TV series of the same name): 21 Jump Street or Mission: Impossible on a Sky Cinema channel lists Subsense subtitles
- [ ] Pick one: the HUD says "timed from the guide", and the lines are roughly right straight away
- [ ] Within a few minutes the HUD says "synced to the audio (+x.xs)" and the lines match the speech
- [ ] Pick one subtitle, then another a minute later: the second syncs within about half a minute (the speech already heard is kept), not after another few minutes
- [ ] On a channel with ads (Film4, ITV), tuned in well after the film started: it still syncs, even when the film is many minutes behind the guide (it used to give up past a minute)
- [ ] A film shown sped up (most UK film channels): still in time half an hour later
- [ ] Rewind with the buffer, then Go live: the subtitles stay in time
- [ ] On a channel with ad breaks: after a break the HUD says "re-synced" and the lines are back in time
- [ ] When the film ends and the next programme starts, the subtitle goes off; the next film offers its own
- [ ] News and sport offer no add-on subtitles; playback is never interrupted by the sync (no second connection)

### Matching live subtitles by their words (Nov 15)

- [ ] After `build.cmd`, `out\Nuvio\nuvio_whisper.dll` is there (the build fetched whisper.cpp into `D:\NuvioBuild\whisper.cpp`); Settings > Voice control no longer says the engine is missing
- [ ] Settings > Live TV has "Match live subtitles by their words" under the subtitles switch; Settings search finds it
- [ ] With a model downloaded in Settings > Voice control (voice control can stay off) and the switch on, pick an English subtitle on a film: within about a minute the HUD says "synced to the audio" and the lines match the speech
- [ ] Nativity 3 (or another film with songs): the lines are in time, not thrown off by the singing
- [ ] On a channel with ads, after a break the lines are back in time within a couple of minutes
- [ ] A subtitle for a different cut, or for another film, does not lock (stays "timed from the guide")
- [ ] With the switch off, or no model downloaded, it works as before (speech detection alone); CPU stays reasonable while it listens

## Live TV: the front door (Nov 15)

- [ ] (must) With Stage on, Live TV opens on the front door ("Live TV", Home first on the strip): favourites on the left, one pick in the centre with Watch highlighted, scores and Next up on the right
- [ ] (must) OK on Watch plays the pick; a pick that starts within 5 minutes puts its channel on at once ("Watch · starts 20:00")
- [ ] The pick's line says why: you follow it, starts in N min, your usual at this hour (with its Your 40 number), or a film that began N min ago
- [ ] Something else swaps the pick for the next one; the pick otherwise stays the same for 15 minutes, unless a followed match comes on
- [ ] A followed team playing live becomes the pick, and its score is on the right with the channel showing it; OK on the score plays that channel
- [ ] Hide scores empties the scores box (it says hidden); Show scores brings them back, and the choice is kept after a restart
- [ ] Favourites now lists the starred channels with what is on and a progress bar; the last channel watched sits above them as Carry on; OK plays, hold OK unstars
- [ ] (must) Back from the player lands on the front door, whichever stage you left from
- [ ] First run: on a playlist with no triage yet, Live TV opens on the triage questions; Done (or Back out of them) leads to the front door, and they don't come back by themselves until Nuvio is restarted
- [ ] Settings > Live TV > Front door picture: the pick plays its channel, muted, with rounded corners and no player controls (the art shows until the picture moves); Watch opens the player with no second connection hanging on; a menu (hold OK) is not hidden behind the picture; off, the art only
- [ ] The phone remote on Home (new Nuvio Remote build): the pick is one big card with its picture, why it was picked, Watch and Something else; Watch plays it on the TV and Something else changes it on both
- [ ] Below the pick on the phone: Favourites now (Carry on first, then the starred channels, even ones with nothing on), Scores (a dot while live; tapping a match on a channel plays it) and Next up (tapping sets the switch at its start)
- [ ] Hide scores on the phone empties the TV's scores box and the phone's Scores; Show scores brings both back
- [ ] An older Nuvio Remote build still shows Home as a plain list of rows that work

## Live TV: polish after the first look (Nov 15)

- [ ] Home and Three things: the picture fills the card down to four lines of description, with no empty gap; cards stay the same size whatever the text
- [ ] Earlier today shows at most two of one show (not a dozen Friends), so other things are in the list
- [ ] Tonight grid: a cell lists only what starts in that half-hour (no 15:00 starts under 18:00)
- [ ] Your 40: "Copies merged" counts the merged copies, and "Unwatched" and the heading's "the other N" agree with the channel count
- [ ] No doubles: I have N minutes (one 5 Minutes, one Abby Hatcher), Following's Coming up (one Brit Cops), Home's favourites (one WSL match)
- [ ] Scores on Home say the day for a match that is not today ("Kick-off tomorrow 12:30", "Kick-off Sat 15:00") and are no longer greyed out
- [ ] Follows typed in lower case show with capitals (Man Utd, Line Of Duty)
- [ ] Just ask: Watch and Not that are hidden until there is an answer, and nothing moves when they appear
- [ ] The strip has one Views chip instead of six; OK on it opens a picker (Map, For you now, River, Picture wall, Tonight grid, Dial); on a view the chip says "Views · Map"; Back closes the picker
- [ ] Settings > Live TV no longer has the six View switches
- [ ] All channels (the classic list) with Stage on: the sidebar has Stages, Your 40, Favourites, Recently watched, All channels and the categories, not On now / Sport / Films / Three things and the rest
- [ ] Settings > Live TV > Home country (United Kingdom): For you now and the picks put UK channels first, and US/Australian local news drops out of the big tiles; "From the triage" uses the triage's country when it kept one
- [ ] Menus over Live TV (Views, the programme menu, Your 40's channel menu, From the start) are solid cards in the middle of the screen over a dark backdrop, nothing shows through them, and the highlighted option is filled with the accent
- [ ] Live TV subtitles on a film the guide does not mark (The Ring on Film4, Matilda on ITV2): the log says "as a film -> movie tt...", and the add-ons' subtitles are offered; "Channel 4 News" is not looked up
- [ ] Right-click with the mouse on any programme on the stages (rows, cards, view tiles) opens the same menu as holding OK; on a favourite on Home the menu has Unstar too (holding OK there no longer unstars straight away)
- [ ] Hold OK on any programme on the stages (Home's pick and Next up, Three things, Starting soon, Tonight, Earlier, the views): a menu with Watch, Follow <title> (or Stop following), Remind me (before it starts) and Close; Follow shows a toast and the show appears under Following

## Phone remote: pointer (upstream merge, Nov 15)

Upstream's windows-tv-adaptive (94a5b339) merged into Spyder: the phone as a pointer came in; everything else stayed the fork's.

- [ ] Nuvio starts and the Live TV stages, the music home, the Stage, the album and artist pages look as they did before the merge
- [ ] Settings still has the built-in AIOStreams and AIOMetadata, and the update check still looks at this fork's Nightly
- [ ] Nuvio Remote: the tag in the pad's corner (or More) goes Touchpad, D-pad, Pointer and back, and the choice is remembered
- [ ] Pointer: turning and tilting the phone moves the Windows cursor on the TV; it holds still while a finger is on the screen and does not drift when the phone is still
- [ ] Pointer: a tap clicks where the cursor is (a poster opens, a button presses)
- [ ] Pointer: dragging up or down scrolls a page; left or right scrolls a poster row sideways
- [ ] Pointer: hold until it buzzes and lift = right-click (Live TV's programme menu opens); hold and turn the phone = drag
- [ ] Pointer: two fingers = Back
- [ ] Pointer: Centre puts the cursor in the middle of the TV; Slow / Normal / Fast changes the speed and is remembered
- [ ] Pointer: the Remote tab stays the pointer while a film, music, the source list, What to watch or Live TV is on the TV
- [ ] Nuvio Remote still updates itself only through its own card (Install / Later) and More > Device > Update: no second "Update available" banner

## Nuvio Lights (Nov 18)

- Nuvio Lights, the tray app: dropped, film-night lighting belongs to Switchboard on the phone (2026-11-21)

## Lights out: Govee Movie DreamView (Nov 18)

- Movie DreamView in Settings > Lights out: dropped, Switchboard on the phone starts and stops it (2026-11-21)
- [ ] Settings > Lights out > Govee has no Movie DreamView rows, and loading the Govee devices still lists the lights
- [ ] (must) Close and reopen Nuvio: the Govee lights stay as Switchboard left them (no DreamView starting by itself)
- [ ] (must) With Lights out on, play and pause a film: the lights ticked under Lights to control still dim and come back

## Phone remote: picture shape (Nov 21)

- [ ] (must) During a film, More › Playback shows a Shape row (Fit, Stretch, Zoom, Auto crop) with the TV's current one lit
- [ ] Each of Fit, Stretch, Zoom and Auto crop changes the picture on the TV, and the TV briefly shows its name
- [ ] Changing the shape from the TV's own Aspect ratio menu lights the matching chip on the phone within a second
- [ ] (must) The shape picked from the phone is still in use when the next film starts
- [ ] During music the Shape row is not shown

## Bedtime: stop after this episode (Nov 24)

Nuvio Remote, during an episode: More › Playback › Bedtime. Needs this Nuvio and the new Nuvio Remote build.

- [ ] (must) During an episode, More › Playback shows a Bedtime row (Off, Stop after this episode); picking Stop after this episode lights it and the Now playing card says "Lights off after this one"
- [ ] (must) With it on and Lights out on, the episode ends: no next episode starts (no Binge Mode advance at the credits either), the player closes and the lights go off instead of coming back up
- [ ] With it on, the TV's own Next episode (or the phone's) still plays the next episode, and Bedtime is still set for that one
- [ ] Bedtime Off after setting it: the episode ends and the next one plays as usual
- [ ] Leaving the player early clears Bedtime: the next film you start ends as usual

## Lights out: per content (Nov 24)

Settings > Lights out > Per content (new switch under When playback starts).

- [ ] (must) Per content on, Films Dark: starting a film switches the ticked lights off
- [ ] (must) Per content on, TV Half: starting an episode (or a live channel) dims the lights that are on to half
- [ ] (must) Per content on, Music: beat lights on: music from the Music tab flashes the beat lights even with Flash a light on the beat off
- [ ] Films or TV set to Leave: starting one leaves the room alone, and stopping it changes nothing
- [ ] As above: uses the When playback starts choice (and its dim level)
- [ ] Per content off: everything is as before (the When playback starts choice for all, beat lights only with their own switch)
- [ ] Pause with Bring up, then play again: the room goes back to the same scene it started with (Dark or Half)

## Live TV favourites on Home (Nov 24)

Settings > Live TV > Favourites on Home (on by default). Needs at least one favourite channel (the star in Live TV).

- [ ] (must) Home shows a Live TV row first, one card per favourite: the channel's logo, what is on now and until when
- [ ] (must) From Home, select a card: the channel plays straight away (two presses from Home)
- [ ] In the player, channel up and down move through the other favourites
- [ ] Starring or unstarring a channel in Live TV adds or removes its card on Home
- [ ] After a programme ends, its card shows the next one within a minute
- [ ] Favourites on Home off: the row goes; on again: it comes back
- [ ] The row works with the mouse, the keyboard and the controller, and the hero does not show a broken page for a card

## Upstream merge (Nov 23)

Upstream's windows-tv-adaptive (42426994) merged into Spyder. A sanity check, and the one change to a fork page (the music dock):

- [ ] (must) Nuvio starts; the music home, Stage, album and artist pages, the Live TV stages and the phone remote work as before
- [ ] Music dock: click a button in the open dock with the mouse, then move the pointer away: the dock folds back (it no longer stays pinned open)
- [ ] Music dock with the keyboard or a pad: reaching the dock opens it and puts focus on its play button, and it stays open while focus is inside
- [ ] After using the mouse, the player's B key moves focus to the dock and opens it with a focus ring (it used to look like it did nothing)
- [ ] Live TV with Stage on: opening the tab waits on Stage's background instead of flashing the plain channel page first

## Upstream merge (Nov 21)

Upstream's windows-tv-adaptive (56d7c587) merged into Spyder. Its new things, and a check that the fork's own pages are untouched:

- [ ] (must) Nuvio starts; the music home, Stage, album and artist pages, the Live TV stages and the phone remote work as before

## Shelf home (Nov 26)

- [ ] (must) Settings > Home screen > Shelf home is off by default, and with it off Home looks and behaves exactly as before
- [ ] Turning Shelf home on shows the Shelf home placeholder on Home straight away; turning it off brings the classic home back
- [ ] With Shelf home on, Search, Library and Discover still open their classic pages
- [ ] Settings search for "shelf" finds the Shelf home switch
- [ ] Shelf home is remembered per profile: on for one profile, off for another after switching
- [ ] (must) With Shelf home on, Home's shelf names in the right-hand panel are in the same order as the classic home's rows (custom row names kept, hidden rows missing)
- [ ] A collection pinned to the top shows as its own tab with one shelf per folder; an unpinned one is a single shelf on Home
- [ ] Continue watching (and Up next / Upcoming when they are separate rows) lead Home's shelves when they have something
- [ ] **BROKEN** (must) With the remote or a controller: moving up and down the shelf names shows each shelf's cards along the bottom; Left goes into the cards, Right comes back to the names (2026-11-26): no keyboard or D-pad focus at all, only the mouse
  - [ ] **BROKEN** (must) Re-check after the fix: opening Home with Shelf home on, the current shelf name is highlighted and the arrows, D-pad and controller move it without touching the mouse (2026-11-26): still no keyboard navigation on the new screens
    - [ ] (must) Re-check after the second fix: the arrow keys, D-pad and controller move between the shelf names, into the cards (Left), back (Right) and up to the slash menu, in every view
- [ ] The mouse wheel over the cards scrolls them sideways
- [ ] (must) Pressing Menu or V (or holding OK, or right-clicking) on a shelf name opens "View <shelf> as" with Strip, Ledger and Spotlight; the current one is ticked, and picking one switches the shelf at once
- [ ] A shelf's chosen view is still there after restarting Nuvio; "Use the default" puts it back; each profile keeps its own choices
- [ ] Back or Escape closes the view picker and focus returns to the same shelf name; Up/Down never leave the picker
- [ ] Continue watching and a "Top 10" or "Top 100" shelf open as Ledger without choosing it
- [ ] Ledger: a big-type list with no artwork; the focused row has an accent bar and accent title and nothing moves or resizes
- [ ] Ledger: a film row shows its length and end time on the right ("1h 49m · 21:16"); a Continue watching row shows "1h 16m left · 20:41"; a Top 10 shelf numbers its rows
- [ ] Ledger: OK opens the focused title; scrolling to the bottom of a long catalog loads more; switching shelf starts back at the top
- [ ] (must) Spotlight: one big poster with smaller ones either side; Left/Right slide the posters past while the big one stays in the same place
- [ ] Spotlight: Right on the last poster moves to the shelf names; Left on the first poster stays put; OK opens the big poster; clicking a small poster makes it the big one; the mouse wheel moves one poster per notch
- [ ] Spotlight: the backdrop and title block follow the big poster; the accent ring shows only while the posters have focus; Top 10 shelves show the rank on the big poster
- [ ] (must) On a pinned collection's tab (Spotlight, Genres...), a folder shelf shows that folder's titles within a few seconds, not just the folder card
- [ ] A long folder shelf loads more titles at its end, and the cards already showing do not move
- [ ] Switching to another folder shelf and back shows the titles at once; the classic folder page still works after using the shelf
- [ ] A folder whose addon is missing still shows the folder card rather than an empty shelf
- [ ] Long-pressing a Continue watching card on the shelf home opens the same menu as on the classic home
- [ ] The view picker now offers Wall and Logos too; a streaming service or network row (Netflix, Disney+, BBC...) starts as Logos
- [ ] (must) Wall: a grid of posters about 8 across with a title under each; the focused poster gets an accent ring and brightens, and nothing moves or resizes
- [ ] Wall: Down scrolls the grid and more titles load at the end; Right from the last column reaches the shelf names; OK opens, a long press opens the menu
- [ ] (must) Logos: the focused title's logo big with an underline, smaller faded logos either side; Left/Right slide them past a fixed spot
- [ ] Logos: a title with no logo shows its name in big capitals in the same space; under the strip "year · genres" (Continue watching "S4 E1"); the underline turns accent while the strip has focus
- [ ] (must) Home's shelf list ends with "+ New shelf": it opens the shelf builder; name it "The Hang", tick two rows, choose 100 min, Hide watched, Save: the shelf appears after Continue watching with titles from both rows alternating
- [ ] The Hang shows nothing over 100 minutes and nothing watched; with "Must finish before bedtime" on, late in the evening nothing ends after bedtime
- [ ] A built shelf with few matches loads more pages by itself, then stops (no endless loading)
- [ ] In the shelf builder the remote reaches every control, the home behind never takes focus, and Back closes without saving
- [ ] Menu on a built shelf shows "Edit this shelf" under the views; edits stick, Delete removes it, and both survive a restart and stay per profile
- [ ] (must) Settings > Home screen: with Shelf home on, a "Shelf home look" Dark / Paper choice appears (gone when Shelf home is off); Dark looks exactly as before
- [ ] Paper: warm paper with dark type and no backdrop picture; slashes, the focused tab and Ledger's focused title in the accent colour (darkened if pale) and readable; the focused shelf name filled with the accent and readable
- [ ] Paper: card titles in Strip and Wall and the logos in Logos are readable (no white on paper); "View … as" opens a light card with dark text
- [ ] Moving onto a tab in the slash menu (Home / a pinned collection) switches to it; selecting Library opens the Library page
- [ ] (must) It looks like the mockups: the focused title's backdrop fills the screen behind everything (darker on the left), the slash menu top left, the clock top right (hour heavy, minutes light), the shelf names in a dark panel down the right with the focused one filled in the accent colour
- [ ] The focused title shows big as "Name / YEAR / 2H 9M" with accent slashes, a letter-spaced line of genres, IMDb and rating, "starts now, ends 9:30 PM" (12-hour clock if set in Tonight), and two lines of description; the cards do not jump as it changes
- [ ] On Continue watching it reads "Show / S4 E1 / 1H 16M LEFT", the episode title, and "finish by 8:41 PM"
- [ ] The backdrop cross-fades as focus moves between cards and shelves
- [ ] Selecting a poster opens its details; a Continue watching card resumes as on the classic home; a folder card opens the folder
- [ ] Scrolling to the end of a long catalog shelf (e.g. Trending) loads more titles
- [ ] Leaving Home and coming back lands on the same tab and shelf

# Archived (not testing)

Set aside on 2026-11-22 (Nick): upstream merge checks for upstream's own features, and the old Live TV views that Stage replaced. Kept for the record; the phone's Checks tab asks nothing below this heading. To test one again, move it back up.

## Upstream merge (Nov 13)

Upstream's windows-tv-adaptive (8e2adb00) merged into Spyder. These check that what the fork kept still works and that what came in did not break it.

- [ ] Nuvio starts, and Settings > About (or the update check) still looks at this fork's Nightly, not UmbraProjects'
- [ ] Music tab with "New music home" on: the music home is still the first screen, typing searches as before, and its Library tab opens the three-panel Shelf
- [ ] With the music home on, the Search key (S) and the top bar's Search on the Music tab put the cursor in the music home's own search line
- [ ] A genre chip on the Stage still opens the Music tab searching that genre
- [ ] Settings > Music still has "New album page", "New artist page" and "New music home", each as you left it; the album page and the artist page look as they did before the merge
- [ ] Settings > Music has a new "Album notes and facts" switch, on: liner notes, song stories and the facts filler work as before
- [ ] Settings > Music > Library views (new): Crates (the three-panel Shelf) and Playlists are on; Shelf (the old row-per-artist one), Constellation and Eras are off and do not show in the Library
- [ ] The Stage looks as it did before the merge: three panels, nothing moves, the liner notes / visualiser box is there, the options menu has no "Side box" row
- [ ] Stage options menu: the arrow keys stay inside the open menu (they do not jump to the page behind it)
- [ ] The dock still shows the quality pill (FLAC) and the beat dot; the mini bar's line still ends with the quality
- [ ] Release tiles still show the "Lossless" text pill (not upstream's icon)
- [ ] Loud lyrics on the Stage: no row is cut off at the top or the foot (upstream's fit fix came in)
- [ ] Music pages from the keyboard: after closing a menu or a dialog the arrows still move (upstream's focus router); Escape or B gets out of the dock and the player panel
- [ ] Live TV with On Now on opens on the wall; the top bar's Search on the Live TV tab narrows the wall and the channel list; Triage still opens from the header
- [ ] Tonight ("What to watch"): the launcher shows real backdrops and every mode has the new look; each mode still shows on the phone remote and a pick from the phone plays on the TV
- [ ] Tonight: a trending title (one with a tmdb id) still opens its details page
- [ ] Phone remote: connects as before; a live-score notification still arrives on the phone
- [ ] R (or holding LB) brings up the panel ring; O opens Settings
- [ ] Top bar Search on the YouTube tab searches YouTube

## Upstream merge (Nov 16)

Upstream's windows-tv-adaptive (43492138) merged into Spyder. Its new things, and a check that the fork's own pages are untouched:

- [ ] Nuvio starts; the music home, Stage, album and artist pages and the Live TV stages look as before
- [ ] Settings > Keyboard shortcuts > Phone remote > Aim assist for the pointer: Off / Light / Medium / Strong; with Medium, the phone's pointer slows over buttons and posters and is pulled gently toward them
- [ ] Clicking a text field with the phone's pointer opens Nuvio's on-screen keyboard
- [ ] Two-finger Back on the pointer always goes back at once and never freezes the cursor
- [ ] TV mode: the pointer's sideways drag (or Shift + wheel) walks along whichever row the cursor is over
- [ ] YouTube at 2x, or a 4K video: plays without stalling (FastStream turns itself on); the watch page's FastStream button offers downloading the whole video
- [ ] Library: every card is a portrait poster, even one saved from a landscape row
- [ ] A person page (click a cast or crew member): the new layout, and a director or writer opens on their crew work
- [ ] A controller's stick moves a cursor where a screen has no focusable item
- [ ] Settings > Appearance > Sidebar items at top: the sidebar's items sit under the profile
- [ ] Live TV: a playlist whose password was pasted with a line break now plays
- [ ] Simkl Next up updates after marking an episode watched

## Upstream merge (Nov 21)

- [ ] Home and Library posters show at once after a reboot, with no blank cards while they load
- [ ] Sources: late addon results show at once while the mouse is over the list, without moving the row under the pointer
- [ ] Settings > Media servers: a Jellyfin, Emby or Plex server can be added, and its files show as a source group for a title it has
- [ ] A film from a media server plays, and its watch progress shows on the server afterwards
- [ ] Settings > Usenet: a provider and indexer can be added and each Test button answers
- [ ] With Search my indexers on, a title's source list has a Built-in Usenet group (playing one may say the engine is missing in this build)
- [ ] Settings > Usenet > Usenet info opens and shows the indexer and provider counts
- [ ] Settings > Random Play > Tonight > 12-hour clock shows What to watch's times as 11:30 PM
- [ ] With TorBox set up, picking a torrent from the player's Sources panel plays through TorBox with no P2P warning
- [ ] Continue watching's click action offers Details for upcoming only, and it opens details for a Next Up episode not aired yet
- [ ] Removing a Next Up card keeps it gone after a restart

## Live TV: On Now (Nov 13), the classic wall

The wall's own tiles and sidebar sections, which Stage replaces. Start over and merged channels stay up in their section.

- [ ] Settings > Live TV has an "On Now" switch under "Show in navigation", off; Settings search finds it by typing "on now"; with it off the Live TV tab is exactly as before
- [ ] With it on, the sidebar starts with On now, Sport and Films, and the tab opens on On now: rows of glass tiles titled Live sport, Films and Your channels (a row with nothing in it is left out)
- [ ] Arrow keys and the pad move a highlight tile to tile, Left from the first tile goes to the sidebar, Up from the top row reaches the header buttons, and a mouse click on a tile does the same as OK
- [ ] Films: the tiles are films that are really on now or start within half an hour, each with how far in it is, a progress bar and the channel; with a TMDB key they show posters
- [ ] Sport, with Live scores on and a followed match in play: its tile shows both teams, the score and the minute, names the right channel, and OK plays that channel
- [ ] Sport, a followed match the guide has no channel for says "Not found in the guide" and does nothing on OK
- [ ] Sport with Live scores off still lists sport programmes from the guide
- [ ] Your channels after a few days: the row leads with what you usually watch at that time of day
- [ ] The header's search box narrows the wall by a film, team or channel name

## Live TV: more views (Nov 15)

The six switches were replaced by the Views chip (Nov 15 polish).

- [ ] Settings > Live TV has six "View:" switches; each adds its stage to the strip, and off takes it away
