# Scriptica Mod Usage

## What this mod adds
- A hotkey-opened in-game GUI to edit, save, load, and run Scriptica scripts.
- A small scripting language focused on client-side automation.

## Open the GUI
- Default hotkey: **`** (backtick / grave accent)
- You can change it in **Options → Controls**

## Save / Load
- Scripts are saved by name as `*.sca` in:
  - `.minecraft/config/scriptica/scripts/`

## Running scripts
- Press **Run** to execute the current editor contents.
- Press **Stop** to request cancellation.

## Events
- `onTick(fn)` runs `fn()` every client tick.
- `onChat(fn)` runs `fn(message)` for incoming game chat/messages.
- `on(name, fn)` / `emit(name, payload)` lets scripts define custom events.
- `off(handle)` removes an event subscription.

## Notes
- `cmd("...")` sends a slash command via chat; it only works if your server/world allows the command.
- This is a client-side mod: it automates *your client*.
- If you get stuck moving/attacking, press **Stop** (it releases keys).

