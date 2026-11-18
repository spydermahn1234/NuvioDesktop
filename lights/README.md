# Nuvio Lights

A small Windows tray app that dims the room while Nuvio plays and puts the lights back when you pause or stop.
It works with **WiZ** bulbs and **Govee** lights, both over your home Wi-Fi (no cloud, no accounts).

It is a separate app: it listens to Nuvio the way Nuvio Remote does, so Nuvio itself is unchanged.

## Set up

1. In Nuvio, turn on **Settings › Keyboard shortcuts › Phone remote** (it shows a six-digit code).
2. For each Govee light, open the Govee Home app, pick the light, tap the settings cog and turn on **LAN Control**.
   (Not every Govee model has it. WiZ bulbs need nothing.)
3. Download `NuvioLights.exe` from the **NuvioLights** release on GitHub and run it on the HTPC. Windows may ask
   whether to let it use the network: allow **Private networks**, or it cannot hear the Govee lights answer.
4. The **Lights** window opens and looks for lights. Use **Flash** to tell them apart, give them names, and choose
   what each does while something plays:
   - **Dim** to a level (WiZ bulbs go no lower than 10 %),
   - **Turn off**,
   - **Leave alone**.

   New WiZ bulbs start on Dim to 10 %; new Govee lights start on Leave alone until you choose.
5. Nuvio Lights asks for the six-digit code from step 1, once. After that it reconnects by itself.

It turns on **Start with Windows** the first time it runs (untick it in the tray menu if you'd rather not).

## What it does

- **Play** (a film, show, Live TV, a YouTube video, or music if ticked): the chosen lights fade down.
- **Pause**: they fade back up straight away, to exactly the brightness they had before.
- **Stop**: they come back after four seconds, so the gap before the next episode or song doesn't flash the room.
- A light that was off before the film is left alone. A light someone switches off during the film stays off.
- If Nuvio closes, the PC restarts or the app is exited, the lights are put back (exit restores before it quits).
- Only power and brightness change: each light keeps its colour, white temperature or scene.
- If a light's address changes (the router gave it a new one), it is found again by its MAC address.

The tray menu has the on/off switch, Films and Music ticks, **Test › Dim now / Restore now**, **Pair with Nuvio
again…**, Start with Windows and **Open log**. The icon is warm while the room is lit, blue while dimmed and grey
when Nuvio isn't connected.

Running Nuvio Lights on a different PC from Nuvio works too: put Nuvio's PC address in **Nuvio PC** in the Lights
window.

## Files

Settings, the log and the readings kept while dimmed are in `%APPDATA%\NuvioLights`.

## Building

`lights/` is a .NET 8 solution: `NuvioLights.Core` (Nuvio link, WiZ and Govee LAN, the dim rules), `NuvioLights`
(the WinForms tray app) and `tests/NuvioLights.Tests` (xunit, including a pretend Nuvio, WiZ bulb and Govee light on
loopback). `dotnet test tests/NuvioLights.Tests` runs on any OS; the `nuvio-lights.yml` workflow publishes the
single-file `NuvioLights.exe` on every push to Spyder that touches `lights/`.

Wire formats: Nuvio's phone-remote protocol is described at the top of
`composeApp/src/desktopMain/kotlin/com/nuvio/app/features/remote/PhoneRemoteProtocol.kt` (Nuvio Lights uses
discovery, HELLO/PAIR, SUBSCRIBE, PING and the STATE/IDLE lines). WiZ: UDP 38899, `getPilot`/`setPilot`. Govee LAN:
scan to 239.255.255.250:4001, answers on 4002, commands to the light on 4003.
